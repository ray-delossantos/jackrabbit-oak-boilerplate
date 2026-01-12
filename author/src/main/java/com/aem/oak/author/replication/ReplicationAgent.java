package com.aem.oak.author.replication;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Replication Agent that processes the replication queue and sends content to publish instances.
 */
@Component(service = ReplicationAgent.class, immediate = true)
@Designate(ocd = ReplicationAgent.Config.class)
public class ReplicationAgent {

    private static final Logger LOG = LoggerFactory.getLogger(ReplicationAgent.class);

    @ObjectClassDefinition(name = "AEM Oak Replication Agent Configuration")
    public @interface Config {
        @AttributeDefinition(name = "Enabled", description = "Enable replication agent")
        boolean enabled() default true;

        @AttributeDefinition(name = "Publish Endpoints", description = "URLs of publish instances (comma-separated)")
        String publishEndpoints() default "http://aem-publish:80";

        @AttributeDefinition(name = "Replication Path", description = "Path on publish for receiving replicated content")
        String replicationPath() default "/bin/replicate";

        @AttributeDefinition(name = "Auth Token", description = "Authentication token for replication")
        String authToken() default "";

        @AttributeDefinition(name = "Poll Interval (ms)", description = "Queue polling interval in milliseconds")
        long pollIntervalMs() default 1000;

        @AttributeDefinition(name = "Connection Timeout (ms)", description = "HTTP connection timeout")
        int connectionTimeoutMs() default 5000;

        @AttributeDefinition(name = "Request Timeout (ms)", description = "HTTP request timeout")
        int requestTimeoutMs() default 30000;

        @AttributeDefinition(name = "Max Connections", description = "Maximum HTTP connections")
        int maxConnections() default 20;

        @AttributeDefinition(name = "Worker Threads", description = "Number of worker threads for parallel replication")
        int workerThreads() default 4;
    }

    @Reference
    private ReplicationQueue queue;

    private Config config;
    private CloseableHttpClient httpClient;
    private ScheduledExecutorService scheduler;
    private ExecutorService workers;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private List<String> publishEndpoints;

    @Activate
    protected void activate(Config config) {
        this.config = config;
        this.publishEndpoints = parseEndpoints(config.publishEndpoints());

        if (!config.enabled()) {
            LOG.info("Replication agent is disabled");
            return;
        }

        // Create HTTP client with connection pooling
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(config.maxConnections());
        connectionManager.setDefaultMaxPerRoute(config.maxConnections() / Math.max(1, publishEndpoints.size()));

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(config.connectionTimeoutMs()))
                .setResponseTimeout(Timeout.ofMilliseconds(config.requestTimeoutMs()))
                .build();

        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        // Create worker thread pool
        this.workers = Executors.newFixedThreadPool(config.workerThreads());

        // Start queue processor
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.running.set(true);

        scheduler.scheduleWithFixedDelay(
                this::processQueue,
                0,
                config.pollIntervalMs(),
                TimeUnit.MILLISECONDS);

        LOG.info("Replication agent activated with {} publish endpoints and {} worker threads",
                publishEndpoints.size(), config.workerThreads());
    }

    @Deactivate
    protected void deactivate() {
        running.set(false);

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (workers != null) {
            workers.shutdown();
            try {
                if (!workers.awaitTermination(30, TimeUnit.SECONDS)) {
                    workers.shutdownNow();
                }
            } catch (InterruptedException e) {
                workers.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                LOG.warn("Error closing HTTP client", e);
            }
        }

        LOG.info("Replication agent deactivated");
    }

    @Modified
    protected void modified(Config config) {
        deactivate();
        activate(config);
    }

    /**
     * Process items from the replication queue.
     */
    private void processQueue() {
        if (!running.get()) {
            return;
        }

        try {
            Optional<ReplicationQueue.QueueItem> optItem = queue.poll();
            while (optItem.isPresent() && running.get()) {
                ReplicationQueue.QueueItem item = optItem.get();

                // Submit replication task to worker pool
                workers.submit(() -> replicateItem(item));

                // Get next item
                optItem = queue.poll();
            }
        } catch (Exception e) {
            LOG.error("Error processing replication queue", e);
        }
    }

    /**
     * Replicate a single item to all publish endpoints.
     */
    private void replicateItem(ReplicationQueue.QueueItem item) {
        if (!running.get()) {
            return;
        }

        LOG.debug("Replicating {} (action={})", item.getPath(), item.getAction());

        List<String> endpoints = item.getEndpoints().isEmpty() ? publishEndpoints : item.getEndpoints();
        boolean allSucceeded = true;
        Exception lastError = null;
        String failedEndpoint = null;

        for (String endpoint : endpoints) {
            try {
                replicateToEndpoint(item, endpoint);
                LOG.debug("Successfully replicated {} to {}", item.getPath(), endpoint);
            } catch (Exception e) {
                allSucceeded = false;
                lastError = e;
                failedEndpoint = endpoint;
                LOG.warn("Failed to replicate {} to {}: {}", item.getPath(), endpoint, e.getMessage());
            }
        }

        if (allSucceeded) {
            queue.markCompleted(item);
        } else {
            queue.markFailed(item, failedEndpoint, lastError);
        }
    }

    /**
     * Send replication request to a single endpoint.
     */
    private void replicateToEndpoint(ReplicationQueue.QueueItem item, String endpoint) throws IOException {
        String url = endpoint + config.replicationPath();

        HttpPost request = new HttpPost(url);

        // Add authentication header
        if (config.authToken() != null && !config.authToken().isEmpty()) {
            request.setHeader("X-Replication-Token", config.authToken());
        }

        // Add metadata headers
        request.setHeader("X-Replication-Path", item.getPath());
        request.setHeader("X-Replication-Action", item.getAction().name());
        request.setHeader("X-Replication-Time", String.valueOf(System.currentTimeMillis()));

        // Set request body
        if (item.getPackageData() != null) {
            request.setEntity(new ByteArrayEntity(
                    item.getPackageData(),
                    ContentType.APPLICATION_OCTET_STREAM));
        }

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();

            if (statusCode >= 200 && statusCode < 300) {
                LOG.debug("Replication successful to {}: status={}", endpoint, statusCode);
            } else {
                String body = EntityUtils.toString(response.getEntity());
                throw new IOException("Replication failed with status " + statusCode + ": " + body);
            }
        }
    }

    /**
     * Manually trigger replication of a specific path.
     */
    public void triggerReplication(String path) {
        LOG.info("Manually triggered replication for: {}", path);
        // This would be called from ReplicationService to add items to queue
    }

    /**
     * Get list of configured publish endpoints.
     */
    public List<String> getPublishEndpoints() {
        return new ArrayList<>(publishEndpoints);
    }

    /**
     * Check if agent is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Check connectivity to all publish endpoints.
     */
    public List<EndpointStatus> checkEndpoints() {
        List<EndpointStatus> statuses = new ArrayList<>();

        for (String endpoint : publishEndpoints) {
            EndpointStatus status = new EndpointStatus(endpoint);
            try {
                HttpPost request = new HttpPost(endpoint + "/system/health");
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    status.setStatusCode(response.getCode());
                    status.setReachable(response.getCode() >= 200 && response.getCode() < 300);
                }
            } catch (Exception e) {
                status.setReachable(false);
                status.setError(e.getMessage());
            }
            statuses.add(status);
        }

        return statuses;
    }

    private List<String> parseEndpoints(String endpoints) {
        if (endpoints == null || endpoints.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(endpoints.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Status of a publish endpoint.
     */
    public static class EndpointStatus {
        private final String endpoint;
        private boolean reachable;
        private int statusCode;
        private String error;

        public EndpointStatus(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getEndpoint() { return endpoint; }
        public boolean isReachable() { return reachable; }
        public void setReachable(boolean reachable) { this.reachable = reachable; }
        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}

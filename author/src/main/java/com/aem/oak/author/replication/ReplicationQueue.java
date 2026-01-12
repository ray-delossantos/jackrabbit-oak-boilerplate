package com.aem.oak.author.replication;

import com.aem.oak.api.replication.ReplicationService.ReplicationAction;
import com.aem.oak.api.replication.ReplicationService.ReplicationStatus;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Queue for managing replication requests.
 * Provides persistent queue with retry logic and status tracking.
 */
@Component(service = ReplicationQueue.class, immediate = true)
@Designate(ocd = ReplicationQueue.Config.class)
public class ReplicationQueue {

    private static final Logger LOG = LoggerFactory.getLogger(ReplicationQueue.class);

    @ObjectClassDefinition(name = "AEM Oak Replication Queue Configuration")
    public @interface Config {
        @AttributeDefinition(name = "Max Queue Size", description = "Maximum number of items in queue")
        int maxQueueSize() default 10000;

        @AttributeDefinition(name = "Max Retries", description = "Maximum retry attempts per item")
        int maxRetries() default 5;

        @AttributeDefinition(name = "Retry Delay (ms)", description = "Base delay between retries in milliseconds")
        long retryDelayMs() default 5000;

        @AttributeDefinition(name = "Retry Backoff Multiplier", description = "Exponential backoff multiplier")
        double retryBackoffMultiplier() default 2.0;
    }

    private final PriorityBlockingQueue<QueueItem> queue;
    private final Map<String, QueueItem> itemsById;
    private final Map<String, QueueItem> itemsByPath;
    private Config config;

    public ReplicationQueue() {
        this.queue = new PriorityBlockingQueue<>();
        this.itemsById = new ConcurrentHashMap<>();
        this.itemsByPath = new ConcurrentHashMap<>();
    }

    @Activate
    @Modified
    protected void activate(Config config) {
        this.config = config;
        LOG.info("Replication queue activated with maxSize={}, maxRetries={}",
                config.maxQueueSize(), config.maxRetries());
    }

    @Deactivate
    protected void deactivate() {
        LOG.info("Replication queue deactivated, {} items remaining", queue.size());
    }

    /**
     * Add a replication request to the queue.
     */
    public QueueItem add(ReplicationRequest request) {
        if (queue.size() >= config.maxQueueSize()) {
            throw new IllegalStateException("Replication queue is full (max: " + config.maxQueueSize() + ")");
        }

        // Check if there's already a pending request for this path
        QueueItem existing = itemsByPath.get(request.getPath());
        if (existing != null && existing.getStatus() == QueueItemStatus.PENDING) {
            // Merge: keep the newer request
            if (request.getAction() == ReplicationAction.DELETE) {
                // Delete supersedes all other actions
                existing.setAction(ReplicationAction.DELETE);
                existing.setPackageData(null);
            } else if (existing.getAction() != ReplicationAction.DELETE) {
                // Update with newer package
                existing.setPackageData(request.getPackageData());
            }
            LOG.debug("Merged replication request for path: {}", request.getPath());
            return existing;
        }

        QueueItem item = new QueueItem(request);
        queue.add(item);
        itemsById.put(item.getId(), item);
        itemsByPath.put(request.getPath(), item);

        LOG.debug("Added replication request to queue: {} (action={})",
                request.getPath(), request.getAction());

        return item;
    }

    /**
     * Get the next item from the queue.
     */
    public Optional<QueueItem> poll() {
        while (true) {
            QueueItem item = queue.poll();
            if (item == null) {
                return Optional.empty();
            }

            // Check if ready for processing
            if (item.getNextAttemptTime() > System.currentTimeMillis()) {
                // Not ready yet, put it back
                queue.add(item);
                return Optional.empty();
            }

            if (item.getStatus() == QueueItemStatus.PENDING ||
                item.getStatus() == QueueItemStatus.RETRY) {
                item.setStatus(QueueItemStatus.PROCESSING);
                return Optional.of(item);
            }

            // Item was cancelled or already completed, skip it
        }
    }

    /**
     * Peek at the next item without removing it.
     */
    public Optional<QueueItem> peek() {
        QueueItem item = queue.peek();
        return Optional.ofNullable(item);
    }

    /**
     * Mark an item as successfully completed.
     */
    public void markCompleted(QueueItem item) {
        item.setStatus(QueueItemStatus.COMPLETED);
        item.setCompletedTime(System.currentTimeMillis());
        itemsByPath.remove(item.getPath());
        LOG.debug("Replication completed: {}", item.getPath());
    }

    /**
     * Mark an item as failed and schedule retry if possible.
     */
    public void markFailed(QueueItem item, String endpoint, Exception error) {
        item.incrementAttempts();
        item.setLastError(error.getMessage());
        item.recordEndpointFailure(endpoint, error.getMessage());

        if (item.getAttempts() < config.maxRetries()) {
            // Schedule retry with exponential backoff
            long delay = (long) (config.retryDelayMs() *
                    Math.pow(config.retryBackoffMultiplier(), item.getAttempts() - 1));
            item.setNextAttemptTime(System.currentTimeMillis() + delay);
            item.setStatus(QueueItemStatus.RETRY);
            queue.add(item);

            LOG.warn("Replication failed for {}, scheduling retry {} of {} in {}ms: {}",
                    item.getPath(), item.getAttempts(), config.maxRetries(), delay, error.getMessage());
        } else {
            // Max retries exceeded
            item.setStatus(QueueItemStatus.FAILED);
            item.setCompletedTime(System.currentTimeMillis());
            itemsByPath.remove(item.getPath());

            LOG.error("Replication permanently failed for {} after {} attempts: {}",
                    item.getPath(), item.getAttempts(), error.getMessage());
        }
    }

    /**
     * Cancel a pending replication request.
     */
    public boolean cancel(String itemId) {
        QueueItem item = itemsById.get(itemId);
        if (item != null && item.getStatus() == QueueItemStatus.PENDING) {
            item.setStatus(QueueItemStatus.CANCELLED);
            itemsByPath.remove(item.getPath());
            LOG.debug("Cancelled replication request: {}", item.getPath());
            return true;
        }
        return false;
    }

    /**
     * Get queue statistics.
     */
    public QueueStats getStats() {
        int pending = 0;
        int processing = 0;
        int retry = 0;
        int failed = 0;
        int completed = 0;

        for (QueueItem item : itemsById.values()) {
            switch (item.getStatus()) {
                case PENDING:
                    pending++;
                    break;
                case PROCESSING:
                    processing++;
                    break;
                case RETRY:
                    retry++;
                    break;
                case FAILED:
                    failed++;
                    break;
                case COMPLETED:
                    completed++;
                    break;
            }
        }

        return new QueueStats(pending, processing, retry, failed, completed);
    }

    /**
     * Get all items in the queue.
     */
    public List<QueueItem> getItems() {
        return new ArrayList<>(itemsById.values());
    }

    /**
     * Get an item by ID.
     */
    public Optional<QueueItem> getItem(String itemId) {
        return Optional.ofNullable(itemsById.get(itemId));
    }

    /**
     * Current queue size.
     */
    public int size() {
        return queue.size();
    }

    /**
     * Clear completed and failed items older than the specified age.
     */
    public int cleanup(long maxAgeMs) {
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        AtomicInteger removed = new AtomicInteger(0);

        itemsById.entrySet().removeIf(entry -> {
            QueueItem item = entry.getValue();
            if ((item.getStatus() == QueueItemStatus.COMPLETED ||
                 item.getStatus() == QueueItemStatus.FAILED ||
                 item.getStatus() == QueueItemStatus.CANCELLED) &&
                item.getCompletedTime() < cutoff) {
                removed.incrementAndGet();
                return true;
            }
            return false;
        });

        return removed.get();
    }

    /**
     * Replication request to be queued.
     */
    public static class ReplicationRequest {
        private final String path;
        private ReplicationAction action;
        private byte[] packageData;
        private final List<String> endpoints;
        private final long createdTime;

        public ReplicationRequest(String path, ReplicationAction action, byte[] packageData, List<String> endpoints) {
            this.path = path;
            this.action = action;
            this.packageData = packageData;
            this.endpoints = endpoints;
            this.createdTime = System.currentTimeMillis();
        }

        public String getPath() { return path; }
        public ReplicationAction getAction() { return action; }
        public void setAction(ReplicationAction action) { this.action = action; }
        public byte[] getPackageData() { return packageData; }
        public void setPackageData(byte[] packageData) { this.packageData = packageData; }
        public List<String> getEndpoints() { return endpoints; }
        public long getCreatedTime() { return createdTime; }
    }

    /**
     * Item in the replication queue.
     */
    public static class QueueItem implements Comparable<QueueItem> {
        private final String id;
        private final String path;
        private ReplicationAction action;
        private byte[] packageData;
        private final List<String> endpoints;
        private final long createdTime;
        private QueueItemStatus status;
        private int attempts;
        private long nextAttemptTime;
        private long completedTime;
        private String lastError;
        private final Map<String, String> endpointErrors;

        public QueueItem(ReplicationRequest request) {
            this.id = UUID.randomUUID().toString();
            this.path = request.getPath();
            this.action = request.getAction();
            this.packageData = request.getPackageData();
            this.endpoints = new ArrayList<>(request.getEndpoints());
            this.createdTime = request.getCreatedTime();
            this.status = QueueItemStatus.PENDING;
            this.attempts = 0;
            this.nextAttemptTime = System.currentTimeMillis();
            this.endpointErrors = new ConcurrentHashMap<>();
        }

        public String getId() { return id; }
        public String getPath() { return path; }
        public ReplicationAction getAction() { return action; }
        public void setAction(ReplicationAction action) { this.action = action; }
        public byte[] getPackageData() { return packageData; }
        public void setPackageData(byte[] packageData) { this.packageData = packageData; }
        public List<String> getEndpoints() { return endpoints; }
        public long getCreatedTime() { return createdTime; }
        public QueueItemStatus getStatus() { return status; }
        public void setStatus(QueueItemStatus status) { this.status = status; }
        public int getAttempts() { return attempts; }
        public void incrementAttempts() { this.attempts++; }
        public long getNextAttemptTime() { return nextAttemptTime; }
        public void setNextAttemptTime(long nextAttemptTime) { this.nextAttemptTime = nextAttemptTime; }
        public long getCompletedTime() { return completedTime; }
        public void setCompletedTime(long completedTime) { this.completedTime = completedTime; }
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }

        public void recordEndpointFailure(String endpoint, String error) {
            endpointErrors.put(endpoint, error);
        }

        public Map<String, String> getEndpointErrors() { return endpointErrors; }

        @Override
        public int compareTo(QueueItem other) {
            // Priority: earlier next attempt time first
            return Long.compare(this.nextAttemptTime, other.nextAttemptTime);
        }
    }

    /**
     * Status of a queue item.
     */
    public enum QueueItemStatus {
        PENDING,
        PROCESSING,
        RETRY,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Queue statistics.
     */
    public static class QueueStats {
        private final int pending;
        private final int processing;
        private final int retry;
        private final int failed;
        private final int completed;

        public QueueStats(int pending, int processing, int retry, int failed, int completed) {
            this.pending = pending;
            this.processing = processing;
            this.retry = retry;
            this.failed = failed;
            this.completed = completed;
        }

        public int getPending() { return pending; }
        public int getProcessing() { return processing; }
        public int getRetry() { return retry; }
        public int getFailed() { return failed; }
        public int getCompleted() { return completed; }
        public int getTotal() { return pending + processing + retry + failed + completed; }
    }
}

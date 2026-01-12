package com.aem.oak.author.health;

import com.aem.oak.api.replication.ReplicationService;
import com.aem.oak.author.replication.ReplicationQueue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check servlet for Kubernetes probes and monitoring.
 * Provides liveness, readiness, and full health status.
 */
@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.paths=/system/health",
        "sling.servlet.methods=GET"
    }
)
public class HealthServlet extends SlingSafeMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(HealthServlet.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Reference
    private Repository repository;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private ReplicationService replicationService;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private ReplicationQueue replicationQueue;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        String type = request.getParameter("type");

        if ("liveness".equals(type)) {
            handleLiveness(response);
        } else if ("readiness".equals(type)) {
            handleReadiness(response);
        } else {
            handleFullHealth(response);
        }
    }

    private void handleLiveness(SlingHttpServletResponse response) throws IOException {
        // Simple liveness check - just verify JVM is running
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("check", "liveness");

        response.setContentType("application/json");
        response.setStatus(200);
        OBJECT_MAPPER.writeValue(response.getOutputStream(), result);
    }

    private void handleReadiness(SlingHttpServletResponse response) throws IOException {
        Map<String, Object> result = new HashMap<>();
        boolean healthy = true;

        // Check repository connectivity
        try {
            Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            session.logout();
            result.put("repository", "ok");
        } catch (Exception e) {
            result.put("repository", "error: " + e.getMessage());
            healthy = false;
        }

        result.put("status", healthy ? "ok" : "unhealthy");
        result.put("check", "readiness");

        response.setContentType("application/json");
        response.setStatus(healthy ? 200 : 503);
        OBJECT_MAPPER.writeValue(response.getOutputStream(), result);
    }

    private void handleFullHealth(SlingHttpServletResponse response) throws IOException {
        Map<String, Object> result = new HashMap<>();
        boolean healthy = true;

        // Basic info
        result.put("status", "running");
        result.put("instance", "author");
        result.put("clusterId", System.getProperty("oak.cluster.id", "unknown"));

        // Repository check
        Map<String, Object> repositoryStatus = new HashMap<>();
        try {
            Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            repositoryStatus.put("status", "ok");
            repositoryStatus.put("workspace", session.getWorkspace().getName());

            // Get Oak cluster info if available
            if (session.nodeExists("/oak:index")) {
                repositoryStatus.put("indexHealth", "ok");
            }

            session.logout();
        } catch (Exception e) {
            repositoryStatus.put("status", "error");
            repositoryStatus.put("error", e.getMessage());
            healthy = false;
        }
        result.put("repository", repositoryStatus);

        // Replication status
        if (replicationService != null) {
            try {
                Map<String, Object> queueStatus = replicationService.getQueueStatus();
                result.put("replication", queueStatus);
            } catch (Exception e) {
                Map<String, Object> repError = new HashMap<>();
                repError.put("status", "error");
                repError.put("error", e.getMessage());
                result.put("replication", repError);
            }
        }

        // Queue status
        if (replicationQueue != null) {
            ReplicationQueue.QueueStats stats = replicationQueue.getStats();
            Map<String, Object> queueStats = new HashMap<>();
            queueStats.put("pending", stats.getPending());
            queueStats.put("processing", stats.getProcessing());
            queueStats.put("retry", stats.getRetry());
            queueStats.put("failed", stats.getFailed());
            queueStats.put("completed", stats.getCompleted());
            result.put("queue", queueStats);
        }

        // JVM stats
        Map<String, Object> jvmStats = new HashMap<>();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        jvmStats.put("uptime", runtimeMXBean.getUptime());
        jvmStats.put("heapUsed", memoryMXBean.getHeapMemoryUsage().getUsed());
        jvmStats.put("heapMax", memoryMXBean.getHeapMemoryUsage().getMax());
        jvmStats.put("nonHeapUsed", memoryMXBean.getNonHeapMemoryUsage().getUsed());
        jvmStats.put("processors", Runtime.getRuntime().availableProcessors());

        // Calculate heap usage percentage
        long heapUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryMXBean.getHeapMemoryUsage().getMax();
        double heapUsagePercent = (double) heapUsed / heapMax * 100;
        jvmStats.put("heapUsagePercent", String.format("%.2f%%", heapUsagePercent));

        result.put("jvm", jvmStats);

        // Environment info
        Map<String, Object> env = new HashMap<>();
        env.put("javaVersion", System.getProperty("java.version"));
        env.put("osName", System.getProperty("os.name"));
        env.put("timezone", System.getProperty("user.timezone"));
        result.put("environment", env);

        result.put("healthy", healthy);

        response.setContentType("application/json");
        response.setStatus(healthy ? 200 : 503);
        OBJECT_MAPPER.writeValue(response.getOutputStream(), result);
    }
}

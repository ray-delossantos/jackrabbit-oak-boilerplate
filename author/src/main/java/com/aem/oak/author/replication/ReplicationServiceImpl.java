package com.aem.oak.author.replication;

import com.aem.oak.api.replication.ReplicationService;
import com.aem.oak.core.repository.JcrSessionFactory;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of ReplicationService for Author instance.
 * Handles content replication from Author to Publish instances.
 */
@Component(service = ReplicationService.class, immediate = true)
@Designate(ocd = ReplicationServiceImpl.Config.class)
public class ReplicationServiceImpl implements ReplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(ReplicationServiceImpl.class);

    @ObjectClassDefinition(name = "AEM Oak Replication Service Configuration")
    public @interface Config {
        @AttributeDefinition(name = "Enabled", description = "Enable replication service")
        boolean enabled() default true;

        @AttributeDefinition(name = "Author ID", description = "Unique identifier for this author instance")
        String authorId() default "author-1";

        @AttributeDefinition(name = "Auto Replicate Paths", description = "Paths to auto-replicate on save")
        String[] autoReplicatePaths() default {};
    }

    @Reference
    private JcrSessionFactory sessionFactory;

    @Reference
    private ReplicationQueue replicationQueue;

    @Reference
    private ReplicationAgent replicationAgent;

    private Config config;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.config = config;
        LOG.info("Replication service activated, enabled={}, authorId={}",
                config.enabled(), config.authorId());
    }

    @Override
    public void replicate(String path, ReplicationAction action) {
        if (!config.enabled()) {
            LOG.debug("Replication disabled, skipping: {}", path);
            return;
        }

        try {
            ContentPackage pkg;

            if (action == ReplicationAction.DELETE || action == ReplicationAction.DEACTIVATE) {
                pkg = ContentPackage.createDelete(path, config.authorId());
            } else {
                // Get node and create package
                Node node = sessionFactory.doWithSession(session -> {
                    if (!session.nodeExists(path)) {
                        throw new RepositoryException("Node not found: " + path);
                    }
                    return session.getNode(path);
                });

                pkg = ContentPackage.create(node, action, config.authorId());
            }

            // Add to replication queue
            byte[] packageData = pkg.toBytes();
            ReplicationQueue.ReplicationRequest request = new ReplicationQueue.ReplicationRequest(
                    path,
                    action,
                    packageData,
                    replicationAgent.getPublishEndpoints()
            );

            ReplicationQueue.QueueItem item = replicationQueue.add(request);

            LOG.info("Queued replication: path={}, action={}, packageId={}, queueItemId={}",
                    path, action, pkg.getId(), item.getId());

        } catch (RepositoryException | IOException e) {
            LOG.error("Failed to queue replication for {}: {}", path, e.getMessage(), e);
            throw new RuntimeException("Replication failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void replicateTree(String rootPath, ReplicationAction action) {
        if (!config.enabled()) {
            LOG.debug("Replication disabled, skipping tree: {}", rootPath);
            return;
        }

        try {
            List<String> paths = sessionFactory.doWithSession(session -> {
                List<String> result = new ArrayList<>();
                collectPaths(session, rootPath, result);
                return result;
            });

            LOG.info("Replicating tree {} with {} nodes", rootPath, paths.size());

            // Replicate in order (parent before children for activate, reverse for delete)
            if (action == ReplicationAction.DELETE || action == ReplicationAction.DEACTIVATE) {
                // Delete children first
                for (int i = paths.size() - 1; i >= 0; i--) {
                    replicate(paths.get(i), action);
                }
            } else {
                // Activate parent first
                for (String path : paths) {
                    replicate(path, action);
                }
            }

        } catch (RepositoryException e) {
            LOG.error("Failed to replicate tree {}: {}", rootPath, e.getMessage(), e);
            throw new RuntimeException("Tree replication failed: " + e.getMessage(), e);
        }
    }

    private void collectPaths(Session session, String path, List<String> paths) throws RepositoryException {
        if (!session.nodeExists(path)) {
            return;
        }

        paths.add(path);

        Node node = session.getNode(path);
        var children = node.getNodes();
        while (children.hasNext()) {
            Node child = children.nextNode();
            // Skip system nodes
            if (!child.getName().startsWith("jcr:") && !child.getName().startsWith("rep:")) {
                collectPaths(session, child.getPath(), paths);
            }
        }
    }

    @Override
    public ReplicationStatus getReplicationStatus(String path) {
        // Check queue for pending/processing items
        for (ReplicationQueue.QueueItem item : replicationQueue.getItems()) {
            if (item.getPath().equals(path)) {
                switch (item.getStatus()) {
                    case PENDING:
                    case PROCESSING:
                    case RETRY:
                        return ReplicationStatus.PENDING;
                    case FAILED:
                        return ReplicationStatus.ERROR;
                    case COMPLETED:
                        return ReplicationStatus.REPLICATED;
                }
            }
        }

        // Check if node has replication metadata
        try {
            return sessionFactory.doWithSession(session -> {
                if (!session.nodeExists(path)) {
                    return ReplicationStatus.NOT_REPLICATED;
                }

                Node node = session.getNode(path);
                if (node.hasProperty("cq:lastReplicated")) {
                    return ReplicationStatus.REPLICATED;
                }

                return ReplicationStatus.NOT_REPLICATED;
            });
        } catch (RepositoryException e) {
            LOG.warn("Error checking replication status for {}: {}", path, e.getMessage());
            return ReplicationStatus.NOT_REPLICATED;
        }
    }

    @Override
    public List<String> getPublishEndpoints() {
        return replicationAgent.getPublishEndpoints();
    }

    @Override
    public Map<String, Object> getQueueStatus() {
        ReplicationQueue.QueueStats stats = replicationQueue.getStats();

        Map<String, Object> status = new HashMap<>();
        status.put("enabled", config.enabled());
        status.put("authorId", config.authorId());
        status.put("queueSize", replicationQueue.size());
        status.put("pending", stats.getPending());
        status.put("processing", stats.getProcessing());
        status.put("retry", stats.getRetry());
        status.put("failed", stats.getFailed());
        status.put("completed", stats.getCompleted());
        status.put("total", stats.getTotal());

        // Endpoint status
        List<Map<String, Object>> endpoints = new ArrayList<>();
        for (ReplicationAgent.EndpointStatus es : replicationAgent.checkEndpoints()) {
            Map<String, Object> ep = new HashMap<>();
            ep.put("url", es.getEndpoint());
            ep.put("reachable", es.isReachable());
            ep.put("statusCode", es.getStatusCode());
            if (es.getError() != null) {
                ep.put("error", es.getError());
            }
            endpoints.add(ep);
        }
        status.put("endpoints", endpoints);

        return status;
    }

    /**
     * Mark a node as replicated (called after successful replication).
     */
    public void markReplicated(String path) {
        try {
            sessionFactory.doWithSessionAndSave(session -> {
                if (session.nodeExists(path)) {
                    Node node = session.getNode(path);
                    node.setProperty("cq:lastReplicated", java.util.Calendar.getInstance());
                    node.setProperty("cq:lastReplicatedBy", config.authorId());
                    node.setProperty("cq:lastReplicationAction", "Activate");
                }
                return null;
            });
        } catch (RepositoryException e) {
            LOG.warn("Failed to mark node as replicated: {}", path, e);
        }
    }

    /**
     * Mark a node as deactivated.
     */
    public void markDeactivated(String path) {
        try {
            sessionFactory.doWithSessionAndSave(session -> {
                if (session.nodeExists(path)) {
                    Node node = session.getNode(path);
                    node.setProperty("cq:lastReplicated", java.util.Calendar.getInstance());
                    node.setProperty("cq:lastReplicatedBy", config.authorId());
                    node.setProperty("cq:lastReplicationAction", "Deactivate");
                }
                return null;
            });
        } catch (RepositoryException e) {
            LOG.warn("Failed to mark node as deactivated: {}", path, e);
        }
    }
}

package com.aem.oak.api.replication;

import javax.jcr.RepositoryException;
import java.util.List;

/**
 * Service interface for content replication between Author and Publish tiers.
 * Handles activation, deactivation, and content synchronization.
 */
public interface ReplicationService {

    /**
     * Replication action types.
     */
    enum ReplicationAction {
        /** Publish content to Publish tier */
        ACTIVATE,
        /** Remove content from Publish tier */
        DEACTIVATE,
        /** Delete content from Publish tier */
        DELETE,
        /** Test replication connectivity */
        TEST
    }

    /**
     * Replication status types.
     */
    enum ReplicationStatus {
        /** Replication not attempted */
        NOT_REPLICATED,
        /** Replication in progress */
        PENDING,
        /** Replication successful */
        ACTIVATED,
        /** Content deactivated */
        DEACTIVATED,
        /** Replication failed */
        FAILED
    }

    /**
     * Replicates content to all configured Publish instances.
     *
     * @param path   the JCR path to replicate
     * @param action the replication action
     * @throws RepositoryException if replication fails
     */
    void replicate(String path, ReplicationAction action) throws RepositoryException;

    /**
     * Replicates content to a specific Publish instance.
     *
     * @param path      the JCR path to replicate
     * @param action    the replication action
     * @param agentId   the replication agent ID
     * @throws RepositoryException if replication fails
     */
    void replicate(String path, ReplicationAction action, String agentId)
            throws RepositoryException;

    /**
     * Replicates a tree of content recursively.
     *
     * @param path      the root JCR path
     * @param action    the replication action
     * @param recursive whether to include all descendants
     * @throws RepositoryException if replication fails
     */
    void replicateTree(String path, ReplicationAction action, boolean recursive)
            throws RepositoryException;

    /**
     * Gets the replication status for a path.
     *
     * @param path the JCR path to check
     * @return the current ReplicationStatus
     * @throws RepositoryException if status check fails
     */
    ReplicationStatus getReplicationStatus(String path) throws RepositoryException;

    /**
     * Gets all pending replication requests in the queue.
     *
     * @return list of ReplicationRequest objects
     */
    List<ReplicationRequest> getPendingRequests();

    /**
     * Retries failed replication requests.
     *
     * @param path the JCR path to retry
     * @throws RepositoryException if retry fails
     */
    void retryReplication(String path) throws RepositoryException;

    /**
     * Cancels pending replication for a path.
     *
     * @param path the JCR path to cancel
     */
    void cancelReplication(String path);

    /**
     * Gets all configured replication agents.
     *
     * @return list of ReplicationAgent configurations
     */
    List<ReplicationAgent> getAgents();

    /**
     * Tests connectivity to a replication agent.
     *
     * @param agentId the agent ID to test
     * @return true if connection successful, false otherwise
     */
    boolean testAgent(String agentId);

    /**
     * Represents a replication request in the queue.
     */
    interface ReplicationRequest {
        String getPath();
        ReplicationAction getAction();
        ReplicationStatus getStatus();
        long getTimestamp();
        String getAgentId();
        String getErrorMessage();
        int getRetryCount();
    }

    /**
     * Represents a replication agent configuration.
     */
    interface ReplicationAgent {
        String getId();
        String getName();
        String getEndpoint();
        boolean isEnabled();
        String getTransportType();
    }
}

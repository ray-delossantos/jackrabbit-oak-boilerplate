package com.aem.oak.core.repository;

import com.aem.oak.core.datasource.PostgresDataSourceFactory;
import com.aem.oak.core.blobstore.S3BlobStoreFactory;
import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.plugins.document.DocumentNodeStore;
import org.apache.jackrabbit.oak.plugins.document.rdb.RDBDocumentNodeStoreBuilder;
import org.apache.jackrabbit.oak.plugins.blob.datastore.DataStoreBlobStore;
import org.apache.jackrabbit.oak.spi.blob.BlobStore;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.security.SecurityProviderImpl;
import org.apache.jackrabbit.oak.InitialContent;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Repository;
import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OSGi component that initializes the Apache Jackrabbit Oak repository
 * with PostgreSQL (RDBDocumentStore) and S3 (MinIO) blob storage.
 */
@Component(
    service = OakRepositoryInitializer.class,
    immediate = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = OakRepositoryConfig.class)
public class OakRepositoryInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(OakRepositoryInitializer.class);

    private final AtomicReference<Repository> repositoryRef = new AtomicReference<>();
    private final AtomicReference<DocumentNodeStore> nodeStoreRef = new AtomicReference<>();

    @Reference
    private PostgresDataSourceFactory dataSourceFactory;

    @Reference
    private S3BlobStoreFactory blobStoreFactory;

    private OakRepositoryConfig config;

    @Activate
    protected void activate(OakRepositoryConfig config) {
        this.config = config;
        LOG.info("Activating Oak Repository Initializer with cluster ID: {}", config.clusterId());
        initializeRepository();
    }

    @Deactivate
    protected void deactivate() {
        LOG.info("Deactivating Oak Repository Initializer");
        shutdownRepository();
    }

    @Modified
    protected void modified(OakRepositoryConfig config) {
        LOG.info("Configuration modified, reinitializing repository");
        this.config = config;
        shutdownRepository();
        initializeRepository();
    }

    /**
     * Initializes the Oak repository with PostgreSQL and S3 storage.
     */
    private void initializeRepository() {
        try {
            LOG.info("Initializing Oak repository...");

            // Get PostgreSQL DataSource
            DataSource dataSource = dataSourceFactory.createDataSource();
            LOG.info("PostgreSQL DataSource created successfully");

            // Create S3 BlobStore
            BlobStore blobStore = blobStoreFactory.createBlobStore();
            LOG.info("S3 BlobStore created successfully");

            // Build DocumentNodeStore with RDB (PostgreSQL)
            DocumentNodeStore nodeStore = RDBDocumentNodeStoreBuilder.newRDBDocumentNodeStoreBuilder()
                    .setRDBConnection(dataSource)
                    .setClusterId(getClusterId())
                    .setBlobStore(blobStore)
                    .setLeaseCheckMode(DocumentNodeStore.LeaseCheckMode.STRICT)
                    .build();
            nodeStoreRef.set(nodeStore);
            LOG.info("DocumentNodeStore created with cluster ID: {}", getClusterId());

            // Create security provider
            SecurityProvider securityProvider = new SecurityProviderImpl();

            // Build Oak instance
            Oak oak = new Oak(nodeStore)
                    .with(new InitialContent())
                    .with(securityProvider);

            // Create JCR Repository
            Repository repository = new Jcr(oak)
                    .with(securityProvider)
                    .createRepository();
            repositoryRef.set(repository);

            LOG.info("Oak Repository initialized successfully");

        } catch (Exception e) {
            LOG.error("Failed to initialize Oak repository", e);
            throw new RuntimeException("Failed to initialize Oak repository", e);
        }
    }

    /**
     * Shuts down the repository and releases resources.
     */
    private void shutdownRepository() {
        try {
            DocumentNodeStore nodeStore = nodeStoreRef.getAndSet(null);
            if (nodeStore != null) {
                LOG.info("Disposing DocumentNodeStore...");
                nodeStore.dispose();
                LOG.info("DocumentNodeStore disposed");
            }

            repositoryRef.set(null);
            LOG.info("Repository shutdown complete");

        } catch (Exception e) {
            LOG.error("Error during repository shutdown", e);
        }
    }

    /**
     * Gets the JCR Repository instance.
     *
     * @return the Repository, or null if not initialized
     */
    public Repository getRepository() {
        return repositoryRef.get();
    }

    /**
     * Gets the DocumentNodeStore instance.
     *
     * @return the DocumentNodeStore, or null if not initialized
     */
    public DocumentNodeStore getNodeStore() {
        return nodeStoreRef.get();
    }

    /**
     * Checks if the repository is initialized and ready.
     *
     * @return true if repository is available
     */
    public boolean isReady() {
        return repositoryRef.get() != null;
    }

    /**
     * Gets the cluster ID for this instance.
     * Uses the pod name from Kubernetes StatefulSet ordinal if available.
     *
     * @return the cluster ID
     */
    private int getClusterId() {
        // Try to get cluster ID from environment (K8s StatefulSet)
        String podName = System.getenv("OAK_CLUSTER_ID");
        if (podName != null && !podName.isEmpty()) {
            // Extract ordinal from pod name (e.g., "aem-author-0" -> 1, "aem-author-1" -> 2)
            try {
                String[] parts = podName.split("-");
                int ordinal = Integer.parseInt(parts[parts.length - 1]);
                return ordinal + 1; // Oak cluster IDs are 1-based
            } catch (NumberFormatException e) {
                LOG.warn("Could not parse cluster ID from pod name: {}", podName);
            }
        }

        // Fall back to configured cluster ID
        return config.clusterId();
    }
}

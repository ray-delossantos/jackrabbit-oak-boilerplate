package com.aem.oak.core.blobstore;

import org.apache.jackrabbit.oak.spi.blob.BlobStore;
import org.apache.jackrabbit.oak.plugins.blob.datastore.DataStoreBlobStore;
import org.apache.jackrabbit.core.data.DataStore;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.net.URI;
import java.util.Properties;

/**
 * Factory for creating S3-compatible BlobStore (MinIO).
 * Uses AWS SDK v2 for S3 operations.
 */
@Component(
    service = S3BlobStoreFactory.class,
    immediate = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = S3BlobStoreConfig.class)
public class S3BlobStoreFactory {

    private static final Logger LOG = LoggerFactory.getLogger(S3BlobStoreFactory.class);

    private S3BlobStoreConfig config;
    private S3Client s3Client;
    private S3BlobStore blobStore;

    @Activate
    protected void activate(S3BlobStoreConfig config) {
        this.config = config;
        LOG.info("S3 BlobStore Factory activated");
        initializeS3Client();
    }

    @Deactivate
    protected void deactivate() {
        if (s3Client != null) {
            s3Client.close();
        }
        if (blobStore != null) {
            try {
                blobStore.close();
            } catch (Exception e) {
                LOG.warn("Error closing BlobStore", e);
            }
        }
        LOG.info("S3 BlobStore Factory deactivated");
    }

    @Modified
    protected void modified(S3BlobStoreConfig config) {
        this.config = config;
        deactivate();
        initializeS3Client();
        LOG.info("S3 BlobStore configuration modified");
    }

    /**
     * Initializes the S3 client with MinIO-compatible settings.
     */
    private void initializeS3Client() {
        String endpoint = getEnvOrConfig("S3_ENDPOINT", config.endpoint());
        String accessKey = getEnvOrConfig("S3_ACCESS_KEY", config.accessKey());
        String secretKey = getEnvOrConfig("S3_SECRET_KEY", config.secretKey());
        String region = getEnvOrConfig("S3_REGION", config.region());

        LOG.info("Initializing S3 client with endpoint: {}", endpoint);

        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true) // Required for MinIO
                .build();

        // Ensure bucket exists
        ensureBucketExists();
    }

    /**
     * Creates the S3 bucket if it doesn't exist.
     */
    private void ensureBucketExists() {
        String bucket = getEnvOrConfig("S3_BUCKET", config.bucket());
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            LOG.info("S3 bucket '{}' exists", bucket);
        } catch (NoSuchBucketException e) {
            LOG.info("Creating S3 bucket '{}'", bucket);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            LOG.info("S3 bucket '{}' created", bucket);
        } catch (Exception e) {
            LOG.warn("Could not verify bucket existence: {}", e.getMessage());
        }
    }

    /**
     * Creates or returns the cached BlobStore.
     *
     * @return the BlobStore
     */
    public BlobStore createBlobStore() {
        if (blobStore == null) {
            blobStore = new S3BlobStore(s3Client, getEnvOrConfig("S3_BUCKET", config.bucket()));
        }
        return blobStore;
    }

    /**
     * Gets the S3 client for direct operations.
     */
    public S3Client getS3Client() {
        return s3Client;
    }

    /**
     * Gets a configuration value from environment or config.
     */
    private String getEnvOrConfig(String envKey, String configValue) {
        String envValue = System.getenv(envKey);
        return (envValue != null && !envValue.isEmpty()) ? envValue : configValue;
    }

    /**
     * Gets S3 properties for Oak DataStore configuration.
     */
    public Properties getS3Properties() {
        Properties props = new Properties();
        props.setProperty("accessKey", getEnvOrConfig("S3_ACCESS_KEY", config.accessKey()));
        props.setProperty("secretKey", getEnvOrConfig("S3_SECRET_KEY", config.secretKey()));
        props.setProperty("s3Bucket", getEnvOrConfig("S3_BUCKET", config.bucket()));
        props.setProperty("s3Region", getEnvOrConfig("S3_REGION", config.region()));
        props.setProperty("s3EndPoint", getEnvOrConfig("S3_ENDPOINT", config.endpoint()));
        props.setProperty("connectionTimeout", String.valueOf(config.connectionTimeout()));
        props.setProperty("socketTimeout", String.valueOf(config.socketTimeout()));
        props.setProperty("maxConnections", String.valueOf(config.maxConnections()));
        return props;
    }
}

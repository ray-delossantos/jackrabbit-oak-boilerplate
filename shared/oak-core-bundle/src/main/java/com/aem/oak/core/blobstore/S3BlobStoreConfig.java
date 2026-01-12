package com.aem.oak.core.blobstore;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration for S3-compatible BlobStore (MinIO).
 */
@ObjectClassDefinition(
    name = "AEM Oak S3 BlobStore Configuration",
    description = "Configuration for S3-compatible blob storage (MinIO)"
)
public @interface S3BlobStoreConfig {

    @AttributeDefinition(
        name = "S3 Endpoint",
        description = "S3-compatible endpoint URL. Can be overridden by S3_ENDPOINT env var."
    )
    String endpoint() default "http://minio:9000";

    @AttributeDefinition(
        name = "Access Key",
        description = "S3 access key. Can be overridden by S3_ACCESS_KEY env var."
    )
    String accessKey() default "minioadmin";

    @AttributeDefinition(
        name = "Secret Key",
        type = AttributeType.PASSWORD,
        description = "S3 secret key. Can be overridden by S3_SECRET_KEY env var."
    )
    String secretKey() default "minioadmin";

    @AttributeDefinition(
        name = "Bucket Name",
        description = "S3 bucket name. Can be overridden by S3_BUCKET env var."
    )
    String bucket() default "oak-blobs";

    @AttributeDefinition(
        name = "Region",
        description = "S3 region. Can be overridden by S3_REGION env var."
    )
    String region() default "us-east-1";

    @AttributeDefinition(
        name = "Connection Timeout (ms)",
        description = "Connection timeout in milliseconds"
    )
    int connectionTimeout() default 30000;

    @AttributeDefinition(
        name = "Socket Timeout (ms)",
        description = "Socket timeout in milliseconds"
    )
    int socketTimeout() default 30000;

    @AttributeDefinition(
        name = "Max Connections",
        description = "Maximum number of concurrent connections"
    )
    int maxConnections() default 50;

    @AttributeDefinition(
        name = "Cache Directory",
        description = "Local cache directory for blob caching"
    )
    String cacheDirectory() default "/tmp/oak-blob-cache";

    @AttributeDefinition(
        name = "Cache Size (MB)",
        description = "Maximum size of local blob cache in megabytes"
    )
    int cacheSize() default 1024;
}

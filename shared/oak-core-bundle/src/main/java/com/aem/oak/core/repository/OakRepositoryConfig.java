package com.aem.oak.core.repository;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration for Oak Repository initialization.
 */
@ObjectClassDefinition(
    name = "AEM Oak Repository Configuration",
    description = "Configuration for Apache Jackrabbit Oak repository with PostgreSQL and S3"
)
public @interface OakRepositoryConfig {

    @AttributeDefinition(
        name = "Cluster ID",
        description = "Unique cluster node ID (1-based). In K8s, this is auto-detected from pod ordinal."
    )
    int clusterId() default 1;

    @AttributeDefinition(
        name = "Instance Type",
        description = "Instance type: 'author' or 'publish'"
    )
    String instanceType() default "author";

    @AttributeDefinition(
        name = "Read-Only Mode",
        description = "Enable read-only mode (typically for publish instances)"
    )
    boolean readOnly() default false;

    @AttributeDefinition(
        name = "Cache Size (MB)",
        description = "Node cache size in megabytes"
    )
    int cacheSize() default 256;

    @AttributeDefinition(
        name = "Lease Check Mode",
        description = "Cluster lease check mode: STRICT, LENIENT, or DISABLED"
    )
    String leaseCheckMode() default "STRICT";

    @AttributeDefinition(
        name = "Journal GC Interval (hours)",
        description = "Interval for journal garbage collection"
    )
    int journalGCInterval() default 24;

    @AttributeDefinition(
        name = "Version GC Max Age (hours)",
        description = "Maximum age for version garbage collection"
    )
    int versionGCMaxAge() default 24;
}

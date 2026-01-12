package com.aem.oak.core.datasource;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration for PostgreSQL DataSource.
 */
@ObjectClassDefinition(
    name = "AEM Oak PostgreSQL DataSource Configuration",
    description = "Configuration for PostgreSQL connection pool (HikariCP)"
)
public @interface PostgresDataSourceConfig {

    @AttributeDefinition(
        name = "JDBC URL",
        description = "PostgreSQL JDBC URL. Can be overridden by POSTGRES_URL env var."
    )
    String jdbcUrl() default "jdbc:postgresql://postgres:5432/oak";

    @AttributeDefinition(
        name = "Username",
        description = "Database username. Can be overridden by POSTGRES_USER env var."
    )
    String username() default "oak";

    @AttributeDefinition(
        name = "Password",
        type = AttributeType.PASSWORD,
        description = "Database password. Can be overridden by POSTGRES_PASSWORD env var."
    )
    String password() default "oak";

    @AttributeDefinition(
        name = "Maximum Pool Size",
        description = "Maximum number of connections in the pool"
    )
    int maxPoolSize() default 50;

    @AttributeDefinition(
        name = "Minimum Idle Connections",
        description = "Minimum number of idle connections to maintain"
    )
    int minIdle() default 10;

    @AttributeDefinition(
        name = "Connection Timeout (ms)",
        description = "Maximum time to wait for a connection from the pool"
    )
    long connectionTimeout() default 30000;

    @AttributeDefinition(
        name = "Idle Timeout (ms)",
        description = "Maximum time a connection can sit idle in the pool"
    )
    long idleTimeout() default 600000;

    @AttributeDefinition(
        name = "Max Lifetime (ms)",
        description = "Maximum lifetime of a connection in the pool"
    )
    long maxLifetime() default 1800000;
}

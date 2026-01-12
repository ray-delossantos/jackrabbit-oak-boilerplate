package com.aem.oak.core.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Factory for creating PostgreSQL DataSource with HikariCP connection pooling.
 * Configured for use with Apache Jackrabbit Oak RDBDocumentStore.
 */
@Component(
    service = PostgresDataSourceFactory.class,
    immediate = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = PostgresDataSourceConfig.class)
public class PostgresDataSourceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresDataSourceFactory.class);

    private HikariDataSource dataSource;
    private PostgresDataSourceConfig config;

    @Activate
    protected void activate(PostgresDataSourceConfig config) {
        this.config = config;
        LOG.info("PostgreSQL DataSource Factory activated");
    }

    @Deactivate
    protected void deactivate() {
        if (dataSource != null && !dataSource.isClosed()) {
            LOG.info("Closing PostgreSQL DataSource");
            dataSource.close();
        }
    }

    @Modified
    protected void modified(PostgresDataSourceConfig config) {
        this.config = config;
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
        LOG.info("PostgreSQL DataSource configuration modified");
    }

    /**
     * Creates or returns the cached PostgreSQL DataSource.
     *
     * @return the DataSource
     */
    public DataSource createDataSource() {
        if (dataSource == null || dataSource.isClosed()) {
            dataSource = createHikariDataSource();
        }
        return dataSource;
    }

    /**
     * Creates a new HikariCP DataSource configured for PostgreSQL.
     */
    private HikariDataSource createHikariDataSource() {
        LOG.info("Creating HikariCP DataSource for PostgreSQL");

        HikariConfig hikariConfig = new HikariConfig();

        // JDBC URL - prefer environment variable for K8s secrets
        String jdbcUrl = getEnvOrConfig("POSTGRES_URL", config.jdbcUrl());
        hikariConfig.setJdbcUrl(jdbcUrl);

        // Credentials - prefer environment variables
        String username = getEnvOrConfig("POSTGRES_USER", config.username());
        String password = getEnvOrConfig("POSTGRES_PASSWORD", config.password());
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);

        // Driver class
        hikariConfig.setDriverClassName("org.postgresql.Driver");

        // Pool configuration
        hikariConfig.setPoolName("oak-postgres-pool");
        hikariConfig.setMaximumPoolSize(config.maxPoolSize());
        hikariConfig.setMinimumIdle(config.minIdle());
        hikariConfig.setConnectionTimeout(config.connectionTimeout());
        hikariConfig.setIdleTimeout(config.idleTimeout());
        hikariConfig.setMaxLifetime(config.maxLifetime());

        // Validation
        hikariConfig.setConnectionTestQuery("SELECT 1");

        // PostgreSQL-specific settings for Oak
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        // Enable automatic reconnection
        hikariConfig.addDataSourceProperty("autosave", "conservative");

        LOG.info("Creating HikariCP DataSource with URL: {}, maxPoolSize: {}",
                maskPassword(jdbcUrl), config.maxPoolSize());

        return new HikariDataSource(hikariConfig);
    }

    /**
     * Gets a value from environment variable or falls back to config.
     */
    private String getEnvOrConfig(String envKey, String configValue) {
        String envValue = System.getenv(envKey);
        return (envValue != null && !envValue.isEmpty()) ? envValue : configValue;
    }

    /**
     * Masks password in URL for logging.
     */
    private String maskPassword(String url) {
        if (url == null) return null;
        return url.replaceAll("password=[^&]*", "password=***");
    }

    /**
     * Gets connection pool statistics.
     */
    public PoolStats getPoolStats() {
        if (dataSource == null) {
            return new PoolStats(0, 0, 0, 0);
        }
        return new PoolStats(
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }

    /**
     * Pool statistics record.
     */
    public record PoolStats(int active, int idle, int total, int waiting) {}
}

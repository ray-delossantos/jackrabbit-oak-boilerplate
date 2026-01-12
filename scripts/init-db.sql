-- PostgreSQL initialization script for AEM Oak
-- Run this to initialize the Oak database

-- Create database with proper encoding for Oak
-- Note: This is usually done by PostgreSQL container, but included for reference

-- CREATE DATABASE oak
--     TEMPLATE = template0
--     ENCODING = 'UTF8'
--     LC_COLLATE = 'C'
--     LC_CTYPE = 'C';

-- Connect to oak database
\c oak;

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE oak TO oak;

-- Oak will create these tables automatically, but here's the schema for reference:
-- - NODES: Primary document storage
-- - CLUSTERNODES: Cluster node tracking with lease info
-- - JOURNAL: Change logging (24h rolling)
-- - SETTINGS: Configuration and checkpoints

-- Performance tuning (adjust based on resources)
-- These settings are optimized for Oak DocumentNodeStore

-- Increase max connections (each Oak instance uses ~20-50)
ALTER SYSTEM SET max_connections = 200;

-- Memory settings (adjust based on available RAM)
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET work_mem = '16MB';
ALTER SYSTEM SET maintenance_work_mem = '128MB';
ALTER SYSTEM SET effective_cache_size = '1GB';

-- WAL settings for better write performance
ALTER SYSTEM SET wal_buffers = '16MB';
ALTER SYSTEM SET checkpoint_completion_target = 0.9;
ALTER SYSTEM SET max_wal_size = '1GB';

-- Query planner settings
ALTER SYSTEM SET random_page_cost = 1.1;
ALTER SYSTEM SET effective_io_concurrency = 200;

-- Logging (for debugging, disable in production)
ALTER SYSTEM SET log_min_duration_statement = 1000;  -- Log queries > 1s
ALTER SYSTEM SET log_checkpoints = on;
ALTER SYSTEM SET log_connections = on;
ALTER SYSTEM SET log_disconnections = on;

-- Reload configuration
SELECT pg_reload_conf();

-- Verify settings
SELECT name, setting, unit
FROM pg_settings
WHERE name IN (
    'max_connections',
    'shared_buffers',
    'work_mem',
    'effective_cache_size'
);

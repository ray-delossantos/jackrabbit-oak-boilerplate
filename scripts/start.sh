#!/bin/bash
# Startup script for AEM Oak instances
# Configures and starts Apache Felix OSGi framework

set -e

echo "=========================================="
echo "AEM Oak Instance Startup"
echo "Instance Type: ${INSTANCE_TYPE:-unknown}"
echo "Cluster ID: ${OAK_CLUSTER_ID:-not-set}"
echo "=========================================="

# Set Felix home
FELIX_HOME="${FELIX_HOME:-/opt/felix}"
AEM_HOME="${AEM_HOME:-/opt/aem}"

# Configure Java options
JAVA_OPTS="${JAVA_OPTS:--Xmx2g -Xms512m -XX:+UseG1GC}"

# Add instance-specific options
JAVA_OPTS="${JAVA_OPTS} -Dinstance.type=${INSTANCE_TYPE:-author}"
JAVA_OPTS="${JAVA_OPTS} -Dfelix.home=${FELIX_HOME}"
JAVA_OPTS="${JAVA_OPTS} -Dfelix.cache.rootdir=${AEM_HOME}/felix-cache"

# Configure Oak cluster ID
if [ -n "${OAK_CLUSTER_ID}" ]; then
    # Extract numeric ID from pod name (e.g., aem-author-0 -> 1)
    NUMERIC_ID=$(echo "${OAK_CLUSTER_ID}" | grep -oE '[0-9]+$' || echo "0")
    CLUSTER_ID=$((NUMERIC_ID + 1))
    JAVA_OPTS="${JAVA_OPTS} -Doak.cluster.id=${CLUSTER_ID}"
    echo "Cluster ID set to: ${CLUSTER_ID}"
fi

# Configure PostgreSQL connection
if [ -n "${POSTGRES_URL}" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Dpostgres.url=${POSTGRES_URL}"
fi
if [ -n "${POSTGRES_USER}" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Dpostgres.user=${POSTGRES_USER}"
fi
if [ -n "${POSTGRES_PASSWORD}" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Dpostgres.password=${POSTGRES_PASSWORD}"
fi

# Configure S3/MinIO connection
if [ -n "${S3_ENDPOINT}" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Ds3.endpoint=${S3_ENDPOINT}"
fi
if [ -n "${S3_ACCESS_KEY}" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Ds3.accessKey=${S3_ACCESS_KEY}"
fi
if [ -n "${S3_SECRET_KEY}" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Ds3.secretKey=${S3_SECRET_KEY}"
fi
if [ -n "${S3_BUCKET}" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Ds3.bucket=${S3_BUCKET}"
fi
if [ -n "${S3_REGION}" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Ds3.region=${S3_REGION}"
fi

# Wait for dependencies
echo "Waiting for dependencies..."

# Wait for PostgreSQL
if [ -n "${POSTGRES_URL}" ]; then
    POSTGRES_HOST=$(echo "${POSTGRES_URL}" | sed -n 's/.*\/\/\([^:\/]*\).*/\1/p')
    POSTGRES_PORT=$(echo "${POSTGRES_URL}" | sed -n 's/.*:\([0-9]*\)\/.*/\1/p')
    POSTGRES_PORT="${POSTGRES_PORT:-5432}"

    echo "Waiting for PostgreSQL at ${POSTGRES_HOST}:${POSTGRES_PORT}..."
    timeout=60
    while ! nc -z "${POSTGRES_HOST}" "${POSTGRES_PORT}" 2>/dev/null; do
        timeout=$((timeout - 1))
        if [ $timeout -le 0 ]; then
            echo "ERROR: PostgreSQL not available after 60 seconds"
            exit 1
        fi
        sleep 1
    done
    echo "PostgreSQL is available"
fi

# Wait for MinIO
if [ -n "${S3_ENDPOINT}" ]; then
    MINIO_HOST=$(echo "${S3_ENDPOINT}" | sed -n 's/.*\/\/\([^:\/]*\).*/\1/p')
    MINIO_PORT=$(echo "${S3_ENDPOINT}" | sed -n 's/.*:\([0-9]*\).*/\1/p')
    MINIO_PORT="${MINIO_PORT:-9000}"

    echo "Waiting for MinIO at ${MINIO_HOST}:${MINIO_PORT}..."
    timeout=60
    while ! nc -z "${MINIO_HOST}" "${MINIO_PORT}" 2>/dev/null; do
        timeout=$((timeout - 1))
        if [ $timeout -le 0 ]; then
            echo "ERROR: MinIO not available after 60 seconds"
            exit 1
        fi
        sleep 1
    done
    echo "MinIO is available"
fi

echo "All dependencies are available"

# Create required directories
mkdir -p "${AEM_HOME}/logs"
mkdir -p "${AEM_HOME}/felix-cache"

# Start Felix
echo "Starting Apache Felix..."
echo "Java Options: ${JAVA_OPTS}"

cd "${FELIX_HOME}"

exec java ${JAVA_OPTS} \
    -jar "${FELIX_HOME}/bin/felix.jar" \
    "${FELIX_HOME}/bundle"

#!/bin/bash
# Build script for AEM Oak Boilerplate
# Builds Maven project and Docker images

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Configuration
DOCKER_REGISTRY="${DOCKER_REGISTRY:-}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
SKIP_TESTS="${SKIP_TESTS:-false}"

echo "=========================================="
echo "AEM Oak Boilerplate Build"
echo "=========================================="
echo "Project Root: ${PROJECT_ROOT}"
echo "Docker Registry: ${DOCKER_REGISTRY:-local}"
echo "Image Tag: ${IMAGE_TAG}"
echo "Skip Tests: ${SKIP_TESTS}"
echo "=========================================="

cd "${PROJECT_ROOT}"

# Step 1: Build Maven project
echo ""
echo "Step 1: Building Maven project..."
echo "=========================================="

MAVEN_OPTS=""
if [ "$SKIP_TESTS" = "true" ]; then
    MAVEN_OPTS="-DskipTests"
fi

mvn clean package ${MAVEN_OPTS}

echo "Maven build completed successfully"

# Step 2: Build Docker images
echo ""
echo "Step 2: Building Docker images..."
echo "=========================================="

# Build base image
echo "Building base image..."
docker build \
    -f docker/Dockerfile.base \
    -t aem-oak/base:${IMAGE_TAG} \
    .

# Build author image
echo "Building author image..."
docker build \
    -f docker/Dockerfile.author \
    -t aem-oak/author:${IMAGE_TAG} \
    .

# Build publish image
echo "Building publish image..."
docker build \
    -f docker/Dockerfile.publish \
    -t aem-oak/publish:${IMAGE_TAG} \
    .

echo "Docker images built successfully"

# Step 3: Tag and push to registry (if configured)
if [ -n "${DOCKER_REGISTRY}" ]; then
    echo ""
    echo "Step 3: Pushing images to registry..."
    echo "=========================================="

    # Tag images
    docker tag aem-oak/base:${IMAGE_TAG} ${DOCKER_REGISTRY}/aem-oak/base:${IMAGE_TAG}
    docker tag aem-oak/author:${IMAGE_TAG} ${DOCKER_REGISTRY}/aem-oak/author:${IMAGE_TAG}
    docker tag aem-oak/publish:${IMAGE_TAG} ${DOCKER_REGISTRY}/aem-oak/publish:${IMAGE_TAG}

    # Push images
    docker push ${DOCKER_REGISTRY}/aem-oak/base:${IMAGE_TAG}
    docker push ${DOCKER_REGISTRY}/aem-oak/author:${IMAGE_TAG}
    docker push ${DOCKER_REGISTRY}/aem-oak/publish:${IMAGE_TAG}

    echo "Images pushed to registry successfully"
fi

echo ""
echo "=========================================="
echo "Build completed successfully!"
echo "=========================================="
echo ""
echo "Docker images available:"
echo "  - aem-oak/base:${IMAGE_TAG}"
echo "  - aem-oak/author:${IMAGE_TAG}"
echo "  - aem-oak/publish:${IMAGE_TAG}"
echo ""
echo "To deploy to Kubernetes:"
echo "  ./scripts/deploy.sh"

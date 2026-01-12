#!/bin/bash
# Deploy script for AEM Oak Boilerplate
# Deploys to Kubernetes using Kustomize

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Configuration
NAMESPACE="${NAMESPACE:-aem-oak}"
ENVIRONMENT="${ENVIRONMENT:-dev}"
KUBECTL="${KUBECTL:-kubectl}"
DRY_RUN="${DRY_RUN:-false}"

echo "=========================================="
echo "AEM Oak Boilerplate Deployment"
echo "=========================================="
echo "Namespace: ${NAMESPACE}"
echo "Environment: ${ENVIRONMENT}"
echo "Dry Run: ${DRY_RUN}"
echo "=========================================="

cd "${PROJECT_ROOT}"

# Check kubectl
if ! command -v ${KUBECTL} &> /dev/null; then
    echo "ERROR: kubectl not found"
    exit 1
fi

# Check Kubernetes connectivity
echo ""
echo "Checking Kubernetes connectivity..."
if ! ${KUBECTL} cluster-info &> /dev/null; then
    echo "ERROR: Cannot connect to Kubernetes cluster"
    exit 1
fi
echo "Connected to Kubernetes cluster"

# Determine kustomize path
KUSTOMIZE_PATH="k8s/base"
if [ -d "k8s/overlays/${ENVIRONMENT}" ]; then
    KUSTOMIZE_PATH="k8s/overlays/${ENVIRONMENT}"
    echo "Using overlay: ${ENVIRONMENT}"
fi

# Deploy
echo ""
echo "Deploying to Kubernetes..."
echo "=========================================="

if [ "$DRY_RUN" = "true" ]; then
    echo "DRY RUN - showing what would be applied:"
    ${KUBECTL} kustomize "${KUSTOMIZE_PATH}"
else
    # Apply namespace first
    ${KUBECTL} apply -f k8s/base/namespace.yaml

    # Wait for namespace
    ${KUBECTL} wait --for=jsonpath='{.status.phase}'=Active namespace/${NAMESPACE} --timeout=30s

    # Apply all resources
    ${KUBECTL} apply -k "${KUSTOMIZE_PATH}"

    echo ""
    echo "Waiting for deployments to be ready..."

    # Wait for PostgreSQL
    echo "Waiting for PostgreSQL..."
    ${KUBECTL} -n ${NAMESPACE} rollout status statefulset/postgres --timeout=300s || true

    # Wait for MinIO
    echo "Waiting for MinIO..."
    ${KUBECTL} -n ${NAMESPACE} rollout status statefulset/minio --timeout=300s || true

    # Wait for Author
    echo "Waiting for Author instances..."
    ${KUBECTL} -n ${NAMESPACE} rollout status statefulset/aem-author --timeout=600s || true

    # Wait for Publish
    echo "Waiting for Publish instances..."
    ${KUBECTL} -n ${NAMESPACE} rollout status deployment/aem-publish --timeout=300s || true
fi

echo ""
echo "=========================================="
echo "Deployment completed!"
echo "=========================================="

# Show status
if [ "$DRY_RUN" != "true" ]; then
    echo ""
    echo "Pod Status:"
    ${KUBECTL} -n ${NAMESPACE} get pods

    echo ""
    echo "Services:"
    ${KUBECTL} -n ${NAMESPACE} get svc

    echo ""
    echo "Ingress:"
    ${KUBECTL} -n ${NAMESPACE} get ingress

    echo ""
    echo "Access URLs (configure DNS/hosts):"
    echo "  - Author: http://author.aem-oak.local"
    echo "  - Publish: http://www.aem-oak.local"
    echo "  - MinIO Console: http://minio.aem-oak.local"
fi

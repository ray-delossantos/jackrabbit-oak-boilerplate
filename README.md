# AEM Oak Boilerplate

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)]()

A Content Management System built on Apache Jackrabbit Oak, designed for Kubernetes deployment. This boilerplate provides a foundation for building content-centric applications with Author/Publish tier separation, digital asset management, and HTL templating.

## Prerequisites

- **Java 21** (OpenJDK or Eclipse Temurin)
- **Maven 3.9+**
- **Docker** (Colima, Podman, or compatible runtime)
- **Kubernetes** cluster (Colima, minikube, kind, or cloud provider)
- **kubectl** configured for your cluster

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/aem-oak-boilerplate.git
cd aem-oak-boilerplate
```

### 2. Build

```bash
./scripts/build.sh
```

This builds the Maven project and creates Docker images:
- `aem-oak/base:latest`
- `aem-oak/author:latest`
- `aem-oak/publish:latest`

### 3. Deploy to Kubernetes

```bash
./scripts/deploy.sh
```

### 4. Access the Application

Add these entries to your `/etc/hosts`:
```
127.0.0.1 author.aem-oak.local
127.0.0.1 www.aem-oak.local
127.0.0.1 minio.aem-oak.local
```

Then access:
- **Author**: http://author.aem-oak.local
- **Publish**: http://www.aem-oak.local
- **MinIO Console**: http://minio.aem-oak.local

## Platform Notes

### Linux

**Option A: Colima (Recommended)**
```bash
# Install Colima
curl -fsSL https://github.com/abiosoft/colima/releases/latest/download/colima-Linux-x86_64 -o colima
chmod +x colima && sudo mv colima /usr/local/bin/

# Install Docker CLI
sudo apt-get install docker.io  # Debian/Ubuntu
# or: sudo dnf install docker    # Fedora

# Start with Kubernetes enabled
colima start --cpu 4 --memory 8 --disk 100 --kubernetes

# Verify setup
docker info
kubectl cluster-info

# Install NGINX Ingress Controller
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/cloud/deploy.yaml
```

**Option B: Podman with Kind**
```bash
# Install Podman
sudo apt-get install podman  # Debian/Ubuntu
# or: sudo dnf install podman  # Fedora

# Install Kind
curl -Lo ./kind https://kind.sigs.k8s.io/dl/latest/kind-linux-amd64
chmod +x ./kind && sudo mv ./kind /usr/local/bin/

# Create cluster
kind create cluster --name aem-oak

# Install NGINX Ingress Controller
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/cloud/deploy.yaml
```

**Option C: Docker Engine**
```bash
# Ensure Docker is running without sudo
sudo usermod -aG docker $USER
newgrp docker
```

For minikube, enable the ingress addon:
```bash
minikube addons enable ingress
minikube tunnel  # Run in separate terminal
```

### macOS (Colima)

Colima provides Docker and Kubernetes without Docker Desktop:

```bash
# Install Colima and Docker CLI
brew install colima docker kubectl

# Start with Kubernetes enabled (allocate sufficient resources)
colima start --cpu 4 --memory 8 --disk 100 --kubernetes

# Verify setup
docker info
kubectl cluster-info

# Install NGINX Ingress Controller
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/cloud/deploy.yaml

# Wait for ingress to be ready
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

**Alternative: minikube**
```bash
brew install minikube
minikube start --driver=docker
minikube addons enable ingress
minikube tunnel  # Run in separate terminal
```

## Project Structure

```
aem-oak-boilerplate/
├── author/          # Author instance (content creation)
├── publish/         # Publish instance (content delivery)
├── shared/          # Shared libraries (Oak, Sling, APIs)
├── ui-apps/         # HTL templates and client libraries
├── config/          # Felix, Oak, and Sling configurations
├── k8s/             # Kubernetes manifests
├── docker/          # Dockerfiles
└── scripts/         # Build and deploy scripts
```

## Configuration

Environment variables for customization:

| Variable | Default | Description |
|----------|---------|-------------|
| `DOCKER_REGISTRY` | (local) | Docker registry for pushing images |
| `IMAGE_TAG` | latest | Docker image tag |
| `NAMESPACE` | aem-oak | Kubernetes namespace |
| `ENVIRONMENT` | dev | Deployment environment (dev/prod) |

Example:
```bash
DOCKER_REGISTRY=myregistry.io IMAGE_TAG=v1.0.0 ./scripts/build.sh
```

## Links

- [Apache Jackrabbit Oak](https://jackrabbit.apache.org/oak/) - Content repository
- [Apache Sling](https://sling.apache.org/) - Web framework
- [Apache Felix](https://felix.apache.org/) - OSGi runtime
- [PostgreSQL](https://www.postgresql.org/) - Document storage backend
- [MinIO](https://min.io/) - S3-compatible blob storage
- [HTL (Apache Sling)](https://sling.apache.org/documentation/bundles/scripting/scripting-htl.html) - Templating language
- [Colima](https://github.com/abiosoft/colima) - Container runtime for macOS/Linux

## Acknowledgments

This project is built on the shoulders of giants:

- **Apache Software Foundation** for Jackrabbit Oak, Sling, Felix, and HTL
- **The PostgreSQL Global Development Group** for PostgreSQL
- **MinIO, Inc.** for the S3-compatible object storage

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

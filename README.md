# AEM Oak Boilerplate

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)]()

An AEM-like Content Management System built on Apache Jackrabbit Oak, designed for Kubernetes deployment. This boilerplate provides a foundation for building content-centric applications with Author/Publish tier separation, digital asset management, and HTL templating.

## Prerequisites

- **Java 21** (OpenJDK or Eclipse Temurin)
- **Maven 3.9+**
- **Docker** (with BuildKit support)
- **Kubernetes** cluster (minikube, kind, or cloud provider)
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

Ensure Docker is running without sudo:
```bash
sudo usermod -aG docker $USER
newgrp docker
```

For minikube, enable the ingress addon:
```bash
minikube addons enable ingress
minikube tunnel  # Run in separate terminal
```

### macOS

For Docker Desktop, ensure Kubernetes is enabled in settings.

For minikube:
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
- [HTL Specification](https://github.com/adobe/htl-spec) - Templating language

## Acknowledgments

This project is built on the shoulders of giants:

- **Apache Software Foundation** for Jackrabbit Oak, Sling, and Felix
- **Adobe** for the HTL (Sightly) templating specification
- **The PostgreSQL Global Development Group** for PostgreSQL
- **MinIO, Inc.** for the S3-compatible object storage

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

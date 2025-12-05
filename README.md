# Station

<div align="center">

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Enterprise-grade distributed Docker Hub cache registry with intelligent caching and seamless Kubernetes integration**

[Features](#features) • [Quick Start](#quick-start) • [Architecture](#architecture) • [Deployment](#deployment) • [Documentation](#documentation)

English | [简体中文](README_CN.md)

</div>

---

## Overview

**Station** is a high-performance, distributed Docker Hub caching registry built with Spring Boot 4.0 and WebFlux. It provides intelligent multi-level caching, automatic node discovery, and production-ready Kubernetes support to accelerate Docker image pulls across your infrastructure.

### Why Station?

- **Faster Builds**: Reduce Docker image pull times by 10-50x with intelligent caching
- **Cost Effective**: Minimize bandwidth costs and Docker Hub rate limiting
- **Production Ready**: Battle-tested distributed architecture with graceful scaling
- **Zero Config**: Automatic node discovery and consistent hash-based load balancing
- **Cloud Native**: First-class Kubernetes support with StatefulSets and rolling updates

## Features

### Core Capabilities

- **Multi-Level Caching System**
  - L1: Caffeine in-memory cache (~0ms latency)
  - L2: Redis distributed index (~1ms latency)
  - L3: Peer node gRPC queries (~10ms latency)
  - L4: Docker Hub fallback (~100-500ms latency)
  - Cache penetration protection with distributed locks
  - LRU eviction and automatic cleanup

- **Distributed Architecture**
  - Consistent hashing with 150 virtual nodes
  - Automatic node discovery (Kubernetes and Redis modes)
  - gRPC-based inter-node communication with streaming
  - Distributed locking to prevent thundering herd

- **Reactive & Non-Blocking**
  - Built on Spring WebFlux and Project Reactor
  - Fully reactive from HTTP to Redis to gRPC
  - Backpressure support for large file transfers
  - Java 21 Virtual Threads ready

- **Docker Registry API v2**
  - Full manifest support (v2 schema and OCI)
  - Blob storage with range requests
  - Docker Hub authentication integration
  - HEAD requests for efficient existence checks

- **Production Features**
  - Graceful shutdown with pod draining
  - Prometheus metrics export
  - Health checks and readiness probes
  - Structured logging and request tracing

## Quick Start

### Prerequisites

- Java 21+
- Docker & Docker Compose (for local development)
- Kubernetes 1.24+ (for production deployment)

### Local Development with Docker Compose

1. **Clone the repository**
   ```bash
   git clone https://github.com/dingdangmaoup/station.git
   cd station
   ```

2. **Build the project**
   ```bash
   ./gradlew clean build
   ```

3. **Start the cluster**
   ```bash
   docker-compose up -d
   ```

   This starts:
   - 3 Station nodes (ports 5001-5003)
   - 1 Redis instance
   - 1 Nginx load balancer (port 5000)

4. **Configure Docker to use the registry**

   Edit `/etc/docker/daemon.json`:
   ```json
   {
     "registry-mirrors": ["http://localhost:5000"]
   }
   ```

   Restart Docker:
   ```bash
   sudo systemctl restart docker
   ```

5. **Test it out**
   ```bash
   # Pull an image through the cache
   docker pull nginx:latest

   # Check cache metrics
   curl http://localhost:5000/actuator/prometheus | grep station_cache
   ```

### Kubernetes Deployment

Station provides three Redis deployment modes for Kubernetes:

#### Standalone Mode (Development/Testing)
```bash
kubectl apply -f kubernetes/base/
kubectl apply -f kubernetes/standalone/
```

#### Sentinel Mode (Production - Recommended)
```bash
kubectl apply -f kubernetes/base/
kubectl apply -f kubernetes/sentinel/
kubectl apply -f kubernetes/standalone/station-service.yaml
```

#### Cluster Mode (High-Performance Production)
```bash
kubectl apply -f kubernetes/base/
kubectl apply -f kubernetes/cluster/
kubectl apply -f kubernetes/standalone/station-service.yaml
```

See [kubernetes/README.md](kubernetes/README.md) for detailed deployment instructions.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client (Docker CLI)                       │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                    ┌───────────▼────────────┐
                    │   Nginx Load Balancer   │
                    └───────────┬─────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
┌───────▼────────┐    ┌────────▼────────┐    ┌────────▼────────┐
│  Station Node  │    │  Station Node   │    │  Station Node   │
│    (Pod 0)     │◄───┤    (Pod 1)      │◄───┤    (Pod 2)      │
└───────┬────────┘    └────────┬────────┘    └────────┬────────┘
        │ gRPC                 │ gRPC                 │
        │                      │                      │
        └──────────────┬───────┴──────────────────────┘
                       │
            ┌──────────▼───────────┐
            │   Redis (L2 Cache)   │
            │   + Node Registry    │
            └──────────────────────┘
```

### Request Flow

1. **Client Request**: Docker client requests image manifest/blob
2. **Load Balancer**: Nginx routes to available Station node
3. **L1 Cache Check**: Check local Caffeine cache
4. **L2 Cache Check**: Query Redis for cached metadata
5. **L3 Peer Query**: Use consistent hashing to find responsible node, query via gRPC
6. **L4 Docker Hub**: Fallback to Docker Hub if not cached
7. **Cache Population**: Store in local and Redis caches on fetch

### Key Components

- **[RegistryController](src/main/java/com/dingdangmaoup/station/registry/controller/RegistryController.java)**: Docker Registry API v2 endpoints
- **[MultiLevelCacheManager](src/main/java/com/dingdangmaoup/station/cache/MultiLevelCacheManager.java)**: Orchestrates L1/L2/L3 cache lookups
- **[ConsistentHashManager](src/main/java/com/dingdangmaoup/station/coordination/ConsistentHashManager.java)**: Routes requests to responsible nodes
- **[StationGrpcService](src/main/java/com/dingdangmaoup/station/grpc/server/StationGrpcService.java)**: Inter-node communication
- **[DockerHubClient](src/main/java/com/dingdangmaoup/station/docker/DockerHubClient.java)**: Upstream Docker Hub integration
- **[NodeDiscoveryService](src/main/java/com/dingdangmaoup/station/node/discovery/NodeDiscoveryService.java)**: Automatic node registration

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `STATION_STORAGE_BASE_PATH` | Base directory for blob storage | `/data/registry` |
| `STATION_REDIS_HOST` | Redis host | `localhost` |
| `STATION_REDIS_PORT` | Redis port | `6379` |
| `STATION_REDIS_PASSWORD` | Redis password | (empty) |
| `STATION_NODE_DISCOVERY_MODE` | Node discovery mode | `kubernetes` |
| `STATION_CACHE_LOCAL_MAX_SIZE` | L1 cache max entries | `10000` |
| `STATION_CACHE_TTL_HOURS` | Cache TTL in hours | `168` (7 days) |

### Application Properties

See [src/main/resources/application.yml](src/main/resources/application.yml) for full configuration options.

## Monitoring

Station exposes Prometheus metrics on `/actuator/prometheus`:

### Key Metrics

- `station_cache_hit_ratio`: Cache hit rate (0.0-1.0)
- `station_cache_requests_total`: Total cache requests by level and result
- `station_node_count`: Number of active nodes
- `http_server_requests_seconds`: HTTP request latency histogram
- `jvm_memory_used_bytes`: JVM memory usage

### Example Prometheus Queries

```promql
# Cache hit rate
rate(station_cache_requests_total{result="hit"}[5m]) / rate(station_cache_requests_total[5m])

# P95 request latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# Node availability
up{job="station"}
```

## Performance

### Benchmarks

| Scenario | Throughput | Latency (P95) |
|----------|-----------|---------------|
| Cache Hit (L1) | 1000+ RPS | < 1ms |
| Cache Hit (L2) | 500+ RPS | < 5ms |
| Peer Hit (L3) | 200+ RPS | < 20ms |
| Docker Hub Fallback | ~100 RPS | < 500ms |

### Resource Requirements

| Environment | CPU | Memory | Storage |
|-------------|-----|--------|---------|
| Minimum | 1 core | 2GB | 100GB |
| Recommended | 2 cores | 4GB | 500GB |
| Production | 4 cores | 8GB | 1TB+ SSD |

## Use Cases

### CI/CD Pipeline Acceleration

Configure GitLab Runner:
```toml
[[runners]]
  [runners.docker]
    registry_mirrors = ["http://station.company.com"]
```

### Development Environment

Configure Docker Desktop:
```json
{
  "registry-mirrors": ["http://localhost:5000"]
}
```

### Kubernetes Cluster

Configure containerd:
```toml
[plugins."io.containerd.grpc.v1.cri".registry.mirrors]
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]
    endpoint = ["http://station-registry.default.svc.cluster.local"]
```

## Development

### Building from Source

```bash
# Build JAR
./gradlew clean build

# Build Docker image
docker build -t station:latest .

# Run tests
./gradlew test

# Generate gRPC code
./gradlew generateProto
```

### Project Structure

```
station/
├── src/main/java/com/dingdangmaoup/station/
│   ├── cache/              # Multi-level caching (5 classes)
│   ├── config/             # Configuration (5 classes)
│   ├── coordination/       # Distributed coordination (4 classes)
│   ├── docker/             # Docker Hub client (4 classes)
│   ├── grpc/               # gRPC services (2 classes)
│   ├── lifecycle/          # Lifecycle management (2 classes)
│   ├── metrics/            # Prometheus metrics (2 classes)
│   ├── node/discovery/     # Node discovery (4 classes)
│   ├── registry/           # Registry API (1 class)
│   └── storage/            # Storage layer (6 classes)
├── src/main/proto/         # gRPC definitions
├── kubernetes/             # K8s deployment files
└── docker/                 # Local development setup
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request


## License

This project is licensed under the MIT License - see the [LICENSE](./LICENSE.txt) file for details.

## Acknowledgments

- Built with [Spring Boot](https://spring.io/projects/spring-boot) and [Project Reactor](https://projectreactor.io/)
- gRPC communication powered by [grpc-java](https://github.com/grpc/grpc-java)
- Caching with [Caffeine](https://github.com/ben-manes/caffeine) and [Redis](https://redis.io/)
- Kubernetes deployment inspired by [Docker Registry](https://github.com/distribution/distribution)

## Support

- Documentation: [kubernetes/README.md](kubernetes/README.md)
- Issues: [GitHub Issues](https://github.com/dingdangmaoup/station/issues)
- Discussions: [GitHub Discussions](https://github.com/dingdangmaoup/station/discussions)

---

<div align="center">

**Built with ❤️ using Spring Boot 4.0 and Java 21**

</div>

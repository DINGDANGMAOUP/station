# Station

<div align="center">

[![构建状态](https://img.shields.io/badge/build-passing-brightgreen.svg)](https://github.com/dingdangmaoup/station/actions)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green.svg)](https://spring.io/projects/spring-boot)
[![许可证](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**企业级分布式 Docker Hub 缓存镜像仓库，具有智能缓存和无缝 Kubernetes 集成**

[特性](#特性) • [快速开始](#快速开始) • [架构](#架构) • [部署](#部署) • [文档](#文档)

[English](README.md) | 简体中文

</div>

---

## 概述

**Station** 是一个基于 Spring Boot 4.0 和 WebFlux 构建的高性能分布式 Docker Hub 缓存镜像仓库。它提供智能多级缓存、自动节点发现和生产就绪的 Kubernetes 支持，以加速整个基础设施的 Docker 镜像拉取。

### 为什么选择 Station？

- **更快的构建速度**：通过智能缓存将 Docker 镜像拉取时间缩短 10-50 倍
- **成本效益**：最小化带宽成本和 Docker Hub 速率限制
- **生产就绪**：经过实战检验的分布式架构，具有优雅的扩展能力
- **零配置**：自动节点发现和基于一致性哈希的负载均衡
- **云原生**：通过 StatefulSets 和滚动更新提供一流的 Kubernetes 支持

## 特性

### 核心能力

- **多级缓存系统**
  - L1：Caffeine 内存缓存（延迟约 0ms）
  - L2：Redis 分布式索引（延迟约 1ms）
  - L3：对等节点 gRPC 查询（延迟约 10ms）
  - L4：Docker Hub 回源（延迟约 100-500ms）
  - 使用分布式锁防止缓存击穿
  - LRU 淘汰策略和自动清理

- **分布式架构**
  - 使用 150 个虚拟节点的一致性哈希
  - 自动节点发现（Kubernetes 和 Redis 模式）
  - 基于 gRPC 的节点间流式通信
  - 分布式锁防止缓存雪崩

- **响应式与非阻塞**
  - 基于 Spring WebFlux 和 Project Reactor 构建
  - 从 HTTP 到 Redis 再到 gRPC 完全响应式
  - 大文件传输支持背压
  - 支持 Java 21 虚拟线程

- **Docker Registry API v2**
  - 完整的 manifest 支持（v2 schema 和 OCI）
  - 支持范围请求的 Blob 存储
  - Docker Hub 身份验证集成
  - HEAD 请求用于高效的存在性检查

- **生产特性**
  - 优雅关闭和 Pod 排空
  - Prometheus 指标导出
  - 健康检查和就绪探测
  - 结构化日志和请求追踪

## 快速开始

### 先决条件

- Java 21+
- Docker & Docker Compose（用于本地开发）
- Kubernetes 1.24+（用于生产部署）

### 使用 Docker Compose 进行本地开发

1. **克隆仓库**
   ```bash
   git clone https://github.com/dingdangmaoup/station.git
   cd station
   ```

2. **构建项目**
   ```bash
   ./gradlew clean build
   ```

3. **启动集群**
   ```bash
   docker-compose up -d
   ```

   这将启动：
   - 3 个 Station 节点（端口 5001-5003）
   - 1 个 Redis 实例
   - 1 个 Nginx 负载均衡器（端口 5000）

4. **配置 Docker 使用镜像仓库**

   编辑 `/etc/docker/daemon.json`：
   ```json
   {
     "registry-mirrors": ["http://localhost:5000"]
   }
   ```

   重启 Docker：
   ```bash
   sudo systemctl restart docker
   ```

5. **测试**
   ```bash
   # 通过缓存拉取镜像
   docker pull nginx:latest

   # 检查缓存指标
   curl http://localhost:5000/actuator/prometheus | grep station_cache
   ```

### Kubernetes 部署

Station 为 Kubernetes 提供三种 Redis 部署模式：

#### 单机模式（开发/测试）
```bash
kubectl apply -f kubernetes/base/
kubectl apply -f kubernetes/standalone/
```

#### 哨兵模式（生产环境 - 推荐）
```bash
kubectl apply -f kubernetes/base/
kubectl apply -f kubernetes/sentinel/
kubectl apply -f kubernetes/standalone/station-service.yaml
```

#### 集群模式（高性能生产环境）
```bash
kubectl apply -f kubernetes/base/
kubectl apply -f kubernetes/cluster/
kubectl apply -f kubernetes/standalone/station-service.yaml
```

详细部署说明请参阅 [kubernetes/README.md](kubernetes/README.md)。

## 架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     客户端（Docker CLI）                          │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                    ┌───────────▼────────────┐
                    │   Nginx 负载均衡器      │
                    └───────────┬─────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
┌───────▼────────┐    ┌────────▼────────┐    ┌────────▼────────┐
│  Station 节点  │    │  Station 节点   │    │  Station 节点   │
│    (Pod 0)     │◄───┤    (Pod 1)      │◄───┤    (Pod 2)      │
└───────┬────────┘    └────────┬────────┘    └────────┬────────┘
        │ gRPC                 │ gRPC                 │
        │                      │                      │
        └──────────────┬───────┴──────────────────────┘
                       │
            ┌──────────▼───────────┐
            │   Redis（L2 缓存）   │
            │   + 节点注册中心      │
            └──────────────────────┘
```

### 请求流程

1. **客户端请求**：Docker 客户端请求镜像 manifest/blob
2. **负载均衡器**：Nginx 路由到可用的 Station 节点
3. **L1 缓存检查**：检查本地 Caffeine 缓存
4. **L2 缓存检查**：查询 Redis 中的缓存元数据
5. **L3 对等节点查询**：使用一致性哈希找到负责的节点，通过 gRPC 查询
6. **L4 Docker Hub**：如果未缓存则回源到 Docker Hub
7. **缓存填充**：获取时存储在本地缓存和 Redis 缓存中

### 核心组件

- **[RegistryController](src/main/java/com/dingdangmaoup/station/registry/controller/RegistryController.java)**：Docker Registry API v2 端点
- **[MultiLevelCacheManager](src/main/java/com/dingdangmaoup/station/cache/MultiLevelCacheManager.java)**：协调 L1/L2/L3 缓存查找
- **[ConsistentHashManager](src/main/java/com/dingdangmaoup/station/coordination/ConsistentHashManager.java)**：将请求路由到负责的节点
- **[StationGrpcService](src/main/java/com/dingdangmaoup/station/grpc/server/StationGrpcService.java)**：节点间通信
- **[DockerHubClient](src/main/java/com/dingdangmaoup/station/docker/DockerHubClient.java)**：上游 Docker Hub 集成
- **[NodeDiscoveryService](src/main/java/com/dingdangmaoup/station/node/discovery/NodeDiscoveryService.java)**：自动节点注册

## 配置

### 环境变量

| 变量 | 描述 | 默认值 |
|------|------|--------|
| `STATION_STORAGE_BASE_PATH` | Blob 存储的基础目录 | `/data/registry` |
| `STATION_REDIS_HOST` | Redis 主机 | `localhost` |
| `STATION_REDIS_PORT` | Redis 端口 | `6379` |
| `STATION_REDIS_PASSWORD` | Redis 密码 | （空） |
| `STATION_NODE_DISCOVERY_MODE` | 节点发现模式 | `kubernetes` |
| `STATION_CACHE_LOCAL_MAX_SIZE` | L1 缓存最大条目数 | `10000` |
| `STATION_CACHE_TTL_HOURS` | 缓存 TTL（小时） | `168`（7 天） |

### 应用程序属性

完整配置选项请参阅 [src/main/resources/application.yml](src/main/resources/application.yml)。

## 监控

Station 在 `/actuator/prometheus` 端点暴露 Prometheus 指标：

### 关键指标

- `station_cache_hit_ratio`：缓存命中率（0.0-1.0）
- `station_cache_requests_total`：按级别和结果统计的缓存请求总数
- `station_node_count`：活跃节点数
- `http_server_requests_seconds`：HTTP 请求延迟直方图
- `jvm_memory_used_bytes`：JVM 内存使用

### Prometheus 查询示例

```promql
# 缓存命中率
rate(station_cache_requests_total{result="hit"}[5m]) / rate(station_cache_requests_total[5m])

# P95 请求延迟
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# 节点可用性
up{job="station"}
```

## 性能

### 基准测试

| 场景 | 吞吐量 | 延迟（P95） |
|------|--------|-------------|
| 缓存命中（L1） | 1000+ RPS | < 1ms |
| 缓存命中（L2） | 500+ RPS | < 5ms |
| 对等节点命中（L3） | 200+ RPS | < 20ms |
| Docker Hub 回源 | ~100 RPS | < 500ms |

### 资源要求

| 环境 | CPU | 内存 | 存储 |
|------|-----|------|------|
| 最低要求 | 1 核 | 2GB | 100GB |
| 推荐配置 | 2 核 | 4GB | 500GB |
| 生产环境 | 4 核 | 8GB | 1TB+ SSD |

## 使用场景

### CI/CD 流水线加速

配置 GitLab Runner：
```toml
[[runners]]
  [runners.docker]
    registry_mirrors = ["http://station.company.com"]
```

### 开发环境

配置 Docker Desktop：
```json
{
  "registry-mirrors": ["http://localhost:5000"]
}
```

### Kubernetes 集群

配置 containerd：
```toml
[plugins."io.containerd.grpc.v1.cri".registry.mirrors]
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]
    endpoint = ["http://station-registry.default.svc.cluster.local"]
```

## 开发

### 从源码构建

```bash
# 构建 JAR
./gradlew clean build

# 构建 Docker 镜像
docker build -t station:latest .

# 运行测试
./gradlew test

# 生成 gRPC 代码
./gradlew generateProto
```

### 项目结构

```
station/
├── src/main/java/com/dingdangmaoup/station/
│   ├── cache/              # 多级缓存（5 个类）
│   ├── config/             # 配置（5 个类）
│   ├── coordination/       # 分布式协调（4 个类）
│   ├── docker/             # Docker Hub 客户端（4 个类）
│   ├── grpc/               # gRPC 服务（2 个类）
│   ├── lifecycle/          # 生命周期管理（2 个类）
│   ├── metrics/            # Prometheus 指标（2 个类）
│   ├── node/discovery/     # 节点发现（4 个类）
│   ├── registry/           # Registry API（1 个类）
│   └── storage/            # 存储层（6 个类）
├── src/main/proto/         # gRPC 定义
├── kubernetes/             # K8s 部署文件
└── docker/                 # 本地开发设置
```

## 贡献

欢迎贡献！请随时提交 Pull Request。

1. Fork 本仓库
2. 创建你的特性分支（`git checkout -b feature/amazing-feature`）
3. 提交你的更改（`git commit -m '添加某个很棒的特性'`）
4. 推送到分支（`git push origin feature/amazing-feature`）
5. 开启一个 Pull Request

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](./LICENSE.txt) 文件。

## 致谢

- 使用 [Spring Boot](https://spring.io/projects/spring-boot) 和 [Project Reactor](https://projectreactor.io/) 构建
- gRPC 通信由 [grpc-java](https://github.com/grpc/grpc-java) 提供支持
- 使用 [Caffeine](https://github.com/ben-manes/caffeine) 和 [Redis](https://redis.io/) 进行缓存
- Kubernetes 部署受 [Docker Registry](https://github.com/distribution/distribution) 启发

## 支持

- 文档：[kubernetes/README.md](kubernetes/README.md)
- 问题反馈：[GitHub Issues](https://github.com/dingdangmaoup/station/issues)
- 讨论：[GitHub Discussions](https://github.com/dingdangmaoup/station/discussions)

---

<div align="center">

**使用 Spring Boot 4.0 和 Java 21 构建**

</div>

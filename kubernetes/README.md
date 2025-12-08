# Station Kubernetes 部署文件

本目录包含 Station 项目在 Kubernetes 环境中部署所需的所有配置文件，按照部署模式分类组织。

## 📁 目录结构

```
kubernetes/
├── base/                      # 公共基础配置
│   ├── namespace.yaml         # Namespace 定义
│   ├── rbac.yaml             # ServiceAccount 和 RBAC
│   └── secret.yaml           # Redis 密码 Secret
│
├── standalone/               # Redis 单机模式
│   ├── redis-config.yaml     # Redis 配置
│   ├── redis-deployment.yaml # Redis Deployment
│   ├── redis-service.yaml    # Redis Service
│   ├── station-config.yaml   # Station 配置
│   ├── station-service.yaml  # Station Service
│   └── station-statefulset.yaml # Station StatefulSet
│
├── sentinel/                 # Redis 哨兵模式
│   ├── redis-config.yaml     # Redis 主从和哨兵配置
│   ├── redis-service.yaml    # Redis Service
│   ├── redis-statefulset.yaml # Redis StatefulSet
│   ├── station-config.yaml   # Station 配置
│   └── station-statefulset.yaml # Station StatefulSet
│
├── cluster/                  # Redis 集群模式
│   ├── redis-config.yaml     # Redis 集群配置
│   ├── redis-service.yaml    # Redis Service
│   ├── redis-statefulset.yaml # Redis StatefulSet
│   ├── redis-init-job.yaml   # Redis 集群初始化 Job
│   ├── station-config.yaml   # Station 配置
│   └── station-statefulset.yaml # Station StatefulSet
│
├── QUICKSTART.md            # 快速开始指南
├── DEPLOYMENT.md            # 详细部署文档
└── README.md               # 本文件
```

## 🚀 快速开始

### 1. 部署基础资源

所有模式都需要先部署基础资源：

```bash
# 应用基础配置（namespace、RBAC、Secret）
kubectl apply -f base/
```

### 2. 选择部署模式

根据需求选择其中一种模式部署：

#### 选项 A: 单机模式（推荐用于开发/测试）

```bash
# 部署 Redis
kubectl apply -f standalone/redis-config.yaml
kubectl apply -f standalone/redis-deployment.yaml
kubectl apply -f standalone/redis-service.yaml

# 部署 Station
kubectl apply -f standalone/station-config.yaml
kubectl apply -f standalone/station-service.yaml
kubectl apply -f standalone/station-statefulset.yaml
```

或使用一条命令：

```bash
kubectl apply -f standalone/
```

#### 选项 B: 哨兵模式（推荐用于生产）

```bash
# 部署 Redis 哨兵
kubectl apply -f sentinel/redis-config.yaml
kubectl apply -f sentinel/redis-statefulset.yaml
kubectl apply -f sentinel/redis-service.yaml

# 部署 Station
kubectl apply -f sentinel/station-config.yaml
kubectl apply -f standalone/station-service.yaml  # 使用 standalone 的 Service
kubectl apply -f sentinel/station-statefulset.yaml
```

或使用一条命令：

```bash
kubectl apply -f sentinel/
kubectl apply -f standalone/station-service.yaml
```

#### 选项 C: 集群模式（高性能生产）

```bash
# 部署 Redis 集群
kubectl apply -f cluster/redis-config.yaml
kubectl apply -f cluster/redis-statefulset.yaml
kubectl apply -f cluster/redis-service.yaml

# 等待 Redis Pod 就绪
kubectl wait --for=condition=ready pod -l app=redis,mode=cluster -n station --timeout=180s

# 初始化集群
kubectl apply -f cluster/redis-init-job.yaml

# 部署 Station
kubectl apply -f cluster/station-config.yaml
kubectl apply -f standalone/station-service.yaml  # 使用 standalone 的 Service
kubectl apply -f cluster/station-statefulset.yaml
```

或使用一条命令：

```bash
kubectl apply -f cluster/
kubectl apply -f standalone/station-service.yaml
```

### 3. 验证部署

```bash
# 查看所有资源
kubectl get all -n station

# 查看 Pod 状态
kubectl get pods -n station

# 查看日志
kubectl logs -n station -l app=station --tail=50

# 检查健康状态
kubectl get pods -n station -o wide
```

## 📋 部署模式对比

| 特性 | 单机模式 | 哨兵模式 | 集群模式 |
|------|----------|----------|----------|
| **高可用性** | ❌ | ✅ | ✅ |
| **自动故障转移** | ❌ | ✅ | ✅ |
| **数据分片** | ❌ | ❌ | ✅ |
| **横向扩展** | ❌ | ❌ | ✅ |
| **Redis 实例** | 1 | 3 (1主2从) | 6 (3主3从) |
| **复杂度** | 低 | 中 | 高 |
| **资源需求** | 低 | 中 | 高 |
| **适用场景** | 开发/测试 | 生产（中小规模） | 生产（大规模） |

## 📦 配置文件说明

### base/ - 基础配置

- **namespace.yaml**: 创建 `station` namespace
- **rbac.yaml**: ServiceAccount 和 ClusterRole，用于 Kubernetes 服务发现
- **secret.yaml**: Redis 密码配置（可选）

### standalone/ - 单机模式

- **redis-config.yaml**: Redis 单机配置（AOF 持久化、LRU 驱逐）
- **redis-deployment.yaml**: Redis Deployment（1 副本 + PVC）
- **redis-service.yaml**: Redis ClusterIP Service
- **station-config.yaml**: Station 环境变量配置
- **station-service.yaml**: Station Headless + ClusterIP Service
- **station-statefulset.yaml**: Station StatefulSet（3 副本）

### sentinel/ - 哨兵模式

- **redis-config.yaml**: Redis 主从配置 + 哨兵配置
- **redis-statefulset.yaml**: Redis StatefulSet（3 副本，包含 Redis + Sentinel 容器）
- **redis-service.yaml**: Redis Headless + ClusterIP Service
- **station-config.yaml**: Station 环境变量配置（哨兵节点列表）
- **station-statefulset.yaml**: Station StatefulSet（3 副本）

### cluster/ - 集群模式

- **redis-config.yaml**: Redis 集群配置（cluster-enabled）
- **redis-statefulset.yaml**: Redis StatefulSet（6 副本）
- **redis-service.yaml**: Redis Headless + ClusterIP Service
- **redis-init-job.yaml**: 集群初始化 Job
- **station-config.yaml**: Station 环境变量配置（集群节点列表）
- **station-statefulset.yaml**: Station StatefulSet（3 副本）

## 🔧 自定义配置

### 修改 Redis 密码

编辑 [base/secret.yaml](base/secret.yaml)：

```yaml
stringData:
  STATION_REDIS_PASSWORD: "your-password"
```

### 调整副本数

编辑对应的 StatefulSet 文件：

```yaml
spec:
  replicas: 5  # 修改为需要的副本数
```

### 修改存储大小

编辑对应的 StatefulSet 文件中的 volumeClaimTemplates：

```yaml
volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      resources:
        requests:
          storage: 200Gi  # 修改为需要的大小
```

### 修改资源限制

编辑对应的 StatefulSet 文件：

```yaml
resources:
  requests:
    memory: "2Gi"
    cpu: "1000m"
  limits:
    memory: "4Gi"
    cpu: "4000m"
```


## 🔍 常用命令

### 查看资源状态

```bash
# 查看所有资源
kubectl get all -n station

# 查看 Pod
kubectl get pods -n station -o wide

# 查看 Service
kubectl get svc -n station

# 查看 ConfigMap
kubectl get cm -n station

# 查看 PVC
kubectl get pvc -n station
```

### 查看日志

```bash
# 查看 Station 日志
kubectl logs -n station -l app=station --tail=100 -f

# 查看特定 Pod 日志
kubectl logs -n station station-standalone-0 -f

# 查看 Redis 日志
kubectl logs -n station -l app=redis --tail=50
```

### 进入 Pod

```bash
# 进入 Station Pod
kubectl exec -it -n station station-standalone-0 -- sh

# 进入 Redis Pod
kubectl exec -it -n station redis-standalone-xxx -- sh
```

### 端口转发

```bash
# 转发 Station Registry 端口
kubectl port-forward -n station svc/station-registry 5000:5000

# 转发特定 Pod 端口
kubectl port-forward -n station station-standalone-0 5000:5000
```

### 清理资源

```bash
# 清理单机模式
kubectl delete -f standalone/

# 清理哨兵模式
kubectl delete -f sentinel/

# 清理集群模式
kubectl delete -f cluster/

# 清理基础资源
kubectl delete -f base/

# 完全清理（包括 PVC）
kubectl delete namespace station
```

## 🛠️ 故障排查

### Pod 无法启动

```bash
# 查看 Pod 详情
kubectl describe pod -n station <pod-name>

# 查看事件
kubectl get events -n station --sort-by='.lastTimestamp'

# 查看日志
kubectl logs -n station <pod-name>
```

### Redis 连接问题

```bash
# 测试 Redis 连接
kubectl run -it --rm redis-test --image=redis:7.4-alpine -n station -- redis-cli -h redis-standalone.station.svc.cluster.local ping

# 查看 Station 日志中的 Redis 连接信息
kubectl logs -n station <station-pod> | grep -i redis
```

### 服务发现问题

```bash
# 检查 RBAC 权限
kubectl auth can-i get pods --as=system:serviceaccount:station:station -n station

# 查看服务发现日志
kubectl logs -n station <station-pod> | grep -i discovery
```


## 📊 监控

Station 暴露 Prometheus 指标：

```bash
# 查看指标
kubectl exec -n station <station-pod> -- curl http://localhost:5000/actuator/prometheus

# 关键指标
# - station_cache_hit_ratio: 缓存命中率
# - station_node_count: 活跃节点数
# - http_server_requests_seconds: HTTP 请求延迟
```

Pod 已配置 Prometheus 注解：

```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "5000"
  prometheus.io/path: "/actuator/prometheus"
```

## 🔐 安全建议

1. **使用 NetworkPolicy** 限制 Pod 间通信
2. **启用 Redis 密码认证** 修改 `base/secret.yaml`
3. **使用 RBAC 最小权限** 已配置在 `base/rbac.yaml`
4. **定期更新镜像** 使用最新的安全补丁
5. **加密存储** 使用加密的 StorageClass



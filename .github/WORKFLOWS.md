# Station - GitHub Actions 配置说明

本文档说明 Station 项目的 GitHub Actions 工作流配置。

## 📋 工作流概览

### 1. CI (Continuous Integration)
**文件**: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

**触发条件**:
- Push 到 `main` 或 `develop` 分支
- Pull Request 到 `main` 或 `develop` 分支

**包含的任务**:

#### Build Job
- ✅ 使用 Java 21 (Temurin)
- ✅ Gradle 缓存加速构建
- ✅ 运行单元测试
- ✅ 生成测试报告
- ✅ 上传构建产物 (JAR)
- ✅ 代码格式检查 (Spotless)

#### Docker Job
- ✅ 构建 Docker 镜像
- ✅ 使用 BuildKit 和缓存
- ✅ 测试镜像可运行性

#### Security Job
- ✅ Trivy 漏洞扫描
- ✅ 上传结果到 GitHub Security

#### Code Quality Job
- ✅ SonarCloud 代码质量分析 (可选)
- ✅ 需要配置 `SONAR_TOKEN`

### 2. Release (发布管理)
**文件**: [`.github/workflows/release.yml`](.github/workflows/release.yml)

**触发条件**:
- 推送 `v*` 标签 (如 `v1.0.0`)

**包含的任务**:

#### Release Job
- ✅ 构建发布版本 JAR
- ✅ 自动生成发布说明
- ✅ 创建 GitHub Release
- ✅ 上传 JAR 文件

#### Docker Job
- ✅ 构建多架构镜像 (amd64, arm64)
- ✅ 推送到 GitHub Container Registry
- ✅ 自动标签管理 (semver)
- ✅ 更新 Docker Hub 描述 (可选)

**镜像标签示例**:
```
ghcr.io/dingdangmaoup/station:1.0.0
ghcr.io/dingdangmaoup/station:1.0
ghcr.io/dingdangmaoup/station:1
ghcr.io/dingdangmaoup/station:sha-abc123
```

### 3. Dependency Review
**文件**: [`.github/workflows/dependency-review.yml`](.github/workflows/dependency-review.yml)

**触发条件**:
- Pull Request 到 `main` 或 `develop` 分支

**功能**:
- ✅ 检查依赖变更
- ✅ 识别安全漏洞
- ✅ 检测许可证问题
- ✅ 阻止 GPL 许可证

### 4. CodeQL Analysis
**文件**: [`.github/workflows/codeql.yml`](.github/workflows/codeql.yml)

**触发条件**:
- Push 到 `main` 或 `develop` 分支
- Pull Request 到 `main` 或 `develop` 分支
- 每周一自动运行

**功能**:
- ✅ 静态代码分析
- ✅ 安全漏洞检测
- ✅ 代码质量检查
- ✅ 结果上传到 GitHub Security

### 5. Stale Management
**文件**: [`.github/workflows/stale.yml`](.github/workflows/stale.yml)

**触发条件**:
- 每天自动运行

**功能**:
- ✅ 标记 30 天无活动的 Issue/PR 为 stale
- ✅ 7 天后自动关闭 stale 项
- ✅ 保护重要标签 (pinned, security, bug)

### 6. Dependabot
**文件**: [`.github/dependabot.yml`](.github/dependabot.yml)

**更新频率**: 每周一

**监控内容**:
- ✅ Gradle 依赖
- ✅ GitHub Actions 版本
- ✅ Docker 基础镜像

## 🔧 配置要求

### 必需的 Secrets

无需额外配置，使用默认的 `GITHUB_TOKEN`。

### 可选的 Secrets

用于增强功能:

| Secret | 用途 | 配置位置 |
|--------|------|----------|
| `SONAR_TOKEN` | SonarCloud 代码分析 | Settings → Secrets → Actions |
| `DOCKERHUB_USERNAME` | Docker Hub 描述更新 | Settings → Secrets → Actions |
| `DOCKERHUB_TOKEN` | Docker Hub 描述更新 | Settings → Secrets → Actions |

### 配置 SonarCloud (可选)

1. 访问 [SonarCloud](https://sonarcloud.io/)
2. 导入 GitHub 仓库
3. 获取 `SONAR_TOKEN`
4. 添加到 GitHub Secrets
5. 更新 `ci.yml` 中的组织和项目 key

### 配置 Docker Hub (可选)

如果要同时发布到 Docker Hub:

1. 在 `release.yml` 中添加 Docker Hub 登录步骤:
```yaml
- name: Log in to Docker Hub
  uses: docker/login-action@v3
  with:
    username: ${{ secrets.DOCKERHUB_USERNAME }}
    password: ${{ secrets.DOCKERHUB_TOKEN }}
```

2. 更新镜像名称:
```yaml
images: |
  ghcr.io/dingdangmaoup/station
  dingdangmaoup/station
```

## 📊 状态徽章

在 README.md 中添加状态徽章:

```markdown
[![CI](https://github.com/dingdangmaoup/station/workflows/CI/badge.svg)](https://github.com/dingdangmaoup/station/actions/workflows/ci.yml)
[![CodeQL](https://github.com/dingdangmaoup/station/workflows/CodeQL%20Analysis/badge.svg)](https://github.com/dingdangmaoup/station/actions/workflows/codeql.yml)
[![Release](https://github.com/dingdangmaoup/station/workflows/Release/badge.svg)](https://github.com/dingdangmaoup/station/actions/workflows/release.yml)
```

## 🚀 使用指南

### 发布新版本

1. **更新版本号**
   ```bash
   # 在 build.gradle.kts 中更新版本
   version = "1.0.0"
   ```

2. **提交变更**
   ```bash
   git add .
   git commit -m "chore: bump version to 1.0.0"
   git push
   ```

3. **创建并推送标签**
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```

4. **自动化流程**
   - Release workflow 自动触发
   - 构建 JAR 和 Docker 镜像
   - 创建 GitHub Release
   - 推送镜像到 GHCR

### 拉取发布的镜像

```bash
# 拉取最新版本
docker pull ghcr.io/dingdangmaoup/station:latest

# 拉取特定版本
docker pull ghcr.io/dingdangmaoup/station:1.0.0
```

### 在 Kubernetes 中使用

```yaml
containers:
  - name: station
    image: ghcr.io/dingdangmaoup/station:1.0.0
```

## 🔍 监控和调试

### 查看工作流运行状态

访问 [Actions 页面](https://github.com/dingdangmaoup/station/actions)

### 查看安全扫描结果

访问 [Security 页面](https://github.com/dingdangmaoup/station/security)

### 查看构建产物

1. 进入成功的 workflow run
2. 查看 "Artifacts" 部分
3. 下载 `station-jar`

### 调试失败的构建

```bash
# 本地运行相同的构建命令
./gradlew clean build

# 检查测试失败
./gradlew test --info

# 检查 Docker 构建
docker build -t station:debug .
```

## 📝 最佳实践

### Commit 消息规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add new cache eviction strategy
fix: correct gRPC connection leak
docs: update deployment guide
test: add integration tests for Redis cluster
chore: update dependencies
```

### Pull Request 流程

1. 创建 feature 分支
2. 提交变更
3. 推送到 GitHub
4. 创建 PR
5. 等待 CI 通过
6. 请求 code review
7. 合并到 main/develop

### Release 策略

- **主版本** (v1.0.0): 破坏性变更
- **次版本** (v1.1.0): 新功能
- **补丁版本** (v1.0.1): Bug 修复
- **预发布** (v1.0.0-beta.1): 测试版本

## 🛡️ 安全

### 自动化安全检查

- ✅ Dependabot 自动更新依赖
- ✅ Trivy 扫描容器漏洞
- ✅ CodeQL 检测代码漏洞
- ✅ Dependency Review 阻止危险依赖

### 手动安全检查

```bash
# 本地运行 Trivy 扫描
docker run --rm -v $(pwd):/workspace aquasec/trivy:latest fs /workspace

# 检查过期依赖
./gradlew dependencyUpdates
```

## 📞 支持

如有问题，请：
1. 查看 [GitHub Discussions](https://github.com/dingdangmaoup/station/discussions)
2. 提交 [Issue](https://github.com/dingdangmaoup/station/issues)
3. 查看现有的 [Actions runs](https://github.com/dingdangmaoup/station/actions)

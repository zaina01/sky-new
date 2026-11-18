# Sky New

基于 Java 25、Spring Boot 和 Quartz 的插件任务调度平台，支持 Java 插件、JavaScript / Python SDK 插件和单文件脚本，提供插件上传、定时执行、配置与账户管理、执行日志、通知和权限管理。平台通过独立 Runner 进程执行插件。

本仓库包含后端、插件 API、Runner 和 Docker Compose 部署配置。Vue 3 管理界面位于独立仓库 [sky-vue-new](https://github.com/zaina01/sky-vue-new)。

## 主要功能

- **插件管理**：上传 Java JAR、JS/Python SDK ZIP 或 `.js`/`.mjs`/`.py` 单文件，管理插件定义、安装实例、依赖和运行状态；SDK 插件支持配置与账户 Schema，单文件脚本支持实例变量和启动参数。
- **任务调度**：基于 Quartz 配置 Cron 定时任务，支持手动执行、执行超时和并发策略。
- **执行日志**：查看任务状态、执行结果和 Runner 日志，定位插件运行问题。
- **配置与账户**：通过 Schema 表单管理插件配置和账户，支持账户分组、批量操作、文件导入导出及列表列设置。
- **插件服务**：支持插件间服务调用、调用权限配置和调用日志查询。
- **通知管理**：配置通知渠道、规则和任务通知，查看发送记录并重试失败通知。
- **权限与审计**：提供 JWT 登录、用户与角色、部门与数据权限，以及登录、系统和插件审计日志。

## Docker 快速部署

### 1. 准备环境

服务器安装 Docker Engine、Docker Compose v2.24 或以上、curl 和 OpenSSL，并确认当前用户可以运行 Docker：

```bash
docker version
docker compose version
```

部署支持 `linux/amd64` 和 `linux/arm64`。以下是 Linux Bash 命令，无需安装 Java、Node.js、Gradle 或 Git，也无需下载前后端源码。

| 服务 | 镜像 |
| --- | --- |
| 后端与 Runner | `ghcr.io/zaina01/sky-new:latest` |
| Nginx 与前端 | `ghcr.io/zaina01/sky-vue-new:latest` |
| 数据库 | `mysql:8.4` |

两个应用镜像已公开，可免登录拉取；Docker 会自动选择服务器对应的架构。

### 2. 下载三个部署文件

以下流程用于首次部署，请在新的部署目录中执行。已有部署不要重新下载覆盖 `.env`，更新方式见下方运维说明。

```bash
mkdir -p sky-new/sql
cd sky-new

curl -fSL https://raw.githubusercontent.com/zaina01/sky-new/main/compose.yaml -o compose.yaml
curl -fSL https://raw.githubusercontent.com/zaina01/sky-new/main/.env.example -o .env
curl -fSL https://raw.githubusercontent.com/zaina01/sky-new/main/sql/schema-mysql.sql -o sql/schema-mysql.sql

chmod 600 .env
```

确认三次下载都成功后再继续。部署目录应为：

```text
sky-new/
├── compose.yaml
├── .env
└── sql/
    └── schema-mysql.sql
```

`compose.yaml` 启动三个服务，`.env` 保存密钥和部署选项，SQL 文件只用于空数据库卷的首次初始化，不导入演示数据。请保留 SQL 的相对目录结构。

### 3. 设置密钥和端口

编辑 `.env`，填写四个必填项。数据库密码和 JWT 密钥分别使用独立的 `openssl rand -hex 32`；脚本变量加密密钥使用 `openssl rand -base64 32`：

```bash
openssl rand -hex 32
openssl rand -base64 32
```

```dotenv
SKY_DB_PASSWORD=填写独立生成的数据库应用用户密码
MYSQL_ROOT_PASSWORD=填写独立生成的数据库root密码
SKY_JWT_SECRET=填写独立生成的JWT密钥
SKY_SCRIPT_ENV_ENCRYPTION_KEY=填写独立生成的32字节Base64密钥
```

密钥不可留空，JWT 密钥至少为 32 字节；脚本变量密钥解码后必须恰好 32 字节。请妥善保存权限为 `600` 的 `.env`，更新和重建容器时保持原加密密钥不变，随数据库备份安全保管。已有加密变量的旧部署需沿用原有效密钥，不能直接生成新值替换；详见 [单文件脚本升级说明](docs/standalone-scripts.md)。

| 配置项 | 默认值 | 用途 |
| --- | --- | --- |
| `BACKEND_TAG` | `latest` | 后端镜像标签 |
| `FRONTEND_TAG` | `latest` | 前端镜像标签 |
| `SKY_HTTP_BIND` | `0.0.0.0` | 宿主机 HTTP 监听地址 |
| `SKY_HTTP_PORT` | `80` | 宿主机 HTTP 端口 |

若 80 端口被占用，可在 `.env` 中将 `SKY_HTTP_PORT` 改为 `8088`；服务器防火墙或云安全组也需放行所选端口。默认仅发布前端 HTTP 端口，数据库和后端通过 Docker 内部网络访问。

快速体验可使用 `latest`。生产环境建议将前后端分别固定为各自已发布的 `sha-<完整提交号>` 或版本标签；固定版本时，部署配置和初始化 SQL 也应从后端对应提交或版本下载。前后端版本独立，不共用提交号。

### 4. 拉取镜像并启动

```bash
docker compose config --quiet
docker compose pull
docker compose up -d --wait --wait-timeout 360
docker compose ps
```

每条命令成功后再执行下一条。Compose 会等待 MySQL 就绪，再启动后端与前端。若启动超时，先查看日志排查，保留现有数据卷。

浏览器访问 `http://服务器IP/init-admin` 创建初始管理员，再登录使用。若端口改为 `8088`，访问 `http://服务器IP:8088/init-admin`。已有用户时不能重复初始化；公网开放前应先完成管理员初始化。

默认提供 HTTP，HTTPS 可由外部 Nginx、1Panel 等代理接入，配置示例见 [完整部署文档](docs/docker-deployment.md)。

## 常用运维

在包含 `compose.yaml` 和 `.env` 的部署目录执行：

```bash
docker compose ps
docker compose logs --tail 200 mysql backend web
docker compose logs -f backend
```

默认端口下可检查以下地址；修改端口后需相应调整 URL：

```bash
curl -f http://127.0.0.1/healthz
curl -f http://127.0.0.1/api/actuator/health
```

`/healthz` 检查 Nginx，`/api/actuator/health` 检查应用和数据库，只返回健康状态；数据库断连时返回 HTTP 503。

更新前先备份，确认没有重要插件任务正在执行，再修改 `.env` 中需要更新的镜像标签并执行：

```bash
docker compose pull
docker compose up -d --wait --wait-timeout 360
```

后端更新或重启会中断正在运行的插件任务。停止服务使用：

```bash
docker compose stop
```

MySQL 数据保存在 `sky-new_mysql-data`，插件文件、工作目录、日志和 Maven 依赖保存在 `sky-new_plugin-data`，容器重建后继续保留。请保持 Compose 项目名 `sky-new`，后端保持单实例。

**不要执行 `docker compose down -v`，它会删除数据库和插件数据卷。** 初始化 SQL 不会自动升级已有数据库，镜像回退也不能撤销数据库结构变更。备份恢复、升级回退、HTTPS 和故障排查参见 [完整部署文档](docs/docker-deployment.md)。

## 本地开发

本节面向需要修改源码的开发者。准备 JDK 25、MySQL 8.4、Git 和 OpenSSL，并将 `JAVA_HOME` 指向 JDK 25。项目包含 Gradle Wrapper，首次运行会下载 Gradle 及依赖。

以下命令在 Linux Bash 中执行：

```bash
git clone https://github.com/zaina01/sky-new.git
cd sky-new

# 初始化本地开发数据库，按提示输入本地 MySQL 管理员密码
mysql -u root -p < sql/schema-mysql.sql

# 先构建平台 Runner
bash ./gradlew :sky-plugin-runner-java:jar

export SKY_DB_URL='jdbc:mysql://localhost:3306/sky_new?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
export SKY_DB_USERNAME='root'
export SKY_DB_PASSWORD='替换为本地数据库密码'
export SKY_JWT_SECRET="$(openssl rand -hex 32)"
export SKY_PLUGIN_JAVA_COMMAND="$JAVA_HOME/bin/java"
export SKY_PLUGIN_RUNNER_JAR="$PWD/sky-plugin-runner-java/build/libs/sky-plugin-runner-java-1.0-SNAPSHOT.jar"

bash ./gradlew :sky-job:bootRun
```

源码启动使用环境变量，**不会自动加载 Compose 的 `.env` 文件**。后端默认地址为 `http://localhost:8080/api`，健康接口为 `http://localhost:8080/api/actuator/health`。上例使用本地数据库管理员账号便于开发；Compose 部署使用独立的 `sky` 应用账号。

Windows 可使用 `gradlew.bat` 执行相同任务，并通过 PowerShell 的 `$env:变量名` 设置环境变量。插件 Runner 必须使用 Java 25，不能只为主程序配置 Gradle toolchain 而忽略插件 Java 命令。

前端开发需 Node.js 24，进入独立仓库 [sky-vue-new](https://github.com/zaina01/sky-vue-new) 后执行：

```bash
npm ci
npm run dev
```

前端默认地址为 `http://localhost:5173`，开发代理将 `/api` 转发到 `http://localhost:8080`。在前端 `/init-admin` 页面创建本地管理员后即可联调。

常用后端构建与测试命令：

```bash
bash ./gradlew test
bash ./gradlew :sky-job:bootJar :sky-plugin-runner-java:jar
bash ./gradlew :plugin-demo:jar
```

普通 `test` 不运行会重置真实测试数据库的联调任务。真实 MySQL 联调应使用独立测试库，步骤见 [前后端联调文档](docs/frontend-backend-integration.md)。插件开发、SPI 注册和依赖打包要求见 [插件开发文档](PLUGIN_DEVELOPMENT.md)。

## 模块说明

| 模块 | 职责 |
| --- | --- |
| `sky-job` | Spring Boot 主程序，提供 REST API、任务调度、权限、MyBatis 持久化和插件回调服务 |
| `sky-plugin-api` | 插件公共接口、注解及配置、账户、通知和服务调用 API |
| `sky-plugin-protocol` | 主程序与 Runner 共用的 Protobuf / gRPC 协议 |
| `sky-plugin-runner-java` | 加载上传的 Java 插件并执行任务或服务调用的独立进程 |
| `sky-plugin-runner-node` / `sky-plugin-runner-python` | JS/Python SDK 与单文件脚本的原生运行器 |
| `plugin-demo` | 插件示例，演示配置、账户、日志和服务调用 |
| `plugin-demo-node` / `plugin-demo-python` / `plugin-demo-standalone` | 两种 SDK ZIP 与单文件脚本示例 |

## 相关文档

- [Docker Compose 部署与运维](docs/docker-deployment.md)
- [插件开发入口与 Java 开发](PLUGIN_DEVELOPMENT.md)
- [JavaScript / Python SDK 开发](docs/script-plugins.md)
- [单文件脚本开发、变量与参数](docs/standalone-scripts.md)
- [插件 SDK 发布到 Maven Central](docs/maven-central-publishing.md)
- [前后端联调与集成验收](docs/frontend-backend-integration.md)
- [Vue 前端仓库](https://github.com/zaina01/sky-vue-new)
- [镜像构建与发布记录](https://github.com/zaina01/sky-new/actions/workflows/docker.yml)

## 许可证

本项目采用 [MIT License](LICENSE)。

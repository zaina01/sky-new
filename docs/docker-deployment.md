# Docker Compose 部署

部署由 `web`（Nginx + Vue）、`backend`（Java 25 主程序 + Runner）和 `mysql`（MySQL 8.4）组成。仅 HTTP 端口对外开放；插件子进程在后端容器内通过回环地址调用主程序。后端保持一个实例，不能直接通过 Compose 扩容，否则当前内存 Quartz 和启动恢复逻辑可能重复调度、重置执行状态。

## 1. 发布镜像

两个仓库各自包含 Dockerfile 和 `.github/workflows/docker.yml`：

- 后端：`ghcr.io/zaina01/sky-new`。
- 前端：`ghcr.io/zaina01/sky-vue-new`。
- PR：测试和双架构镜像构建、容器冒烟验证，不推送镜像。
- 默认分支：上述检查成功后发布 `sha-<完整提交号>` 和 `latest`。
- `v*` 标签：发布原样版本标签，例如 `v1.0.0`，同时发布提交号标签，不更新 `latest`。
- 支持 `linux/amd64`、`linux/arm64`，通过原生架构 Runner 分别构建并冒烟验证，再合并镜像清单。架构专用的 `sha-<提交号>-amd64/arm64` 标签是发布中间产物。

推送代码后到 GitHub Actions 查看 `Container images`。工作流使用 `GITHUB_TOKEN`，不需要保存个人推送凭据。若组织策略限制 Packages 写入，需要允许工作流的 `packages: write` 权限。分支保护可将 `checks` 和两个 `image` 任务设为必需检查。手动运行非默认分支仅验证，不发布。

前后端工作流互不依赖，首次部署前须等待两个仓库的镜像均发布成功。没有自动推送代码、创建 Release 或更新服务器的步骤。

## 2. 准备服务器

安装 Docker Engine、Docker Compose v2 插件（2.24 或以上）、curl 和 OpenSSL，确认当前用户可以运行 Docker：

```bash
docker version
docker compose version
```

使用已发布镜像部署时，无需克隆项目，也无需安装 Java、Node.js、Gradle 或 Git。首次部署只需下载 Compose 配置、环境变量模板和初始化 SQL。以下为 Linux Bash 命令，请在新的部署目录执行，已有部署不要重复下载覆盖 `.env`：

```bash
mkdir -p sky-new/sql
cd sky-new

curl -fSL https://raw.githubusercontent.com/zaina01/sky-new/main/compose.yaml -o compose.yaml
curl -fSL https://raw.githubusercontent.com/zaina01/sky-new/main/.env.example -o .env
curl -fSL https://raw.githubusercontent.com/zaina01/sky-new/main/sql/schema-mysql.sql -o sql/schema-mysql.sql

chmod 600 .env
```

确认三次下载均成功后继续。部署目录只需以下文件；Compose 按相对路径挂载 SQL，请保留目录结构：

```text
sky-new/
├── compose.yaml
├── .env
└── sql/
    └── schema-mysql.sql
```

上述 `main` 文件可用于跟随 `latest` 的部署；生产环境固定版本时，应从后端对应提交或版本下载部署配置和初始化 SQL，避免混用不同版本的文件。

需要修改源码的开发者也可选择克隆仓库，然后执行 `cp .env.example .env` 和 `chmod 600 .env`；源码启动说明见 [项目 README](../README.md#本地开发)。镜像部署不需要前端源码。

编辑 `.env`，填写三个必填密钥，每项可独立用 `openssl rand -hex 32` 生成：

```dotenv
SKY_DB_PASSWORD=填写生成的随机值
MYSQL_ROOT_PASSWORD=填写另一个随机值
SKY_JWT_SECRET=填写至少32字节的随机值
BACKEND_TAG=sha-后端完整提交号
FRONTEND_TAG=sha-前端完整提交号
SKY_HTTP_BIND=0.0.0.0
SKY_HTTP_PORT=80
```

示例文件将密钥留空，空值会使 Compose 拒绝启动；长度不足的 JWT 密钥会使应用启动失败。使用随机十六进制值可以避免 `.env` 中 `$` 插值和特殊字符引用问题。不要提交 `.env`；`docker compose config` 展开后包含密钥，检查时使用 `--quiet`。

默认标签是 `latest`，生产部署建议记录两个不可变提交号或版本标签。前后端版本独立，不能将后端提交号直接用作前端标签。默认数据库名为 `sky_new`、应用数据库用户为 `sky`，root 仅用于初始化、备份和恢复。若 80 端口被占用，可将 `.env` 中 `SKY_HTTP_PORT` 改为 `8088`，并在防火墙或云安全组中放行所选端口。

官方两个 GHCR 应用镜像已公开，可免登录拉取，Docker 自动选择 amd64 或 arm64。自行 Fork 发布的 GHCR 包首次发布可能为私有；可在 GitHub Packages 设置中公开包。保持私有时，在服务器使用具有 `read:packages` 且能访问相应包的 Token 登录：

```bash
read -rsp 'GHCR token: ' GHCR_TOKEN; echo
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
unset GHCR_TOKEN
```

## 3. 首次启动与验证

```bash
docker compose config --quiet
docker compose pull
docker compose up -d --wait --wait-timeout 360
docker compose ps
```

访问 `http://服务器IP/`（若修改端口则补充端口），通过 `/init-admin` 创建初始管理员，再登录。现有用户存在时不能再次初始化管理员。公网启用前先完成管理员初始化。

初始化脚本只在 MySQL 数据目录为空时运行，不导入演示数据。Compose 等待数据库可查询后启动后端，再等待后端健康才启动前端。若启动超时，先查看日志，不要删除数据卷来尝试修复。

```bash
curl -f http://127.0.0.1/healthz
curl -f http://127.0.0.1/api/actuator/health
docker compose logs --tail 200 mysql backend web
docker compose logs -f backend
docker compose exec backend /opt/java/openjdk/bin/java -version
```

`/healthz` 只检查 Nginx，`/api/actuator/health` 检查应用和数据库，仅返回状态，数据库断连时返回 HTTP 503。业务接口仍需 JWT；其他 Actuator 接口不对外暴露。修改 HTTP 端口后，以上 curl 命令也需要相应修改。

插件、工作目录、日志及 Maven 缓存位于容器内 `/app/data/plugins`，统一存入命名卷 `sky-new_plugin-data`；MySQL 使用 `sky-new_mysql-data`。插件数据目录不要改为开发机绝对路径。镜像以 UID/GID `10001:10001` 运行后端，新命名卷会继承正确权限；导入旧插件文件时需保持这一所有者。

主程序和插件均使用 Java 25；系统时区设为 Asia/Shanghai，确保清理过环境变量的 Runner 子进程使用同样时区。每个插件 JVM 默认最大堆为 256m，`SKY_PLUGIN_MAX_HEAP` 提供初始默认值；若已在系统设置中保存对应项，以数据库中保存的值为准，应通过系统设置调整。`SKY_JAVA_TOOL_OPTIONS` 只配置主程序 JVM；为主程序、所有并发插件及 MySQL 总计预留足够内存。

## 4. 更新、回退及停止

更新前确认没有重要任务正在执行，并按下一节备份。修改 `.env` 中要更新的镜像标签，然后执行：

```bash
docker compose pull
docker compose up -d --wait --wait-timeout 360
docker compose ps
```

前端和后端可独立更新。后端停止时会尝试关闭插件子进程，Compose 给出 60 秒宽限期；更新会中断运行中的任务。不要在关键任务运行时更新。主机异常断电后，启动恢复逻辑会处理遗留运行状态。

回退时恢复旧 `BACKEND_TAG` / `FRONTEND_TAG`，重新执行上述命令。镜像回退不会回退数据库结构或插件数据。若升级包含 SQL 变更，必须先确认旧版本兼容性，必要时恢复对应备份。

```bash
docker compose stop     # 停止，保留容器和数据
docker compose down    # 删除容器与网络，保留数据卷
```

不要在正常更新时使用 `docker compose down -v`，它会删除数据库和插件数据。不要修改 Compose 项目名 `sky-new`，也不要通过 `-p` 或 `COMPOSE_PROJECT_NAME` 覆盖，否则会使用另一套命名卷。

已有 MySQL 数据卷不会重新执行初始化 SQL；`CREATE TABLE IF NOT EXISTS` 也不能升级旧表结构。结构升级须按版本说明审核并单独执行适用 SQL，不要无差别执行 `sql/` 下的全部脚本。修改 `.env` 不会自动修改已初始化的 MySQL 用户密码；需要通过数据库管理操作同步修改后再更新应用配置。

## 5. 备份与恢复

以下为 Linux Bash 命令，备份期间停止 web/backend 以保持数据库与插件文件一致。先确认卷和数据库属于本部署；备份包含账号等敏感信息，应限制访问并复制到服务器之外。

```bash
mkdir -p backups
chmod 700 backups
docker compose stop web backend
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers --events --databases sky_new' \
  > backups/sky_new.sql
docker compose run --rm --no-deps --user 0 --entrypoint tar \
  -v "$PWD/backups:/backup" backend \
  -czf /backup/plugins.tar.gz -C /app/data/plugins .
chmod 600 backups/sky_new.sql backups/plugins.tar.gz
docker compose up -d --wait --wait-timeout 360
```

确认命令成功并检查备份文件后再恢复服务；失败时不要把空文件当作有效备份。每次备份使用不同目录保留历史，并另外安全记录 `.env` 和镜像标签。

恢复建议在新部署/空卷中进行，并先设置为备份时匹配的镜像标签及密钥。以下导入会覆盖 `sky_new` 中同名表，仅在确定要恢复的目标上执行：

```bash
docker compose stop web backend
docker compose up -d --wait --wait-timeout 360 mysql
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot' < backups/sky_new.sql
docker compose run --rm --no-deps --user 0 --entrypoint sh \
  -v "$PWD/backups:/backup:ro" backend \
  -c 'tar -xzf /backup/plugins.tar.gz -C /app/data/plugins && chown -R 10001:10001 /app/data/plugins'
docker compose up -d --wait --wait-timeout 360
```

插件压缩包应解压到空卷，避免旧文件残留。数据库中的插件路径保持 `/app/data/plugins` 不变，因此在另一台服务器恢复后仍有效。首次正式使用前，应在独立机器演练备份恢复。

## 6. 外部 HTTPS 代理

若宿主机已有 Nginx、1Panel 等，将 `.env` 改为：

```dotenv
SKY_HTTP_BIND=127.0.0.1
SKY_HTTP_PORT=8088
```

应用更新后，宿主机 HTTPS Nginx 的 server 配置示例：

```nginx
server {
    listen 443 ssl;
    server_name sky.example.com;
    ssl_certificate /etc/nginx/certs/sky.example.com/fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/sky.example.com/privkey.pem;
    client_max_body_size 60m;

    location / {
        proxy_pass http://127.0.0.1:8088;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 600s;
    }
}
```

HTTP 到 HTTPS 的跳转、证书签发和续期由现有代理负责；将域名替换为实际域名，并正确配置 DNS。代理若运行在另一容器中，`127.0.0.1` 指向代理自身，需要通过共享 Docker 网络访问 `web:80`，不要使用上面的宿主机地址。

## 7. 容器验收与排查

工作流对两种 CPU 架构分别进行容器启动验证。后端冒烟使用独立的临时 MySQL、网络和卷，验证管理员登录、超过 1MB 的插件上传、Runner 任务、配置与账号回调、日志读取、容器重建后数据保留和数据库断连健康状态。使用已有 `frontendIntegrationPluginJar` 作为独立样例；`plugin-demo` 当前会调用其他业务插件，不适合独立冒烟。

在有 Docker 的开发环境手动运行后端冒烟：

```bash
bash ./gradlew :sky-job:frontendIntegrationPluginJar
docker build -t sky-backend:local .
bash scripts/docker-smoke.sh sky-backend:local sky-job/build/fixtures/frontend-integration-plugin.jar
```

前端仓库提供 `bash deploy/docker-smoke.sh IMAGE`，使用临时 Python 后端验证首页、History 路由、带查询参数的 `/api` 转发、2MB POST 和后端容器替换后 DNS 更新。

完整三服务验收应使用一套独立配置和测试数据，依次验证 `/init-admin`、登录、插件上传执行、日志、容器重建以及备份恢复；不能指向现有开发数据库。若使用 CI 的 Python 工具进行完整验收，`initialize` 会创建管理员及测试任务，只能在全新测试部署中运行：

```bash
python3 scripts/deployment-smoke.py http://127.0.0.1 \
  sky-job/build/fixtures/frontend-integration-plugin.jar /tmp/sky-smoke-state.json initialize
docker compose up -d --force-recreate --wait --wait-timeout 360 backend web
python3 scripts/deployment-smoke.py http://127.0.0.1 \
  sky-job/build/fixtures/frontend-integration-plugin.jar /tmp/sky-smoke-state.json verify
```

工具状态文件包含临时测试账号密码，验收后删除。常见问题：

- `unauthorized` / `denied`：核对 GHCR 登录、Token 权限和包可见性。
- 找不到镜像标签：等待对应仓库 CI 成功，核对前后端各自的提交号。
- MySQL 不健康：检查初始化 SQL、磁盘空间、卷版本及数据库日志，不要直接删卷。
- 后端不健康：检查数据库密码、JWT 长度、表结构和内存；健康接口隐藏细节，具体原因看后端日志。
- 插件启动失败：检查 `/app/runner.jar`、Java 25、卷权限和 `/app/data/plugins/logs`，同时确认有足够内存。
- 上传 413：同时检查容器 Nginx、外部代理和后端 multipart 上限。
- 更新后短暂 502：Docker DNS 缓存最长约 10 秒；持续失败则检查后端健康与网络。

本地没有 Docker 时，只能完成代码测试和配置静态检查，不能据此声明镜像或三服务实际启动验收通过。

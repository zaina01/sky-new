# Ubuntu 虚拟机部署记录

验证日期：2026-11-12（Asia/Shanghai）。

## 当前部署

- 系统入口：<http://192.168.59.135/>。
- 初始管理员创建入口：<http://192.168.59.135/init-admin>。部署时未预置管理员或复用 SSH 密码。
- 主机：Ubuntu 24.04，4 核 / 8GB，`linux/amd64`。
- Compose 目录：`/opt/sky-new/backend`；前端源码：`/opt/sky-new/frontend`。
- Compose 项目：`sky-new`，三个服务均已启动并通过健康检查。
- 对外端口：80；数据库、后端 HTTP 和 gRPC 只在内部使用。

| 服务 | 已构建的本地镜像 |
| --- | --- |
| 后端 | `ghcr.io/zaina01/sky-new:vm-20261112` |
| 前端 | `ghcr.io/zaina01/sky-vue-new:vm-20261112` |
| 数据库 | `mysql:8.4` |

这两个应用镜像已在虚拟机上完成多阶段源码构建。镜像名称包含 GHCR 地址，但本次未上传到 GHCR，也未推送 Git 代码。当前实际构建和验证的是 amd64；arm64 发布仍需后续 GitHub Actions 执行验证。

数据库密码及 JWT 密钥均随机生成，保存在服务器 `/opt/sky-new/backend/.env`，权限为 `600`，未写入本文或输出日志。主程序 JVM 堆上限设为 1GB，插件初始堆上限为 256MB；后端以 UID/GID `10001:10001` 运行，系统时区为 Asia/Shanghai。

## 日常操作

通过 SSH 进入虚拟机后：

```bash
cd /opt/sky-new/backend
sudo docker compose ps
sudo docker compose logs --tail 200 backend web mysql
sudo docker compose logs -f backend
```

启动或按现有本地镜像重建容器：

```bash
sudo docker compose up -d --pull never --wait --wait-timeout 360
```

本次镜像仅在虚拟机本地，因此使用 `--pull never`。未来通过 CI 发布 GHCR 镜像后，可修改 `.env` 中的两个标签，再按通用部署文档执行登录、拉取和更新。

```bash
sudo docker compose stop  # 停止，保留容器和数据
sudo docker compose down  # 删除容器和网络，保留数据
```

不要执行 `down -v` 或更改项目名，否则可能删除数据或改用新卷。生产持久化卷为 `sky-new_mysql-data`、`sky-new_plugin-data`。备份、恢复及外部 HTTPS 接入参见 [完整部署文档](docker-deployment.md)。

## 已完成的实际验收

测试使用独立的 `sky-new-acceptance` 项目、数据库、卷和临时管理员账号，未向正式实例写入测试账号或任务。

- 前端首页、History 路由刷新、健康接口、查询参数及 `/api` 路径转发。
- Nginx 的 2MB POST，以及后端更换 IP 后的 DNS 更新。
- MySQL 首次初始化、管理员创建与登录、业务接口鉴权。
- 超过 1MB 的插件上传、Runner 启动、任务成功执行、配置/账号回调及日志读取。
- 保留活跃 Runner 后重建后端；重新登录并读取原插件、任务和日志。
- 数据库及插件目录备份，删除临时项目数据卷，再恢复到空卷；恢复后原账号、插件、任务和日志可访问。
- 数据库停机时，应用健康接口返回 HTTP 503。
- Windows 直接访问虚拟机首页、初始化页面、嵌套路由和健康接口，均返回 HTTP 200。

验收项目的容器、网络和数据卷均已删除；正式项目继续运行。已有的其他项目服务未重启。

## 环境说明

虚拟机直连 Docker Hub 失败，首次构建通过临时 SSH 回环下载桥接获得官方基础镜像；同时传入同版本 Gradle 和依赖缓存完成源码编译。临时桥接及其专用证书信任已移除，没有修改 Docker 的全局代理或重启 Docker。虚拟机上的 `/opt/sky-new/vm-build.sh` 保留了本次构建用的离线 Gradle 适配，仓库中的标准 Dockerfile 和 CI 配置不受影响。

虚拟磁盘为 50GB，但根逻辑卷实际约 24GB；部署及临时文件清理后约剩 4.6GB。卷组另有约 24GB 未分配，本次没有调整分区或逻辑卷大小。

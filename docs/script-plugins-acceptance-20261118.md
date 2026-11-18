# JS / Python 真实 MySQL 与 Linux Docker 验收

日期：2026-11-18（Asia/Shanghai）。本次使用当前工作区源码、独立测试数据库和临时部署，不向正式业务库执行升级或写入测试数据。

## 环境与结果

| 环境 | 版本 | 结果 |
| --- | --- | --- |
| Windows 宿主与真实数据库 | JDK 25、Node 24.11.1、Python 3.12.9、MySQL 8.0.36 | 数据库升级、宿主 API、原生 Runner、真实 Chrome 操作通过 |
| Ubuntu 24.04 Docker，linux/amd64 | JDK 25.0.5、Node 24.22.3、Python 3.12.3、MySQL 8.4.13、Nginx | 源码镜像构建、数据库升级、三服务浏览器联调、重启与恢复通过 |
| 回归 | 宿主 197 项、Java Runner 3 项、原生协议 7 组 | 全部通过；Java Runner 本次为 UP-TO-DATE，其余重新运行 |
| 前端 | 当前 Vue 前端生产构建与 Chrome | 构建通过；浏览器使用真实 API，无模拟响应 |

Linux 验收镜像：`sky-script-acceptance:20261118`，镜像 ID 为 `sha256:6e1c899047142bdf45cf3bf664a0a8b37fb33169d80ca99e2f36ef53e60d7b5f`。后端以 `sky:sky`（UID/GID 10001）运行。

源码构建使用 `Dockerfile.multilang` 的阶段与命令；验收副本仅去掉需额外下载的 Dockerfile syntax 指令，并将 Gradle wrapper 指向同版本 9.2.0 离线发行包。应用与 Runner 在 Linux 容器中从源码编译，没有用 Windows 构建的宿主 JAR 替代。

## 实际覆盖

- 新库执行 `schema-mysql.sql`；旧库基线使用提交 `ac7c4ff74c81d5ad9bc333d3e0824e3afeb1b9e8` 的建表脚本，依次执行四份脚本升级。升级后 Java 插件入口、变量 ID 与密文字段保留；重复名称可插入，新旧两条建库路径的字段和索引一致。
- 两种语言通过真实 API 初始化依赖环境，安装 npm `is-number` / pip `six`，升级成功；指定不存在版本的升级失败，数据库中的当前修订保持不变。
- JS/Python ZIP SDK 插件执行成功；通过真实 gRPC 回调读写配置、账号与数据，只读取启用账号，中文日志可查询，执行详情保存语言、制品 SHA、环境修订和解释器版本。
- `.js`（CommonJS）、`.mjs`（ESM）、`.py` 单文件均成功执行；敏感变量不回显，数据库中无变量明文，脚本环境不继承 `SKY_*` 平台秘密。
- 同实例两个 Job 共享参数与变量；另一实例保持隔离。同名变量的默认合并、指定覆盖、删除覆盖、真实换行、批量启停和禁用后变量缺失均通过；空参数、中文、空格、换行与字面 shell 字符保留。
- 真实 MySQL 并发修改参数的六个请求没有丢失修订；执行记录与实际参数快照一致。运行中修改配置不会改变原进程的参数或修订。
- 跨实例批量操作、超过大小限制的修改均不写入。通过 MySQL 触发器让后续审计插入失败，确认已经写入的变量和修订一起回滚。
- 非零退出码、主动取消、超时、Quartz 定时执行与实例卸载清理通过。旧任务配置会阻止同实例执行；Windows 重启后实际验证启动告警、受影响 Job ID 返回和清理后恢复。
- 真实角色/SELF 数据范围限制、只读配置权限、脚本依赖管理员限制通过。
- Windows 和 Linux 三服务均通过真实 Chrome 登录、环境变量新增、分隔符和多行参数保存、刷新持久化、Job 手动运行弹窗、执行详情及日志检查；两种语言均覆盖。
- Linux 宿主重启后保留配置、密钥对应的密文、历史日志与依赖；将数据库和插件目录备份后删除测试数据卷，恢复至全新卷，SDK 与三种单文件任务再次执行成功。Nginx 路由及 API 转发在后端重建后正常。
- Linux MySQL 停机时健康接口返回 HTTP 503；恢复数据库后连接及脚本执行恢复。

MySQL、宿主和前端部署均应使用仓库 Compose 中的 `Asia/Shanghai` 时区设置。验收最初的自定义 MySQL 容器遗漏此设置，已对齐配置并补充执行时间与耗时一致性断言；不要将只设置 JDBC `serverTimezone` 视为已配置数据库进程时区。

## 验收发现与修复

1. 自动生成的单文件插件 ID 叠加安装/执行 ID，会使 Windows 工作目录超过进程启动限制，产生 `CreateProcess error=267`。脚本执行目录改为运行根目录下的 `scripts/<runId>`，仍按执行隔离；三种扩展名重新执行通过。
2. 数据库写入异常原本进入受鉴权的错误分派，客户端收到 HTTP 401。增加数据库异常处理，返回不含 SQL 或变量值的 HTTP 500 / `500001`；故障注入验证回滚和正常身份继续可用。

## 复验入口

新增 `scripts/accept-script-plugins.py`，支持 `database`、`initialize`、`verify` 三阶段，使用真实 HTTP 与 MySQL 客户端，不模拟 Mapper。`MYSQL_COMMAND` 是客户端命令的 JSON 参数数组，可以是本机 mysql，也可以是 `docker exec -i ... mysql`；密码通过环境或容器内环境提供。

```powershell
$env:MYSQL_COMMAND='["mysql","--host=127.0.0.1","--user=root"]'
$env:MYSQL_PWD='<测试数据库密码>'
python scripts/accept-script-plugins.py database --database sky_script_acceptance_example_20261118 --baseline build/schema-before-scripts.sql --state build/acceptance/state.json --report build/acceptance/database.json
# 将专用宿主指向刚创建的测试库，使用独立插件目录、端口和随机加密密钥，再启动宿主。
python scripts/accept-script-plugins.py initialize --database sky_script_acceptance_example_20261118 --base-url http://127.0.0.1:18081 --state build/acceptance/state.json --report build/acceptance/initialize.json
python scripts/accept-script-plugins.py verify --database sky_script_acceptance_example_20261118 --base-url http://127.0.0.1:18081 --state build/acceptance/state.json --report build/acceptance/verify.json
```

基线文件从上述提交提取。数据库阶段仅接受 `sky_script_acceptance_*`，并拒绝覆盖已经存在的库；另建 `_upgrade` 库验证历史升级。初始化阶段要求测试库中没有用户且没有既有状态文件。状态文件包含临时账号凭据，仅保存在忽略的构建目录；不要提交或发送此文件。

汇总证据见 [验收汇总 JSON](script-plugins-acceptance-20261118.json)。逐阶段 JSON、构建/恢复日志和浏览器截图保存在本机 `build/script-acceptance/`；Linux 的无凭据报告副本在其 `linux-evidence/` 子目录。本次未覆盖 arm64 镜像、正式业务数据升级和生产环境压测；这些不属于本次独立环境验收结论。

同日补充的密钥配置、官方多语言发布工作流和扩展容器冒烟结果，见 [发布前收尾验证](script-plugins-release-readiness-20261118.md)；本页保留原验收时的测试计数。

## 清理

- 已删除本次三个 Linux 验收容器、两个测试数据卷、专用网络、测试备份和临时凭据。保留独立目录中的源码、镜像及无凭据报告，便于复现。
- 已停止 Windows 临时宿主与前端预览，删除本次两个专用 MySQL 测试库及临时账号状态、JWT/加密密钥文件；本机报告与截图保留。
- Ubuntu 原有 `sky-new-web-1`、`sky-new-backend-1`、`sky-new-mysql-1` 均保持运行并通过健康检查；正式库和部署没有更新。

# JS / Python 发布前收尾验证

日期：2026-11-18（Asia/Shanghai）。本记录补充 [真实环境验收](script-plugins-acceptance-20261118.md)，不修改其中历史测试计数。本次仅完成本地改动和独立环境验证，未提交、推送、发布镜像或更新正式部署。

## 发布配置

- 官方后端工作流改为构建 `Dockerfile.multilang`，包含 Java 25、Node 24、Python 3.12 与对应 Runner；手动使用根 `Dockerfile` 仍为 Java 专用镜像。
- CI 增加 Node 单元测试和两种原生 Runner 的真实进程/gRPC 检查。amd64、arm64 分别构建并运行扩展冒烟；两个架构都成功后才能发布最终多架构标签。
- 删除脚本变量加密密钥的共享默认值。Compose 强制提供数据库应用密码、MySQL root 密码、JWT 密钥和 `SKY_SCRIPT_ENV_ENCRYPTION_KEY`；最后一项使用 `openssl rand -base64 32`，解码后必须为 32 字节。
- 更新、重建和恢复必须沿用原有效加密密钥。已有加密变量的旧部署需先外置原有效值，本版不自动重加密或轮换；不能用新生成的密钥覆盖原值。
- 镜像设置系统时区为 `Asia/Shanghai`，即使 Runner 清理 `TZ` 环境变量也保持正确时区；构建上下文排除 Python 缓存和虚拟环境。

## 本轮验证结果

| 检查 | 结果 |
| --- | --- |
| `gradle :sky-job:test :sky-plugin-runner-java:test :sky-job:bootJar :sky-job:frontendIntegrationPluginJar --console=plain` | 构建成功；宿主 199 项通过，含新增密钥配置与跨上下文解密检查；Java Runner 3 项报告通过，本轮为 UP-TO-DATE |
| Node Runner 单元测试 | Linux 临时容器内 3 项通过 |
| `scripts/test-native-runners.py` | Linux 临时容器内 7 组通过，覆盖鉴权、SDK、ID 精度、失败、取消、超时和单文件执行 |
| `scripts/validate-deployment-config.sh` | 显式配置通过；四项必填变量各自缺失或为空的 8 个负向检查均通过 |
| Linux 源码镜像构建 | `sky-script-release:20261118`，linux/amd64，成功 |
| 容器运行身份和时区 | UID 10001；移除进程 `TZ` 后系统偏移仍为 `+0800` |
| 扩展容器冒烟 | Java、Node/Python ZIP SDK、`.js`/`.mjs`/`.py` 均通过 |
| 重建与故障检查 | 同卷同密钥重建后历史日志、依赖修订、参数、敏感变量解密与再次执行通过；MySQL 停机后健康接口返回 503 |
| 静态检查 | Bash 语法、Python 编译、工作流 YAML 解析与 `git diff --check` 通过 |

扩展冒烟同时检查启用账号过滤、配置/数据回调、中文日志、敏感变量接口不回显、重复变量名按 `@@` 合并、空参数/中文空格/真实换行/字面 shell 字符保留、脚本环境无 `SKY_*` 泄漏、非零退出码 9、配置修订与执行时间/耗时一致性。

镜像 ID：`sha256:62dcea21490efdda1c08956668105d8396bbc4a12069069193efc987fbdf4e06`。构建沿用此前独立验收副本的 Gradle 9.2.0 离线发行包；Dockerfile 验证副本仅去掉额外下载的 syntax 指令。宿主与 Java Runner 在 Linux 构建阶段从源码编译。

无凭据构建与测试日志保存在本机忽略目录 `build/release-readiness/release-readiness.log` 和 `build/release-readiness/release-native-checks.log`。临时容器、数据卷、网络和账号状态已清理；Ubuntu 原有三个 `sky-new-*` 容器均健康，正式库和部署未更新。

## 提交和发布边界

本地与 linux/amd64 收尾验证通过，可以进入提交、推送阶段。提交应包含功能源码、Runner、锁文件、SQL、文档、发布配置和测试脚本，排除 `.idea` 的本机改动以及忽略目录中的环境文件、凭据、验收状态和构建产物。

本轮没有执行 arm64 构建，也没有触发 GitHub Actions。推送后的 CI 必须通过两个架构的构建与冒烟，才能确认多架构镜像发布成功。正式已有数据库升级、原密钥迁移和生产压测不包含在本次独立环境结论中；部署前仍应按升级文档准备数据库/插件目录备份并保留原密钥。

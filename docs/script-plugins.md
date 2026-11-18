# JavaScript / Python 原生插件

[插件开发入口与 Java 开发](../PLUGIN_DEVELOPMENT.md)。本文介绍 ZIP SDK 插件的开发、打包与运行环境。

普通 `.js` / `.mjs` / `.py` 单文件程序也已支持，使用安装实例级环境变量与启动参数，详见 [单文件脚本说明](standalone-scripts.md)。下文的 `run(ctx)` 和 ZIP 契约针对 SDK 插件模式。

宿主直接启动 Node.js 或 Python Runner，通过现有 gRPC 协议执行任务。每次执行独立进程；配置、账号、数据和通知继续按安装实例授权。脚本服务插件、在线代码编辑、TypeScript 编译暂不提供。

## 升级与部署

1. 现有数据库按部署版本依次执行尚未应用的四份升级脚本，顺序见 [数据库升级说明](standalone-scripts.md#部署升级)；新数据库直接使用 `sql/schema-mysql.sql`，不要叠加升级脚本。宿主启动不会自动升级已有数据库。
2. 运行器要求 Node.js 24、Python 3.12；Java 仍使用 JDK 25。
3. Node Runner：进入 `sky-plugin-runner-node`，执行 `npm ci`、`npm run build`。
4. Python 代码生成：创建构建 venv，在其中 `pip install -r sky-plugin-runner-python/requirements-build.txt`，再执行 `python sky-plugin-runner-python/generate.py`。运行依赖由平台环境初始化安装，构建工具不安装到系统 Python。
5. 构建 Java：`gradle :sky-job:bootJar :sky-plugin-runner-java:jar`。
6. 部署时一起复制两个 Runner 目录及生成的协议文件。Node Runner 的私有 `node_modules` 必须包含在部署文件中。
7. 在配套 Vue 前端以管理员登录，进入「插件管理 → 脚本依赖」，初始化两种环境，或直接提交第一次安装。

官方发布工作流使用多语言镜像：`docker build -f Dockerfile.multilang -t sky-backend:multilang .`。原有 Dockerfile 继续供手动构建 Java 专用镜像。环境数据位于 `/app/data/plugins`，须挂载持久卷；Compose 还要求在 `.env` 中保存独立的 `SKY_SCRIPT_ENV_ENCRYPTION_KEY`，生成与恢复要求见 [单文件脚本说明](standalone-scripts.md)。

主要环境变量：

| 变量 | 默认值 / 用途 |
|---|---|
| `SKY_PLUGIN_NODE_COMMAND` | `node`；建议绝对路径 |
| `SKY_PLUGIN_PYTHON_COMMAND` | `python`；建议绝对路径 |
| `SKY_PLUGIN_NPM_CLI` | 自动从 Node 安装目录查找 `npm-cli.js`，无法找到时明确配置 |
| `SKY_PLUGIN_NODE_RUNNER` | `./sky-plugin-runner-node/runner.mjs` |
| `SKY_PLUGIN_PYTHON_RUNNER` | `./sky-plugin-runner-python/runner.py` |
| `SKY_PLUGIN_SCRIPT_ROOT` | `./data/plugins/script-environments` |
| `SKY_PLUGIN_SCRIPT_MAX_PROCESSES` | `8`；达到上限时执行失败并提示容量不足 |
| `SKY_PLUGIN_SCRIPT_MAX_LOG_BYTES` | `10485760`；日志达到上限后持续排空并丢弃超额输出 |
| `SKY_PLUGIN_SCRIPT_INSTALL_TIMEOUT` | `10m`；每个包管理子命令的超时 |

Windows 的 npm 配置示例：`D:\App\nodejs\node_modules\npm\bin\npm-cli.js`。平台通过 `node npm-cli.js` 调用 npm，不拼接 cmd/shell 命令。

## 插件包

根目录包含 `sky-script.json` 和入口；参考 `plugin-demo-node`、`plugin-demo-python`。执行 `python scripts/build-script-plugins.py` 后，示例 ZIP 输出到 `build/script-plugins`。

```json
{
  "formatVersion": 1,
  "pluginId": "my-script",
  "version": "1.0.0",
  "displayName": "我的任务",
  "runtime": { "engine": "node", "entry": "main.mjs" },
  "capabilities": { "task": true, "config": true, "account": true, "notification": true, "service": false },
  "configSchema": { "fields": [] },
  "accountSchema": { "fields": [] }
}
```

Python 使用 `engine: python` 和 `.py` 入口；JS 使用 `.mjs`、ESM `export function run(ctx)`，支持 Promise。Python 提供同步 `def run(ctx)`。

与上述清单配套的最小 JavaScript 入口 `main.mjs`：

```javascript
export async function run(ctx) {
  console.log('开始执行', ctx.jobId);
  return ctx.success('完成', { input: ctx.input });
}
```

Python 将清单的 `runtime` 改为 `{"engine":"python","entry":"main.py"}`，入口 `main.py`：

```python
def run(ctx):
    print('开始执行', ctx.job_id, flush=True)
    return ctx.success('完成', {'input': ctx.input})
```

ZIP 打开后应直接看到 `sky-script.json` 和入口文件，不要再套项目目录。平台提供 `ctx`，入口不需要导入或打包平台 SDK。上传 ZIP 后创建安装实例，填写配置与启用账户，再创建 Job 选择该实例并手动执行；在执行详情查看结果与日志。需要第三方依赖时，请管理员在「插件管理 → 脚本依赖」安装对应语言的包。

上传不会导入代码或安装依赖。包内允许自己的代码与资源，不允许 `node_modules`、venv、`site-packages`。路径、符号链接、重复文件名、Windows 保留文件名、解压大小均有检查。单文件上限 16 MiB、累计解压上限 100 MiB、条目上限 10000，压缩上传上限沿用现有配置。

上传后创建安装实例，再配置 Job。脚本实例按任务运行，不提供常驻启动和 JDWP 调试；Maven 依赖接口仅支持 Java。

## SDK

JS 平台方法均返回 Promise，Python 平台方法同步返回。输入在 `ctx.input`；JS 身份属性为 `pluginId/installationId/jobId/requestId`，Python 为 snake_case。标识始终按字符串处理。

| 功能 | 方法 |
|---|---|
| 配置 | `config.get()`、`config.update(value)` |
| 账号 | `accounts.list()`、`accounts.get(id)`、`accounts.update(id, value)` |
| 数据 | `data.get(collection,key)`、`put(collection,key,value)`、`delete(collection,key)`、`list(collection)` |
| 渠道 | JS `notifications.listChannels()`；Python `list_channels()` |
| 通知 | `notifications.send({channelIds,title,content,severity,labels})` |
| 服务 | JS `services.callInstallation(id,operation,data)`；Python `call_installation(...)` |
| 日志 | `log.debug/info/warn/error`；也可直接使用 console / print / logging |
| 结果 | `success(message,data)`、`failure(message)` |

账号返回 `{accountId,displayName,data}`，列表仅包含启用账号。数据列表返回 `{key,data}` 数组。缺失数据返回 null / None。服务方法返回解析后的结果；失败抛异常。通知返回发送回执，Python 回执 ID 也转为字符串。

必须返回 SDK 构造的结果，返回普通对象、忘记 return、非 JSON 值或未捕获异常均失败。平台修改请求不会自动重试。

启动健康检查不导入入口；入口导入受任务期限限制。JS 同步死循环无法处理取消 RPC时，由宿主终止进程。Python 取消事件为 `ctx.cancelled`，JS 为 `ctx.signal`，可用于主动结束耗时操作。

缺包异常会在执行错误和日志中显示，管理员安装后可手动重新运行；平台不自动重跑失败任务。

## 共享依赖

每种语言有一个当前生效的环境。所有操作由管理员提交，串行构建候选修订；成功后在数据库事务中发布环境和成功状态。运行中的任务固定原修订，新任务使用新修订。失败不改变当前环境，重启后未完成操作标记失败。

支持仓库普通包与 npm scoped package。版本可为空（最新兼容稳定版）或精确版本，不支持 URL、Git、本地路径和任意安装命令。其他直接依赖固定原版本，传递依赖由包管理器重新解析；完整实际清单保存在修订快照中。

Node 入口副本位于修订目录下，使 ESM/CommonJS 都能正常向上查找共享 `node_modules`。Python venv 直接在最终路径建立，不移动。平台固定 `grpcio`、`protobuf` 等基础版本；冲突使候选环境失败。

包源支持不含账号密码、query 和 fragment 的 HTTP(S) URL。首版不支持私有仓库凭据配置。

旧环境保留以保证运行中任务可用；不要手动删除仍有运行任务引用的目录。操作日志、环境快照和执行记录中的修订号可以用于排查版本变更。依赖安装可能执行包的构建脚本，适用内部可信插件场景。

## REST 契约

路径均以 `/api/plugins/script-environments` 开头，所有接口要求 `ADMIN` 角色，响应遵循 `{code,msg,data}`。

| 方法与路径 | 请求 / 返回 |
|---|---|
| GET `/` | 两种环境列表 |
| GET `/{engine}` | engine、registry、revision、interpreterVersion、directDependencies、packages |
| GET `/{engine}/health` | available、initialized、interpreterVersion、message |
| GET `/{engine}/operations` | 操作记录，最新更新在前 |
| POST `/{engine}/operations` | `{action,name,version}`；action 为 initialize/install/upgrade/uninstall；立即返回操作 ID |
| PUT `/{engine}/registry` | `{url}` |
| GET `/operations/{id}` | 操作状态与错误 |
| GET `/operations/{id}/log` | `{content}` |
| POST `/operations/{id}/retry` | 仅失败操作可重试，返回新操作 ID |

依赖操作状态：QUEUED、RUNNING、SUCCEEDED、FAILED。重试按重试时的当前环境和包源创建新操作。

插件上传与定义/实例响应新增 `runtimeEngine`、`entryPoint`。执行详情新增 `runtimeEngine`、`artifactSha256`、`environmentRevision`、`interpreterVersion`。

## 验证

- 常规：`gradle test`，Node Runner 目录 `npm test`。
- 原生协议：构建协议后，使用构建 venv 的 Python 执行 `scripts/test-native-runners.py`；`SKY_TEST_NODE` 指定 Node 24。
- 真实 npm/pip 和宿主启动：`gradle :sky-job:nativeScriptIntegrationTest`，使用 `SKY_TEST_NODE`、`SKY_TEST_PYTHON`、`SKY_TEST_NPM_CLI` 指定路径。此测试下载依赖，仅写入临时目录，不连接业务数据库。
- 前端：`npm run type-check`、`npm run test`、`npm run build`。

跨平台认证需在 Windows 和 Linux Docker 各自执行。当前实现采用单宿主节点、本地解释器及共享目录，不提供不可信代码沙箱或跨节点依赖分发。

### 本次验证记录（2026-11-17）

- Windows：后端与 Java Runner 单元回归通过；原生宿主集成测试通过，覆盖两种语言的执行、中文日志、子进程终止、工作副本清理、Token 撤销和宿主关闭。
- 真实 npm/pip 临时环境测试通过：安装、升级、卸载、旧环境继续可用、失败不切换、ESM 导入及传递依赖保留。测试使用模拟持久层，不连接业务 MySQL。
- 两种 Runner 的真实 gRPC 测试通过：身份校验、SDK 平台接口、字符串 ID、错误结果、Python SystemExit、入口导入超时、取消。
- 配套前端位于 `D:\Files\vue\sky-vue-new`，相关 API、权限、页面测试及生产构建通过；Chrome 使用模拟 API 验证依赖页面操作。
- 该次验证未执行真实数据库与 Docker 联调；后续已于 2026-11-18 完成独立真实 MySQL、Windows 与 Linux Docker 验收，见 [完整记录](script-plugins-acceptance-20261118.md)。正式业务库未在验收中升级。

2026-11-18 的多语言发布工作流、密钥配置和扩展容器冒烟结果见 [发布前收尾验证](script-plugins-release-readiness-20261118.md)；其中本地 linux/amd64 已通过，arm64 仍需推送后的 CI 验证。

当前不会自动回收旧环境修订；历史环境会占用磁盘。任务结束会清理该次执行的脚本副本，环境目录须在确认没有任务引用后再维护。

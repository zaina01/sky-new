# 单文件 JS / Python 任务

[插件开发入口与 Java 开发](../PLUGIN_DEVELOPMENT.md)。需要 `run(ctx)` 和平台配置、账号等 SDK 能力时，使用 [JS / Python ZIP SDK 插件](script-plugins.md)。

上传 `.js`、`.mjs` 或 `.py` 即可创建单文件插件，不需要 ZIP、`sky-script.json`、`run(ctx)` 或插件账号/配置 Schema。沿用「上传插件 → 创建安装实例 → 创建任务」。环境变量和启动参数配置在安装实例，实例下所有 Job 共享，Job 不提供覆盖。

## 部署升级

1. 按当前部署版本依次执行尚未应用的升级脚本：`sql/upgrade-script-plugins-mysql.sql` → `sql/upgrade-standalone-scripts-mysql.sql` → `sql/upgrade-instance-script-options-mysql.sql` → `sql/upgrade-script-environment-merge-mysql.sql`。各执行一次；新库直接使用 `sql/schema-mysql.sql`，不要叠加升级脚本。最新脚本将变量名称唯一索引替换为普通索引，须先升级数据库再更新宿主；不改变变量 ID 或密文。
2. 升级前用旧版本清空任务参数和变量，或另行迁移。本次不自动迁移、不删除 `job_script_options`。启动日志列出非空旧配置的 Job ID，实例配置页面也显示这些 ID，相关实例执行会失败并提示处理。不要删除旧表，也不要直接复制旧密文到新表：附加认证数据已改为实例 ID + 变量 ID。
3. 在 **Java 宿主进程的环境变量**中配置 `SKY_SCRIPT_ENV_ENCRYPTION_KEY`，值为 **32 字节随机数据的 Base64 编码**。Windows 可在启动 PowerShell、IDE Run Configuration 或服务环境中设置；Docker 通过容器环境/部署秘密配置注入。对应 `application.yml` 的 `sky.plugin.script-encryption-key`。它不是实例页面内的脚本变量，不能传给用户脚本。
4. 密钥独立于 JWT 密钥，重启/更新后保持不变；首次部署可用 `openssl rand -base64 32` 生成，保存在权限为 `600` 的部署 `.env` 或秘密管理系统中，备份密钥与数据库，勿提交仓库。宿主不再提供固定默认密钥，Compose 强制要求此项。已有加密变量的旧部署需先确认原有效密钥并外置；如果曾依赖旧默认值，应沿用其有效值验证解密，不可直接新生成一个值替换。本版无在线轮换，不自动重加密，换错密钥会导致已有启用变量解密失败。
5. 同步发布宿主与配套 Vue 前端。此前未发布单文件支持的部署还需构建并发布原生 Runner 和协议代码，见 `script-plugins.md`。本次迁移不改变 Runner 协议。解释器仍为 Node.js 24、Python 3.12，共享 npm/pip 环境不变。

所有变量值均用 AES-256-GCM 加密，敏感标记只控制回显。未配置密钥时不能保存新变量值；没有启用变量的执行不需要密钥，禁用变量不参与执行解密。数据库保存版本、nonce 和密文，不保存密钥。

Windows 宿主通过 Job Object 回收 Runner 及其后代，Linux 使用脚本进程组和宿主回收机制。本机可信脚本模式不提供安全沙箱。

## 页面操作

1. 上传脚本、创建安装实例。在实例详情「环境变量」分别新增两行 `dxqy`，值填 `账号1`、`账号2`。默认启用、敏感，可填备注。执行时默认合并为 `账号1@账号2`。每行可独立增删改、启停，列表支持名称/备注搜索、状态筛选、分页和批量操作。
2. 在「启动参数」逐项填写 `--limit`、`2`，可添加、修改、删除、上移、下移，保存整体列表。每项是完整字符串，空值、空格、中文和换行均保留；不需要 shell 引号，不展开 `$VAR`、`%VAR%` 或 `$(...)`。参数按非敏感信息管理。
3. 创建/编辑 Job 时选择实例，页面仅显示「使用安装实例运行配置」提示，不提供环境变量/参数管理链接，请到安装实例维护。手动执行弹窗保留原有入口。手动和定时执行读取同一实例配置；没有配置读取权限仍按原 Job 权限创建/执行，不显示配置值。
4. 不同任务需要不同账号或参数时创建不同实例。修改影响本实例全部任务的下一次执行；已运行任务保持原快照。

脚本通过 `os.getenv('dxqy')` 或 `process.env.dxqy` 读取合并后的值。平台不解析账号，不修改每行值内的空格、换行或 `=`。同名行全部禁用后进程完全没有该变量，脚本可以回退到 `chinaTelecomAccount`；只有一个启用空值时注入空字符串。

敏感值不回显，不提供包含敏感值的合并预览。编辑明确选择「保留原值」「替换值」或「设置为空字符串」。改名可保留原密文。允许完全相同的名称重复添加；`key1` 与 `KEY1` 不能同时存在，包括禁用行和分隔符覆盖规则。

### 合并设置

环境变量页提供实例默认分隔符（默认 `@`）和按变量名覆盖。例如默认 `@`，给 `chinaTelecomAccount` 配置 `#`，则两行 `dxqy` 用 `@` 合并，两行 `chinaTelecomAccount` 用 `#` 合并。

- 支持 `@`、`#`、真实换行和自定义字符串；自定义空字符串表示直接连接。字面 `\n` 不会转成换行，请选择“换行”选项或输入真实换行。
- 仅合并启用行，按原始创建时间、变量 ID 排序。新增行的创建时间保持递增；编辑、禁用后启用不改顺序，删除后重新添加排在后面。
- 不去重、不裁剪、不跳过空值。`aa`、空字符串、`bb` 默认合并为 `aa@@bb`；单行不追加分隔符。
- 删除覆盖规则恢复实例默认值。规则可以提前配置，删除全部变量后仍保留；改名后使用目标名称的规则，不搬迁原规则。
- 每个分隔符最多 128 个 UTF-8 字节、禁止 NUL；最多 128 条覆盖规则，每个名称只能有一条规则。

## 接口契约

路径前缀 `/api/plugins/installations/{installationId}`；沿用 `{code,msg,data}` 包装，ID 为字符串。读取使用 `plugin:config:read`，修改使用 `plugin:config:write`，还校验实例数据范围与 `executionMode=standalone`，不要求 SDK CONFIG 能力。

| 方法 / 路径 | 请求与返回 |
| --- | --- |
| GET `/script-environment-variables` | `keyword` 搜索名称/备注，`enabled` 布尔筛选，`page`/`size` 分页；返回 `{items,total,page,size}` |
| POST `/script-environment-variables` | `{name,value,sensitive?,enabled?,remark?}`；默认敏感、启用 |
| PUT `/script-environment-variables/{id}` | `{name,value?,sensitive?,remark?}`；省略 value 保留，空字符串清空 |
| PATCH `/script-environment-variables/{id}/enabled` | `{enabled:true}` |
| DELETE `/script-environment-variables/{id}` | 删除变量 |
| POST `/script-environment-variables/batch-enabled` | `{ids:["…"],enabled:false}` |
| POST `/script-environment-variables/batch-delete` | `{ids:["…"]}` |
| GET `/script-arguments` | `{arguments:["--limit","2"],revision:"3",legacyJobIds:[],updatedBy?,updatedAt?}` |
| PUT `/script-arguments` | `{arguments:["--limit","2"]}` 整体替换；返回配置与修订 |
| GET `/script-environment-settings` | `{defaultSeparator:"@",overrides:[{name:"chinaTelecomAccount",separator:"#"}],revision:"3"}` |
| PUT `/script-environment-settings` | `{defaultSeparator:"@",overrides:[{name:"chinaTelecomAccount",separator:"#"}]}` 整体替换；返回设置及字符串修订号 |

变量返回 `id`、`name`、`sensitive`、`enabled`、`remark`、`hasValue` 及创建/修改信息；敏感变量没有 `value` 或可回传掩码，非敏感变量可返回明文，存储仍加密。状态/删除操作返回 `{changed:数量}`。批量 ID 必须全部属于当前实例，任一无效则整批失败。

新增变量示例：

```json
{"name":"dxqy","value":"账号1@账号2","sensitive":true,"enabled":true,"remark":"脚本账号"}
```

最多 128 行变量（含禁用项和重复名称）、256 个参数；参数与最终合并后的启用变量 UTF-8 合计最多 24 KiB，包含分隔符和进程字符串终止开销。禁用变量不计入注入大小。保存变量、参数、合并设置及启停操作均校验最终大小，超限则整次操作失败。变量名仅字母、数字、下划线，不能数字开头，不允许 `SKY_*`、`NODE_*`、`PYTHON*`、`PATH` 等保留变量。Runner 另检查目标系统环境长度。

任务创建/更新/查询移除 `scriptOptions`。旧客户端提交非空字段明确报错，需使用实例接口。单文件任务拒绝非空业务 JSON，为兼容允许 `{}`；手动执行没有临时覆盖。Java/ZIP SDK 契约保持不变。

## 存储、快照与执行

`installation_script_options` 保存实例参数、`environmentSettings`、修订、修改人和时间；`installation_script_variable` 保存变量身份、名称查询索引及包含密文的记录。缺省合并设置为 `@` 和空覆盖列表，已有单行变量行为不变。修改与快照事务锁定同一实例配置记录，变量使用当前读，避免混合新旧配置。有效修改递增修订；卸载实例清理两类记录。

执行按 `pluginInstallationId` 一致读取参数、变量、分隔符和修订，只解密启用变量，合并后生成已有不可变 protobuf 选项，无需修改 Runner。`job_execution.script_options_revision` 与执行详情 `scriptOptionsRevision` 只记录修订，不保存变量明文。审计记录变量 ID/名称和操作，合并设置审计仅记录规则数量与修订，不记录值、合并结果或完整参数。

Runner 经认证 gRPC 接收快照，脚本只接收白名单系统变量和实例变量，不继承平台 Token、宿主 gRPC 参数或平台密钥。脚本主动打印的秘密仍会进入日志。

`.js` 为 CommonJS，`.mjs` 为 ESM，`.py` 为普通 Python 程序。UTF-8（允许 BOM）、上限 16 MiB；上传不执行代码或安装依赖。未填写 ID/版本自动生成；更新使用原插件「更新脚本」。原文件按 SHA 保存，副本入口为 `main.js`/`main.mjs`/`main.py`，同一 ID 禁止跨语言/模式替换。

退出码 0 成功，非零失败；`sys.exit(0)` 即使打印“缺少账号”仍代表成功。结果 JSON 保存退出码/信号，不解析 stdout；超时、取消或启动失败时退出码可为空。详情展示语言、制品、依赖环境与实例配置修订。

工作目录独立于代码目录；Python 使用修订 venv，Node 沿标准路径查找共享依赖。stdin 关闭，无交互输入。没有任务附件上传，`--accounts-file` 不会自动获得文件；引用作者自己的辅助模块需使用多文件插件。

## 示例与验证

`plugin-demo-standalone/demo.py`、`demo.js`、`demo.mjs` 可直接上传，按实例页面配置 `dxqy` 或 `chinaTelecomAccount` 和 `--limit` 后执行。

- 后端：`gradle :sky-job:test :sky-plugin-runner-java:test`。
- Runner：构建 venv 执行 `scripts/test-native-runners.py`，`SKY_TEST_NODE` 指向 Node 24。
- 原生集成：`gradle :sky-job:nativeScriptIntegrationTest`，可设置 `SKY_TEST_NODE`、`SKY_TEST_PYTHON`、`SKY_TEST_NPM_CLI`。使用隔离临时目录及模拟持久层，不操作业务库。
- 前端：实例配置 API/页面、Job 表单/执行与详情测试、类型检查、生产构建。

2026-11-18 已完成独立真实 MySQL 升级、并发事务、Windows 与 Linux Docker 全链路及真实浏览器验收，见 [完整记录](script-plugins-acceptance-20261118.md)。此前模拟 API 与单元测试记录保留在 `instance-script-options-validation.md`，不代替此次真实环境结果。

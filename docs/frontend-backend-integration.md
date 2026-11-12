# 前端与真实后端联调

## 自动验收

`frontendIntegrationTest` 使用真实 MySQL，但只允许重置固定数据库 `sky_new_frontend_it`。数据库名校验和显式确认缺一都会终止任务，默认的 `test`、`build` 不会运行它。

PowerShell：

```powershell
$env:SKY_IT_ALLOW_RESET='true'
$env:SKY_IT_DB_URL='jdbc:mysql://localhost:3306/sky_new_frontend_it?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true'
$env:SKY_IT_DB_USERNAME='root'
$env:SKY_IT_DB_PASSWORD='root'
gradle :sky-job:frontendIntegrationTest
```

任务会构建测试专用 TASK 插件、SERVICE 插件和本地依赖 JAR，启动随机 HTTP 端口及独立 gRPC 端口，并完成部门兼容、依赖安装、双实例隔离、主动推送、任务渠道、规则、服务权限 CRUD、真实服务调用日志、分页、历史快照和四类归属转交。执行成功后测试库会保留，便于继续前端联调。

## 启动可供前端访问的宿主

先成功运行自动验收，再使用相同的测试库和插件目录启动宿主：

```powershell
$env:SKY_DB_URL='jdbc:mysql://localhost:3306/sky_new_frontend_it?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:SKY_DB_USERNAME='root'
$env:SKY_DB_PASSWORD='root'
$env:SKY_HTTP_PORT='18080'
$env:SKY_HOST_GRPC_PORT='29090'
$env:SKY_PLUGIN_STORAGE_ROOT=(Resolve-Path 'sky-job/build/frontend-integration/storage').Path
$env:SKY_PLUGIN_ARTIFACT_ROOT=(Resolve-Path 'sky-job/build/frontend-integration/artifacts').Path
$env:SKY_PLUGIN_RUNTIME_WORK_ROOT=(Resolve-Path 'sky-job/build/frontend-integration/runtime').Path
$env:SKY_PLUGIN_RUNTIME_LOG_ROOT=(Resolve-Path 'sky-job/build/frontend-integration/logs').Path
$env:SKY_PLUGIN_MAVEN_REPOSITORY_ROOT=(Resolve-Path 'sky-job/build/frontend-integration/maven-repository').Path
$env:SKY_PLUGIN_RUNNER_JAR=(Resolve-Path 'sky-plugin-runner-java/build/libs/sky-plugin-runner-java-1.0-SNAPSHOT.jar').Path
gradle :sky-job:bootRun
```

联调地址为 `http://localhost:18080/api`。管理员账号为 `frontend_it_admin / FrontendIt!2026`，SELF 角色账号为 `frontend_it_target / FrontendIt!2026`。可直接运行 [frontend-backend-integration.http](../http/frontend-backend-integration.http)。

## 关键协议

- 实例渠道池读取：`GET /plugins/installations/{installationId}/notification-channels`，成功响应的 `data` 是渠道 ID 数组；只有请求成功后前端才能把该数组作为已知状态。
- 实例渠道池更新：`PUT` 同路径，请求字段为 `channelIds`。
- 任务创建字段为 `notificationChannelIds`；后续编辑只使用 `PUT /jobs/{id}/notification-channels` 和 `channelIds`。
- 本地依赖上传字段为 `file/groupId/artifactId/version`；返回 `coordinate`。定义安装的 `mode` 只接受 `merge` 或 `replace`。
- 未匹配路由及业务资源不存在返回 HTTP 404、`code=404001`。导出失败返回 `application/json` 的 `{code,msg,data}`；浏览器配置 `responseType=blob` 时该 JSON 位于 Blob 内容中。
- 历史只保证用户 ID、部门 ID 和 `installationId` 快照稳定；本次不新增显示名称快照字段。

## SERVICE 插件与调用日志

自动联调会额外安装一个 SERVICE 实例：

- `pluginId`：`frontend-integration-service`
- 实例名称：`Frontend IT Echo Service`
- 支持操作：`echo`
- 调用方：任务实例 `Frontend IT B`

服务调用没有面向浏览器的直调 REST 接口。它必须由任务 runner 使用绑定了 `installationId/jobId/executionId` 的运行令牌发起。创建权限后，可通过现有任务触发：

```http
POST /api/jobs/{jobId}/run
Content-Type: application/json

{
  "inputJson": "{\"serviceInstallationId\":\"目标 SERVICE installationId\"}",
  "timeoutMillis": 30000
}
```

权限请求为：

```json
{
  "callerInstallationId": "Frontend IT B 的 installationId",
  "targetInstallationId": "Frontend IT Echo Service 的 installationId",
  "operation": "echo",
  "enabled": true
}
```

自动验收结束后，`plugin_service_permission` 和 `plugin_service_call_log` 均保留非空数据。前端可通过 `/plugin-service/permissions` 完成查询、详情、启停、删除和重建，通过 `/plugin-service/calls` 查询真实调用，并通过 `/plugin-service/calls/{id}/logs` 读取目标 SERVICE runner 日志。完整顺序见 HTTP 请求集。

## 手工 seed 与清理

自动任务已经准备完整数据。若只需在已初始化的测试库中补充“停用部门遗留用户”和 CUSTOM 停用部门关系，可运行 `sql/frontend-integration-seed-mysql.sql`。清理脚本只删除固定测试库：

```text
sql/frontend-integration-cleanup-mysql.sql
```

## Plugin account list columns

Plugins continue to define only the account schema. Each user selects up to five columns independently for each plugin installation:

- `GET /plugins/installations/{installationId}/accounts/view-preference` returns the ordered selection, safe candidate fields, and `maxFields=5`.
- `PUT /plugins/installations/{installationId}/accounts/view-preference` accepts `{"fields":["expireAt","region"]}`; an empty list restores the fixed-column-only default.
- Candidate fields exclude the account ID, sensitive/password fields, and complex `LIST`, `MAP`, and `OBJECT_LIST` values.
- Account list items contain `displayValues` for the current user's selection and no longer contain `accountJson`. Use the account detail endpoint when editing the full account payload.
- The backend revalidates saved preferences against the current plugin schema and removes fields that were deleted or became sensitive after a plugin upgrade.

The account ID, display name, enabled state, creation time, and update time are fixed columns. The first UI version must not hide them or add custom-field filtering, sorting, or column widths. Selectable field types are `TEXT`, `NUMBER`, `SWITCH`, `RADIO`, `SELECT`, `CHECKBOX`, `MULTI_SELECT`, `DATE`, `TIME`, and `DATETIME`; `availableFields` supplies each field's label, type, and option values.

The list projection preserves JSON scalar and array types. A missing, empty, or conditionally hidden value is returned as JSON `null`. The frontend should render choice option labels, booleans as states, arrays as tags, typed dates/times using the matching formatter, and `null` as `-`. Duplicate, unknown, non-displayable, or more than five requested fields return HTTP 400.

## Plugin account file import and export

Account CSV/XLSX files preserve complex values as strict JSON inside each cell:

- `LIST`, `MULTI_SELECT`, and `OBJECT_LIST` cells contain JSON arrays, such as `["商品A","商品B"]`.
- `MAP` cells contain JSON objects, such as `{"key":"value"}`.
- Blank array cells are imported as `[]`; blank `MAP` cells are imported as `{}`.
- Export writes compact JSON for array and object values so the generated file can be imported without changing their JSON types.
- Invalid JSON or a value with the wrong container type fails only that row and is reported in the import response.

Scalar account fields keep their existing text-cell behavior. Comma-separated list shorthand is not supported.

## 账户排序与分组

账户分页列表与管理端 CSV/XLSX 导出均按 `created_at ASC, id ASC` 排序：最早创建的账户在前，相同创建时间以数据库 ID 升序固定顺序。编辑账户、启停及移动分组不会改变创建时间。

分组按插件安装实例隔离，一个账户最多属于一个组，也可以未分组。分组名称去除首尾空格后为 1–128 字符，同实例内按 MySQL 排序规则唯一（默认大小写不敏感）。所有路径均以 `/plugins/installations/{installationId}` 为前缀，沿用 `{code,msg,data}` 响应包装。

| 接口 | 请求及返回 |
| --- | --- |
| `GET /account-groups` | `data` 为分组数组，按创建时间、分组 ID 升序；每项包含 `groupId/name/accountCount/createdAt/updatedAt`，数量包含启用和停用账户 |
| `POST /account-groups` | `{"name":"主账户"}`；返回创建的分组 |
| `PUT /account-groups/{groupId}` | `{"name":"新名称"}`；返回重命名后的分组 |
| `DELETE /account-groups/{groupId}` | 事务内将所属账户转为未分组并删除分组，返回 `{"success":true}`，账户及账户 JSON 保留 |
| `PATCH /accounts/{accountId}/group` | `{"groupId":123}` 分配分组，`{"groupId":null}` 移出分组；返回账户详情 |
| `PATCH /accounts/group` | `{"accountIds":["a","b"],"groupId":123}`；`groupId:null` 批量移出分组，返回 `installationId/groupId/total/succeeded/failed/receipts`，每项 receipt 为 `accountId/success/message` |

- 分组读取使用 `plugin:account:read`，管理及账户移动使用 `plugin:account:write`；还需具备对应安装实例的数据范围和 ACCOUNT 能力。
- 账户列表及详情增加 `groupId/groupName`，未分组时均为 JSON `null`。列表继续返回 `displayValues`，不返回 `accountJson`。
- 列表与导出支持 `groupId`、`ungrouped`，并可与 `enabled/accountId/displayName` 组合。缺省查询全部；`groupId=123` 查询指定组；`ungrouped=true` 查询未分组；`ungrouped=false` 不限制分组。`groupId` 和 `ungrouped=true` 同时使用返回 400，列表 `total` 使用相同过滤条件。
- 创建和更新账户请求可带 `groupId`。有效 ID 表示分配；省略或 `null` 保持已有分组，新账户默认未分组。清空分组必须使用分组 PATCH。
- 分组 PATCH 必须包含 `groupId` 字段，明确传入 `null` 和漏传不同；漏传返回 400。批量移动先校验目标分组，之后逐项处理，重复、非法及不存在的账户 ID 记录为单项失败。
- 不存在或其他实例的分组返回 404；非法参数返回 400；重名返回 409。批量请求的目标组无效时整次请求失败，不移动任何账户。
- 导出支持按组过滤，CSV/XLSX 列结构不增加分组列。文件导入及插件回调新建账户默认未分组，覆盖已有账户时保留分组。删除安装实例同时清理账户及分组。

已有数据库在停止宿主后、部署新版本前执行一次 `sql/add-plugin-account-groups-mysql.sql`（默认目标 `sky_new`；其他库需明确修改 `USE`）。脚本为一次性增量升级，新增表、可空列、索引及复合外键，不修改历史账户 JSON、启用状态或时间戳。全新数据库使用更新后的 `sql/schema-mysql.sql`，无需再执行增量脚本。

真实 MySQL 自动验收仅操作 `sky_new_frontend_it`，覆盖全新建表、历史账户升级、排序分页、跨实例外键、分组增删改、批量移动、筛选导出、导入及回调保留分组、安装实例清理。

## 批量添加插件服务权限

`POST /api/plugin-service/permissions/batch` 一次为同一调用方实例、同一目标实例保存多个方法：

```json
{
  "callerInstallationId": "caller-installation",
  "targetInstallationId": "service-installation",
  "operations": ["lookup", "refresh", "status"],
  "enabled": true
}
```

- 沿用 `{code,msg,data}` 包装，`data` 是本次保存的权限数组，元素结构与原单条接口一致，顺序为去重后的输入顺序。
- Operation 去除首尾空格、按完整方法名去重后为 1–100 项，每项满足 `[A-Za-z0-9._-]{1,128}`。空数组、空白或 null 元素、非法方法名返回 400；错误消息中的 index 从 1 开始。前端粘贴时先忽略空行。
- `enabled` 省略或为 null 时默认 true；已存在的同一权限更新为本次启用状态，未提交的其他权限保持原状。
- 使用 `plugin:service-permission:write`，调用方与目标方均须在写入数据范围内，目标方须支持 SERVICE。所有校验通过后在一个事务内保存，失败整批回滚。
- 原单条创建、查询、启停和删除接口保持兼容，无需数据库迁移。部署时先更新后端，再发布使用批量接口的前端。

本地验证：`gradle :sky-job:test --tests com.sky.job.plugin.callback.PluginServicePermissionBatchTest --tests com.sky.job.security.PluginServicePermissionBatchSecurityTest`。数据库测试使用 H2 MySQL 模式运行实际权限 Mapper 和 Spring 事务，覆盖重复授权、保留其他权限及写入中途失败回滚，不连接业务 MySQL。

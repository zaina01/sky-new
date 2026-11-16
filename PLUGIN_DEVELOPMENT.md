# Sky 插件开发文档

本文档面向插件开发者，说明如何基于当前项目的 `sky-plugin-api` 开发、打包和上传 Java 插件。

## 1. 插件模型

Sky 插件运行在平台启动的独立 Java 插件运行器进程中。插件 jar 上传到宿主应用后，宿主会读取插件元数据、配置 schema、账户 schema 和依赖声明，并在任务执行或服务调用时启动插件运行器。

插件可以提供以下能力：

- `TASK`：作为任务插件被定时或手动执行。
- `CONFIG`：声明插件配置 schema，并通过运行时 API 读取/更新配置。
- `ACCOUNT`：声明插件账户 schema，并读取/更新平台管理的启用账户。
- `NOTIFICATION`：通过平台通知渠道发送通知。
- `SERVICE`：作为服务插件被其他插件调用。

插件只需要依赖 `sky-plugin-api`，不要依赖宿主应用或插件运行器实现。

## 2. 基础要求

### 2.1 Java 版本

项目目标 Java 版本为 Java 25，插件运行器也要求运行在 JDK 25 上。插件建议使用 Java 25 编译。

### 2.2 平台依赖不能打入插件 jar

插件 jar 不应该内置以下平台依赖：

- `sky-plugin-api`
- `sky-plugin-protocol`
- `sky-plugin-runner-java`
- gRPC 平台相关依赖

宿主上传校验会拒绝内置这些平台类的插件。插件自己的第三方依赖可以打进 fat jar，也可以通过依赖声明文件交给平台解析安装。

### 2.3 不再使用旧 manifest

旧的：

```text
META-INF/sky-plugin.json
```

已经废弃，上传时会被拒绝。当前插件元数据来自：

1. SPI 文件中的插件主类；或
2. 没有 SPI 文件时，jar 内唯一一个带 `@SkyPlugin` 的插件类。

推荐始终提供 SPI 文件。

## 3. 最小任务插件

```java
package com.open.demo;

import com.sky.plugin.api.PluginCapability;
import com.sky.plugin.api.SkyPlugin;
import com.sky.plugin.api.TaskExecutionContext;
import com.sky.plugin.api.TaskPlugin;
import com.sky.plugin.api.TaskResult;

@SkyPlugin(
        pluginId = "demo",
        version = "1.0.0",
        displayName = "Demo 插件",
        description = "最小任务插件",
        capabilities = {PluginCapability.TASK}
)
public class DemoTaskPlugin implements TaskPlugin {
    @Override
    public TaskResult run(TaskExecutionContext context) {
        System.out.println("pluginId=" + context.pluginId());
        System.out.println("jobId=" + context.jobId());
        System.out.println("requestId=" + context.requestId());
        System.out.println("inputJson=" + context.inputJson());
        return TaskResult.success("执行成功", "{\"ok\":true}");
    }
}
```

任务插件 SPI 文件：

```text
src/main/resources/META-INF/services/com.sky.plugin.api.TaskPlugin
```

内容为插件主类全限定名：

```text
com.open.demo.DemoTaskPlugin
```

## 4. `@SkyPlugin` 元数据

```java
@SkyPlugin(
        pluginId = "demo",
        version = "1.0.0",
        displayName = "Demo 插件",
        description = "示例插件",
        capabilities = {
                PluginCapability.TASK,
                PluginCapability.CONFIG,
                PluginCapability.ACCOUNT,
                PluginCapability.NOTIFICATION,
                PluginCapability.SERVICE
        },
        configClass = DemoConfig.class,
        accountClass = DemoAccount.class
)
```

| 字段 | 说明 |
|---|---|
| `pluginId` | 插件唯一 ID。上传覆盖、运行时、配置、账户和任务都按它关联。 |
| `version` | 插件版本。 |
| `displayName` | 展示名称。 |
| `description` | 插件描述。 |
| `runtime` | 运行时类型，当前默认 `JAVA_PROCESS`。 |
| `capabilities` | 插件能力集合。 |
| `configClass` | 插件配置 schema 类，不需要配置时省略。 |
| `accountClass` | 插件账户 schema 类，不需要账户时省略。 |

`pluginId` 不要随意修改；修改后平台会把它当作另一个新插件。

## 5. SPI 注册规则

任务插件：

```text
META-INF/services/com.sky.plugin.api.TaskPlugin
```

服务插件：

```text
META-INF/services/com.sky.plugin.api.ServicePlugin
```

规则：

- SPI 文件内容是插件主类全限定名。
- 同一个插件 jar 必须最终定位到一个插件主类。
- 插件主类必须带 `@SkyPlugin`。
- 插件主类必须直接实现 `TaskPlugin` 或 `ServicePlugin`。
- 一个类可以同时实现 `TaskPlugin` 和 `ServicePlugin`，两个 SPI 文件都写同一个类名。

## 6. 任务上下文 `TaskExecutionContext`

任务执行和服务调用都会得到 `TaskExecutionContext`：

```java
String pluginId();
String jobId();
String requestId();
String inputJson();
PluginConfigClient config();
PluginAccountClient accounts();
PluginNotificationClient notifications();
PluginServiceClient services();
PluginDataClient data();
```

常用基础信息：

```java
context.pluginId();
context.jobId();
context.requestId();
context.inputJson();
```

日志建议使用插件自己的日志或标准输出：

```java
System.out.println("业务日志");
System.err.println("错误日志");
```

也可以使用 SLF4J：

```java
private static final Logger log = LoggerFactory.getLogger(DemoTaskPlugin.class);
log.info("任务开始");
```

插件日志会写入插件运行器日志文件。当前 `TaskExecutionContext` 不再提供 `logger()` 方法。

## 7. 任务返回值

```java
return TaskResult.success();
return TaskResult.success("执行成功");
return TaskResult.success("执行成功", "{\"count\":10}");
return TaskResult.failure("执行失败原因");
```

`TaskResult` 字段：

| 字段 | 说明 |
|---|---|
| `succeeded` | 是否成功。 |
| `message` | 结果消息。 |
| `resultJson` | 结果 JSON 字符串。 |

## 8. 插件配置

### 8.1 定义配置类

```java
package com.open.demo;

import com.sky.plugin.api.ConfigField;
import com.sky.plugin.api.ConfigFieldType;
import com.sky.plugin.api.PluginConfig;

@PluginConfig("Demo 配置")
public class DemoConfig {
    @ConfigField(label = "消息", type = ConfigFieldType.TEXT, required = true)
    private String message;

    @ConfigField(label = "是否启用", type = ConfigFieldType.SWITCH)
    private Boolean enabled;

    @ConfigField(label = "认证方式", type = ConfigFieldType.RADIO, options = {"密码", "IP"})
    private String auth;

    @ConfigField(label = "密码", type = ConfigFieldType.TEXT, visibleWhenField = "auth", visibleWhenEquals = "密码", required = true)
    private String password;

    @ConfigField(label = "IP 地址", type = ConfigFieldType.TEXT, visibleWhenField = "auth", visibleWhenEquals = "IP", required = true)
    private String ip;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
```

当前配置字段类型：

```java
ConfigFieldType.TEXT
ConfigFieldType.NUMBER
ConfigFieldType.PASSWORD
ConfigFieldType.SWITCH
ConfigFieldType.RADIO
ConfigFieldType.SELECT
ConfigFieldType.CHECKBOX
ConfigFieldType.MULTI_SELECT
ConfigFieldType.LIST
ConfigFieldType.MAP
ConfigFieldType.OBJECT_LIST
ConfigFieldType.DATE
ConfigFieldType.TIME
ConfigFieldType.DATETIME
```

### 8.2 声明配置能力

```java
@SkyPlugin(
        pluginId = "demo",
        version = "1.0.0",
        capabilities = {PluginCapability.TASK, PluginCapability.CONFIG},
        configClass = DemoConfig.class
)
```

### 8.3 条件展示字段

`@ConfigField` 支持条件展示：

```java
@ConfigField(label = "认证方式", type = ConfigFieldType.RADIO, options = {"password", "ip"})
private String authType;

@ConfigField(label = "密码", visibleWhenField = "authType", visibleWhenEquals = "password")
private String password;

@ConfigField(label = "IP", visibleWhenField = "authType", visibleWhenIn = {"ip"})
private String ip;

@ConfigField(label = "启用代理", type = ConfigFieldType.SWITCH)
private Boolean proxyEnabled;

@ConfigField(label = "代理地址", visibleWhenField = "proxyEnabled", visibleWhenEquals = "true")
private String proxyHost;
```

语义：

- 不设置 `visibleWhenField`：字段始终显示。
- `visibleWhenField + visibleWhenEquals`：控制字段等于指定值时显示。
- `visibleWhenField + visibleWhenIn`：控制字段值在集合中时显示。

上传时会校验：

- 控制字段必须存在。
- 字段不能依赖自身。
- `visibleWhenEquals` 和 `visibleWhenIn` 不能同时设置。
- 设置控制字段时必须设置条件值。
- 如果控制字段是 `ConfigFieldType.SWITCH`，条件值只能写 `"true"` 或 `"false"`；后端输出 schema 时会转成 JSON boolean。

后端保存到 `config_schema_json` 的形态示例：

```json
{
  "fields": [
    {
      "name": "authType",
      "label": "认证方式",
      "type": "RADIO",
      "required": false,
      "options": ["password", "ip"]
    },
    {
      "name": "password",
      "label": "密码",
      "type": "TEXT",
      "required": false,
      "options": [],
      "visibleWhen": {
        "field": "authType",
        "equals": "password"
      }
    },
    {
      "name": "proxyEnabled",
      "label": "启用代理",
      "type": "SWITCH",
      "required": false,
      "options": []
    },
    {
      "name": "proxyHost",
      "label": "代理地址",
      "type": "TEXT",
      "required": false,
      "options": [],
      "visibleWhen": {
        "field": "proxyEnabled",
        "equals": true
      }
    }
  ]
}
```

前端负责根据 `visibleWhen` 动态显示字段。当前前端策略是：隐藏字段不展示、不参与 required 校验，但值会保留在表单状态中，保存时也会继续写入 `configJson`，因此切换配置组并保存不会清空另一组配置内容。

### 8.4 成组列表配置

如果一个配置项本身是一组可重复的关联字段，例如“商品名 + 排序序号”，可以使用 `ConfigFieldType.OBJECT_LIST`：

```java
import com.sky.plugin.api.ConfigField;
import com.sky.plugin.api.ConfigFieldType;
import com.sky.plugin.api.PluginConfig;
import java.util.List;

@PluginConfig("商品配置")
public class ProductConfig {
    @ConfigField(
            label = "商品列表",
            type = ConfigFieldType.OBJECT_LIST,
            required = true,
            itemClass = ProductItem.class
    )
    private List<ProductItem> products;

    public static class ProductItem {
        @ConfigField(label = "商品名", type = ConfigFieldType.TEXT, required = true)
        private String productName;

        @ConfigField(label = "排序序号", type = ConfigFieldType.NUMBER, required = true)
        private Integer sortOrder;

        @ConfigField(
                label = "规格列表",
                type = ConfigFieldType.OBJECT_LIST,
                itemClass = VariantItem.class
        )
        private List<VariantItem> variants;
    }

    public static class VariantItem {
        @ConfigField(label = "规格名", required = true)
        private String name;

        @ConfigField(label = "库存", type = ConfigFieldType.NUMBER, required = true)
        private Integer stock;
    }
}
```

后端保存到 `config_schema_json` 的形态示例：

```json
{
  "fields": [
    {
      "name": "products",
      "label": "商品列表",
      "type": "OBJECT_LIST",
      "required": true,
      "options": [],
      "itemSchema": {
        "fields": [
          {
            "name": "productName",
            "label": "商品名",
            "type": "TEXT",
            "required": true,
            "options": []
          },
          {
            "name": "sortOrder",
            "label": "排序序号",
            "type": "NUMBER",
            "required": true,
            "options": []
          },
          {
            "name": "variants",
            "label": "规格列表",
            "type": "OBJECT_LIST",
            "required": false,
            "options": [],
            "itemSchema": {
              "fields": [
                {
                  "name": "name",
                  "label": "规格名",
                  "type": "TEXT",
                  "required": true,
                  "options": []
                },
                {
                  "name": "stock",
                  "label": "库存",
                  "type": "NUMBER",
                  "required": true,
                  "options": []
                }
              ]
            }
          }
        ]
      }
    }
  ]
}
```

对应 `configJson` 形态示例：

```json
{
  "products": [
    {
      "productName": "商品A",
      "sortOrder": 1,
      "variants": [
        {"name": "红色", "stock": 10},
        {"name": "蓝色", "stock": 8}
      ]
    }
  ]
}
```

限制：

- `OBJECT_LIST` 必须设置 `itemClass`。
- `itemClass` 必须是当前插件 jar 内的类。
- `itemClass` 不要求带 `@PluginConfig`，但至少要有一个字段声明 `@ConfigField`。
- `OBJECT_LIST` 不能设置 `options`。
- `OBJECT_LIST` 可以递归嵌套，顶层对象列表计为第 1 层，最多 16 层。
- `itemClass` 不能引用当前递归链中的祖先类；不同 sibling 分支可以复用同一个 item 类。
- 非 `OBJECT_LIST` 字段不能设置 `itemClass`。
- 子字段的 `visibleWhen` 只能引用同一行 item 内的 sibling 字段。

前端渲染建议：`OBJECT_LIST` 渲染为可新增/删除行的成组列表控件，每一行按 `itemSchema.fields` 复用现有字段渲染器；遇到子字段 `type = OBJECT_LIST` 时继续递归渲染其 `itemSchema`。每一层父字段的 `required` 都表示对应数组至少一行，子字段 `required` 表示该行里可见字段必填。

### 8.5 读取配置

```java
DemoConfig config = context.config().get(DemoConfig.class);
System.out.println(config.getMessage());
```

读取原始 JSON：

```java
String configJson = context.config().getConfigJson();
```

### 8.5 更新配置

```java
DemoConfig config = new DemoConfig();
config.setMessage("hello");
config.setEnabled(true);
boolean ok = context.config().update(config);
```

## 9. 插件账户

账户适合保存账号、token、cookie、第三方登录态等数据。

### 9.1 定义账户类

```java
package com.open.demo;

import com.sky.plugin.api.AccountField;
import com.sky.plugin.api.AccountFieldType;
import com.sky.plugin.api.AccountId;
import com.sky.plugin.api.PluginAccount;

@PluginAccount("Demo 账户")
public class DemoAccount {
    @AccountId
    @AccountField(label = "账户 ID", type = AccountFieldType.TEXT, required = true, readonly = true)
    private String accountId;

    @AccountField(label = "用户名", type = AccountFieldType.TEXT, required = true)
    private String username;

    @AccountField(label = "密码", type = AccountFieldType.PASSWORD, sensitive = true)
    private String password;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
```

当前账户字段类型：

```java
AccountFieldType.TEXT
AccountFieldType.NUMBER
AccountFieldType.PASSWORD
AccountFieldType.SWITCH
AccountFieldType.RADIO
AccountFieldType.SELECT
AccountFieldType.CHECKBOX
AccountFieldType.MULTI_SELECT
AccountFieldType.LIST
AccountFieldType.MAP
AccountFieldType.OBJECT_LIST
AccountFieldType.DATE
AccountFieldType.TIME
AccountFieldType.DATETIME
```

`@AccountId` 标记账户主键字段。该字段必须同时声明 `@AccountField`，且不能是敏感字段。

### 9.2 条件展示字段

`@AccountField` 支持与 `@ConfigField` 相同的条件展示属性：

```java
@AccountField(label = "认证方式", type = AccountFieldType.SELECT, options = {"password", "token"})
private String authType;

@AccountField(label = "密码", type = AccountFieldType.PASSWORD,
        sensitive = true, visibleWhenField = "authType", visibleWhenEquals = "password")
private String password;

@AccountField(label = "Token", sensitive = true,
        visibleWhenField = "authType", visibleWhenIn = {"token"})
private String token;
```

- `visibleWhenField + visibleWhenEquals` 表示控制字段等于指定值时显示。
- `visibleWhenField + visibleWhenIn` 表示控制字段值在集合中时显示。
- 控制字段必须是同一个账户类、或同一个 `OBJECT_LIST` item 内的字段，不能引用自身。
- `visibleWhenEquals` 和 `visibleWhenIn` 不能同时设置。
- `SWITCH` 控制字段的条件值只能是 `"true"` 或 `"false"`，生成 Schema 时会转换为 JSON boolean。

后端将条件写入账户 Schema 的 `visibleWhen` 属性，账户表单渲染端负责根据该属性控制字段展示。

### 9.3 成组列表账户字段

可重复的关联账户字段可以使用 `AccountFieldType.OBJECT_LIST`：

```java
import java.util.List;

@AccountField(
        label = "凭据列表",
        type = AccountFieldType.OBJECT_LIST,
        required = true,
        itemClass = Credential.class
)
private List<Credential> credentials;

public static class Credential {
    @AccountField(label = "名称", required = true)
    private String name;

    @AccountField(label = "Token", type = AccountFieldType.PASSWORD, sensitive = true)
    private String token;

    @AccountField(
            label = "请求头",
            type = AccountFieldType.OBJECT_LIST,
            itemClass = Header.class
    )
    private List<Header> headers;
}

public static class Header {
    @AccountField(label = "名称", required = true)
    private String name;

    @AccountField(label = "值", sensitive = true)
    private String value;
}
```

限制与配置 `OBJECT_LIST` 基本一致：

- 必须设置 `itemClass`，且该类必须位于当前插件 jar 内。
- item 类不要求带 `@PluginAccount`，但至少要有一个字段声明 `@AccountField`。
- `OBJECT_LIST` 不能设置 `options`，可以递归嵌套且最多 16 层。
- `itemClass` 不能引用当前递归链中的祖先类；不同 sibling 分支可以复用同一个 item 类。
- 非 `OBJECT_LIST` 字段不能设置 `itemClass`。
- 任意层 item 字段都不能声明 `@AccountId`。
- 子字段的 `visibleWhen` 只能引用同一行 item 内的 sibling 字段。

### 9.4 声明账户能力

```java
@SkyPlugin(
        pluginId = "demo",
        version = "1.0.0",
        capabilities = {PluginCapability.TASK, PluginCapability.ACCOUNT},
        accountClass = DemoAccount.class
)
```

### 9.5 读取账户

平台只会向插件返回启用账户。

```java
List<DemoAccount> accounts = context.accounts().list(DemoAccount.class);
```

读取指定账户：

```java
DemoAccount account = context.accounts()
        .get("account-1", DemoAccount.class)
        .orElseThrow();
```

读取第一个启用账户：

```java
DemoAccount account = context.accounts()
        .first(DemoAccount.class)
        .orElseThrow();
```

读取原始 JSON：

```java
List<String> accountJsonList = context.accounts().listJson();
```

### 9.6 更新账户

```java
DemoAccount account = new DemoAccount();
account.setAccountId("account-1");
account.setUsername("demo");
account.setPassword("secret");
boolean ok = context.accounts().update(account);
```

## 10. 插件私有数据 `context.data()`

`context.data()` 是当前插件私有的持久化数据存储，适合保存登录态、同步游标、缓存、业务状态等。数据按 `pluginId` 隔离，插件不能访问其他插件的数据。

写入 JSON：

```java
context.data().putJson("session", "main", "{\"token\":\"abc\"}");
```

写入对象：

```java
record LoginState(String token, String expiresAt) {}
context.data().put("session", "main", new LoginState("abc", "2026-12-31T00:00:00Z"));
```

读取 JSON：

```java
String json = context.data().getJson("session", "main").orElse("{}");
```

读取对象：

```java
LoginState state = context.data()
        .get("session", "main", LoginState.class)
        .orElseThrow();
```

列举 collection：

```java
List<PluginDataRecord> records = context.data().listJson("session");
```

删除：

```java
boolean deleted = context.data().delete("session", "main");
```

命名限制：

- `collection`：`[a-zA-Z0-9._-]{1,128}`
- `key`：`[a-zA-Z0-9._:-]{1,255}`

## 11. 插件通知

插件需要声明 `NOTIFICATION` 能力：

```java
@SkyPlugin(
        pluginId = "demo",
        version = "1.0.0",
        capabilities = {PluginCapability.TASK, PluginCapability.NOTIFICATION}
)
```

列出渠道：

```java
List<NotificationChannelInfo> channels = context.notifications().listChannels();
```

发送到指定渠道：

```java
NotificationSendResult result = context.notifications().send(
        "email-main",
        "任务完成",
        "Demo 插件任务已完成"
);
```

发送到全部启用渠道：

```java
NotificationSendResult result = context.notifications().sendAll(
        "任务完成",
        "Demo 插件任务已完成"
);
```

使用完整请求：

```java
NotificationSendResult result = context.notifications().sendAll(
        new NotificationSendRequest(
                List.of(),
                "任务异常",
                "Demo 插件任务失败",
                "ERROR",
                Map.of("pluginId", context.pluginId(), "jobId", context.jobId())
        )
);
```

查看发送结果：

```java
System.out.println(result.success());
System.out.println(result.message());
result.receipts().forEach(receipt -> System.out.println(receipt.status()));
```

## 12. 插件服务调用

插件可以调用其他具备 `SERVICE` 能力的插件：

```java
PluginServiceResponse response = context.services().call(
        "target-plugin-id",
        "queryStatus",
        "{\"id\":\"10001\"}"
);

if (response.success()) {
    System.out.println(response.resultJson());
} else {
    System.err.println(response.message());
    System.err.println(response.errorType());
    System.err.println(response.errorMessage());
}
```

服务调用要求：

- 目标插件已上传。
- 目标插件具备 `SERVICE` 能力。
- 目标服务运行器能正常启动。
- 平台侧允许当前插件调用目标插件对应 operation。
- 调用必须在平台配置的服务调用超时时间内返回。

## 13. 服务型插件

服务插件实现 `ServicePlugin`：

```java
package com.open.demo;

import com.sky.plugin.api.PluginCapability;
import com.sky.plugin.api.PluginServiceRequest;
import com.sky.plugin.api.PluginServiceResponse;
import com.sky.plugin.api.ServicePlugin;
import com.sky.plugin.api.SkyPlugin;
import com.sky.plugin.api.TaskExecutionContext;

@SkyPlugin(
        pluginId = "demo-service",
        version = "1.0.0",
        displayName = "Demo 服务插件",
        capabilities = {PluginCapability.SERVICE}
)
public class DemoServicePlugin implements ServicePlugin {
    @Override
    public PluginServiceResponse call(PluginServiceRequest request, TaskExecutionContext context) {
        if ("ping".equals(request.operation())) {
            return PluginServiceResponse.success("{\"pong\":true}");
        }
        return PluginServiceResponse.failed("Unsupported operation: " + request.operation());
    }
}
```

服务插件 SPI 文件：

```text
src/main/resources/META-INF/services/com.sky.plugin.api.ServicePlugin
```

内容：

```text
com.open.demo.DemoServicePlugin
```

## 14. 同时支持任务和服务

```java
@SkyPlugin(
        pluginId = "demo-task-service",
        version = "1.0.0",
        capabilities = {
                PluginCapability.TASK,
                PluginCapability.SERVICE,
                PluginCapability.CONFIG,
                PluginCapability.ACCOUNT,
                PluginCapability.NOTIFICATION
        },
        configClass = DemoConfig.class,
        accountClass = DemoAccount.class
)
public class DemoTaskServicePlugin implements TaskPlugin, ServicePlugin {
    @Override
    public TaskResult run(TaskExecutionContext context) {
        return TaskResult.success("任务执行成功");
    }

    @Override
    public PluginServiceResponse call(PluginServiceRequest request, TaskExecutionContext context) {
        return PluginServiceResponse.success("{\"ok\":true}");
    }
}
```

同时提供两个 SPI 文件，内容都写同一个类名：

```text
META-INF/services/com.sky.plugin.api.TaskPlugin
META-INF/services/com.sky.plugin.api.ServicePlugin
```

## 15. 插件依赖

插件第三方依赖有两种方式。

### 15.1 Fat jar

把插件自有第三方依赖打进插件 jar，但不能打入平台依赖。

### 15.2 依赖声明文件

在插件 jar 中添加：

```text
META-INF/sky-plugin-dependencies.json
```

内容：

```json
{
  "dependencies": [
    "org.jsoup:jsoup:1.17.2",
    "com.fasterxml.jackson.core:jackson-databind:2.17.2"
  ]
}
```

限制：

- 坐标格式必须是 `groupId:artifactId:version`。
- 每个坐标段只允许字母、数字、下划线、点、短横线。
- 单段最大长度 128。
- 不支持动态版本：`LATEST`、`RELEASE`、`+`。
- 不支持 Maven range：例如 `[1.0,2.0)`。
- 默认不允许 `-SNAPSHOT`。
- 不允许声明平台依赖：`sky-plugin-api`、`sky-plugin-protocol`、`sky-plugin-runner-java`、`io.grpc`。

平台也支持上传本地 jar 到共享 Maven 仓库。上传后会保存坐标和文件元数据，其他插件可以通过同一坐标复用该本地依赖，不需要重复上传同一个 jar。

## 16. Gradle 示例

当前仓库内插件模块可参考 `plugin-demo/build.gradle`：

```gradle
plugins {
    id 'java'
}

dependencies {
    compileOnly project(':sky-plugin-api')
}
```

独立插件工程可以使用已发布或本地安装的 `sky-plugin-api`，原则是：

```gradle
repositories {
    mavenCentral()
}

dependencies {
    compileOnly 'io.github.zaina01:sky-plugin-api:<version>'
}
```

将 `<version>` 替换为已经发布、且与目标平台兼容的 SDK 版本。API 与 protocol 共用独立的 `sdkVersion`，不要使用主程序或 Docker 镜像标签作为 Maven 版本。

尚未发布时，可在平台源码根目录运行 `:sky-plugin-protocol:publishToMavenLocal :sky-plugin-api:publishToMavenLocal`，然后在独立插件工程的仓库配置中加入 `mavenLocal()`；默认本地 SDK 版本为 `1.0.0-SNAPSHOT`。维护者的签名、Central 上传和 GitHub Actions 操作见 [SDK 发布文档](docs/maven-central-publishing.md)。

如果使用 SLF4J API：

```gradle
dependencies {
    compileOnly 'io.github.zaina01:sky-plugin-api:<version>'
    implementation 'org.slf4j:slf4j-api:2.0.17'
}
```

如果构建 fat jar，要确保 `sky-plugin-api`、`sky-plugin-protocol`、`sky-plugin-runner-java` 和 gRPC 平台依赖不被打包进去。

## 17. 推荐目录结构

```text
my-plugin/
  build.gradle
  src/
    main/
      java/
        com/open/demo/
          DemoTaskPlugin.java
          DemoConfig.java
          DemoAccount.java
      resources/
        META-INF/
          services/
            com.sky.plugin.api.TaskPlugin
          sky-plugin-dependencies.json
```

`sky-plugin-dependencies.json` 只有在需要平台解析第三方依赖时才需要。

## 18. 当前仓库示例插件

可参考：

```text
plugin-demo/src/main/java/com/open/demo/TaskServiceImpl.java
plugin-demo/src/main/java/com/open/demo/DemoConfig.java
plugin-demo/src/main/java/com/open/demo/DemoAccount.java
plugin-demo/src/main/resources/META-INF/services/com.sky.plugin.api.TaskPlugin
```

构建示例插件：

```bash
gradle :plugin-demo:jar
```

或使用本地指定 Gradle：

```bash
/d/App/gradle-9.2.0/bin/gradle :plugin-demo:jar
```

## 19. 开发流程建议

1. 创建插件模块或独立插件工程。
2. 添加 `sky-plugin-api` 为 `compileOnly`。
3. 编写插件主类，实现 `TaskPlugin` 或 `ServicePlugin`。
4. 在插件主类上添加 `@SkyPlugin`。
5. 如需配置，编写 `@PluginConfig` 类并在 `@SkyPlugin.configClass` 中声明。
6. 如需账户，编写 `@PluginAccount` 类并在 `@SkyPlugin.accountClass` 中声明。
7. 添加 SPI 文件。
8. 如需第三方依赖，选择 fat jar 或 `META-INF/sky-plugin-dependencies.json`。
9. 构建 jar。
10. 上传插件 jar。
11. 在平台配置插件参数、账户、依赖、通知渠道和服务调用权限。
12. 创建任务或调用服务验证。

## 20. 常见注意事项

### 20.1 配置组切换和保存

条件配置字段只影响前端展示。当前前端实现中，隐藏字段不展示、不参与 required 校验，但值仍保留在前端表单状态里，保存时也会继续提交到 `configJson`。因此只要字段曾经填写过，切换到其他配置组并保存也不会清空这组隐藏字段；后端会按前端提交的完整配置 JSON 保存。

### 20.2 任务超时和服务调用超时不同

任务执行超时来自任务自身的 `timeout_millis` 和运行时最大任务超时限制。插件服务调用超时来自插件运行时配置中的服务调用超时设置。两者不是同一个配置。

### 20.3 任务超时会停止运行器进程

任务执行完成、失败或超时后，宿主会停止对应插件运行器进程。

### 20.4 服务型插件可能按需启动

调用服务插件时，如果目标插件服务运行器未运行，宿主会尝试启动目标插件运行器。服务调用需要在配置的 deadline 内完成。

### 20.5 插件不要跨插件读写数据

`context.data()` 只访问当前插件自己的数据。如果需要插件间交互，应通过 `context.services().call(...)` 调用服务型插件。

### 20.6 账户只返回启用项

`context.accounts().list(...)` 和 `first(...)` 只返回平台中启用的账户。

### 20.7 输出必须是合法 JSON 时由插件负责

`resultJson`、服务调用请求/响应、配置和 data JSON 都是字符串边界。需要作为 JSON 使用时，插件应保证字符串内容是合法 JSON。

## 21. 最小验证代码

```java
@Override
public TaskResult run(TaskExecutionContext context) {
    System.out.println("pluginId=" + context.pluginId());
    System.out.println("inputJson=" + context.inputJson());

    context.data().putJson("test", "hello", "{\"value\":\"world\"}");
    String data = context.data().getJson("test", "hello").orElse("{}");

    return TaskResult.success("OK", data);
}
```

如果这段代码可以正常执行，说明插件加载、任务调用、上下文和私有数据存储都已连通。

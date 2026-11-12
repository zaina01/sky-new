# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Commands

- Build everything: `gradle build`
- Build a module: `gradle :sky-job:build`
- Run the Spring Boot host app: `gradle :sky-job:bootRun`
- Build the demo plugin artifact: `gradle :plugin-demo:jar`
- Run all tests: `gradle test`
- Run tests for one module: `gradle :sky-job:test`
- Run one test class: `gradle :sky-job:test --tests com.example.ClassName`
- Regenerate gRPC/protobuf Java sources: `gradle :sky-plugin-protocol:generateProto`

The Gradle wrapper may need to download Gradle; if that times out and system Gradle is installed, use `gradle` directly. The project targets Java 25 via Gradle toolchains.

## Repository architecture

This is a Java 25 multi-module Gradle project:

- `sky-job`: Spring Boot host application. It exposes REST APIs under `/api`, uses Spring Security/JWT, Quartz scheduling, MyBatis XML persistence, MySQL, and gRPC host services for plugin callbacks.
- `sky-plugin-protocol`: protobuf/gRPC contract shared by host and plugin runner. The proto source is `src/main/proto/sky_plugin.proto`; generated Java is produced by the protobuf Gradle plugin.
- `sky-plugin-api`: public API for plugin authors, including `TaskPlugin`, `TaskExecutionContext`, plugin config/account clients, and annotations such as `@SkyPlugin`, `@PluginConfig`, `@PluginAccount`, `@ConfigField`, `@AccountField`, and `@AccountId`.
- `sky-plugin-runner-java`: standalone Java process runner for uploaded plugins. The host starts this platform runner jar and passes the uploaded plugin jar path; the runner loads the plugin with `ServiceLoader<TaskPlugin>`, starts the plugin gRPC server, talks back to the host over gRPC, and uses SLF4J simple logging so plugin logger output goes to the runner process log file.
- `plugin-demo`: sample plugin jar that does not embed the runner. It demonstrates SPI registration, `@SkyPlugin`, typed config/account access, and plugin logging.

## Host application structure

`sky-job` is organized by domain packages rather than layers. Controllers call services, services call Store classes, and Store classes delegate to MyBatis mapper interfaces plus XML files under `sky-job/src/main/resources/mapper/**`.

Important domains:

- `scheduler`: scheduled job definitions, Quartz integration, job execution state, and execution orchestration.
- `plugin.artifact`: plugin upload, artifact validation, metadata extraction, and runtime registration.
- `plugin.runtime`: runtime abstraction and process-based plugin lifecycle management.
- `plugin.callback`: gRPC callbacks from plugin processes into the host for config/account/data/log/notification operations.
- `plugin.storage`: plugin config/account/data/task-log/audit persistence.
- `notification`: system and plugin-triggered notification channels, rules, send history, and retry worker.
- `security`, `user`, `system`, `audit`, `retention`: auth, RBAC, audit/login/system logs, and cleanup jobs.

MyBatis mapper scanning is configured in `com.sky.job.config.MyBatisConfig`. When adding a new persistence package, add its mapper package there and put XML under `src/main/resources/mapper/**` so it matches `mybatis.mapper-locations`.

## Plugin model

Uploaded Java plugin jars no longer use `META-INF/sky-plugin.json`. That legacy manifest is rejected. Metadata is discovered from:

1. `META-INF/services/com.sky.plugin.api.TaskPlugin` with exactly one provider, or
2. exactly one class annotated with `@SkyPlugin` if no SPI file exists.

The host extracts annotation metadata and generated config/account schemas at upload time without instantiating the plugin. Uploaded plugin jars are loaded by the platform `sky-plugin-runner-java` process and must not embed `sky-plugin-runner-java`, `sky-plugin-api`, `sky-plugin-protocol`, or gRPC platform classes. Plugin jars may be fat jars for plugin-owned third-party dependencies only. The runner uses `ServiceLoader<TaskPlugin>` from the uploaded plugin jar and requires the provider class to have `@SkyPlugin` with the runtime plugin id.

The plugin runner process must run on JDK 25. If the default `java` command is not JDK 25, set `SKY_PLUGIN_JAVA_COMMAND` to the JDK 25 `java` executable.

Config and account runtime APIs are JSON on the host boundary but typed inside the plugin runner process:

- `context.config().get(MyConfig.class)` deserializes config JSON in the runner.
- `context.accounts().list(MyAccount.class)` only returns enabled accounts.
- `context.config().update(configObj)` serializes the object.
- `context.accounts().update(accountObj)` serializes the object and reads account id from the field annotated with `@AccountId`.

Plugin code should use its own logging/printing such as SLF4J, `System.out`, or `System.err`; execution logs are read from the plugin runner `.log` file through process stdout/stderr redirection. `context.logger()` is deprecated and no longer writes task logs into `plugin_task_log`.

## Runtime flow

Job execution in `JobExecutionService` loads plugin runtime metadata, starts the configured platform runner jar through `ProcessPluginRuntimeAdapter`, passes the uploaded plugin artifact path as the runner argument, invokes the plugin task over gRPC, records execution state, emits audit entries, and sends notification events. Process runtime startup sets environment variables such as `SKY_PLUGIN_ID`, `SKY_PLUGIN_INSTANCE_ID`, `SKY_PLUGIN_PORT`, `SKY_HOST_GRPC_PORT`, and `SKY_PLUGIN_TOKEN`; the host validates the token on plugin callbacks.

Plugin artifact upload registers rows in `plugin_runtime`. Re-uploading the same plugin id replaces runtime metadata and resets state to `STOPPED`.

## Persistence notes

Persistence uses MyBatis XML mappers. Keep business rules in Store or Service classes; mapper XML should contain SQL and dynamic filters only. Java records with primitive constructor parameters must use MyBatis primitive aliases in constructor mappings, for example `_long`, `_int`, and `_boolean`, not `long`, `int`, or `boolean`.

JSON columns are passed as `String` and cast in SQL with `CAST(#{...} AS JSON)`. Store classes are responsible for defaulting blank JSON to `{}` or `[]` where needed.

## Configuration

Default host settings are in `sky-job/src/main/resources/application.yml`:

- HTTP port defaults to `8080`; context path is `/api`.
- MySQL defaults to `jdbc:mysql://localhost:3306/sky_new` with `root/root`.
- Multipart upload defaults to 50 MB.
- Plugin artifact/runtime/log roots default under `./data/plugins`.
- Host gRPC port defaults to `19090`.
- Quartz uses an in-memory job store.

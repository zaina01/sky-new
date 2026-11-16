# 插件 SDK 发布到 Maven Central

本文面向项目维护者。插件开发者的接口与打包说明见 [插件开发文档](../PLUGIN_DEVELOPMENT.md)。

## 发布内容与版本

按当前依赖关系，需要一起发布：

| Maven 坐标 | 内容 |
| --- | --- |
| `io.github.zaina01:sky-plugin-api:<SDK版本>` | 插件接口、注解及配置、账户等客户端接口 |
| `io.github.zaina01:sky-plugin-protocol:<SDK版本>` | API 当前传递依赖的 Protobuf / gRPC 协议 |

两个 SDK 模块共用 `sdkVersion`，默认值在项目 `gradle.properties` 中，为 `1.0.0-SNAPSHOT`。发布正式版本时通过 `-PsdkVersion=1.0.0` 覆盖；主程序、Runner 和示例插件仍使用原有版本，Docker 的固定 JAR 路径不变。

Vanniktech Maven Publish 插件会生成二进制 JAR、源码 JAR、真实 Javadoc JAR、POM、校验和及配置密钥后的 PGP 签名。POM 包含项目地址、MIT 许可证、开发者和 SCM 信息。开发、测试和本地暂存不需要 Central 凭据或签名私钥。

这里的 `1.0.0` 是首个正式版本的示例；只有 Central Portal 正式发布完成后，其他用户才能从 Maven Central 下载该版本。已发布版本不能覆盖，后续修改应增加 SDK 版本号。

## 1. 准备 Central 账号和签名

1. 使用 GitHub 账号登录 [Central Portal](https://central.sonatype.com/)，确认已验证 `io.github.zaina01` 命名空间。
2. 在账号页面生成 User Token，保存其用户名和密码。它们不是 GitHub 凭据或 Central 登录密码。
3. 生成有签名能力的 GPG 密钥，妥善保存私钥密码，并将公钥发布到公共密钥服务器。
4. 导出 ASCII 格式的完整私钥供 CI 使用，包括 `BEGIN PGP PRIVATE KEY BLOCK` 与 `END PGP PRIVATE KEY BLOCK` 头尾。私钥不要提交到仓库、放进项目 `gradle.properties` 或输出到构建日志。

参考 [Sonatype 命名空间验证](https://central.sonatype.org/register/namespace/)、[发布要求](https://central.sonatype.org/publish/requirements/) 和 [Vanniktech 发布说明](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)。

## 2. 设置 GitHub Actions Secrets

在后端仓库 Settings → Secrets and variables → Actions → New repository secret，分别添加：

| Secret 名称 | 内容 |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central User Token 的用户名 |
| `MAVEN_CENTRAL_PASSWORD` | Central User Token 的密码 |
| `SIGNING_KEY` | 完整的 ASCII 格式 GPG 私钥，不是公钥或指纹 |
| `SIGNING_PASSWORD` | 该私钥的密码；无密码的密钥可留空 |

工作流将这些 Secret 映射到 `ORG_GRADLE_PROJECT_mavenCentralUsername`、`ORG_GRADLE_PROJECT_mavenCentralPassword`、`ORG_GRADLE_PROJECT_signingInMemoryKey` 和 `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`。私钥在内存中用于签名，无需导入 CI 的 GPG 密钥目录。

## 3. 本地检查，无需上传

安装 JDK 25 后，在项目根目录执行（PowerShell）：

```powershell
.\gradlew.bat stageSdk "-PsdkVersion=1.0.0"
python scripts/verify-sdk-publication.py build/sdk-repository 1.0.0
```

Linux Bash 使用 `bash ./gradlew` 和 `python3` 执行相同任务。

产物位于 `build/sdk-repository/io/github/zaina01/` 下的两个模块目录中。检查脚本验证二进制、源码和 Javadoc 包、必填 POM 元数据，以及 API 对同版本 protocol 的依赖。没有配置签名密钥时，本地暂存会跳过签名，不影响普通测试和构建。

需要在独立插件工程中试用尚未发布的 SDK，可先安装到 Maven Local：

```powershell
.\gradlew.bat :sky-plugin-protocol:publishToMavenLocal :sky-plugin-api:publishToMavenLocal
```

插件工程添加 `mavenLocal()` 和 `mavenCentral()` 仓库，并以 `compileOnly` 引用 `io.github.zaina01:sky-plugin-api:1.0.0-SNAPSHOT`。该命令仅安装在当前开发机，不会发布到公网。

## 4. 上传到 Central Portal

将发布配置提交到默认分支后，有两种触发方式：

- **首次发布推荐手动触发**：GitHub Actions → SDK Maven Central → Run workflow，选择默认分支 `main`，填写 `sdk_version`，例如 `1.0.0`。
- **标签触发**：将 `sdk-v1.0.0` 这样的 Git 标签推送到远程。工作流从标签提取版本号；`sdk-v*` 与容器镜像使用的 `v*` 标签独立。

```bash
git tag sdk-v1.0.0
git push origin sdk-v1.0.0
```

PR 和相关源码推送仅运行普通测试与无密钥的本地 SDK 暂存检查，不上传。手动上传只允许默认分支；其他分支的手动运行仅检查。上传任务串行执行，避免两次发布同时操作 Central。

上传任务会先校验正式版本及必填凭据，再签名并上传两个 SDK 模块。正式版本接受 `1.0.0`、`1.1.0-rc.1` 等形式，不接受 `-SNAPSHOT`。SDK 当前的默认开发版本不可直接用于正式上传。

上传采用 **手动确认发布**：工作流成功表示上传完成，前往 [Central Portal Deployments](https://central.sonatype.com/publishing/deployments) 检查验证结果。若出现校验错误，修正后重新上传；状态为 `VALIDATED` 后点击 **Publish**。

发布和同步完成后才能通过 `mavenCentral()` 引用，通常需要等待一段时间。不要在版本尚未可下载时让插件开发者切换依赖。

## 5. 插件开发者引用

确认目标版本已发布后，独立插件项目的 Gradle 配置示例：

```groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    compileOnly 'io.github.zaina01:sky-plugin-api:1.0.0'
}
```

平台依赖使用 `compileOnly`，不能打进上传的插件 JAR。运行时由平台 Runner 提供实现；开发者无需 Maven Central 账号或 Token。

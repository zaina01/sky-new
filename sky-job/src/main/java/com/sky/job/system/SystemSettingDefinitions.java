package com.sky.job.system;

import com.sky.job.notification.NotificationProperties;
import com.sky.job.plugin.artifact.PluginArtifactProperties;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeProperties;
import com.sky.job.plugin.storage.PluginStorageProperties;
import com.sky.job.retention.SkyRetentionProperties;
import com.sky.job.security.SkySecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
@EnableConfigurationProperties({
        SkyRetentionProperties.class,
        NotificationProperties.class,
        PluginStorageProperties.class,
        PluginArtifactProperties.class,
        ProcessPluginRuntimeProperties.class,
        SkySecurityProperties.class
})
public class SystemSettingDefinitions {
    public static final String RETENTION_ENABLED = "sky.retention.enabled";
    public static final String RETENTION_SCHEDULED_ENABLED = "sky.retention.scheduled-enabled";
    public static final String RETENTION_CRON = "sky.retention.cron";
    public static final String RETENTION_DRY_RUN = "sky.retention.dry-run";
    public static final String RETENTION_MINIMUM_DAYS = "sky.retention.minimum-retention-days";
    public static final String RETENTION_BATCH_SIZE = "sky.retention.batch-size";
    public static final String RETENTION_JOB_EXECUTION_DAYS = "sky.retention.job-execution-days";
    public static final String RETENTION_PLUGIN_TASK_LOG_DAYS = "sky.retention.plugin-task-log-days";
    public static final String RETENTION_PLUGIN_AUDIT_LOG_DAYS = "sky.retention.plugin-audit-log-days";
    public static final String RETENTION_LOGIN_LOG_DAYS = "sky.retention.login-log-days";
    public static final String RETENTION_SYSTEM_LOG_DAYS = "sky.retention.system-log-days";
    public static final String RETENTION_NOTIFICATION_SEND_HISTORY_DAYS = "sky.retention.notification-send-history-days";

    public static final String NOTIFICATION_ALERT_CHANNEL_IDS = "sky.notification.alert-channel-ids";
    public static final String NOTIFICATION_MAX_TITLE_BYTES = "sky.notification.max-title-bytes";
    public static final String NOTIFICATION_MAX_CONTENT_BYTES = "sky.notification.max-content-bytes";
    public static final String NOTIFICATION_MAX_PREVIEW_CHARS = "sky.notification.max-preview-chars";
    public static final String NOTIFICATION_HTTP_TIMEOUT = "sky.notification.http-timeout";
    public static final String NOTIFICATION_RETRY_ENABLED = "sky.notification.retry-enabled";
    public static final String NOTIFICATION_RETRY_CRON = "sky.notification.retry-cron";
    public static final String NOTIFICATION_RETRY_BATCH_SIZE = "sky.notification.retry-batch-size";
    public static final String NOTIFICATION_RETRY_MAX_ATTEMPTS = "sky.notification.retry-max-attempts";
    public static final String NOTIFICATION_RETRY_INITIAL_DELAY = "sky.notification.retry-initial-delay";
    public static final String NOTIFICATION_RETRY_MAX_DELAY = "sky.notification.retry-max-delay";
    public static final String NOTIFICATION_RETRY_STALE_TIMEOUT = "sky.notification.retry-stale-timeout";

    public static final String PLUGIN_STORAGE_MAX_DATA_JSON_BYTES = "sky.plugin.storage.max-data-json-bytes";
    public static final String PLUGIN_STORAGE_MAX_CONFIG_JSON_BYTES = "sky.plugin.storage.max-config-json-bytes";
    public static final String PLUGIN_STORAGE_MAX_ACCOUNT_JSON_BYTES = "sky.plugin.storage.max-account-json-bytes";
    public static final String PLUGIN_STORAGE_MAX_LOG_MESSAGE_BYTES = "sky.plugin.storage.max-log-message-bytes";
    public static final String PLUGIN_STORAGE_SELECT_ALL_LIMIT = "sky.plugin.storage.select-all-limit";
    public static final String PLUGIN_STORAGE_MAX_DATA_ROWS_PER_PLUGIN = "sky.plugin.storage.max-data-rows-per-plugin";
    public static final String PLUGIN_UPLOAD_MAX_ARTIFACT_BYTES = "sky.plugin.upload.max-artifact-bytes";

    public static final String PLUGIN_RUNTIME_STARTUP_TIMEOUT = "sky.plugin.runtime.process.startup-timeout";
    public static final String PLUGIN_RUNTIME_STOP_TIMEOUT = "sky.plugin.runtime.process.stop-timeout";
    public static final String PLUGIN_RUNTIME_TASK_TIMEOUT = "sky.plugin.runtime.process.task-timeout";
    public static final String PLUGIN_RUNTIME_SERVICE_CALL_TIMEOUT = "sky.plugin.runtime.process.service-call-timeout";
    public static final String PLUGIN_RUNTIME_MAX_SERVICE_CALL_TIMEOUT = "sky.plugin.runtime.process.max-service-call-timeout";
    public static final String PLUGIN_RUNTIME_SERVICE_IDLE_TIMEOUT = "sky.plugin.runtime.process.service-idle-timeout";
    public static final String PLUGIN_RUNTIME_SERVICE_IDLE_SCAN_INTERVAL = "sky.plugin.runtime.process.service-idle-scan-interval";
    public static final String PLUGIN_RUNTIME_MAX_RESTARTS = "sky.plugin.runtime.process.max-restarts";
    public static final String PLUGIN_RUNTIME_MAX_HEAP = "sky.plugin.runtime.process.max-heap";

    public static final String SECURITY_JWT_EXPIRATION = "sky.security.jwt-expiration";

    public static final Set<String> RETENTION_SCHEDULER_KEYS = Set.of(RETENTION_ENABLED, RETENTION_SCHEDULED_ENABLED, RETENTION_CRON);
    public static final Set<String> NOTIFICATION_SCHEDULER_KEYS = Set.of(NOTIFICATION_RETRY_ENABLED, NOTIFICATION_RETRY_CRON);

    private final Map<String, SystemSettingDefinition> definitions;

    public SystemSettingDefinitions(
            SkyRetentionProperties retention,
            NotificationProperties notification,
            PluginStorageProperties pluginStorage,
            PluginArtifactProperties pluginArtifact,
            ProcessPluginRuntimeProperties pluginRuntime,
            SkySecurityProperties security
    ) {
        List<SystemSettingDefinition> values = List.of(
                bool(RETENTION_ENABLED, "RETENTION", "启用数据清理", "是否允许执行数据保留清理任务", "数据清理策略", retention::enabled),
                bool(RETENTION_SCHEDULED_ENABLED, "RETENTION", "启用定时清理", "是否按 cron 自动执行数据清理", "数据清理策略", retention::scheduledEnabled),
                cron(RETENTION_CRON, "RETENTION", "清理任务 Cron", "数据清理任务的 Quartz cron 表达式", "数据清理策略", "例如 0 0 3 * * ?", retention::cron),
                bool(RETENTION_DRY_RUN, "RETENTION", "仅预演清理", "启用后只统计待清理数据，不实际删除", "数据清理策略", retention::dryRun),
                integer(RETENTION_MINIMUM_DAYS, "RETENTION", "最小保留天数", "所有保留天数配置都不能低于该值", "天", "数据清理策略", "例如 7", retention::minimumRetentionDays, 1, 3650),
                integer(RETENTION_BATCH_SIZE, "RETENTION", "单批清理数量", "每次清理任务最多处理的数据条数", "条", "数据清理策略", "例如 1000", retention::batchSize, 1, 100000),
                retentionDays(RETENTION_JOB_EXECUTION_DAYS, "任务执行记录保留天数", "任务执行历史记录的保留天数", retention::jobExecutionDays),
                retentionDays(RETENTION_PLUGIN_TASK_LOG_DAYS, "插件任务日志保留天数", "插件任务运行日志的保留天数", retention::pluginTaskLogDays),
                retentionDays(RETENTION_PLUGIN_AUDIT_LOG_DAYS, "插件审计日志保留天数", "插件审计日志的保留天数", retention::pluginAuditLogDays),
                retentionDays(RETENTION_LOGIN_LOG_DAYS, "登录日志保留天数", "用户登录日志的保留天数", retention::loginLogDays),
                retentionDays(RETENTION_SYSTEM_LOG_DAYS, "系统日志保留天数", "系统操作日志的保留天数", retention::systemLogDays),
                retentionDays(RETENTION_NOTIFICATION_SEND_HISTORY_DAYS, "通知发送历史保留天数", "通知发送历史记录的保留天数", retention::notificationSendHistoryDays),

                stringList(NOTIFICATION_ALERT_CHANNEL_IDS, "NOTIFICATION", "默认告警通道", "系统告警默认使用的通知通道 ID 列表", "通知通道", "输入通道 ID", notification::alertChannelIds),
                integer(NOTIFICATION_MAX_TITLE_BYTES, "NOTIFICATION", "标题最大字节数", "通知标题允许的最大 UTF-8 字节数", "字节", "通知内容限制", "例如 512", notification::maxTitleBytes, 1, 8192),
                integer(NOTIFICATION_MAX_CONTENT_BYTES, "NOTIFICATION", "内容最大字节数", "通知正文允许的最大 UTF-8 字节数", "字节", "通知内容限制", "例如 8192", notification::maxContentBytes, 1, 1048576),
                integer(NOTIFICATION_MAX_PREVIEW_CHARS, "NOTIFICATION", "预览最大字符数", "通知历史中内容预览的最大字符数", "字符", "通知内容限制", "例如 500", notification::maxPreviewChars, 1, 10000),
                duration(NOTIFICATION_HTTP_TIMEOUT, "NOTIFICATION", "HTTP 通知超时", "Webhook 和 PushPlus 的默认 HTTP 请求超时时间", "", "通知发送策略", "例如 10s", notification::httpTimeout, "PT0.1S", "PT1M", durationRange(Duration.ofMillis(100), Duration.ofSeconds(60))),
                bool(NOTIFICATION_RETRY_ENABLED, "NOTIFICATION", "启用失败重试", "是否启用通知发送失败后的自动重试", "失败重试策略", notification::retryEnabled),
                cron(NOTIFICATION_RETRY_CRON, "NOTIFICATION", "重试任务 Cron", "通知重试扫描任务的 Quartz cron 表达式", "失败重试策略", "例如 0/30 * * * * ?", notification::retryCron),
                integer(NOTIFICATION_RETRY_BATCH_SIZE, "NOTIFICATION", "单批重试数量", "每次重试任务最多领取的通知数量", "条", "失败重试策略", "例如 20", notification::retryBatchSize, 1, 1000),
                integer(NOTIFICATION_RETRY_MAX_ATTEMPTS, "NOTIFICATION", "最大重试次数", "每条通知最多尝试发送的次数", "次", "失败重试策略", "例如 3", notification::retryMaxAttempts, 1, 100),
                duration(NOTIFICATION_RETRY_INITIAL_DELAY, "NOTIFICATION", "初始重试延迟", "首次失败后等待多久再重试", "", "失败重试策略", "例如 30s", notification::retryInitialDelay, "PT1S", "PT24H", retryInitialDelayValidator()),
                duration(NOTIFICATION_RETRY_MAX_DELAY, "NOTIFICATION", "最大重试延迟", "指数退避允许的最大重试延迟", "", "失败重试策略", "例如 10m", notification::retryMaxDelay, "PT1S", "PT24H", retryMaxDelayValidator()),
                duration(NOTIFICATION_RETRY_STALE_TIMEOUT, "NOTIFICATION", "发送锁超时", "发送任务长时间未完成时可被重新领取的超时时间", "", "失败重试策略", "例如 5m", notification::retryStaleTimeout, "PT1S", "PT24H", durationRange(Duration.ofSeconds(1), Duration.ofHours(24))),

                integer(PLUGIN_STORAGE_MAX_DATA_JSON_BYTES, "PLUGIN_STORAGE", "数据 JSON 最大字节数", "插件数据单条 JSON 允许的最大字节数", "字节", "插件数据容量限制", "例如 65536", pluginStorage::maxDataJsonBytes, 1024, 10485760),
                integer(PLUGIN_STORAGE_MAX_CONFIG_JSON_BYTES, "PLUGIN_STORAGE", "配置 JSON 最大字节数", "插件配置 JSON 允许的最大字节数", "字节", "插件数据容量限制", "例如 65536", pluginStorage::maxConfigJsonBytes, 1024, 10485760),
                integer(PLUGIN_STORAGE_MAX_ACCOUNT_JSON_BYTES, "PLUGIN_STORAGE", "账号 JSON 最大字节数", "插件账号 JSON 允许的最大字节数", "字节", "插件数据容量限制", "例如 65536", pluginStorage::maxAccountJsonBytes, 1024, 10485760),
                integer(PLUGIN_STORAGE_MAX_LOG_MESSAGE_BYTES, "PLUGIN_STORAGE", "日志消息最大字节数", "单条插件日志消息允许的最大字节数", "字节", "插件日志限制", "例如 8192", pluginStorage::maxLogMessageBytes, 256, 1048576),
                integer(PLUGIN_STORAGE_SELECT_ALL_LIMIT, "PLUGIN_STORAGE", "批量查询上限", "插件数据 selectAll 默认最多返回的记录数量", "条", "插件数据查询限制", "例如 100", pluginStorage::selectAllLimit, 1, 10000),
                integer(PLUGIN_STORAGE_MAX_DATA_ROWS_PER_PLUGIN, "PLUGIN_STORAGE", "单插件数据行数上限", "每个插件最多可保存的数据行数", "行", "插件数据容量限制", "例如 10000", pluginStorage::maxDataRowsPerPlugin, 1, 10000000),
                longValue(PLUGIN_UPLOAD_MAX_ARTIFACT_BYTES, "PLUGIN_UPLOAD", "插件包上传上限", "插件 jar 包允许上传的业务层最大字节数", "字节", "插件上传限制", "例如 52428800", pluginArtifact::maxArtifactBytes, 1024L, 1073741824L),

                duration(PLUGIN_RUNTIME_STARTUP_TIMEOUT, "PLUGIN_RUNTIME", "插件启动超时", "插件进程启动并通过健康检查的超时时间，仅影响后续启动", "", "插件进程运行参数", "例如 10s", pluginRuntime::startupTimeout, "PT1S", "PT10M", durationRange(Duration.ofSeconds(1), Duration.ofMinutes(10))),
                duration(PLUGIN_RUNTIME_STOP_TIMEOUT, "PLUGIN_RUNTIME", "插件停止超时", "停止插件进程时等待正常退出的最长时间", "", "插件进程运行参数", "例如 5s", pluginRuntime::stopTimeout, "PT1S", "PT10M", durationRange(Duration.ofSeconds(1), Duration.ofMinutes(10))),
                duration(PLUGIN_RUNTIME_TASK_TIMEOUT, "PLUGIN_RUNTIME", "插件任务超时", "传递给后续插件进程的最大任务执行超时时间", "", "插件进程运行参数", "例如 120s", pluginRuntime::taskTimeout, "PT1S", "PT24H", durationRange(Duration.ofSeconds(1), Duration.ofHours(24))),
                duration(PLUGIN_RUNTIME_SERVICE_CALL_TIMEOUT, "PLUGIN_RUNTIME", "插件服务调用超时", "插件调用其他服务插件时默认使用的超时时间", "", "插件进程运行参数", "例如 10s", pluginRuntime::serviceCallTimeout, "PT1S", "PT10M", durationRange(Duration.ofSeconds(1), Duration.ofMinutes(10))),
                duration(PLUGIN_RUNTIME_MAX_SERVICE_CALL_TIMEOUT, "PLUGIN_RUNTIME", "插件服务调用最大超时", "插件调用其他服务插件时允许申请的最大超时时间", "", "插件进程运行参数", "例如 30s", pluginRuntime::maxServiceCallTimeout, "PT1S", "PT10M", durationRange(Duration.ofSeconds(1), Duration.ofMinutes(10))),
                duration(PLUGIN_RUNTIME_SERVICE_IDLE_TIMEOUT, "PLUGIN_RUNTIME", "服务插件空闲超时", "服务插件无调用超过该时间后自动停止", "", "插件进程运行参数", "例如 10m", pluginRuntime::serviceIdleTimeout, "PT10S", "PT24H", durationRange(Duration.ofSeconds(10), Duration.ofHours(24))),
                duration(PLUGIN_RUNTIME_SERVICE_IDLE_SCAN_INTERVAL, "PLUGIN_RUNTIME", "服务插件空闲扫描间隔", "后台扫描并停止空闲服务插件的间隔", "", "插件进程运行参数", "例如 1m", pluginRuntime::serviceIdleScanInterval, "PT5S", "PT1H", durationRange(Duration.ofSeconds(5), Duration.ofHours(1))),
                integer(PLUGIN_RUNTIME_MAX_RESTARTS, "PLUGIN_RUNTIME", "最大重启次数", "预留的插件进程最大重启次数配置", "次", "插件进程运行参数", "例如 3", pluginRuntime::maxRestarts, 0, 100),
                string(PLUGIN_RUNTIME_MAX_HEAP, "PLUGIN_RUNTIME", "插件 JVM 最大堆", "后续插件进程启动时使用的 JVM -Xmx 大小", "", "插件进程运行参数", "例如 256m 或 1g", pluginRuntime::maxHeap, null, null, maxHeapValidator()),

                longValue(SECURITY_JWT_EXPIRATION, "SECURITY", "登录令牌有效期", "新签发 JWT 的有效期，已签发令牌不受影响", "秒", "登录安全策略", "例如 86400", security::jwtExpiration, 60L, 2592000L)
        );
        this.definitions = values.stream().collect(Collectors.toUnmodifiableMap(SystemSettingDefinition::key, definition -> definition));
    }

    public List<SystemSettingDefinition> all() {
        return definitions.values().stream().sorted((left, right) -> left.key().compareTo(right.key())).toList();
    }

    public SystemSettingDefinition require(String key) {
        SystemSettingDefinition definition = definitions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown system setting");
        }
        return definition;
    }

    public boolean contains(String key) {
        return definitions.containsKey(key);
    }

    private SystemSettingDefinition bool(String key, String category, String displayName, String description, String group, Supplier<Object> defaultValue) {
        return definition(key, category, SystemSettingValueType.BOOLEAN, displayName, description, "", group, "", null, null, defaultValue, null);
    }

    private SystemSettingDefinition cron(String key, String category, String displayName, String description, String group, String placeholder, Supplier<Object> defaultValue) {
        return definition(key, category, SystemSettingValueType.CRON, displayName, description, "", group, placeholder, null, null, defaultValue, null);
    }

    private SystemSettingDefinition string(String key, String category, String displayName, String description, String unit, String group, String placeholder, Supplier<Object> defaultValue, Object min, Object max, SystemSettingDefinition.SettingValidator validator) {
        return definition(key, category, SystemSettingValueType.STRING, displayName, description, unit, group, placeholder, min, max, defaultValue, validator);
    }

    private SystemSettingDefinition stringList(String key, String category, String displayName, String description, String group, String placeholder, Supplier<Object> defaultValue) {
        return definition(key, category, SystemSettingValueType.STRING_LIST, displayName, description, "", group, placeholder, null, null, defaultValue, (value, lookup) -> {
            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) value;
            values.forEach(item -> {
                if (item.length() > 128 || !item.matches("[A-Za-z0-9_.:-]+")) {
                    throw new IllegalArgumentException("Invalid notification channel id");
                }
            });
        });
    }

    private SystemSettingDefinition integer(String key, String category, String displayName, String description, String unit, String group, String placeholder, Supplier<Object> defaultValue, int min, int max) {
        return definition(key, category, SystemSettingValueType.INTEGER, displayName, description, unit, group, placeholder, min, max, defaultValue, range(min, max));
    }

    private SystemSettingDefinition longValue(String key, String category, String displayName, String description, String unit, String group, String placeholder, Supplier<Object> defaultValue, long min, long max) {
        return definition(key, category, SystemSettingValueType.LONG, displayName, description, unit, group, placeholder, min, max, defaultValue, longRange(min, max));
    }

    private SystemSettingDefinition duration(String key, String category, String displayName, String description, String unit, String group, String placeholder, Supplier<Object> defaultValue, Object min, Object max, SystemSettingDefinition.SettingValidator validator) {
        return definition(key, category, SystemSettingValueType.DURATION, displayName, description, unit, group, placeholder, min, max, defaultValue, validator);
    }

    private SystemSettingDefinition retentionDays(String key, String displayName, String description, Supplier<Object> defaultValue) {
        return definition(key, "RETENTION", SystemSettingValueType.INTEGER, displayName, description, "天", "日志保留天数", "例如 90", 1, 3650, defaultValue, (value, lookup) -> {
            int days = (Integer) value;
            if (days < lookup.getInt(RETENTION_MINIMUM_DAYS) || days > 3650) {
                throw new IllegalArgumentException("Retention days is out of range");
            }
        });
    }

    private SystemSettingDefinition definition(
            String key,
            String category,
            SystemSettingValueType valueType,
            String displayName,
            String description,
            String unit,
            String group,
            String placeholder,
            Object min,
            Object max,
            Supplier<Object> defaultValue,
            SystemSettingDefinition.SettingValidator validator
    ) {
        return new SystemSettingDefinition(key, category, valueType, displayName, description, unit, group, placeholder, min, max, defaultValue, validator);
    }

    private SystemSettingDefinition.SettingValidator range(int min, int max) {
        return (value, lookup) -> {
            int intValue = (Integer) value;
            if (intValue < min || intValue > max) {
                throw new IllegalArgumentException("Integer setting value is out of range");
            }
        };
    }

    private SystemSettingDefinition.SettingValidator longRange(long min, long max) {
        return (value, lookup) -> {
            long longValue = (Long) value;
            if (longValue < min || longValue > max) {
                throw new IllegalArgumentException("Long setting value is out of range");
            }
        };
    }

    private SystemSettingDefinition.SettingValidator durationRange(Duration min, Duration max) {
        return (value, lookup) -> {
            Duration duration = (Duration) value;
            if (duration.compareTo(min) < 0 || duration.compareTo(max) > 0) {
                throw new IllegalArgumentException("Duration setting value is out of range");
            }
        };
    }

    private SystemSettingDefinition.SettingValidator retryInitialDelayValidator() {
        return (value, lookup) -> {
            Duration duration = (Duration) value;
            durationRange(Duration.ofSeconds(1), Duration.ofHours(24)).validate(duration, lookup);
            if (duration.compareTo(lookup.getDuration(NOTIFICATION_RETRY_MAX_DELAY)) > 0) {
                throw new IllegalArgumentException("Retry initial delay cannot exceed max delay");
            }
        };
    }

    private SystemSettingDefinition.SettingValidator retryMaxDelayValidator() {
        return (value, lookup) -> {
            Duration duration = (Duration) value;
            durationRange(Duration.ofSeconds(1), Duration.ofHours(24)).validate(duration, lookup);
            if (duration.compareTo(lookup.getDuration(NOTIFICATION_RETRY_INITIAL_DELAY)) < 0) {
                throw new IllegalArgumentException("Retry max delay cannot be below initial delay");
            }
        };
    }

    private SystemSettingDefinition.SettingValidator maxHeapValidator() {
        return (value, lookup) -> {
            String text = (String) value;
            if (!text.matches("[1-9][0-9]*[mMgG]")) {
                throw new IllegalArgumentException("Invalid plugin max heap setting value");
            }
        };
    }
}

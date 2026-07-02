package com.sky.job.security;

public final class PermissionNames {
    public static final String USER_READ_SELF = "user:read:self";
    public static final String USER_UPDATE_SELF = "user:update:self";
    public static final String USER_PASSWORD_CHANGE_SELF = "user:password:change:self";
    public static final String SYSTEM_USER_READ = "system:user:read";
    public static final String SYSTEM_USER_WRITE = "system:user:write";
    public static final String SYSTEM_USER_DELETE = "system:user:delete";
    public static final String SYSTEM_USER_PASSWORD_RESET = "system:user:password:reset";
    public static final String SYSTEM_ROLE_READ = "system:role:read";
    public static final String SYSTEM_ROLE_WRITE = "system:role:write";
    public static final String SYSTEM_ROLE_DELETE = "system:role:delete";
    public static final String SYSTEM_PERMISSION_READ = "system:permission:read";
    public static final String SYSTEM_LOGIN_LOG_READ = "system:login-log:read";
    public static final String SYSTEM_LOG_READ = "system:log:read";
    public static final String SYSTEM_RETENTION_CLEANUP = "system:retention:cleanup";
    public static final String SYSTEM_SETTING_READ = "system:setting:read";
    public static final String SYSTEM_SETTING_WRITE = "system:setting:write";
    public static final String NOTIFICATION_CHANNEL_READ = "notification:channel:read";
    public static final String NOTIFICATION_CHANNEL_WRITE = "notification:channel:write";
    public static final String NOTIFICATION_CHANNEL_TEST = "notification:channel:test";
    public static final String NOTIFICATION_RULE_READ = "notification:rule:read";
    public static final String NOTIFICATION_RULE_WRITE = "notification:rule:write";
    public static final String NOTIFICATION_HISTORY_READ = "notification:history:read";
    public static final String NOTIFICATION_HISTORY_RETRY = "notification:history:retry";
    public static final String PLUGIN_ARTIFACT_UPLOAD = "plugin:artifact:upload";
    public static final String PLUGIN_RUNTIME_READ = "plugin:runtime:read";
    public static final String PLUGIN_RUNTIME_MANAGE = "plugin:runtime:manage";
    public static final String PLUGIN_CONFIG_READ = "plugin:config:read";
    public static final String PLUGIN_CONFIG_WRITE = "plugin:config:write";
    public static final String PLUGIN_ACCOUNT_READ = "plugin:account:read";
    public static final String PLUGIN_ACCOUNT_WRITE = "plugin:account:write";
    public static final String PLUGIN_AUDIT_READ = "plugin:audit:read";
    public static final String PLUGIN_SERVICE_PERMISSION_READ = "plugin:service-permission:read";
    public static final String PLUGIN_SERVICE_PERMISSION_WRITE = "plugin:service-permission:write";
    public static final String JOB_READ = "job:read";
    public static final String JOB_WRITE = "job:write";
    public static final String JOB_DELETE = "job:delete";
    public static final String JOB_RUN = "job:run";
    public static final String JOB_EXECUTION_READ = "job:execution:read";
    public static final String JOB_EXECUTION_CANCEL = "job:execution:cancel";

    private PermissionNames() {
    }
}

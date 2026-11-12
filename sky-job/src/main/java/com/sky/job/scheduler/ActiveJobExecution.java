package com.sky.job.scheduler;

import com.sky.job.plugin.runtime.PluginRuntimeHandle;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ActiveJobExecution {
    private final long executionId;
    private final long jobId;
    private final String jobName;
    private final String pluginInstallationId;
    private final String pluginId;
    private final String instanceId;
    private final String requestId;
    private final JobTriggerType triggerType;
    private final PluginRuntimeHandle handle;
    private final LocalDateTime startedAt;
    private final long timeoutMillis;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    public ActiveJobExecution(
            long executionId,
            long jobId,
            String jobName,
            String pluginInstallationId,
            String pluginId,
            String instanceId,
            String requestId,
            JobTriggerType triggerType,
            PluginRuntimeHandle handle,
            LocalDateTime startedAt,
            long timeoutMillis
    ) {
        this.executionId = executionId;
        this.jobId = jobId;
        this.jobName = jobName;
        this.pluginInstallationId = pluginInstallationId;
        this.pluginId = pluginId;
        this.instanceId = instanceId;
        this.requestId = requestId;
        this.triggerType = triggerType;
        this.handle = handle;
        this.startedAt = startedAt;
        this.timeoutMillis = timeoutMillis;
    }

    public long executionId() {
        return executionId;
    }

    public long jobId() {
        return jobId;
    }

    public String jobName() {
        return jobName;
    }

    public String pluginId() {
        return pluginId;
    }

    public String pluginInstallationId() {
        return pluginInstallationId;
    }

    public String instanceId() {
        return instanceId;
    }

    public String requestId() {
        return requestId;
    }

    public JobTriggerType triggerType() {
        return triggerType;
    }

    public PluginRuntimeHandle handle() {
        return handle;
    }

    public LocalDateTime startedAt() {
        return startedAt;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public boolean requestStop() {
        return stopRequested.compareAndSet(false, true);
    }

    public boolean requestCancel() {
        return cancelRequested.compareAndSet(false, true);
    }
}

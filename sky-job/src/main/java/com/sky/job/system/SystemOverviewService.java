package com.sky.job.system;

import com.sky.job.notification.NotificationSendHistoryStore;
import com.sky.job.notification.NotificationSendStatus;
import com.sky.job.scheduler.JobExecutionStatus;
import com.sky.job.scheduler.JobExecutionStore;
import com.sky.job.scheduler.JobStatus;
import com.sky.job.scheduler.JobStore;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.system.dto.SystemOverviewResponse;
import org.springframework.stereotype.Service;

@Service
public class SystemOverviewService {
    private final PluginRuntimeMetadataStore pluginRuntimeStore;
    private final JobStore jobStore;
    private final JobExecutionStore jobExecutionStore;
    private final NotificationSendHistoryStore notificationHistoryStore;

    public SystemOverviewService(
            PluginRuntimeMetadataStore pluginRuntimeStore,
            JobStore jobStore,
            JobExecutionStore jobExecutionStore,
            NotificationSendHistoryStore notificationHistoryStore
    ) {
        this.pluginRuntimeStore = pluginRuntimeStore;
        this.jobStore = jobStore;
        this.jobExecutionStore = jobExecutionStore;
        this.notificationHistoryStore = notificationHistoryStore;
    }

    public SystemOverviewResponse overview() {
        return new SystemOverviewResponse(
                pluginRuntimeStore.countRuntimes(null, null),
                jobStore.countByStatus(JobStatus.ENABLED),
                jobExecutionStore.count(null, null, JobExecutionStatus.FAILED),
                notificationHistoryStore.countByStatus(NotificationSendStatus.RETRY_WAITING)
        );
    }
}

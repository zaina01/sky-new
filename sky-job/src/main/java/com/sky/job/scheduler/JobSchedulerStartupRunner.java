package com.sky.job.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class JobSchedulerStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(JobSchedulerStartupRunner.class);
    private final JobStore jobStore;
    private final JobExecutionStore executionStore;
    private final PluginRuntimeMetadataStore runtimeMetadataStore;
    private final CronUtils cronUtils;
    private final QuartzJobUtils quartzJobUtils;

    public JobSchedulerStartupRunner(
            JobStore jobStore,
            JobExecutionStore executionStore,
            PluginRuntimeMetadataStore runtimeMetadataStore,
            CronUtils cronUtils,
            QuartzJobUtils quartzJobUtils
    ) {
        this.jobStore = jobStore;
        this.executionStore = executionStore;
        this.runtimeMetadataStore = runtimeMetadataStore;
        this.cronUtils = cronUtils;
        this.quartzJobUtils = quartzJobUtils;
    }

    @Override
    public void run(ApplicationArguments args) {
        jobStore.resetRunningToIdle();
        executionStore.markSystemExitForRunning();
        quartzJobUtils.clear();
        for (ScheduledJob job : jobStore.findEnabledJobsUnscoped()) {
            try {
                cronUtils.validate(job.cronExpression());
                runtimeMetadataStore.findByInstallationId(job.pluginInstallationId()).orElseThrow(() -> new IllegalArgumentException("Plugin installation not found"));
                quartzJobUtils.schedule(job);
            } catch (Exception exception) {
                log.warn("Failed to restore job schedule: {}", job.id(), exception);
            }
        }
    }
}

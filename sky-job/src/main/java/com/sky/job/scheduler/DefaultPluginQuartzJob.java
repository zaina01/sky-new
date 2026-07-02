package com.sky.job.scheduler;

public class DefaultPluginQuartzJob extends PluginQuartzJobSupport {
    public DefaultPluginQuartzJob(JobExecutionService executionService) {
        super(executionService);
    }
}

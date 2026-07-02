package com.sky.job.scheduler;

import org.quartz.DisallowConcurrentExecution;

@DisallowConcurrentExecution
public class DisallowConcurrentPluginQuartzJob extends PluginQuartzJobSupport {
    public DisallowConcurrentPluginQuartzJob(JobExecutionService executionService) {
        super(executionService);
    }
}

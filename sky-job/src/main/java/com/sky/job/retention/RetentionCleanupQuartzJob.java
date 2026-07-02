package com.sky.job.retention;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

public class RetentionCleanupQuartzJob implements Job {
    @Autowired
    private RetentionCleanupService cleanupService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            cleanupService.runScheduled();
        } catch (Exception exception) {
            throw new JobExecutionException(exception);
        }
    }
}

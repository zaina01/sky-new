package com.sky.job.notification;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class NotificationRetryQuartzJob implements Job {
    @Autowired
    private NotificationDeliveryService deliveryService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            deliveryService.runScheduled();
        } catch (Exception exception) {
            throw new JobExecutionException(exception);
        }
    }
}

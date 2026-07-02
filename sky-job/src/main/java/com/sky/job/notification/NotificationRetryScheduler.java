package com.sky.job.notification;

import com.sky.job.system.DynamicNotificationSettings;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(120)
public class NotificationRetryScheduler implements ApplicationRunner {
    private static final JobKey JOB_KEY = JobKey.jobKey("notification-retry", "system");
    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey("notification-retry-trigger", "system");

    private final DynamicNotificationSettings properties;
    private final Scheduler scheduler;

    public NotificationRetryScheduler(DynamicNotificationSettings properties, Scheduler scheduler) {
        this.properties = properties;
        this.scheduler = scheduler;
    }

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    public synchronized void refresh() {
        try {
            if (!properties.retryEnabled()) {
                scheduler.unscheduleJob(TRIGGER_KEY);
                scheduler.deleteJob(JOB_KEY);
                return;
            }
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_KEY)
                    .withSchedule(CronScheduleBuilder.cronSchedule(properties.retryCron()))
                    .forJob(JOB_KEY)
                    .build();
            if (scheduler.checkExists(JOB_KEY)) {
                scheduler.rescheduleJob(TRIGGER_KEY, trigger);
                return;
            }
            scheduler.scheduleJob(
                    JobBuilder.newJob(NotificationRetryQuartzJob.class)
                            .withIdentity(JOB_KEY)
                            .storeDurably(false)
                            .build(),
                    trigger
            );
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Failed to schedule notification retry", exception);
        }
    }
}

package com.sky.job.scheduler;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

@Component
public class QuartzJobUtils {
    private static final String GROUP = "sky-jobs";
    private final Scheduler scheduler;

    public QuartzJobUtils(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void schedule(ScheduledJob job) {
        try {
            JobDataMap dataMap = new JobDataMap();
            dataMap.put(PluginQuartzJobSupport.JOB_ID, job.id());
            dataMap.put(PluginQuartzJobSupport.TRIGGER_TYPE, JobTriggerType.SCHEDULED.name());
            Class<? extends org.quartz.Job> jobClass = job.concurrent() ? DefaultPluginQuartzJob.class : DisallowConcurrentPluginQuartzJob.class;
            scheduler.scheduleJob(
                    JobBuilder.newJob(jobClass)
                            .withIdentity(jobKey(job.id()))
                            .usingJobData(dataMap)
                            .storeDurably(false)
                            .build(),
                    TriggerBuilder.newTrigger()
                            .withIdentity(triggerKey(job.id()))
                            .withSchedule(scheduleBuilder(job))
                            .forJob(jobKey(job.id()))
                            .build()
            );
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Failed to schedule job " + job.id(), exception);
        }
    }

    public void reschedule(ScheduledJob job) {
        delete(job.id());
        if (job.jobStatus() == JobStatus.ENABLED) {
            schedule(job);
        }
    }

    public void pause(long jobId) {
        try {
            scheduler.pauseJob(jobKey(jobId));
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Failed to pause job " + jobId, exception);
        }
    }

    public void resume(ScheduledJob job) {
        reschedule(job);
    }

    public void delete(long jobId) {
        try {
            scheduler.deleteJob(jobKey(jobId));
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Failed to delete job " + jobId, exception);
        }
    }

    public void trigger(long jobId, String inputJson, Long timeoutMillis) {
        try {
            JobDataMap dataMap = new JobDataMap();
            dataMap.put(PluginQuartzJobSupport.TRIGGER_TYPE, JobTriggerType.MANUAL.name());
            if (inputJson != null) dataMap.put(PluginQuartzJobSupport.INPUT_JSON, inputJson);
            if (timeoutMillis != null) dataMap.put(PluginQuartzJobSupport.TIMEOUT_MILLIS, timeoutMillis);
            scheduler.triggerJob(jobKey(jobId), dataMap);
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Failed to trigger job " + jobId, exception);
        }
    }

    public void clear() {
        try {
            scheduler.clear();
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Failed to clear scheduler", exception);
        }
    }

    private CronScheduleBuilder scheduleBuilder(ScheduledJob job) {
        CronScheduleBuilder builder = CronScheduleBuilder.cronSchedule(job.cronExpression());
        return switch (job.misfirePolicy()) {
            case IGNORE_MISFIRES -> builder.withMisfireHandlingInstructionIgnoreMisfires();
            case FIRE_AND_PROCEED -> builder.withMisfireHandlingInstructionFireAndProceed();
            case DO_NOTHING -> builder.withMisfireHandlingInstructionDoNothing();
            case DEFAULT -> builder;
        };
    }

    private JobKey jobKey(long jobId) {
        return JobKey.jobKey("job-" + jobId, GROUP);
    }

    private TriggerKey triggerKey(long jobId) {
        return TriggerKey.triggerKey("job-" + jobId + "-trigger", GROUP);
    }
}

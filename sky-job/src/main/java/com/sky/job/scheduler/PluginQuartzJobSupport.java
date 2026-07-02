package com.sky.job.scheduler;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public abstract class PluginQuartzJobSupport implements Job {
    static final String JOB_ID = "jobId";
    static final String TRIGGER_TYPE = "triggerType";
    static final String INPUT_JSON = "inputJson";
    static final String TIMEOUT_MILLIS = "timeoutMillis";
    static final String SCHEDULER_ACTOR = "system:scheduler";

    private final JobExecutionService executionService;

    protected PluginQuartzJobSupport(JobExecutionService executionService) {
        this.executionService = executionService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        try {
            Long timeoutMillis = dataMap.containsKey(TIMEOUT_MILLIS) ? dataMap.getLong(TIMEOUT_MILLIS) : null;
            executionService.execute(
                    dataMap.getLong(JOB_ID),
                    JobTriggerType.valueOf(dataMap.getString(TRIGGER_TYPE)),
                    dataMap.getString(INPUT_JSON),
                    timeoutMillis,
                    SCHEDULER_ACTOR
            );
        } catch (Exception exception) {
            throw new JobExecutionException(exception);
        }
    }
}

package com.sky.job.scheduler;

public enum JobExecutionStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    CANCELLED,
    SYSTEM_EXIT
}

package com.sky.plugin.api;

public interface TaskPlugin {
    TaskResult run(TaskExecutionContext context) throws Exception;
}

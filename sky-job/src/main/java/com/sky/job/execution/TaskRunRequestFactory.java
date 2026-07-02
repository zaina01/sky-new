package com.sky.job.execution;

import com.sky.plugin.protocol.TaskRunRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TaskRunRequestFactory {
    public TaskRunRequest create(String pluginId, String jobId, String jobName, long timeoutMillis, String inputJson) {
        return TaskRunRequest.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setPluginId(pluginId)
                .setJobId(jobId)
                .setJobName(jobName)
                .setTriggerTimeEpochMillis(System.currentTimeMillis())
                .setTimeoutMillis(timeoutMillis)
                .setInputJson(inputJson == null ? "" : inputJson)
                .build();
    }
}

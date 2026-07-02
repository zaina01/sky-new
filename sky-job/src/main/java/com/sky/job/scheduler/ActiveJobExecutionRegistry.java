package com.sky.job.scheduler;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ActiveJobExecutionRegistry {
    private final ConcurrentMap<Long, ActiveJobExecution> executions = new ConcurrentHashMap<>();

    public void register(ActiveJobExecution execution) {
        executions.put(execution.executionId(), execution);
    }

    public Optional<ActiveJobExecution> get(long executionId) {
        return Optional.ofNullable(executions.get(executionId));
    }

    public Optional<ActiveJobExecution> remove(long executionId) {
        return Optional.ofNullable(executions.remove(executionId));
    }
}

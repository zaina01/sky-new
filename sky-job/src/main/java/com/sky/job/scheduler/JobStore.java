package com.sky.job.scheduler;

import com.sky.job.common.mapper.StatusCountRow;
import com.sky.job.scheduler.mapper.JobMapper;
import com.sky.job.scheduler.mapper.ScheduledJobRow;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JobStore {
    private final JobMapper mapper;

    public JobStore(JobMapper mapper) {
        this.mapper = mapper;
    }

    public long insert(String jobName, String pluginId, String cronExpression, MisfirePolicy misfirePolicy, boolean concurrent, String inputJson, long timeoutMillis, String remark, String actor) {
        Map<String, Object> values = new HashMap<>();
        values.put("jobName", jobName);
        values.put("pluginId", pluginId);
        values.put("cronExpression", cronExpression);
        values.put("jobStatus", JobStatus.ENABLED.name());
        values.put("misfirePolicy", misfirePolicy.name());
        values.put("concurrent", concurrent);
        values.put("inputJson", json(inputJson));
        values.put("timeoutMillis", timeoutMillis);
        values.put("executedStatus", ExecutedStatus.IDLE.name());
        values.put("remark", remark);
        values.put("actor", actor);
        mapper.insert(values);
        Object id = values.get("id");
        if (id instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Job was not saved");
    }

    public void update(long id, String jobName, String pluginId, String cronExpression, MisfirePolicy misfirePolicy, boolean concurrent, String inputJson, long timeoutMillis, String remark, String actor) {
        mapper.update(id, jobName, pluginId, cronExpression, misfirePolicy.name(), concurrent, json(inputJson), timeoutMillis, remark, actor);
    }

    public Optional<ScheduledJob> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::map);
    }

    public List<ScheduledJob> list(Integer page, Integer size) {
        int normalizedPage = Math.max(page == null ? 1 : page, 1);
        int normalizedSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        return mapper.list(normalizedSize, (normalizedPage - 1) * normalizedSize).stream().map(this::map).toList();
    }

    public long count() {
        return mapper.count();
    }

    public boolean existsByPluginId(String pluginId) {
        return mapper.countByPluginId(pluginId) > 0;
    }

    public long countByStatus(JobStatus status) {
        return mapper.countByStatus(status.name());
    }

    public List<StatusCountRow> countByJobStatus() {
        return mapper.countByJobStatus();
    }

    public List<StatusCountRow> countByExecutedStatus() {
        return mapper.countByExecutedStatus();
    }

    public void delete(long id) {
        mapper.delete(id);
    }

    public void updateStatus(long id, JobStatus status, String actor) {
        mapper.updateStatus(id, status.name(), actor);
    }

    public void updateExecutedStatus(long id, ExecutedStatus status) {
        mapper.updateExecutedStatus(id, status.name());
    }

    public int markRunningIfIdle(long id) {
        return mapper.markRunningIfIdle(id, ExecutedStatus.RUNNING.name(), ExecutedStatus.IDLE.name());
    }

    public int markIdleIfRunning(long id) {
        return mapper.markIdleIfRunning(id, ExecutedStatus.IDLE.name(), ExecutedStatus.RUNNING.name());
    }

    public void resetRunningToIdle() {
        mapper.resetRunningToIdle(ExecutedStatus.IDLE.name(), ExecutedStatus.RUNNING.name());
    }

    public List<ScheduledJob> findEnabledJobs() {
        return mapper.findEnabledJobs(JobStatus.ENABLED.name()).stream().map(this::map).toList();
    }

    private ScheduledJob map(ScheduledJobRow row) {
        return new ScheduledJob(
                row.id(),
                row.jobName(),
                row.pluginId(),
                row.cronExpression(),
                JobStatus.valueOf(row.jobStatus()),
                MisfirePolicy.valueOf(row.misfirePolicy()),
                row.concurrent(),
                row.inputJson(),
                row.timeoutMillis(),
                ExecutedStatus.valueOf(row.executedStatus()),
                row.remark(),
                row.createdBy(),
                row.updatedBy(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    private String json(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }
}

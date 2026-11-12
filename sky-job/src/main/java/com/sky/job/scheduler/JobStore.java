package com.sky.job.scheduler;

import com.sky.job.common.mapper.StatusCountRow;
import com.sky.job.scheduler.mapper.JobMapper;
import com.sky.job.scheduler.mapper.ScheduledJobRow;
import com.sky.job.security.EffectiveDataScope;
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

    public long insert(
            String jobName,
            String pluginInstallationId,
            String cronExpression,
            MisfirePolicy misfirePolicy,
            boolean concurrent,
            String inputJson,
            long timeoutMillis,
            String remark,
            String ownerUserId,
            Long ownerDepartmentId,
            String actor
    ) {
        Map<String, Object> values = new HashMap<>();
        values.put("jobName", jobName);
        values.put("pluginInstallationId", pluginInstallationId);
        values.put("cronExpression", cronExpression);
        values.put("jobStatus", JobStatus.ENABLED.name());
        values.put("misfirePolicy", misfirePolicy.name());
        values.put("concurrent", concurrent);
        values.put("inputJson", json(inputJson));
        values.put("timeoutMillis", timeoutMillis);
        values.put("executedStatus", ExecutedStatus.IDLE.name());
        values.put("remark", remark);
        values.put("ownerUserId", ownerUserId);
        values.put("ownerDepartmentId", ownerDepartmentId);
        values.put("actor", actor);
        mapper.insert(values);
        Object id = values.get("id");
        if (id instanceof Number number) return number.longValue();
        throw new IllegalStateException("Job was not saved");
    }

    public void update(long id, String jobName, String pluginInstallationId, String cronExpression, MisfirePolicy misfirePolicy, boolean concurrent, String inputJson, long timeoutMillis, String remark, String actor) {
        mapper.update(id, jobName, pluginInstallationId, cronExpression, misfirePolicy.name(), concurrent, json(inputJson), timeoutMillis, remark, actor);
    }

    public Optional<ScheduledJob> findById(long id, EffectiveDataScope scope) {
        return Optional.ofNullable(mapper.findById(id, scope)).map(this::map);
    }

    public Optional<ScheduledJob> findByIdUnscoped(long id) {
        return Optional.ofNullable(mapper.findByIdUnscoped(id)).map(this::map);
    }

    public List<ScheduledJob> list(EffectiveDataScope scope, Integer page, Integer size) {
        int normalizedPage = Math.max(page == null ? 1 : page, 1);
        int normalizedSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        return mapper.list(scope, normalizedSize, (normalizedPage - 1) * normalizedSize).stream().map(this::map).toList();
    }

    public long count(EffectiveDataScope scope) {
        return mapper.count(scope);
    }

    public boolean existsByPluginInstallationId(String installationId) {
        return mapper.countByPluginInstallationId(installationId) > 0;
    }

    public long countByStatus(JobStatus status, EffectiveDataScope scope) {
        return mapper.countByStatus(status.name(), scope);
    }

    public List<StatusCountRow> countByJobStatus(EffectiveDataScope scope) {
        return mapper.countByJobStatus(scope);
    }

    public List<StatusCountRow> countByExecutedStatus(EffectiveDataScope scope) {
        return mapper.countByExecutedStatus(scope);
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

    public List<ScheduledJob> findEnabledJobsUnscoped() {
        return mapper.findEnabledJobs(JobStatus.ENABLED.name()).stream().map(this::map).toList();
    }

    public List<String> notificationChannelIds(long jobId) {
        return mapper.notificationChannelIds(jobId);
    }

    public void replaceNotificationChannels(long jobId, List<String> channelIds, String actor) {
        mapper.deleteNotificationChannels(jobId);
        for (String channelId : channelIds == null ? List.<String>of() : channelIds) {
            mapper.insertNotificationChannel(jobId, channelId, actor);
        }
    }

    public int transferOwnership(long id, String ownerUserId, Long ownerDepartmentId, String actor) {
        return mapper.transferOwnership(id, ownerUserId, ownerDepartmentId, actor);
    }

    private ScheduledJob map(ScheduledJobRow row) {
        return new ScheduledJob(
                row.id(),
                row.jobName(),
                row.pluginInstallationId(),
                row.pluginId(),
                row.pluginDisplayName(),
                row.cronExpression(),
                JobStatus.valueOf(row.jobStatus()),
                MisfirePolicy.valueOf(row.misfirePolicy()),
                row.concurrent(),
                row.inputJson(),
                row.timeoutMillis(),
                ExecutedStatus.valueOf(row.executedStatus()),
                row.remark(),
                row.ownerUserId(),
                row.ownerUsername(),
                row.ownerDepartmentId(),
                row.ownerDepartmentName(),
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

package com.sky.job.scheduler;

import com.sky.job.common.mapper.DailyStatusCountRow;
import com.sky.job.common.mapper.StatusCountRow;
import com.sky.job.scheduler.mapper.JobExecutionMapper;
import com.sky.job.scheduler.mapper.JobExecutionRow;
import com.sky.job.security.EffectiveDataScope;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JobExecutionStore {
    private final JobExecutionMapper mapper;

    public JobExecutionStore(JobExecutionMapper mapper) {
        this.mapper = mapper;
    }

    public long insertRunning(ScheduledJob job, String instanceId, JobTriggerType triggerType, String runnerLogPath) {
        Map<String, Object> values = new HashMap<>();
        values.put("jobId", job.id());
        values.put("pluginInstallationId", job.pluginInstallationId());
        values.put("pluginId", job.pluginId());
        values.put("instanceId", instanceId);
        values.put("status", JobExecutionStatus.RUNNING.name());
        values.put("triggerType", triggerType.name());
        values.put("jobName", job.jobName());
        values.put("runnerLogPath", runnerLogPath);
        values.put("ownerUserId", job.ownerUserId());
        values.put("ownerDepartmentId", job.ownerDepartmentId());
        mapper.insertRunning(values);
        Object id = values.get("id");
        if (id instanceof Number number) return number.longValue();
        throw new IllegalStateException("Job execution was not saved");
    }

    public void updateRuntimeSnapshot(long id, String engine, String sha, String revision, String version, String mode) { mapper.updateRuntimeSnapshot(id, engine, sha, revision, version, mode); }

    public void updateScriptOptionsRevision(long id,String revision) { mapper.updateScriptOptionsRevision(id,revision); }

    public void updateRequestId(long id, String requestId) { mapper.updateRequestId(id, requestId); }

    public void markTerminal(long id, JobExecutionStatus status, String message, String resultJson, String errorType, String errorMessage, LocalDateTime startedAt, LocalDateTime finishedAt) {
        mapper.markTerminal(id, status.name(), message, json(resultJson), errorType, errorMessage, finishedAt, durationMillis(startedAt, finishedAt));
    }

    public int markTerminalIfRunning(long id, JobExecutionStatus status, String message, String resultJson, String errorType, String errorMessage, LocalDateTime startedAt, LocalDateTime finishedAt) {
        return mapper.markTerminalIfRunning(id, status.name(), JobExecutionStatus.RUNNING.name(), message, json(resultJson), errorType, errorMessage, finishedAt, durationMillis(startedAt, finishedAt));
    }

    public int markCancelledIfRunning(long id, String message, String errorType, LocalDateTime startedAt, LocalDateTime finishedAt) {
        return mapper.markCancelledIfRunning(id, JobExecutionStatus.CANCELLED.name(), JobExecutionStatus.RUNNING.name(), message, errorType, finishedAt, durationMillis(startedAt, finishedAt));
    }

    public void markSystemExitForRunning() { mapper.markSystemExitForRunning(JobExecutionStatus.SYSTEM_EXIT.name(), JobExecutionStatus.RUNNING.name()); }
    public long countOlderThan(LocalDateTime cutoff) { return mapper.countOlderThan(cutoff, JobExecutionStatus.RUNNING.name()); }
    public int deleteOlderThan(LocalDateTime cutoff, int limit) { return mapper.deleteOlderThan(cutoff, JobExecutionStatus.RUNNING.name(), limit); }
    public boolean hasRunningForJob(long jobId) { return mapper.countRunningByJobId(jobId, JobExecutionStatus.RUNNING.name()) > 0; }
    public int deleteByJobId(long jobId) { return mapper.deleteByJobId(jobId); }

    public Optional<JobExecution> findById(long id, EffectiveDataScope scope) {
        return Optional.ofNullable(mapper.findById(id, scope)).map(this::map);
    }

    public Optional<JobExecution> findByIdUnscoped(long id) {
        return Optional.ofNullable(mapper.findByIdUnscoped(id)).map(this::map);
    }

    public List<JobExecution> list(Long jobId, String installationId, String pluginId, JobExecutionStatus status, EffectiveDataScope scope, Integer page, Integer size) {
        int normalizedPage = Math.max(page == null ? 1 : page, 1);
        int normalizedSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        return mapper.list(jobId, trim(installationId), trim(pluginId), status == null ? null : status.name(), scope, normalizedSize, (normalizedPage - 1) * normalizedSize).stream().map(this::map).toList();
    }

    public long count(Long jobId, String installationId, String pluginId, JobExecutionStatus status, EffectiveDataScope scope) {
        return mapper.count(jobId, trim(installationId), trim(pluginId), status == null ? null : status.name(), scope);
    }

    public List<StatusCountRow> countByStatusBetween(LocalDateTime startTime, LocalDateTime endTime, EffectiveDataScope scope) {
        return mapper.countByStatusBetween(startTime, endTime, scope);
    }

    public List<DailyStatusCountRow> countDailyByStatus(LocalDateTime startTime, LocalDateTime endTime, EffectiveDataScope scope) {
        return mapper.countDailyByStatus(startTime, endTime, scope);
    }

    public List<JobExecution> listRecentFailures(List<JobExecutionStatus> statuses, EffectiveDataScope scope, int limit) {
        return mapper.listRecentFailures(statuses.stream().map(Enum::name).toList(), scope, limit).stream().map(this::map).toList();
    }

    private JobExecution map(JobExecutionRow row) {
        return new JobExecution(
                row.id(), row.jobId(), row.pluginInstallationId(), row.pluginId(), row.instanceId(), row.requestId(),
                JobExecutionStatus.valueOf(row.status()), JobTriggerType.valueOf(row.triggerType()), row.jobName(),
                row.startedAt(), row.finishedAt(), row.durationMillis(), row.message(), row.resultJson(), row.errorType(),
                row.errorMessage(), row.runnerLogPath(), row.ownerUserId(), row.ownerDepartmentId(), row.createdAt(), row.runtimeEngine(), row.artifactSha256(), row.environmentRevision(), row.interpreterVersion(), row.executionMode(), row.scriptOptionsRevision()
        );
    }

    private long durationMillis(LocalDateTime startedAt, LocalDateTime finishedAt) {
        return java.time.Duration.between(startedAt, finishedAt).toMillis();
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String json(String value) { return value == null || value.isBlank() ? "{}" : value; }
}

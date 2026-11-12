package com.sky.job.plugin.callback;

import com.sky.job.plugin.callback.dto.PluginServiceCallLogResponse;
import com.sky.job.plugin.callback.dto.PluginServiceCallRunnerLogResponse;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeProperties;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.security.PermissionNames;
import com.sky.job.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PluginServiceCallLogService {
    private static final int DEFAULT_LOG_TAIL_BYTES = 64 * 1024;
    private static final int MAX_LOG_TAIL_BYTES = 1024 * 1024;

    private final PluginServiceCallLogStore store;
    private final ProcessPluginRuntimeProperties runtimeProperties;
    private final DataScopeService dataScopeService;

    public PluginServiceCallLogService(PluginServiceCallLogStore store, ProcessPluginRuntimeProperties runtimeProperties, DataScopeService dataScopeService) {
        this.store = store;
        this.runtimeProperties = runtimeProperties;
        this.dataScopeService = dataScopeService;
    }

    public PageResponse<PluginServiceCallLogResponse> list(Long jobId, String callerInstallationId, String callerPluginId, String targetInstallationId, String targetPluginId, String operation, Boolean success, Integer page, Integer size, AuthenticatedUser user) {
        EffectiveDataScope scope = dataScopeService.resolve(user, PermissionNames.PLUGIN_AUDIT_READ);
        return new PageResponse<>(
                store.list(jobId, callerInstallationId, callerPluginId, targetInstallationId, targetPluginId, operation, success, scope, page, size).stream().map(PluginServiceCallLogResponse::from).toList(),
                normalizedPage(page),
                normalizedSize(size),
                store.count(jobId, callerInstallationId, callerPluginId, targetInstallationId, targetPluginId, operation, success, scope)
        );
    }

    public PluginServiceCallLogResponse get(long id, AuthenticatedUser user) {
        return PluginServiceCallLogResponse.from(log(id, user));
    }

    public PluginServiceCallRunnerLogResponse runnerLog(long id, Long offset, Integer limitBytes, Integer tailBytes, AuthenticatedUser user) {
        PluginServiceCallLog log = log(id, user);
        int limit = normalizedLimitBytes(limitBytes, tailBytes);
        Path logPath = runnerLogPath(log.targetRunnerLogPath());
        if (!Files.isRegularFile(logPath)) {
            return new PluginServiceCallRunnerLogResponse(log.id(), log.targetInstallationId(), log.targetPluginId(), log.targetInstanceId(), logPath.toString(), false, "", 0, 0, limit, 0, true, false);
        }
        try {
            long sizeBytes = Files.size(logPath);
            long start = normalizedLogOffset(offset, tailBytes, sizeBytes, limit);
            int bytesToRead = (int) Math.min(limit, Math.max(sizeBytes - start, 0));
            byte[] bytes = new byte[bytesToRead];
            try (RandomAccessFile file = new RandomAccessFile(logPath.toFile(), "r")) {
                file.seek(start);
                file.readFully(bytes);
            }
            long nextOffset = start + bytesToRead;
            boolean eof = nextOffset >= sizeBytes;
            boolean truncated = offset == null && tailBytes != null && start > 0;
            return new PluginServiceCallRunnerLogResponse(log.id(), log.targetInstallationId(), log.targetPluginId(), log.targetInstanceId(), logPath.toString(), true, new String(bytes, StandardCharsets.UTF_8), start, nextOffset, limit, sizeBytes, eof, truncated);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read plugin service runner log", exception);
        }
    }

    private PluginServiceCallLog log(long id, AuthenticatedUser user) {
        EffectiveDataScope scope = dataScopeService.resolve(user, PermissionNames.PLUGIN_AUDIT_READ);
        return store.findById(id, scope).orElseThrow(() -> new ResourceNotFoundException("Plugin service call log not found"));
    }

    private Path runnerLogPath(String value) {
        Path logRoot = runtimeProperties.logRoot().toAbsolutePath().normalize();
        Path logPath = Path.of(value == null ? "" : value).toAbsolutePath().normalize();
        if (!logPath.startsWith(logRoot) || !"runner.log".equals(logPath.getFileName().toString())) {
            throw new IllegalStateException("Plugin service runner log path escapes configured root");
        }
        return logPath;
    }

    private int normalizedLimitBytes(Integer limitBytes, Integer tailBytes) {
        int requested = limitBytes == null ? tailBytes == null ? DEFAULT_LOG_TAIL_BYTES : tailBytes : limitBytes;
        return Math.min(Math.max(requested, 1), MAX_LOG_TAIL_BYTES);
    }

    private long normalizedLogOffset(Long offset, Integer tailBytes, long sizeBytes, int limitBytes) {
        if (offset != null) {
            return Math.min(Math.max(offset, 0), sizeBytes);
        }
        if (tailBytes != null) {
            return Math.max(sizeBytes - limitBytes, 0);
        }
        return 0;
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}

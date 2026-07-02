package com.sky.job.plugin.callback;

import com.sky.job.plugin.callback.dto.PluginServiceCallLogResponse;
import com.sky.job.plugin.callback.dto.PluginServiceCallRunnerLogResponse;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeProperties;
import com.sky.job.scheduler.dto.PageResponse;
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

    public PluginServiceCallLogService(PluginServiceCallLogStore store, ProcessPluginRuntimeProperties runtimeProperties) {
        this.store = store;
        this.runtimeProperties = runtimeProperties;
    }

    public PageResponse<PluginServiceCallLogResponse> list(Long jobId, String callerPluginId, String targetPluginId, String operation, Boolean success, Integer page, Integer size) {
        return new PageResponse<>(
                store.list(jobId, callerPluginId, targetPluginId, operation, success, page, size).stream().map(PluginServiceCallLogResponse::from).toList(),
                normalizedPage(page),
                normalizedSize(size),
                store.count(jobId, callerPluginId, targetPluginId, operation, success)
        );
    }

    public PluginServiceCallLogResponse get(long id) {
        return PluginServiceCallLogResponse.from(log(id));
    }

    public PluginServiceCallRunnerLogResponse runnerLog(long id, Long offset, Integer limitBytes, Integer tailBytes) {
        PluginServiceCallLog log = log(id);
        int limit = normalizedLimitBytes(limitBytes, tailBytes);
        Path logPath = runnerLogPath(log.targetRunnerLogPath());
        if (!Files.isRegularFile(logPath)) {
            return new PluginServiceCallRunnerLogResponse(log.id(), log.targetPluginId(), log.targetInstanceId(), logPath.toString(), false, "", 0, 0, limit, 0, true, false);
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
            return new PluginServiceCallRunnerLogResponse(log.id(), log.targetPluginId(), log.targetInstanceId(), logPath.toString(), true, new String(bytes, StandardCharsets.UTF_8), start, nextOffset, limit, sizeBytes, eof, truncated);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read plugin service runner log", exception);
        }
    }

    private PluginServiceCallLog log(long id) {
        return store.findById(id).orElseThrow(() -> new IllegalArgumentException("Plugin service call log not found"));
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

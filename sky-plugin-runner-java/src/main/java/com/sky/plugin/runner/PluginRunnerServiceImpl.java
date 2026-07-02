package com.sky.plugin.runner;

import com.sky.plugin.api.PluginServiceRequest;
import com.sky.plugin.api.PluginServiceResponse;
import com.sky.plugin.api.ServicePlugin;
import com.sky.plugin.api.TaskPlugin;
import com.sky.plugin.api.TaskResult;
import com.sky.plugin.protocol.CancelTaskRequest;
import com.sky.plugin.protocol.CancelTaskResponse;
import com.sky.plugin.protocol.HealthRequest;
import com.sky.plugin.protocol.HealthResponse;
import com.sky.plugin.protocol.PluginRunnerServiceGrpc;
import com.sky.plugin.protocol.PluginServiceRunRequest;
import com.sky.plugin.protocol.PluginServiceRunResponse;
import com.sky.plugin.protocol.StartPluginRequest;
import com.sky.plugin.protocol.StartPluginResponse;
import com.sky.plugin.protocol.StopPluginRequest;
import com.sky.plugin.protocol.StopPluginResponse;
import com.sky.plugin.protocol.TaskRunRequest;
import com.sky.plugin.protocol.TaskRunResponse;
import com.sky.plugin.protocol.TaskRunStatus;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class PluginRunnerServiceImpl extends PluginRunnerServiceGrpc.PluginRunnerServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(PluginRunnerServiceImpl.class);

    private final RunnerEnvironment environment;
    private final Object plugin;
    private final HostGrpcClient hostClient;
    private final ExecutorService taskExecutor = Executors.newCachedThreadPool();
    private final Map<String, Future<TaskRunResponse>> runningTasks = new ConcurrentHashMap<>();

    PluginRunnerServiceImpl(RunnerEnvironment environment, Object plugin, HostGrpcClient hostClient) {
        this.environment = environment;
        this.plugin = plugin;
        this.hostClient = hostClient;
    }

    @Override
    public void health(HealthRequest request, StreamObserver<HealthResponse> responseObserver) {
        try {
            send(responseObserver, HealthResponse.newBuilder().setHealthy(true).setMessage("OK").build());
        } finally {
            complete(responseObserver, "health");
        }
    }

    @Override
    public void startPlugin(StartPluginRequest request, StreamObserver<StartPluginResponse> responseObserver) {
        try {
            send(responseObserver, StartPluginResponse.newBuilder().setStarted(true).setMessage("Started").build());
        } finally {
            complete(responseObserver, "startPlugin");
        }
    }

    @Override
    public void runTask(TaskRunRequest request, StreamObserver<TaskRunResponse> responseObserver) {
        long startedAt = System.currentTimeMillis();
        Future<TaskRunResponse> future = taskExecutor.submit(() -> executeTask(request, startedAt));
        Future<TaskRunResponse> existing = runningTasks.putIfAbsent(request.getRequestId(), future);
        if (existing != null) {
            future.cancel(true);
            try {
                send(responseObserver, failed(request.getRequestId(), startedAt, IllegalStateException.class.getName(), "Task is already running", "Duplicate task request"));
            } finally {
                complete(responseObserver, request.getRequestId());
            }
            return;
        }
        try {
            send(responseObserver, future.get(timeoutMillis(request), TimeUnit.MILLISECONDS));
        } catch (TimeoutException exception) {
            future.cancel(true);
            send(responseObserver, terminal(request.getRequestId(), startedAt, TaskRunStatus.TASK_RUN_STATUS_TIMEOUT, TimeoutException.class.getName(), "Plugin task timed out", "Plugin task timed out"));
        } catch (CancellationException exception) {
            send(responseObserver, terminal(request.getRequestId(), startedAt, TaskRunStatus.TASK_RUN_STATUS_CANCELLED, CancellationException.class.getName(), "Plugin task cancelled", "Plugin task cancelled"));
        } catch (Throwable throwable) {
            Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
            log.error("Plugin task RPC failed: requestId={}", request.getRequestId(), cause);
            send(responseObserver, failed(request.getRequestId(), startedAt, cause.getClass().getName(), cause.getMessage(), "Plugin task failed"));
        } finally {
            runningTasks.remove(request.getRequestId(), future);
            complete(responseObserver, request.getRequestId());
        }
    }

    @Override
    public void callService(PluginServiceRunRequest request, StreamObserver<PluginServiceRunResponse> responseObserver) {
        long startedAt = System.currentTimeMillis();
        try {
            if (!(plugin instanceof ServicePlugin servicePlugin)) {
                send(responseObserver, serviceFailed(startedAt, IllegalStateException.class.getName(), "Plugin does not implement ServicePlugin", "Plugin service is not available"));
                return;
            }
            DefaultServiceExecutionContext context = new DefaultServiceExecutionContext(request, hostClient);
            PluginServiceResponse response = callWithPluginClassLoader(servicePlugin, new PluginServiceRequest(
                    request.getCallerPluginId(),
                    request.getTargetPluginId(),
                    request.getOperation(),
                    request.getRequestJson(),
                    request.getRequestId(),
                    request.getTimeoutMillis()
            ), context);
            send(responseObserver, PluginServiceRunResponse.newBuilder()
                    .setSuccess(response.success())
                    .setResultJson(response.resultJson() == null ? "" : response.resultJson())
                    .setMessage(response.message() == null ? "" : response.message())
                    .setErrorType(response.errorType() == null ? "" : response.errorType())
                    .setErrorMessage(response.errorMessage() == null ? "" : response.errorMessage())
                    .setStartedAtEpochMillis(startedAt)
                    .setFinishedAtEpochMillis(System.currentTimeMillis())
                    .build());
        } catch (Throwable throwable) {
            log.error("Plugin service RPC failed: requestId={}", request.getRequestId(), throwable);
            send(responseObserver, serviceFailed(startedAt, throwable.getClass().getName(), throwable.getMessage(), "Plugin service call failed"));
        } finally {
            complete(responseObserver, request.getRequestId());
        }
    }

    @Override
    public void cancelTask(CancelTaskRequest request, StreamObserver<CancelTaskResponse> responseObserver) {
        Future<TaskRunResponse> future = runningTasks.remove(request.getRequestId());
        try {
            if (future == null) {
                send(responseObserver, CancelTaskResponse.newBuilder().setCancelled(false).setMessage("Task is not running").build());
                return;
            }
            send(responseObserver, CancelTaskResponse.newBuilder().setCancelled(future.cancel(true)).setMessage("Cancellation requested").build());
        } finally {
            complete(responseObserver, request.getRequestId());
        }
    }

    @Override
    public void stopPlugin(StopPluginRequest request, StreamObserver<StopPluginResponse> responseObserver) {
        runningTasks.values().forEach(future -> future.cancel(true));
        taskExecutor.shutdownNow();
        try {
            send(responseObserver, StopPluginResponse.newBuilder().setStopped(true).setMessage("Stopping").build());
        } finally {
            complete(responseObserver, "stopPlugin");
        }
        new Thread(() -> System.exit(0), "plugin-runner-stop").start();
    }

    private TaskRunResponse executeTask(TaskRunRequest request, long startedAt) {
        try {
            if (!(plugin instanceof TaskPlugin taskPlugin)) {
                return failed(request.getRequestId(), startedAt, IllegalStateException.class.getName(), "Plugin does not implement TaskPlugin", "Plugin task is not available");
            }
            DefaultTaskExecutionContext context = new DefaultTaskExecutionContext(request, environment, hostClient);
            TaskResult result = runWithPluginClassLoader(taskPlugin, context);
            return TaskRunResponse.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setStatus(result.succeeded() ? TaskRunStatus.TASK_RUN_STATUS_SUCCESS : TaskRunStatus.TASK_RUN_STATUS_FAILED)
                    .setMessage(result.message() == null ? "" : result.message())
                    .setResultJson(result.resultJson() == null ? "" : result.resultJson())
                    .setStartedAtEpochMillis(startedAt)
                    .setFinishedAtEpochMillis(System.currentTimeMillis())
                    .build();
        } catch (Throwable throwable) {
            log.error("Plugin task execution failed: requestId={}", request.getRequestId(), throwable);
            return failed(request.getRequestId(), startedAt, throwable.getClass().getName(), throwable.getMessage(), "Plugin task failed");
        }
    }

    private TaskResult runWithPluginClassLoader(TaskPlugin taskPlugin, DefaultTaskExecutionContext context) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(plugin.getClass().getClassLoader());
        try {
            return taskPlugin.run(context);
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    private PluginServiceResponse callWithPluginClassLoader(ServicePlugin servicePlugin, PluginServiceRequest request, DefaultServiceExecutionContext context) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(plugin.getClass().getClassLoader());
        try {
            return servicePlugin.call(request, context);
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    private <T> void send(StreamObserver<T> responseObserver, T response) {
        try {
            responseObserver.onNext(response);
        } catch (Throwable throwable) {
            log.error("Failed to send plugin runner RPC response", throwable);
            throw throwable;
        }
    }

    private void complete(StreamObserver<?> responseObserver, String requestId) {
        try {
            responseObserver.onCompleted();
        } catch (Throwable throwable) {
            log.error("Failed to complete plugin runner RPC: requestId={}", requestId, throwable);
            throw throwable;
        }
    }

    private TaskRunResponse failed(String requestId, long startedAt, String errorType, String errorMessage, String message) {
        return terminal(requestId, startedAt, TaskRunStatus.TASK_RUN_STATUS_FAILED, errorType, errorMessage, message);
    }

    private PluginServiceRunResponse serviceFailed(long startedAt, String errorType, String errorMessage, String message) {
        return PluginServiceRunResponse.newBuilder()
                .setSuccess(false)
                .setMessage(message == null ? "" : message)
                .setErrorType(errorType == null ? "" : errorType)
                .setErrorMessage(errorMessage == null ? "" : errorMessage)
                .setStartedAtEpochMillis(startedAt)
                .setFinishedAtEpochMillis(System.currentTimeMillis())
                .build();
    }

    private TaskRunResponse terminal(String requestId, long startedAt, TaskRunStatus status, String errorType, String errorMessage, String message) {
        return TaskRunResponse.newBuilder()
                .setRequestId(requestId)
                .setStatus(status)
                .setStartedAtEpochMillis(startedAt)
                .setFinishedAtEpochMillis(System.currentTimeMillis())
                .setErrorType(errorType == null ? "" : errorType)
                .setErrorMessage(errorMessage == null ? "" : errorMessage)
                .setMessage(message)
                .build();
    }

    private long timeoutMillis(TaskRunRequest request) {
        if (request.getTimeoutMillis() > 0 && request.getTimeoutMillis() <= environment.maxTaskTimeoutMillis()) {
            return request.getTimeoutMillis();
        }
        return environment.maxTaskTimeoutMillis();
    }
}

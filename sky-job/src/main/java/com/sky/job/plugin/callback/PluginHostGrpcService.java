package com.sky.job.plugin.callback;

import com.sky.job.plugin.security.PluginRuntimePrincipal;
import com.sky.job.plugin.security.PluginSecurityContexts;
import com.sky.plugin.protocol.AccountRequest;
import com.sky.plugin.protocol.AccountResponse;
import com.sky.plugin.protocol.Ack;
import com.sky.plugin.protocol.ConfigRequest;
import com.sky.plugin.protocol.ConfigResponse;
import com.sky.plugin.protocol.LogStreamResult;
import com.sky.plugin.protocol.NotificationChannelListRequest;
import com.sky.plugin.protocol.NotificationChannelListResponse;
import com.sky.plugin.protocol.NotificationRequest;
import com.sky.plugin.protocol.NotificationResponse;
import com.sky.plugin.protocol.PluginDataRequest;
import com.sky.plugin.protocol.PluginDataResponse;
import com.sky.plugin.protocol.PluginHostServiceGrpc;
import com.sky.plugin.protocol.PluginLogEvent;
import com.sky.plugin.protocol.PluginServiceCallRequest;
import com.sky.plugin.protocol.PluginServiceCallResponse;
import io.grpc.stub.StreamObserver;

public class PluginHostGrpcService extends PluginHostServiceGrpc.PluginHostServiceImplBase {
    private final PluginCallbackService callbackService;

    public PluginHostGrpcService(PluginCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @Override
    public StreamObserver<PluginLogEvent> openLogStream(StreamObserver<LogStreamResult> responseObserver) {
        PluginRuntimePrincipal principal = PluginSecurityContexts.currentPrincipal();
        return new StreamObserver<>() {
            @Override
            public void onNext(PluginLogEvent event) {
                callbackService.writeLog(principal, event);
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(LogStreamResult.newBuilder().setAccepted(true).build());
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void writeLog(PluginLogEvent request, StreamObserver<Ack> responseObserver) {
        callbackService.writeLog(PluginSecurityContexts.currentPrincipal(), request);
        responseObserver.onNext(Ack.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getConfig(ConfigRequest request, StreamObserver<ConfigResponse> responseObserver) {
        responseObserver.onNext(callbackService.getConfig(PluginSecurityContexts.currentPrincipal(), request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateConfig(ConfigRequest request, StreamObserver<ConfigResponse> responseObserver) {
        responseObserver.onNext(callbackService.updateConfig(PluginSecurityContexts.currentPrincipal(), request));
        responseObserver.onCompleted();
    }

    @Override
    public void listAccounts(AccountRequest request, StreamObserver<AccountResponse> responseObserver) {
        responseObserver.onNext(callbackService.listAccounts(PluginSecurityContexts.currentPrincipal(), request));
        responseObserver.onCompleted();
    }

    @Override
    public void getAccount(AccountRequest request, StreamObserver<AccountResponse> responseObserver) {
        responseObserver.onNext(callbackService.getAccount(PluginSecurityContexts.currentPrincipal(), request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateAccount(AccountRequest request, StreamObserver<AccountResponse> responseObserver) {
        responseObserver.onNext(callbackService.updateAccount(PluginSecurityContexts.currentPrincipal(), request));
        responseObserver.onCompleted();
    }

    @Override
    public void getData(PluginDataRequest request, StreamObserver<PluginDataResponse> responseObserver) {
        responseObserver.onNext(callbackService.getData(PluginSecurityContexts.currentPrincipal(), request));
        responseObserver.onCompleted();
    }

    @Override
    public void putData(PluginDataRequest request, StreamObserver<PluginDataResponse> responseObserver) {
        responseObserver.onNext(callbackService.putData(PluginSecurityContexts.currentPrincipal(), request));
        responseObserver.onCompleted();
    }

    @Override
    public void deleteData(PluginDataRequest request, StreamObserver<PluginDataResponse> responseObserver) {
        responseObserver.onNext(callbackService.deleteData(PluginSecurityContexts.currentPrincipal(), request));
        responseObserver.onCompleted();
    }

    @Override
    public void listData(PluginDataRequest request, StreamObserver<PluginDataResponse> responseObserver) {
        responseObserver.onNext(callbackService.listData(PluginSecurityContexts.currentPrincipal(), request));
        responseObserver.onCompleted();
    }

    @Override
    public void listNotificationChannels(NotificationChannelListRequest request, StreamObserver<NotificationChannelListResponse> responseObserver) {
        responseObserver.onNext(callbackService.listNotificationChannels(PluginSecurityContexts.currentPrincipal(), request));
        responseObserver.onCompleted();
    }

    @Override
    public void sendNotification(NotificationRequest request, StreamObserver<NotificationResponse> responseObserver) {
        responseObserver.onNext(callbackService.sendNotification(PluginSecurityContexts.currentPrincipal(), request));
        responseObserver.onCompleted();
    }

    @Override
    public void callPluginService(PluginServiceCallRequest request, StreamObserver<PluginServiceCallResponse> responseObserver) {
        responseObserver.onNext(callbackService.callPluginService(PluginSecurityContexts.currentPrincipal(), request));
        responseObserver.onCompleted();
    }
}

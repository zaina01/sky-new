package com.sky.job.plugin.runtime.process;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;

@Component
public class ProcessPluginPortAllocator {
    public int allocate() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to allocate plugin runtime port", exception);
        }
    }
}

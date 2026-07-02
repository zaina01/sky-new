package com.sky.plugin.runner;

import com.sky.plugin.api.ServicePlugin;
import com.sky.plugin.api.SkyPlugin;
import com.sky.plugin.api.TaskPlugin;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

public class PluginRunnerMain {
    public static void main(String[] args) throws Exception {
        RunnerEnvironment environment = RunnerEnvironment.from(args, System.getenv());
        URLClassLoader pluginClassLoader = pluginClassLoader(environment);
        Object plugin = loadPlugin(environment.pluginId(), environment.mainClass(), pluginClassLoader);
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(2);
        EventLoopGroup clientGroup = new NioEventLoopGroup(1);
        HostGrpcClient hostClient = HostGrpcClient.create(environment, clientGroup);
        PluginRunnerServiceImpl runnerService = new PluginRunnerServiceImpl(environment, plugin, hostClient);
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress(environment.bindHost(), environment.port()))
                .bossEventLoopGroup(bossGroup)
                .workerEventLoopGroup(workerGroup)
                .channelType(NioServerSocketChannel.class)
                .maxInboundMessageSize(environment.maxInboundMessageBytes())
                .addService(runnerService)
                .intercept(new RunnerAuthInterceptor(environment))
                .build()
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(server, hostClient, bossGroup, workerGroup, clientGroup, Duration.ofSeconds(5))));
        server.awaitTermination();
    }

    private static URLClassLoader pluginClassLoader(RunnerEnvironment environment) throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(environment.pluginArtifactPath().toUri().toURL());
        for (var dependencyPath : environment.pluginDependencyPaths()) {
            urls.add(dependencyPath.toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), PluginRunnerMain.class.getClassLoader());
    }

    private static Object loadPlugin(String pluginId, String mainClass, ClassLoader pluginClassLoader) {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(pluginClassLoader);
        try {
            if (mainClass != null && !mainClass.isBlank()) {
                return loadPluginClass(pluginId, mainClass, pluginClassLoader);
            }
            return loadPluginWithServiceLoader(pluginId, pluginClassLoader);
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    private static Object loadPluginClass(String pluginId, String mainClass, ClassLoader pluginClassLoader) {
        try {
            Class<?> pluginClass = Class.forName(mainClass, true, pluginClassLoader);
            if (!TaskPlugin.class.isAssignableFrom(pluginClass) && !ServicePlugin.class.isAssignableFrom(pluginClass)) {
                throw new IllegalArgumentException("Plugin main class must implement TaskPlugin or ServicePlugin: " + mainClass);
            }
            Object plugin = pluginClass.getDeclaredConstructor().newInstance();
            validatePlugin(pluginId, plugin);
            return plugin;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Failed to instantiate plugin main class: " + mainClass, exception);
        }
    }

    private static Object loadPluginWithServiceLoader(String pluginId, ClassLoader pluginClassLoader) {
        List<Object> plugins = new ArrayList<>();
        ServiceLoader.load(TaskPlugin.class, pluginClassLoader).forEach(plugins::add);
        ServiceLoader.load(ServicePlugin.class, pluginClassLoader).forEach(plugin -> {
            if (plugins.stream().noneMatch(existing -> existing.getClass().equals(plugin.getClass()))) {
                plugins.add(plugin);
            }
        });
        if (plugins.isEmpty()) {
            throw new IllegalArgumentException("Plugin artifact is missing TaskPlugin or ServicePlugin SPI provider");
        }
        if (plugins.size() > 1) {
            throw new IllegalArgumentException("Plugin artifact must declare exactly one plugin provider");
        }
        Object plugin = plugins.getFirst();
        validatePlugin(pluginId, plugin);
        return plugin;
    }

    private static void validatePlugin(String pluginId, Object plugin) {
        SkyPlugin annotation = plugin.getClass().getAnnotation(SkyPlugin.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Plugin provider is missing @SkyPlugin: " + plugin.getClass().getName());
        }
        if (!pluginId.equals(annotation.pluginId())) {
            throw new IllegalArgumentException("Plugin provider pluginId does not match runtime pluginId");
        }
    }

    private static void shutdown(Server server, HostGrpcClient hostClient, EventLoopGroup bossGroup, EventLoopGroup workerGroup, EventLoopGroup clientGroup, Duration timeout) {
        server.shutdown();
        hostClient.close();
        try {
            if (!server.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                server.shutdownNow();
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            server.shutdownNow();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            clientGroup.shutdownGracefully();
        }
    }
}

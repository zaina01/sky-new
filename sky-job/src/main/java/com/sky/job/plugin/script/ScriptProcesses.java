package com.sky.job.plugin.script;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Common bounded logging and lifecycle primitives for native processes. */
public final class ScriptProcesses {
    private ScriptProcesses() { }
    public static void environment(Map<String,String> env) {
        Map<String,String> inherited = new HashMap<>(env);
        env.clear();
        for (String key : List.of("SystemRoot", "WINDIR", "PATH", "Path", "TEMP", "TMP", "TMPDIR", "HOME", "USERPROFILE", "LANG", "LC_ALL", "SSL_CERT_FILE", "SSL_CERT_DIR"))
            if (inherited.containsKey(key)) env.put(key, inherited.get(key));
        env.put("PYTHONUTF8", "1"); env.put("PYTHONUNBUFFERED", "1");
        env.put("PYTHONNOUSERSITE", "1"); env.put("PIP_DISABLE_PIP_VERSION_CHECK", "1");
        env.put("PIP_CONFIG_FILE", isWindows() ? "NUL" : "/dev/null");
        env.put("npm_config_userconfig", isWindows() ? "NUL" : "/dev/null");
    }
    public static boolean isWindows() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"); }
    public static Thread drain(Process process, Path log, long maxBytes) throws IOException {
        Files.createDirectories(log.getParent());
        var output = Files.newOutputStream(log, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        long initial = Files.size(log);
        return Thread.ofVirtual().start(() -> {
            try (output; var input=process.getInputStream()) {
                byte[] bytes=new byte[8192]; long written=initial; boolean truncated=initial>=maxBytes;
                int count;
                while ((count=input.read(bytes))!=-1) {
                    int accepted=(int)Math.min(count, Math.max(0,maxBytes-written));
                    if (accepted>0) { output.write(bytes,0,accepted); written+=accepted; }
                    if (accepted<count && !truncated) { output.write("\n[sky] Log limit reached; further output discarded.\n".getBytes(StandardCharsets.UTF_8)); truncated=true; }
                    output.flush();
                }
            } catch (IOException ignored) { /* Process teardown can close its streams. */ }
        });
    }
    public static String run(List<String> command, Path directory, Path log, Duration timeout, long limit) throws IOException, InterruptedException {
        Files.createDirectories(directory);
        ProcessBuilder builder=new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        environment(builder.environment());
        Process process=builder.start();
        Thread reader;
        try { reader=drain(process, log, limit); }
        catch (IOException e) { stop(process, Duration.ZERO); throw e; }
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new IOException("Command timed out: " + Path.of(command.getFirst()).getFileName());
            reader.join(5000);
            if (process.exitValue()!=0) throw new IOException("Command failed (exit " + process.exitValue() + "); see operation log");
            return new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
        } finally { stop(process, Duration.ofSeconds(1)); }
    }
    public static void stop(Process process, Duration timeout) {
        // Capture descendants before terminating the parent (which otherwise reparents children).
        List<ProcessHandle> children=process.descendants().toList();
        if (isWindows() && process.isAlive()) {
            try {
                Path taskkill=Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"), "System32", "taskkill.exe");
                var killer=new ProcessBuilder(taskkill.toString(), "/PID", Long.toString(process.pid()), "/T", "/F").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                if (!killer.waitFor(5,TimeUnit.SECONDS)) killer.destroyForcibly();
            } catch (IOException ignored) { } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        children.reversed().forEach(ProcessHandle::destroy);
        process.destroy();
        try { process.waitFor(Math.max(0,timeout.toMillis()),TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        children.reversed().stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) process.destroyForcibly();
        try { process.waitFor(5,TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

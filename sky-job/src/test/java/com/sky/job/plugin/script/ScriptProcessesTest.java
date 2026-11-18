package com.sky.job.plugin.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ScriptProcessesTest {
    @TempDir Path temporary;
    public static class Output {
        public static void main(String[] args) throws Exception {
            if(args.length>0) {Thread.sleep(60000);return;}
            for(int i=0;i<10000;i++) System.out.println("中文日志输出："+"x".repeat(100));
        }
    }
    private List<String> command(String... arguments) throws Exception {
        var command=new java.util.ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin",ScriptProcesses.isWindows()?"java.exe":"java").toString(),"-Dfile.encoding=UTF-8","-cp",Path.of(Output.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),Output.class.getName()));
        command.addAll(List.of(arguments));return command;
    }
    @Test void largeOutputIsDrainedAndTruncatedWithoutBlocking()throws Exception {
        Path log=temporary.resolve("output.log");String output=ScriptProcesses.run(command(),temporary,log,Duration.ofSeconds(10),1025);
        assertThat(output).contains("Log limit reached");assertThat(Files.size(log)).isLessThan(1200);
    }
    @Test void commandTimeoutTerminatesProcess()throws Exception {
        long started=System.nanoTime();
        assertThatThrownBy(()->ScriptProcesses.run(command("sleep"),temporary,temporary.resolve("timeout.log"),Duration.ofMillis(300),1024)).isInstanceOf(java.io.IOException.class).hasMessageContaining("timed out");
        assertThat(Duration.ofNanos(System.nanoTime()-started)).isLessThan(Duration.ofSeconds(10));
    }
}

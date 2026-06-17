package edu.szu.agent.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HomeworkDownloadCommand")
class HomeworkDownloadCommandTest {

    @Test
    @DisplayName("required --homework-id 缺失时 picocli 报错退出码 2")
    void missingHomeworkIdExitsWith2() {
        StringWriter sw = new StringWriter();
        var cmd = new CommandLine(new HomeworkCommand());
        cmd.setOut(new PrintWriter(sw));
        cmd.setErr(new PrintWriter(sw));

        int exit = cmd.execute("download", "--output-dir", "/tmp");

        assertThat(exit).isEqualTo(2);
    }

    @Test
    @DisplayName("required --output-dir 缺失时 picocli 报错退出码 2")
    void missingOutputDirExitsWith2() {
        StringWriter sw = new StringWriter();
        var cmd = new CommandLine(new HomeworkCommand());
        cmd.setOut(new PrintWriter(sw));
        cmd.setErr(new PrintWriter(sw));

        int exit = cmd.execute("download", "--homework-id", "169193");

        assertThat(exit).isEqualTo(2);
    }

    @Test
    @DisplayName("--homework download 子命令在 HomeworkCommand 下注册")
    void downloadSubcommandIsRegistered() {
        var cmd = new CommandLine(new HomeworkCommand());
        assertThat(cmd.getSubcommands().keySet())
            .contains("download");
    }
}

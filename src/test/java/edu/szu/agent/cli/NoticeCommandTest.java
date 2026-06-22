package edu.szu.agent.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeCommandTest {

    @Test
    void listRequiresUsername() {
        CommandLine cmd = new CommandLine(new NoticeCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("list");

        assertThat(exit).isEqualTo(2);
        assertThat(out.toString()).contains("Missing required option");
    }

    @Test
    void listReturnsJsonByDefault() {
        CommandLine cmd = new CommandLine(new NoticeCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("list", "--username", "2023150090", "--days-back", "90");

        assertThat(exit).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("\"success\":true");
        assertThat(output).contains("\"notices\"");
        assertThat(output).contains("深大");
    }

    @Test
    void listFiltersByCategory() {
        CommandLine cmd = new CommandLine(new NoticeCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("list", "--username", "2023150090",
            "--category", "LECTURE", "--days-back", "90", "--format", "json");

        assertThat(exit).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("\"category\":\"LECTURE\"");
    }
}

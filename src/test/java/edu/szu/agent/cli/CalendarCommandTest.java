package edu.szu.agent.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarCommandTest {

    @Test
    void getReturnsJsonByDefault() {
        CommandLine cmd = new CommandLine(new CalendarCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("get", "--academic-year", "2025-2026");

        assertThat(exit).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("\"success\":true");
        assertThat(output).contains("\"academicYear\":\"2025-2026\"");
        assertThat(output).contains("\"events\"");
        assertThat(output).contains("学生报到");
    }

    @Test
    void getReturnsHumanFormat() {
        CommandLine cmd = new CommandLine(new CalendarCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("get", "--academic-year", "2025-2026", "--format", "human");

        assertThat(exit).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("Academic year:");
        assertThat(output).contains("Events:");
        assertThat(output).contains("学生报到");
    }

    @Test
    void getUnsupportedYearReturnsEmptyEvents() {
        CommandLine cmd = new CommandLine(new CalendarCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("get", "--academic-year", "2099-2100");

        assertThat(exit).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("\"count\":0");
    }
}

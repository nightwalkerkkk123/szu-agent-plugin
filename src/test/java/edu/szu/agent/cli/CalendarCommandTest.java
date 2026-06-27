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
        // CommandOutput.formatHuman renders data fields with their JSON key
        // name verbatim (shared envelope across all subcommands), so the
        // human view exposes "academicYear:" / "events:" rather than
        // title-cased labels. The richer human-friendly rendering for
        // calendar is intentionally not implemented in P1 阶段 3 — JSON
        // is the canonical contract for downstream tooling.
        assertThat(output).contains("academicYear: 2025-2026");
        assertThat(output).contains("events:");
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

package edu.szu.agent.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * // 编程技术: JUnit 5 / AssertJ / picocli CommandLine.execute()
 *
 * @since 0.4.0
 * @author 王子豪
 */
@DisplayName("ExamCommand")
class ExamCommandTest {

    @Test
    @DisplayName("list requires --username → exit 2")
    void listRequiresUsername() {
        CommandLine cmd = new CommandLine(new ExamCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("list");

        assertThat(exit).isEqualTo(2);
        assertThat(out.toString()).contains("Missing required option");
    }

    @Test
    @DisplayName("list --username 2023150090 returns JSON success")
    void listReturnsJsonSuccess() {
        CommandLine cmd = new CommandLine(new ExamCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("list", "--username", "2023150090");

        assertThat(exit).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("\"success\":true");
        assertThat(output).contains("\"exams\"");
        assertThat(output).contains("\"count\":2");
    }

    @Test
    @DisplayName("list with --status filter returns filtered results")
    void listWithStatusFilter() {
        CommandLine cmd = new CommandLine(new ExamCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("list", "--username", "2023150090",
            "--status", "待开始考试");

        assertThat(exit).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("\"success\":true");
        assertThat(output).contains("\"status\":\"待开始考试\"");
    }

    @Test
    @DisplayName("list --format human returns human-readable output")
    void listHumanFormat() {
        CommandLine cmd = new CommandLine(new ExamCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("list", "--username", "2023150090",
            "--format", "human");

        assertThat(exit).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("Exam Schedules:");
        assertThat(output).contains("操作系统");
        assertThat(output).contains("致理楼L1-601");
    }
}

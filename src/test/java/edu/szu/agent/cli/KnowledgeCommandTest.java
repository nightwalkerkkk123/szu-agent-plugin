package edu.szu.agent.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeCommandTest {

    @Test
    void queryReturnsResultsInHumanFormat() {
        CommandLine cmd = new CommandLine(new KnowledgeCommand());
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exit = cmd.execute("query", "图书馆");

        assertThat(exit).isEqualTo(0);
        assertThat(out.toString()).contains("Results");
        assertThat(out.toString()).contains("knowledge/03-library.md");
    }

    @Test
    void queryReturnsJson() {
        CommandLine cmd = new CommandLine(new KnowledgeCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("query", "图书馆", "--format", "json");

        assertThat(exit).isEqualTo(0);
        assertThat(out.toString()).contains("\"count\"");
    }

    @Test
    void missingQueryReportsError() {
        CommandLine cmd = new CommandLine(new KnowledgeCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("query");

        assertThat(exit).isEqualTo(2);
        assertThat(out.toString()).contains("Missing required option");
    }

    @Test
    void categoryFilterWorks() {
        CommandLine cmd = new CommandLine(new KnowledgeCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("query", "--query", "图书馆", "--category", "LIBRARY", "--format", "json");

        assertThat(exit).isEqualTo(0);
        assertThat(out.toString()).contains("knowledge/03-library.md");
    }
}

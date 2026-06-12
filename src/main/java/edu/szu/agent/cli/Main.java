package edu.szu.agent.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * CLI entry point for SZU Agent Plugin.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@Command(
    name = "szu-agent",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    description = "SZU campus automation CLI tool",
    subcommands = {BookingCommand.class}
)
public class Main implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("szu-agent-plugin v0.1.0 — skeleton ready");
        return 0;
    }

    /**
     * Main entry point.
     *
     * @param args command-line arguments
     * @since 0.1.0
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}

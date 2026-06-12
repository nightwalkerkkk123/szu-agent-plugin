package edu.szu.agent.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * {@code booking} subcommand — parent for booking-related actions.
 *
 * <p>Per ADR-0001 D1: the CLI is the first-class work unit.
 * {@code java -jar szu-agent-plugin.jar booking venue ...} is the
 * P0 demo path. This parent command groups all booking sub-actions;
 * currently only {@code venue} is implemented.
 *
 * // Design Pattern: Command (picocli @Command)
 * // 编程技术: 注解 / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
@Command(
    name = "booking",
    description = "Campus booking operations",
    mixinStandardHelpOptions = true,
    subcommands = {VenueCommand.class}
)
public class BookingCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        // No sub-action specified; picocli prints usage automatically
        // when requireSubcommand is set on the parent.
        return 0;
    }
}

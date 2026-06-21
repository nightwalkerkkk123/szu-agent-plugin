package edu.szu.agent.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * {@code schedule} subcommand — parent for schedule-related actions.
 *
 * <p>Groups the ehall schedule query commands.
 *
 * // 编程技术: 注解 / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
@Command(
    name = "schedule",
    description = "Campus schedule operations",
    mixinStandardHelpOptions = true,
    subcommands = {ScheduleListCommand.class}
)
public class ScheduleCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }
}

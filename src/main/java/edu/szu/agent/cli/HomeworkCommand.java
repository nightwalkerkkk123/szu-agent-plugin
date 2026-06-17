package edu.szu.agent.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * {@code homework} subcommand — parent for homework-related actions.
 *
 * <p>Groups the Chaoxing / LMS homework query commands.
 *
 * // 编程技术: 注解 / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
@Command(
    name = "homework",
    description = "Campus homework operations",
    mixinStandardHelpOptions = true,
    subcommands = {HomeworkListCommand.class, HomeworkDownloadCommand.class}
)
public class HomeworkCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }
}

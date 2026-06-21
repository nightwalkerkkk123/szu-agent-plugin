package edu.szu.agent.cli;

import edu.szu.agent.account.Account;
import edu.szu.agent.client.ChaoxingAttachmentDownloadClient;
import edu.szu.agent.client.ChaoxingHomeworkClient;
import edu.szu.agent.client.EhallScheduleClient;
import edu.szu.agent.client.VenueBookingClient;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.retry.RetryPolicies;
import edu.szu.agent.skill.Skill;
import edu.szu.agent.skill.Skills;
import edu.szu.agent.task.BookingTask;
import edu.szu.agent.task.HomeworkDownloadTask;
import edu.szu.agent.task.HomeworkTask;
import edu.szu.agent.task.ScheduleListTask;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.Set;
import java.util.concurrent.Callable;
import java.nio.file.Path;
import java.time.Duration;

/**
 * CLI entry point for SZU Agent Plugin.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code booking venue ...} — P0 business (Playwright real run)
 *   <li>{@code skill list|call} — Skill registry access (P1)
 *   <li>{@code mcp list|call} — MCP protocol surface (P1)
 * </ul>
 *
 * <p>On startup, the main task(s) are eagerly registered with the
 * Skills singleton so {@code skill list} / {@code mcp list} reflect
 * the current build. The {@link BookingTask} is constructed with
 * a default {@link VenueBookingClient} using {@link RetryPolicies#defaultBooking()}
 * and a placeholder account — actual account resolution happens
 * per-call inside the task (matches the existing CLI flow).
 *
 * @since 0.1.0
 * @author 王子豪
 */
@Command(
    name = "szu-agent",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    description = "SZU campus automation CLI tool",
    subcommands = {
        BookingCommand.class,
        HomeworkCommand.class,
        ScheduleCommand.class,
        SkillCommand.class,
        MCPCommand.class
    }
)
public class Main implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("szu-agent-plugin v0.1.0 — skeleton ready");
        return 0;
    }

    /**
     * Registers the default Skills at startup. Idempotent — safe to
     * call multiple times (subsequent calls are no-ops if the same
     * name is already registered). Public for test setup.
     *
     * @since 0.1.0
     */
    public static void registerDefaultSkills() {
        Skills registry = Skills.getInstance();
        Set<String> existing = registry.all().stream()
            .map(Skill::name)
            .collect(java.util.stream.Collectors.toSet());
        if (existing.contains("booking_venue")
            && existing.contains("homework_list")
            && existing.contains("homework_download")
            && existing.contains("schedule_list")) {
            return;
        }

        ConfigManager.getInstance().load();
        Account placeholder = new Account("placeholder", "placeholder", "P1-stub");

        if (!existing.contains("booking_venue")) {
            VenueBookingClient client = new VenueBookingClient(
                placeholder,
                ConfigManager.getInstance().browser(),
                RetryPolicies.defaultBooking());
            registry.register(new Skill<>("booking_venue", "体育场馆定时预约", new BookingTask(client, placeholder)));
        }

        if (!existing.contains("homework_list")) {
            ChaoxingHomeworkClient client = new ChaoxingHomeworkClient(
                placeholder,
                ConfigManager.getInstance().browser(),
                RetryPolicies.defaultBooking());
            registry.register(new Skill<>("homework_list", "查询畅课作业列表", new HomeworkTask(client, placeholder)));
        }

        if (!existing.contains("homework_download")) {
            SessionStore store = new SessionStore(
                Path.of(System.getProperty("user.home")),
                placeholder.studentId());
            SessionProbe probe = new SessionProbe(
                "https://lms.szu.edu.cn/user/index", ".todo-list-container");
            ChaoxingAttachmentDownloadClient client = new ChaoxingAttachmentDownloadClient(
                placeholder,
                ConfigManager.getInstance().browser(),
                RetryPolicies.defaultBooking(),
                store,
                probe,
                Duration.ofDays(30));
            registry.register(new Skill<>("homework_download",
                "下载畅课作业的全部附件到本地目录",
                new HomeworkDownloadTask(client, placeholder)));
        }

        if (!existing.contains("schedule_list")) {
            EhallScheduleClient client = new EhallScheduleClient(
                placeholder,
                ConfigManager.getInstance().browser(),
                RetryPolicies.defaultBooking());
            registry.register(new Skill<>("schedule_list", "查询学生课表",
                new ScheduleListTask(client, placeholder)));
        }
    }

    /**
     * Main entry point.
     *
     * @param args command-line arguments
     * @since 0.1.0
     */
    public static void main(String[] args) {
        registerDefaultSkills();
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}

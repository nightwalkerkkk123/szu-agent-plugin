package edu.szu.agent.cli;

import edu.szu.agent.client.ChaoxingAttachmentDownloadClient;
import edu.szu.agent.client.ChaoxingHomeworkClient;
import edu.szu.agent.client.BookingFlowLauncher;
import edu.szu.agent.client.VenueBookingClient;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.retry.RetryPolicies;
import edu.szu.agent.skill.Skill;
import edu.szu.agent.skill.Skills;
import edu.szu.agent.skill.external.ExternalSkillLoader;
import edu.szu.agent.task.BookingTask;
import edu.szu.agent.task.ExamListTask;
import edu.szu.agent.task.HomeworkDownloadTask;
import edu.szu.agent.task.HomeworkTask;
import edu.szu.agent.task.KnowledgeTask;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * CLI entry point for SZU Agent Plugin.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code booking venue ...} — P0 business (Playwright real run)
 *   <li>{@code homework ...} — Chaoxing LMS homework list / attachment download
 *   <li>{@code schedule ...} — ehall schedule list query
 *   <li>{@code kb query ...} — knowledge-base query (P1 skeleton)
 *   <li>{@code skill list|call} — Skill registry access (P1)
 *   <li>{@code mcp list|call|serve} — MCP protocol surface (P1)
 * </ul>
 *
 * <p>On startup, the main task(s) are eagerly registered with the
 * Skills singleton so {@code skill list} / {@code mcp list} reflect
 * the current build. The {@link BookingTask} is constructed with a
 * {@link VenueBookingClient} that has no fixed account — credentials
 * are resolved per-call inside the task using {@link edu.szu.agent.account.AccountResolver}.
 *
 * @since 0.6.0
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
        CalendarCommand.class,
        NoticeCommand.class,
        ExamCommand.class,
        KnowledgeCommand.class,
        SkillCommand.class,
        MCPCommand.class
    }
)
public class Main implements Callable<Integer> {

    @Override
    public Integer call() {
        // Skeleton message is printed by main() so that this class does not
        // depend on System.out directly; ArchUnit allows stdout only in main().
        return 0;
    }

    /**
     * Registers the default Skills at startup. Idempotent — safe to
     * call multiple times (subsequent calls are no-ops if the same
     * name is already registered). Public for test setup.
     *
     * @since 0.6.0
     */
    public static void registerDefaultSkills() {
        Skills registry = Skills.getInstance();
        Set<String> existing = registry.all().stream()
            .map(Skill::name)
            .collect(Collectors.toSet());

        ConfigManager.getInstance().load();
        if (!existing.contains("booking_venue")) {
            // Per-user session reuse: BookingFlowLauncher builds a SessionStore
            // keyed by the resolved account at launch time, so a manual MFA pass
            // (headed) is reused on later headless MCP/daemon calls. The
            // headed-fallback path reuses the same launcher with a freshly
            // built headed browser — see BookingTask.execute().
            BookingFlowLauncher launcher = new BookingFlowLauncher(
                ConfigManager.getInstance().browser(),
                RetryPolicies.defaultBooking());
            registry.register(Skill.of(new BookingTask(
                launcher::clientFor,
                (browser, username) -> launcher.clientFor(username, browser))));
        }

        if (!existing.contains("homework_list")) {
            registry.register(Skill.of(new HomeworkTask()));
        }

        if (!existing.contains("homework_download")) {
            registry.register(Skill.of(new HomeworkDownloadTask()));
        }

        if (!existing.contains("schedule_list")) {
            // Dynamic route per PLAN-p1-real-fetch.md §4 阶段 1: try real
            // (Playwright + 30d session reuse), fall back to embedded static
            // course list on any failure (no session / CAS expired / page
            // change / network). See ResilientScheduleClient.
            registry.register(Skill.of(ScheduleListCommand.defaultTask()));
        }

        if (!existing.contains("calendar_get")) {
            // Dynamic route per PLAN-p1-real-fetch.md §5 阶段 3: try real
            // (Playwright on https://www.szu.edu.cn/xxgk/xl.htm), fall back
            // to embedded 2025-2026 spring snapshot on any failure or empty
            // parse result. See ResilientCalendarClient.
            registry.register(Skill.of(CalendarCommand.defaultTask()));
        }

        if (!existing.contains("notice_list")) {
            // Dynamic route per PLAN-p1-real-fetch.md §5 阶段 2: try real
            // (Playwright on https://www1.szu.edu.cn/board/), fall back
            // to embedded static snapshot on any failure. See
            // ResilientNoticeClient.
            registry.register(Skill.of(NoticeCommand.defaultTask()));
        }

        if (!existing.contains("exam_list")) {
            registry.register(Skill.of(new ExamListTask()));
        }

        if (!existing.contains("kb_query")) {
            registry.register(Skill.of(new KnowledgeTask()));
        }

        ExternalSkillLoader.loadFromEnvironment();
    }

    /**
     * Main entry point.
     *
     * @param args command-line arguments
     * @since 0.6.0
     */
    public static void main(String[] args) {
        registerDefaultSkills();
        // ArchUnit allows System.out only inside main(); keep the skeleton
        // greeting here rather than in call().
        System.out.println("szu-agent-plugin v0.1.0 — skeleton ready");
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
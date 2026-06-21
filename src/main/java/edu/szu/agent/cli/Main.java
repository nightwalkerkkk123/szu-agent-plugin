package edu.szu.agent.cli;

import edu.szu.agent.client.VenueBookingClient;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.retry.RetryPolicies;
import edu.szu.agent.skill.Skill;
import edu.szu.agent.skill.Skills;
import edu.szu.agent.task.BookingTask;
import edu.szu.agent.task.KnowledgeTask;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * CLI entry point for SZU Agent Plugin.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code booking venue ...} — P0 business (Playwright real run)
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
     * @since 0.1.0
     */
    public static void registerDefaultSkills() {
        Skills registry = Skills.getInstance();
        registerBookingVenue(registry);
        registerKbQuery(registry);
    }

    private static void registerBookingVenue(Skills registry) {
        if (isRegistered(registry, "booking_venue")) {
            return;
        }
        ConfigManager.getInstance().load();
        VenueBookingClient client = new VenueBookingClient(
            ConfigManager.getInstance().browser(),
            RetryPolicies.defaultBooking());
        registry.register(new Skill<>("booking_venue", "体育场馆定时预约", new BookingTask(client)));
    }

    private static void registerKbQuery(Skills registry) {
        if (isRegistered(registry, "kb_query")) {
            return;
        }
        registry.register(new Skill<>("kb_query", "深大知识库查询", new KnowledgeTask()));
    }

    private static boolean isRegistered(Skills registry, String name) {
        return registry.all().stream().anyMatch(s -> s.name().equals(name));
    }

    /**
     * Main entry point.
     *
     * @param args command-line arguments
     * @since 0.1.0
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

package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.client.EhallScheduleClient;
import edu.szu.agent.client.cache.CacheStore;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.client.step.NavigateToScheduleStep;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.CourseEntry;
import edu.szu.agent.domain.ScheduleListResult;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicies;
import edu.szu.agent.task.CampusTask;
import edu.szu.agent.task.ScheduleListTask;
import edu.szu.agent.task.TaskInput;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * {@code schedule list} subcommand — returns the student's weekly course grid.
 *
 * <p>Routing (per PLAN-p1-real-fetch.md §4 阶段 1): 默认真实抓取 ehall
 * (Playwright + 30 天 session 复用),任何阶段失败回退到静态 MVP 课表。
 *
 * <p>Output JSON schema:
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": {
 *     "snapshotAt": "2026-06-18T10:00:00+08:00",
 *     "count": 4,
 *     "courses": [...]
 *   }
 * }
 * }</pre>
 *
 * // 编程技术: 注解 / 枚举 / Lambda / record
 *
 * @since 0.6.0
 * @author 王子豪
 */
@Command(
    name = "list",
    description = "List schedule courses (real ehall fetch with static fallback)",
    mixinStandardHelpOptions = true
)
public class ScheduleListCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter SNAPSHOT_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final CampusTask<ScheduleListResult> task;

    /**
     * Constructs the production command with the schedule task as its backend.
     *
     * @since 0.6.0
     * @author 王子豪
     */
    public ScheduleListCommand() {
        this(defaultTask());
    }

    /**
     * Creates the shared production task used by both CLI and Skill/MCP
     * registration. The real client factory creates a fresh browser lifecycle
     * per invocation; callers never pass credentials through CLI/MCP payloads.
     *
     * // Design Pattern: Factory Method
     * // 编程技术: 泛型 / Lambda
     *
     * @return schedule-list task with real-fetch and static fallback routing
     * @since 0.6.0
     * @author 王子豪
     */
    public static CampusTask<ScheduleListResult> defaultTask() {
        ConfigManager config = ConfigManager.getInstance();
        config.load();
        CacheStore sharedCache = config.cacheStore();
        Path userHome = Path.of(System.getProperty("user.home"));
        Duration sessionTtl = Duration.ofDays(30);
        Function<Account, EhallScheduleClient> realScheduleFactory = account -> {
            SessionStore store = new SessionStore(userHome, account.studentId());
            SessionProbe probe = new SessionProbe(
                NavigateToScheduleStep.EHALL_SCHEDULE_URL, "table.wut_table");
            return new EhallScheduleClient(
                account, config.browser(), RetryPolicies.defaultBooking(),
                store, probe, sessionTtl, sharedCache);
        };
        return new ScheduleListTask(realScheduleFactory);
    }

    /**
     * Test seam — injects the task so CLI routing can be verified without
     * starting Playwright or reading real credentials.
     *
     * @param task backend task for {@code schedule_list}
     * @since 0.6.0
     * @author 王子豪
     */
    ScheduleListCommand(CampusTask<ScheduleListResult> task) {
        this.task = Objects.requireNonNull(task, "task");
    }

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID (e.g. 2023150090)")
    private String username;

    @Option(names = {"-f", "--format"}, description = "Output format: json or human", defaultValue = "json")
    private String format;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        long startMs = System.currentTimeMillis();
        String traceId = Tracer.getInstance().generateTraceId();

        try {
            if (username == null || username.isBlank()) {
                long elapsed = System.currentTimeMillis() - startMs;
                out.println(CommandOutput.formatResult(false, null,
                    "INVALID_REQUEST", "Missing required option: --username",
                    traceId, elapsed, format));
                return 2;
            }

            ScheduleListResult result = task.execute(
                new TaskInput(Map.of("username", username.trim())));

            long elapsed = System.currentTimeMillis() - startMs;
            return formatAndOutput(out, result, traceId, elapsed);
        } catch (IllegalArgumentException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                "INVALID_REQUEST", e.getMessage(), traceId, elapsed, format));
            return 2;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                "UNKNOWN", e.getMessage(), traceId, elapsed, format));
            return 1;
        }
    }

    int formatAndOutput(PrintWriter out, ScheduleListResult result,
                        String traceId, long elapsedMs) {
        if (result instanceof ScheduleListResult.Success s) {
            ObjectNode data = buildSuccessData(s);
            out.println(CommandOutput.formatResult(true, data, null, null,
                traceId, elapsedMs, format));
            return 0;
        } else if (result instanceof ScheduleListResult.Failure f) {
            out.println(CommandOutput.formatResult(false, null,
                f.code().name(), f.message(), traceId, elapsedMs, format));
            return CommandOutput.exitCodeFor(f.code());
        }
        out.println(CommandOutput.formatResult(false, null, "UNKNOWN",
            "unexpected result type", traceId, elapsedMs, format));
        return 1;
    }

    static ObjectNode buildSuccessData(ScheduleListResult.Success s) {
        ObjectNode data = JSON.createObjectNode();
        data.put("snapshotAt",
            s.snapshotAt().atZone(ZoneId.systemDefault()).format(SNAPSHOT_FMT));
        data.put("count", s.courses().size());

        ArrayNode courses = JSON.createArrayNode();
        for (CourseEntry c : s.courses()) {
            courses.add(buildCourseNode(c));
        }
        data.set("courses", courses);
        return data;
    }

    static ObjectNode buildCourseNode(CourseEntry c) {
        ObjectNode node = JSON.createObjectNode();
        node.put("courseName", c.courseName());
        node.put("section", c.section());
        node.put("teacher", c.teacher());
        node.put("room", c.room());
        node.put("weekday", c.weekday().code());
        node.put("weekdayName", c.weekday().displayName());
        node.put("beginUnit", c.period().beginUnit());
        node.put("endUnit", c.period().endUnit());
        node.put("startTime", c.period().startTime().toString());
        node.put("endTime", c.period().endTime().toString());
        node.put("weekRange", c.weekRange().compact());
        ArrayNode weeks = JSON.createArrayNode();
        for (int w : c.weekRange().weeks()) {
            weeks.add(w);
        }
        node.set("weeks", weeks);
        node.put("isAdjusted", c.isAdjusted());
        return node;
    }
}

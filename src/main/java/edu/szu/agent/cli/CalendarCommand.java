package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.client.calendar.CalendarFetchProvider;
import edu.szu.agent.client.calendar.PlaywrightCalendarFetchProvider;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.calendar.AcademicEvent;
import edu.szu.agent.domain.calendar.CalendarListResult;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.task.CalendarTask;
import edu.szu.agent.task.CampusTask;
import edu.szu.agent.task.TaskInput;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * {@code calendar} subcommand — academic calendar operations.
 *
 * <p>Usage:
 * <pre>{@code
 * szu-agent calendar get
 * szu-agent calendar get --academic-year 2025-2026 --format json
 * }</pre>
 *
 * <p>P1 阶段 3: 默认真实抓取 {@code https://www.szu.edu.cn/xxgk/xl.htm}
 * (Playwright + 公开页),任何阶段失败或解析为空(当前页面渲染为 PNG 图像,无可
 * 解析文本)时自动回退到 2025-2026 春季学期静态 MVP。CLI / Skill / MCP 三条分发
 * 路径共用 {@link #defaultTask()} 工厂,行为一致。
 *
 * <p>// 编程技术: 注解 / Lambda / record / 工厂方法
 *
 * @since 0.3.0
 * @author 王子豪
 */
@Command(
    name = "calendar",
    description = "深大校历查询(真实抓取 + 静态回退,P1 阶段 3)",
    mixinStandardHelpOptions = true,
    subcommands = {CalendarCommand.GetAction.class}
)
public class CalendarCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }

    /**
     * Creates the shared production task used by both CLI and Skill/MCP
     * registration. The real supplier binds a fresh
     * {@link PlaywrightCalendarFetchProvider} per invocation; the static
     * snapshot is the fallback.
     *
     * <p>// Design Pattern: Factory Method
     * <p>// 编程技术: 泛型 / Lambda
     *
     * @return calendar-get task with real-fetch and static fallback routing
     * @since 0.4.0
     * @author 王子豪
     */
    public static CampusTask<CalendarListResult> defaultTask() {
        ConfigManager config = ConfigManager.getInstance();
        config.load();
        CalendarFetchProvider provider = new PlaywrightCalendarFetchProvider(
            config.browser(), null);
        return new CalendarTask(
            () -> provider.fetchAndParse(),
            CalendarTask::spring2026Events);
    }

    @Command(
        name = "get",
        description = "Get the SZU academic calendar (real fetch with static fallback, P1 阶段 3)"
    )
    public static class GetAction implements Callable<Integer> {

        private static final ObjectMapper JSON = new ObjectMapper();

        private final CampusTask<CalendarListResult> task;

        /**
         * Production constructor — wires the shared real-fetch task.
         */
        public GetAction() {
            this(CalendarCommand.defaultTask());
        }

        /**
         * Test seam — inject the task so CLI routing can be verified
         * without starting Playwright.
         *
         * @param task backend task for {@code calendar_get}
         * @since 0.4.0
         */
        GetAction(CampusTask<CalendarListResult> task) {
            this.task = Objects.requireNonNull(task, "task");
        }

        @Spec
        private CommandSpec spec;

        @Option(names = {"-y", "--academic-year"},
            description = "Academic year, e.g. 2025-2026")
        private String academicYear;

        @Option(names = {"-f", "--format"},
            description = "Output format: json or human", defaultValue = "json")
        private String format;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            long startMs = System.currentTimeMillis();
            String traceId = Tracer.getInstance().generateTraceId();

            try {
                Map<String, String> params = new LinkedHashMap<>();
                if (academicYear != null && !academicYear.isBlank()) {
                    params.put("academicYear", academicYear);
                }

                CalendarListResult result = task.execute(new TaskInput(params));
                long elapsed = System.currentTimeMillis() - startMs;
                return formatAndOutput(out, result, traceId, elapsed, format);
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

        int formatAndOutput(PrintWriter out, CalendarListResult result,
                            String traceId, long elapsedMs, String fmt) {
            if (result instanceof CalendarListResult.Success s) {
                ObjectNode data = buildJsonData(s.events());
                out.println(CommandOutput.formatResult(true, data, null, null,
                    traceId, elapsedMs, fmt));
                return 0;
            }
            if (result instanceof CalendarListResult.Failure f) {
                out.println(CommandOutput.formatResult(false, null,
                    f.code().name(), f.message(), traceId, elapsedMs, fmt));
                return CommandOutput.exitCodeFor(f.code());
            }
            out.println(CommandOutput.formatResult(false, null, "UNKNOWN",
                "unexpected result type", traceId, elapsedMs, fmt));
            return 1;
        }

        private ObjectNode buildJsonData(List<AcademicEvent> events) {
            ObjectNode data = JSON.createObjectNode();
            data.put("academicYear", academicYearOrDefault());
            data.put("count", events.size());
            ArrayNode array = JSON.createArrayNode();
            for (AcademicEvent e : events) {
                ObjectNode node = JSON.createObjectNode();
                node.put("date", e.date().toString());
                node.put("type", e.type().name());
                node.put("description", e.description());
                node.put("semester", e.semester());
                node.put("weekOfTerm", e.weekOfTerm() != null ? e.weekOfTerm() : null);
                array.add(node);
            }
            data.set("events", array);
            return data;
        }

        private String academicYearOrDefault() {
            return (academicYear != null && !academicYear.isBlank())
                ? academicYear
                : CalendarTask.defaultAcademicYear();
        }
    }
}
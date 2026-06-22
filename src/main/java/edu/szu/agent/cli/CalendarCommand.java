package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.domain.calendar.AcademicEvent;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.task.CalendarTask;
import edu.szu.agent.task.TaskInput;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>This is the static MVP for PRD §3.2.4 {@code calendar_get}.  No
 * browser is launched; the 2025-2026 spring semester data is embedded.
 *
 * // 编程技术: 注解 / Lambda
 *
 * @since 0.3.0
 * @author 王子豪
 */
@Command(
    name = "calendar",
    description = "深大校历查询",
    mixinStandardHelpOptions = true,
    subcommands = {CalendarCommand.GetAction.class}
)
public class CalendarCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }

    @Command(
        name = "get",
        description = "Get the static SZU academic calendar"
    )
    public static class GetAction implements Callable<Integer> {

        private static final ObjectMapper JSON = new ObjectMapper();

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

                List<AcademicEvent> events = new CalendarTask().execute(new TaskInput(params));

                long elapsed = System.currentTimeMillis() - startMs;
                if ("json".equalsIgnoreCase(format)) {
                    ObjectNode data = buildJsonData(events);
                    out.println(CommandOutput.formatResult(true, data, null, null,
                        traceId, elapsed, format));
                } else {
                    out.println(formatHuman(events, traceId, elapsed));
                }
                return 0;
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

        private String formatHuman(List<AcademicEvent> events,
                                   String traceId, long elapsedMs) {
            StringBuilder sb = new StringBuilder();
            sb.append("Academic year: ").append(academicYearOrDefault()).append('\n');
            sb.append("Events: ").append(events.size()).append('\n');
            for (AcademicEvent e : events) {
                sb.append("  ").append(e.date())
                    .append(" [").append(e.type()).append("] ")
                    .append(e.description()).append('\n');
            }
            sb.append("Trace: ").append(traceId).append('\n');
            sb.append("Elapsed: ").append(elapsedMs).append("ms");
            return sb.toString();
        }

        private String academicYearOrDefault() {
            return (academicYear != null && !academicYear.isBlank())
                ? academicYear
                : CalendarTask.defaultAcademicYear();
        }
    }
}

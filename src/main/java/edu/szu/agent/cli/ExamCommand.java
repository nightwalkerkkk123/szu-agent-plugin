package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.exam.ExamListClient;
import edu.szu.agent.client.exam.PlaywrightExamFetchProvider;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.exam.ExamSchedule;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.task.CampusTask;
import edu.szu.agent.task.ExamListTask;
import edu.szu.agent.task.TaskInput;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code exam} subcommand — SZU exam schedule operations.
 *
 * <p>Usage:
 * <pre>{@code
 * szu-agent exam list
 * szu-agent exam list --username 2023150090 --semester 2025-2026-2 --status 待开始考试 --format json
 * }</pre>
 *
 * <p>This is the static MVP for exam_list.
 *
 * // 编程技术: 注解 / Lambda
 *
 * @since 0.4.0
 * @author 王子豪
 */
@Command(
    name = "exam",
    description = "深大考试安排查询",
    mixinStandardHelpOptions = true,
    subcommands = {ExamCommand.ListAction.class}
)
public class ExamCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }

    @Command(
        name = "list",
        description = "List SZU exam schedules from the static snapshot"
    )
    public static class ListAction implements Callable<Integer> {

        private static final ObjectMapper JSON = new ObjectMapper();
        private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
        private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        @Spec
        private CommandSpec spec;

        @Option(names = {"-u", "--username"}, description = "Student ID (e.g. 2023150090)")
        private String username;

        @Option(names = {"-s", "--semester"}, description = "Semester filter (e.g. 2025-2026-2)")
        private String semester;

        @Option(names = {"-t", "--status"}, description = "Status filter: 待开始考试 / 已结束")
        private String status;

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

                Map<String, String> params = new LinkedHashMap<>();
                params.put("username", username);
                if (semester != null && !semester.isBlank()) {
                    params.put("semester", semester);
                }
                if (status != null && !status.isBlank()) {
                    params.put("status", status);
                }

                List<ExamSchedule> exams = new ExamListTask().execute(new TaskInput(params));

                long elapsed = System.currentTimeMillis() - startMs;
                if ("json".equalsIgnoreCase(format)) {
                    ObjectNode data = buildJsonData(exams);
                    out.println(CommandOutput.formatResult(true, data, null, null,
                        traceId, elapsed, format));
                } else {
                    out.println(formatHuman(exams, traceId, elapsed));
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

        private ObjectNode buildJsonData(List<ExamSchedule> exams) {
            ObjectNode data = JSON.createObjectNode();
            data.put("count", exams.size());
            data.put("semester", semester != null ? semester : "ALL");
            data.put("status", status != null ? status : "ALL");
            ArrayNode array = JSON.createArrayNode();
            for (ExamSchedule e : exams) {
                ObjectNode node = JSON.createObjectNode();
                node.put("date", e.date());
                node.put("weekday", e.weekday());
                node.put("courseName", e.courseName());
                node.put("courseCode", e.courseCode());
                node.put("examDate", e.examDate().format(DATE_FORMAT));
                node.put("startTime", e.startTime().format(TIME_FORMAT));
                node.put("endTime", e.endTime().format(TIME_FORMAT));
                node.put("venue", e.venue());
                node.put("invigilator", e.invigilator());
                array.add(node);
            }
            data.set("exams", array);
            return data;
        }

        private String formatHuman(List<ExamSchedule> exams, String traceId, long elapsedMs) {
            StringBuilder sb = new StringBuilder();
            sb.append("Exam Schedules: ").append(exams.size()).append('\n');
            sb.append("Filter: semester=").append(semester != null ? semester : "ALL")
                .append(", status=").append(status != null ? status : "ALL").append('\n');
            for (ExamSchedule e : exams) {
                sb.append("  ").append(e.examDate())
                    .append(" ").append(e.weekday())
                    .append(" ").append(e.courseName())
                    .append(" [").append(e.courseCode()).append("]")
                    .append(" ").append(e.startTime()).append("-").append(e.endTime())
                    .append(" @ ").append(e.venue())
                    .append(" (监考: ").append(e.invigilator()).append(")\n");
            }
            sb.append("Trace: ").append(traceId).append('\n');
            sb.append("Elapsed: ").append(elapsedMs).append("ms");
            return sb.toString();
        }
    }

    /**
     * Factory method to create a fully-wired {@link ExamListTask} with real-fetch
     * and static fallback for use by CLI / Skill / MCP registration.
     *
     * <p>// Design Pattern: Factory Method
     *
     * @return configured task instance
     */
    public static CampusTask<List<ExamSchedule>> defaultTask() {
        ConfigManager config = ConfigManager.getInstance();
        config.load();
        BrowserLifecycle browser = config.browser();
        PlaywrightExamFetchProvider provider = new PlaywrightExamFetchProvider(browser);
        return new ExamListTask(
            provider::fetchAndParse,
            () -> new ExamListClient().list());
    }
}
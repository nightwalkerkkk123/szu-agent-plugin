package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.domain.notice.Notice;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.task.NoticeTask;
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
 * {@code notice} subcommand — SZU board (公文通) operations.
 *
 * <p>Usage:
 * <pre>{@code
 * szu-agent notice list
 * szu-agent notice list --category LECTURE --days-back 7 --format json
 * }</pre>
 *
 * <p>This is the static MVP for PRD §3.2.2 {@code notice_list}.
 *
 * // 编程技术: 注解 / Lambda
 *
 * @since 0.3.0
 * @author 王子豪
 */
@Command(
    name = "notice",
    description = "深大公文通通知查询",
    mixinStandardHelpOptions = true,
    subcommands = {NoticeCommand.ListAction.class}
)
public class NoticeCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }

    @Command(
        name = "list",
        description = "List SZU board notices from the static snapshot"
    )
    public static class ListAction implements Callable<Integer> {

        private static final ObjectMapper JSON = new ObjectMapper();

        @Spec
        private CommandSpec spec;

        @Option(names = {"-u", "--username"}, description = "Student ID (e.g. 2023150090)")
        private String username;

        @Option(names = {"-c", "--category"}, description = "Optional category: ANNOUNCEMENT / LECTURE / COMPETITION / PUBLICITY")
        private String category;

        @Option(names = {"-d", "--days-back"}, description = "Only notices published within last N days", defaultValue = "30")
        private int daysBack;

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
                if (category != null && !category.isBlank()) {
                    params.put("category", category);
                }
                params.put("daysBack", String.valueOf(daysBack));

                List<Notice> notices = new NoticeTask().execute(new TaskInput(params));

                long elapsed = System.currentTimeMillis() - startMs;
                if ("json".equalsIgnoreCase(format)) {
                    ObjectNode data = buildJsonData(notices);
                    out.println(CommandOutput.formatResult(true, data, null, null,
                        traceId, elapsed, format));
                } else {
                    out.println(formatHuman(notices, traceId, elapsed));
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

        private ObjectNode buildJsonData(List<Notice> notices) {
            ObjectNode data = JSON.createObjectNode();
            data.put("count", notices.size());
            data.put("category", category != null ? category : "ALL");
            data.put("daysBack", daysBack);
            ArrayNode array = JSON.createArrayNode();
            for (Notice n : notices) {
                ObjectNode node = JSON.createObjectNode();
                node.put("id", n.id());
                node.put("title", n.title());
                node.put("category", n.category().name());
                node.put("publishedAt", n.publishedAt().toString());
                node.put("url", n.url());
                node.put("hasAttachment", n.hasAttachment());
                array.add(node);
            }
            data.set("notices", array);
            return data;
        }

        private String formatHuman(List<Notice> notices, String traceId, long elapsedMs) {
            StringBuilder sb = new StringBuilder();
            sb.append("Notices: ").append(notices.size()).append('\n');
            sb.append("Filter: category=").append(category != null ? category : "ALL")
                .append(", daysBack=").append(daysBack).append('\n');
            for (Notice n : notices) {
                sb.append("  ").append(n.publishedAt())
                    .append(" [").append(n.category()).append("] ")
                    .append(n.title()).append('\n');
            }
            sb.append("Trace: ").append(traceId).append('\n');
            sb.append("Elapsed: ").append(elapsedMs).append("ms");
            return sb.toString();
        }
    }
}

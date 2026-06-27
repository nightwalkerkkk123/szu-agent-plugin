package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.client.notice.NoticeListClient;
import edu.szu.agent.client.notice.PlaywrightNoticeFetchProvider;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.notice.Notice;
import edu.szu.agent.domain.notice.NoticeListResult;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.task.CampusTask;
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
import java.util.Objects;
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
 * <p>P1 阶段 2: 默认真实抓取 ehall 公文通(Playwright + 公开页),失败回退到
 * 静态 snapshot。CLI / Skill / MCP 三条分发路径共用 {@link #defaultTask()}
 * 工厂,行为一致。
 *
 * <p>// 编程技术: 注解 / Lambda / record / 工厂方法
 *
 * @since 0.3.0
 * @author 王子豪
 */
@Command(
    name = "notice",
    description = "深大公文通通知查询(真实抓取 + 静态回退,P1 阶段 2)",
    mixinStandardHelpOptions = true,
    subcommands = {NoticeCommand.ListAction.class}
)
public class NoticeCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }

    /**
     * Creates the shared production task used by both CLI and Skill/MCP
     * registration. The real client supplier binds a fresh
     * {@link PlaywrightNoticeFetchProvider} per invocation; the static
     * snapshot is the fallback.
     *
     * <p>// Design Pattern: Factory Method
     * <p>// 编程技术: 泛型 / Lambda
     *
     * @return notice-list task with real-fetch and static fallback routing
     * @since 0.4.0
     * @author 王子豪
     */
    public static CampusTask<NoticeListResult> defaultTask() {
        ConfigManager config = ConfigManager.getInstance();
        config.load();
        return new NoticeTask(
            () -> new NoticeListClient(new PlaywrightNoticeFetchProvider(
                config.browser(), null)),
            NoticeListClient::new);
    }

    @Command(
        name = "list",
        description = "List SZU board notices (real fetch with static fallback, P1 阶段 2)"
    )
    public static class ListAction implements Callable<Integer> {

        private static final ObjectMapper JSON = new ObjectMapper();

        private final CampusTask<NoticeListResult> task;

        /**
         * Production constructor — wires the shared real-fetch task.
         */
        public ListAction() {
            this(NoticeCommand.defaultTask());
        }

        /**
         * Test seam — inject the task so CLI routing can be verified
         * without starting Playwright.
         *
         * @param task backend task for {@code notice_list}
         * @since 0.4.0
         */
        ListAction(CampusTask<NoticeListResult> task) {
            this.task = Objects.requireNonNull(task, "task");
        }

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

                NoticeListResult result = task.execute(new TaskInput(params));
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

        int formatAndOutput(PrintWriter out, NoticeListResult result,
                            String traceId, long elapsedMs, String fmt) {
            if (result instanceof NoticeListResult.Success s) {
                ObjectNode data = buildJsonData(s.notices());
                out.println(CommandOutput.formatResult(true, data, null, null,
                    traceId, elapsedMs, fmt));
                return 0;
            }
            if (result instanceof NoticeListResult.Failure f) {
                out.println(CommandOutput.formatResult(false, null,
                    f.code().name(), f.message(), traceId, elapsedMs, fmt));
                return CommandOutput.exitCodeFor(f.code());
            }
            out.println(CommandOutput.formatResult(false, null, "UNKNOWN",
                "unexpected result type", traceId, elapsedMs, fmt));
            return 1;
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
    }
}

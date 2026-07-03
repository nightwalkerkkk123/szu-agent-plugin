package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.ChaoxingHomeworkClient;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.Homework;
import edu.szu.agent.domain.HomeworkListResult;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicies;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code homework list} subcommand — lists pending homework from LMS.
 *
 * <p>Output JSON schema per PRD §5.2:
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": [
 *     {
 *       "homeworkId": "169193",
 *       "courseName": "操作系统",
 *       "title": "综合实验二",
 *       "deadline": "2026.06.24 23:59",
 *       "status": "待提交"
 *     }
 *   ],
 *   "errorCode": null,
 *   "errorMessage": null,
 *   "traceId": "20260614-ABC123",
 *   "elapsedMs": 4321
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
    description = "List homework from SZU LMS",
    mixinStandardHelpOptions = true
)
public class HomeworkListCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID (e.g. 2023150090)")
    private String username;

    @Option(names = {"-f", "--format"}, description = "Output format: json or human", defaultValue = "json")
    private String format;

    @Option(names = {"-e", "--env-file"}, description = "Path to .env file for credentials")
    private String envFile;

    @Option(names = {"--dry-run"}, description = "Dry-run mode (unit test fixture only, not for demo)")
    private boolean dryRun;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        long startMs = System.currentTimeMillis();
        String traceId = Tracer.getInstance().generateTraceId();

        try {
            if (dryRun) {
                ArrayNode data = JSON.createArrayNode();
                ObjectNode item = JSON.createObjectNode();
                item.put("homeworkId", "dry-run-stub");
                item.put("courseName", "dry-run-course");
                item.put("title", "dry-run-homework");
                item.put("deadline", "2099.12.31 23:59");
                item.put("status", "待提交");
                data.add(item);
                long elapsed = System.currentTimeMillis() - startMs;
                out.println(formatAndPrint(true, data, null, null, traceId, elapsed));
                return 0;
            }

            if (envFile != null) {
                Path envPath = Path.of(envFile);
                if (!Files.exists(envPath)) {
                    long elapsed = System.currentTimeMillis() - startMs;
                    out.println(formatAndPrint(false, null,
                        "INVALID_REQUEST", "env file not found: " + envFile,
                        traceId, elapsed));
                    return 3;
                }
                ConfigManager.getInstance().loadEnvFile(envPath);
            }

            Map<String, String> effectiveEnv = new LinkedHashMap<>(System.getenv());
            if (envFile != null) {
                effectiveEnv.putAll(ConfigManager.getInstance().envFileProps());
            }

            Account account = AccountResolver.resolve(username, effectiveEnv);

            ConfigManager.getInstance().load();
            ChaoxingHomeworkClient client = new ChaoxingHomeworkClient(
                account,
                ConfigManager.getInstance().browser(),
                RetryPolicies.defaultBooking());

            HomeworkListResult result = client.list();

            long elapsed = System.currentTimeMillis() - startMs;
            return formatAndOutput(out, result, traceId, elapsed);
        } catch (AccountResolutionException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(formatAndPrint(false, null,
                "CREDENTIAL_NOT_FOUND", e.getMessage(), traceId, elapsed));
            return 3;
        } catch (BookingException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(formatAndPrint(false, null,
                e.code().name(), e.getMessage(), traceId, elapsed));
            return CommandOutput.exitCodeFor(e.code());
        } catch (IllegalArgumentException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(formatAndPrint(false, null,
                "INVALID_REQUEST", e.getMessage(), traceId, elapsed));
            return 2;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(formatAndPrint(false, null,
                "UNKNOWN", e.getMessage(), traceId, elapsed));
            return 1;
        }
    }

    private int formatAndOutput(PrintWriter out, HomeworkListResult result,
                                String traceId, long elapsedMs) {
        if (result instanceof HomeworkListResult.Success s) {
            ArrayNode data = JSON.createArrayNode();
            for (Homework h : s.homeworks()) {
                ObjectNode item = JSON.createObjectNode();
                item.put("homeworkId", h.homeworkId());
                item.put("courseName", h.courseName());
                item.put("title", h.title());
                item.put("deadline", h.deadline());
                item.put("status", h.status());
                data.add(item);
            }
            out.println(formatAndPrint(true, data, null, null, traceId, elapsedMs));
            return 0;
        } else if (result instanceof HomeworkListResult.Failure f) {
            out.println(formatAndPrint(false, null,
                f.code().name(), f.message(), traceId, elapsedMs));
            return CommandOutput.exitCodeFor(f.code());
        }
        out.println(formatAndPrint(false, null, "UNKNOWN",
            "unexpected result type", traceId, elapsedMs));
        return 1;
    }

    private String formatAndPrint(boolean success, ArrayNode data,
                                  String errorCode, String errorMessage,
                                  String traceId, long elapsedMs) {
        if ("human".equalsIgnoreCase(format)) {
            return formatHuman(success, data, errorCode, errorMessage, traceId, elapsedMs);
        }
        return CommandOutput.formatResult(success, data, errorCode, errorMessage,
            traceId, elapsedMs, format);
    }

    private String formatHuman(boolean success, ArrayNode data,
                               String errorCode, String errorMessage,
                               String traceId, long elapsedMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Success: ").append(success).append('\n');
        if (data != null) {
            sb.append("Homework count: ").append(data.size()).append('\n');
            for (int i = 0; i < data.size(); i++) {
                ObjectNode item = (ObjectNode) data.get(i);
                sb.append("  - ")
                    .append(item.path("courseName").asText()).append(": ")
                    .append(item.path("title").asText()).append(" (")
                    .append(item.path("status").asText()).append(")\n");
            }
        }
        if (errorCode != null) {
            sb.append("Error: ").append(errorCode).append('\n');
            sb.append("Detail: ").append(errorMessage).append('\n');
        }
        sb.append("Trace: ").append(traceId).append('\n');
        sb.append("Elapsed: ").append(elapsedMs).append("ms");
        return sb.toString();
    }
}

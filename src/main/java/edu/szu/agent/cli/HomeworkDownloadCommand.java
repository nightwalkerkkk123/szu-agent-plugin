package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.ChaoxingAttachmentDownloadClient;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.HomeworkAttachment;
import edu.szu.agent.domain.HomeworkDownloadRequest;
import edu.szu.agent.domain.HomeworkDownloadResult;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicies;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code homework download} subcommand — downloads all attachments of a
 * single LMS homework to a local directory.
 *
 * <p>Output JSON schema:
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": {
 *     "homeworkId": "169193",
 *     "attachments": [
 *       {"fileName":"期末大作业.docx",
 *        "localPath":"/tmp/dl/期末大作业.docx",
 *        "sizeBytes": 26312}
 *     ]
 *   },
 *   "errorCode": null,
 *   "errorMessage": null,
 *   "traceId": "...",
 *   "elapsedMs": 4321
 * }
 * }</pre>
 *
 * <p>Empty result is reported as {@code success: true} with
 * {@code data.attachments: []} (not an error).
 *
 * // 编程技术: 注解 / Builder / Lambda / sealed result
 *
 * @since 0.1.0
 * @author 王子豪
 */
@Command(
    name = "download",
    description = "Download all attachments of a single LMS homework",
    mixinStandardHelpOptions = true
)
public class HomeworkDownloadCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID (e.g. 2023150090)")
    private String username;

    @Option(names = {"--homework-id"}, required = true,
        description = "Numeric homework id (e.g. 169193)")
    private String homeworkId;

    @Option(names = {"-o", "--output-dir"}, required = true,
        description = "Local directory to write files to (must exist)")
    private String outputDir;

    @Option(names = {"-f", "--format"}, description = "Output format: json or human", defaultValue = "json")
    private String format;

    @Option(names = {"-e", "--env-file"}, description = "Path to .env file for credentials")
    private String envFile;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        long startMs = System.currentTimeMillis();
        String traceId = Tracer.getInstance().generateTraceId();

        try {
            if (envFile != null) {
                Path envPath = Path.of(envFile);
                if (!Files.exists(envPath)) {
                    long elapsed = System.currentTimeMillis() - startMs;
                    out.println(formatResult(false, null,
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
            Path outDir = Path.of(outputDir);
            if (!Files.isDirectory(outDir)) {
                long elapsed = System.currentTimeMillis() - startMs;
                out.println(formatResult(false, null,
                    "OUTPUT_DIR_INVALID",
                    "outputDir is not a directory: " + outputDir,
                    traceId, elapsed));
                return 2;
            }

            ConfigManager.getInstance().load();
            SessionStore store = new SessionStore(
                Path.of(System.getProperty("user.home")), account.studentId());
            SessionProbe probe = new SessionProbe(
                "https://lms.szu.edu.cn/user/index", ".todo-list-container");
            ChaoxingAttachmentDownloadClient client = new ChaoxingAttachmentDownloadClient(
                account,
                ConfigManager.getInstance().browser(),
                RetryPolicies.defaultBooking(),
                store,
                probe,
                Duration.ofDays(30));

            HomeworkDownloadRequest req = HomeworkDownloadRequest.builder()
                .homeworkId(homeworkId)
                .outputDir(outDir)
                .build();

            HomeworkDownloadResult result = client.download(req);

            long elapsed = System.currentTimeMillis() - startMs;
            return formatAndOutput(out, result, traceId, elapsed);
        } catch (AccountResolutionException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(formatResult(false, null,
                "CREDENTIAL_NOT_FOUND", e.getMessage(), traceId, elapsed));
            return 3;
        } catch (BookingException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(formatResult(false, null,
                e.code().name(), e.getMessage(), traceId, elapsed));
            return exitCodeFor(e.code());
        } catch (IllegalArgumentException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(formatResult(false, null,
                "INVALID_REQUEST", e.getMessage(), traceId, elapsed));
            return 2;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(formatResult(false, null,
                "UNKNOWN", e.getMessage(), traceId, elapsed));
            return 1;
        }
    }

    private String formatResult(boolean success, ObjectNode data,
                                String errorCode, String errorMessage,
                                String traceId, long elapsedMs) {
        if ("human".equalsIgnoreCase(format)) {
            return formatHuman(success, data, errorCode, errorMessage, traceId, elapsedMs);
        }
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("success", success);
            root.set("data", data != null ? data : JSON.nullNode());
            root.put("errorCode", errorCode);
            root.put("errorMessage", errorMessage);
            root.put("traceId", traceId);
            root.put("elapsedMs", elapsedMs);
            return JSON.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON output", e);
        }
    }

    private String formatHuman(boolean success, ObjectNode data,
                               String errorCode, String errorMessage,
                               String traceId, long elapsedMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Success: ").append(success).append('\n');
        if (data != null) {
            sb.append("HomeworkId: ").append(data.path("homeworkId").asText()).append('\n');
            ArrayNode atts = (ArrayNode) data.path("attachments");
            sb.append("Attachments: ").append(atts.size()).append('\n');
            for (int i = 0; i < atts.size(); i++) {
                ObjectNode a = (ObjectNode) atts.get(i);
                sb.append("  - ")
                    .append(a.path("fileName").asText())
                    .append(" (").append(a.path("sizeBytes").asLong()).append(" bytes) -> ")
                    .append(a.path("localPath").asText()).append('\n');
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

    private int formatAndOutput(PrintWriter out, HomeworkDownloadResult result,
                                String traceId, long elapsedMs) {
        if (result instanceof HomeworkDownloadResult.Success s) {
            out.println(formatResult(true, buildData(s, s.attachments().isEmpty() ? "" : s.attachments().get(0).homeworkId()),
                null, null, traceId, elapsedMs));
            return 0;
        } else if (result instanceof HomeworkDownloadResult.Empty e) {
            ObjectNode data = JSON.createObjectNode();
            data.put("homeworkId", e.homeworkId());
            data.set("attachments", JSON.createArrayNode());
            out.println(formatResult(true, data, null, null, traceId, elapsedMs));
            return 0;
        } else if (result instanceof HomeworkDownloadResult.Failure f) {
            out.println(formatResult(false, null,
                f.code().name(), f.message(), traceId, elapsedMs));
            return exitCodeFor(f.code());
        }
        out.println(formatResult(false, null, "UNKNOWN", "unexpected result type",
            traceId, elapsedMs));
        return 1;
    }

    private ObjectNode buildData(HomeworkDownloadResult.Success s, String homeworkId) {
        ObjectNode data = JSON.createObjectNode();
        data.put("homeworkId", homeworkId);
        ArrayNode atts = JSON.createArrayNode();
        for (HomeworkAttachment a : s.attachments()) {
            ObjectNode item = JSON.createObjectNode();
            item.put("fileName", a.fileName());
            item.put("localPath", a.localPath() != null ? a.localPath().toString() : null);
            item.put("sizeBytes", a.sizeBytes());
            atts.add(item);
        }
        data.set("attachments", atts);
        return data;
    }

    static int exitCodeFor(ErrorCode code) {
        return switch (code.severity()) {
            case LOW -> 2;
            case MEDIUM -> 1;
            case HIGH -> switch (code) {
                case BROWSER_CRASH -> 4;
                default -> 1;
            };
            case CRITICAL -> 3;
        };
    }
}

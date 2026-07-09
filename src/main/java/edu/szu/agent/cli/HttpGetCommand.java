package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.client.http.CampusHttpClient;
import edu.szu.agent.client.http.CookieJar;
import edu.szu.agent.client.session.HttpSession;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code http-get} subcommand — fetch an internal URL using a persisted session.
 *
 * <p>This demonstrates session reuse after a successful {@code direct-login}:
 * the command loads the saved cookies for the given username and performs an
 * authenticated GET without re-authenticating.
 *
 * <p>// Design Pattern: Adapter (CLI wrapper around session-aware HTTP client)
 * // 编程技术: 注解 / Builder / try-with-resources
 *
 * @since 0.6.0
 * @author 王子豪
 */
@Command(
    name = "http-get",
    description = "Fetch an internal URL with a persisted session",
    mixinStandardHelpOptions = true
)
public class HttpGetCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID whose session to load", required = true)
    private String username;

    @Option(names = {"--url"}, description = "URL to fetch", required = true)
    private String url;

    @Option(names = {"--session-home"}, description = "Directory under which .szu-agent/sessions is created",
        defaultValue = "${sys:user.home}")
    private String sessionHome;

    @Option(names = {"--trust-all"}, description = "Disable TLS certificate validation (dev/internal only)")
    private boolean trustAll;

    @Option(names = {"-o", "--output"}, description = "Write the response body to this file")
    private String outputPath;

    @Option(names = {"--preview-limit"}, description = "Maximum characters of body preview to return", defaultValue = "500")
    private int previewLimit;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        long startMs = System.currentTimeMillis();
        String traceId = Tracer.getInstance().generateTraceId();

        SessionStore store = new SessionStore(Path.of(sessionHome), username);
        if (!store.exists()) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.SESSION_NOT_FOUND.name(),
                "No persisted state for " + username + "; run direct-login first",
                traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.SESSION_NOT_FOUND);
        }

        try {
            HttpSession session = HttpSession.read(store);
            CookieJar jar = new CookieJar(session.cookies());

            try (CampusHttpClient http = CampusHttpClient.builder()
                    .trustAll(trustAll)
                    .cookieJar(jar)
                    .build()) {
                String body = http.get(url);
                String outputFile = null;
                if (outputPath != null) {
                    Path target = Path.of(outputPath);
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, body);
                    outputFile = target.toAbsolutePath().toString();
                }

                String preview = null;
                if (body != null && !body.isBlank() && outputFile == null) {
                    int limit = Math.min(body.length(), Math.max(0, previewLimit));
                    preview = body.substring(0, limit)
                        .replace("\r", "")
                        .replace("\n", " ")
                        .trim();
                }

                ObjectNode data = JSON.createObjectNode();
                data.put("traceId", traceId);
                data.put("url", url);
                data.put("status", 200);
                data.put("bodyLength", body != null ? body.length() : -1);
                data.put("bodyPreview", preview);
                data.put("outputPath", outputFile);
                data.put("sessionAgeMs",
                    System.currentTimeMillis() - session.savedAt().toEpochMilli());
                data.put("durationMs", System.currentTimeMillis() - startMs);

                out.println(CommandOutput.formatResult(true, data, null, null,
                    traceId, data.get("durationMs").asLong(), "json"));
                return 0;
            }
        } catch (BookingException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                e.code().name(), e.getMessage(), traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(e.code());
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.SESSION_READ_FAILED.name(),
                "Failed to load persisted state: " + e.getMessage(), traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.SESSION_READ_FAILED);
        } catch (RuntimeException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.UNKNOWN.name(), "Unexpected error: " + e.getMessage(),
                traceId, elapsed, "json"));
            return 1;
        }
    }
}

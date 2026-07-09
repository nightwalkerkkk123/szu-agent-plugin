package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.client.http.CampusHttpClient;
import edu.szu.agent.client.http.CookieJar;
import edu.szu.agent.client.http.EhallSportVenueClient;
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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code direct-dates} subcommand — list dates currently open for booking.
 *
 * <p>// Design Pattern: Adapter (CLI wrapper around direct HTTP discovery)
 * // 编程技术: 注解 / Builder / try-with-resources
 *
 * @since 0.6.0
 * @author 王子豪
 */
@Command(
    name = "direct-dates",
    description = "List bookable dates from ehall (direct HTTP)",
    mixinStandardHelpOptions = true
)
public class DirectDatesCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--campus"}, description = "Campus name (YUEHAI/LIHU) or code; for reference in output")
    private String campusName;

    @Option(names = {"--sport"}, description = "Sport name (TENNIS/BASKETBALL/etc) or code; for reference in output")
    private String sportName;

    @Option(names = {"--session-home"}, description = "Directory under which .szu-agent/sessions is created",
        defaultValue = "${sys:user.home}")
    private String sessionHome;

    @Option(names = {"--trust-all"}, description = "Disable TLS certificate validation (dev/internal only)")
    private boolean trustAll;

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
                EhallSportVenueClient api = new EhallSportVenueClient(http);
                List<String> dates = api.getAvailableDates();

                ObjectNode result = JSON.createObjectNode();
                result.put("traceId", traceId);
                result.put("username", username);
                result.put("campus", campusName != null ? campusName : "");
                result.put("sport", sportName != null ? sportName : "");
                ArrayNode array = result.putArray("dates");
                for (String d : dates) {
                    array.add(d);
                }
                result.put("durationMs", System.currentTimeMillis() - startMs);

                out.println(CommandOutput.formatResult(true, result, null, null,
                    traceId, result.get("durationMs").asLong(), "json"));
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

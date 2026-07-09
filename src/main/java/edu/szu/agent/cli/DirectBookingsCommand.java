package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.client.http.CampusHttpClient;
import edu.szu.agent.client.http.CookieJar;
import edu.szu.agent.client.http.EhallSportVenueClient;
import edu.szu.agent.client.http.EhallSportVenueClient.BookingRecord;
import edu.szu.agent.client.http.EhallSportVenueClient.MyBookingsPage;
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
import java.util.concurrent.Callable;

/**
 * {@code direct-bookings} subcommand — list the current user's ehall
 * sport-venue bookings via direct HTTP.
 *
 * <p>Loads a persisted CAS session and calls the ehall my-booking API
 * directly, without Playwright.
 *
 * <p>// Design Pattern: Adapter (CLI wrapper around direct HTTP booking client)
 * // 编程技术: 注解 / Builder / try-with-resources
 *
 * @since 0.6.0
 * @author 王子豪
 */
@Command(
    name = "direct-bookings",
    description = "List current user's ehall sport-venue bookings (direct HTTP)",
    mixinStandardHelpOptions = true
)
public class DirectBookingsCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--page"}, description = "1-based page number", defaultValue = "1")
    private int pageNumber;

    @Option(names = {"--page-size"}, description = "Rows per page", defaultValue = "20")
    private int pageSize;

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

        if (pageNumber < 1) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(), "page must be >= 1",
                traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }
        if (pageSize < 1 || pageSize > 100) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(), "page-size must be between 1 and 100",
                traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }

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
                MyBookingsPage page = api.getMyBookings(pageNumber, pageSize);

                ObjectNode data = JSON.createObjectNode();
                data.put("traceId", traceId);
                data.put("username", username);
                data.put("totalSize", page.totalSize());
                data.put("pageNumber", page.pageNumber());
                data.put("pageSize", page.pageSize());
                ArrayNode rows = data.putArray("rows");
                for (BookingRecord r : page.rows()) {
                    ObjectNode row = rows.addObject();
                    row.put("dhid", r.dhid());
                    row.put("campusCode", r.campusCode());
                    row.put("campusName", r.campusName());
                    row.put("sportCode", r.sportCode());
                    row.put("sportName", r.sportName());
                    row.put("venueGroupCode", r.venueGroupCode());
                    row.put("venueGroupName", r.venueGroupName());
                    row.put("venueWid", r.venueWid());
                    row.put("venueName", r.venueName());
                    row.put("yylx", r.yylx());
                    row.put("yyzt", r.yyzt());
                    row.put("statusText", r.statusText());
                    row.put("timeSlot", r.timeSlot());
                    row.put("createTime", r.createTime());
                    row.put("amount", r.amount());
                }
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

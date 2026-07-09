package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.client.http.CampusHttpClient;
import edu.szu.agent.client.http.CookieJar;
import edu.szu.agent.client.http.EhallSportVenueClient;
import edu.szu.agent.client.http.EhallSportVenueClient.VenueOption;
import edu.szu.agent.client.session.HttpSession;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.TimeSlot;
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
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code direct-venues} subcommand — list available venues/courts for a given
 * campus, sport, date, and time slot via direct HTTP.
 *
 * <p>// Design Pattern: Adapter (CLI wrapper around direct HTTP discovery)
 * // 编程技术: 注解 / Builder / try-with-resources
 *
 * @since 0.6.0
 * @author 王子豪
 */
@Command(
    name = "direct-venues",
    description = "List venues for a campus/sport/date/slot from ehall (direct HTTP)",
    mixinStandardHelpOptions = true
)
public class DirectVenuesCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--campus-code"}, description = "Raw campus code (XQDM)", required = true)
    private String campusCode;

    @Option(names = {"--sport-code"}, description = "Raw sport code (XMDM)", required = true)
    private String sportCode;

    @Option(names = {"--date"}, description = "Booking date (ISO 8601)", required = true)
    private String dateValue;

    @Option(names = {"--slot"}, description = "Time slot HH:mm-HH:mm", required = true)
    private String slotValue;

    @Option(names = {"--venue-group"}, description = "Optional venue group code (CGBM) to filter results")
    private String venueGroupCode;

    @Option(names = {"--session-home"}, description = "Directory under which .szu-agent/sessions is created",
        defaultValue = "${sys:user.home}")
    private String sessionHome;

    @Option(names = {"--trust-all"}, description = "Disable TLS certificate validation (dev/internal only)")
    private boolean trustAll;

    @Option(names = {"--yylx"}, description = "Booking type: 1.0 (package) or 2.0 (dismissal/scattered); default 1.0",
        defaultValue = "1.0")
    private String yylx;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        long startMs = System.currentTimeMillis();
        String traceId = Tracer.getInstance().generateTraceId();

        LocalDate date;
        TimeSlot slot;
        try {
            date = LocalDate.parse(dateValue);
            slot = TimeSlot.of(slotValue);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(), "Invalid date or slot: " + e.getMessage(),
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
                List<VenueOption> venues = api.getOpeningRooms(
                    campusCode, sportCode, date, slot, venueGroupCode, yylx);

                ObjectNode result = JSON.createObjectNode();
                result.put("traceId", traceId);
                result.put("username", username);
                result.put("campusCode", campusCode);
                result.put("sportCode", sportCode);
                result.put("yylx", yylx);
                result.put("date", date.toString());
                result.put("slot", slot.slotId());
                ArrayNode array = result.putArray("venues");
                for (VenueOption v : venues) {
                    ObjectNode node = array.addObject();
                    node.put("wid", v.wid());
                    node.put("name", v.name());
                    node.put("venueGroupCode", v.venueGroupCode());
                    node.put("campusCode", v.campusCode());
                    node.put("sportCode", v.sportCode());
                    node.put("text", v.text());
                    node.put("disabled", v.disabled());
                    node.put("stateExplain", v.stateExplain());
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

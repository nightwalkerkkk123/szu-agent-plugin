package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.client.http.CampusHttpClient;
import edu.szu.agent.client.http.CookieJar;
import edu.szu.agent.client.http.EhallSportVenueClient;
import edu.szu.agent.client.http.EhallSportVenueClient.TimeSlotOption;
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
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code direct-slots} subcommand — list time-slot options for a given
 * campus, sport, and date via direct HTTP.
 *
 * <p>// Design Pattern: Adapter (CLI wrapper around direct HTTP discovery)
 * // 编程技术: 注解 / Builder / try-with-resources
 *
 * @since 0.6.0
 * @author 王子豪
 */
@Command(
    name = "direct-slots",
    description = "List time slots for a campus/sport/date from ehall (direct HTTP)",
    mixinStandardHelpOptions = true
)
public class DirectSlotsCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--campus"}, description = "Campus name (YUEHAI/LIHU) or code; auto-resolves if not a code")
    private String campusName;

    @Option(names = {"--campus-code"}, description = "Raw campus code (XQDM); overrides --campus")
    private String campusCode;

    @Option(names = {"--sport"}, description = "Sport name (TENNIS/BASKETBALL/etc) or code; auto-resolves if not a code")
    private String sportName;

    @Option(names = {"--sport-code"}, description = "Raw sport code (XMDM); overrides --sport")
    private String sportCode;

    @Option(names = {"--date"}, description = "Booking date (ISO 8601)", required = true)
    private String dateValue;

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

        String resolvedCampusCode;
        String resolvedSportCode;
        LocalDate date;
        try {
            // Resolve campus: --campus-code takes precedence
            if (campusCode != null && !campusCode.isBlank()) {
                resolvedCampusCode = campusCode;
            } else if (campusName != null && !campusName.isBlank()) {
                // Try parsing as enum name first
                try {
                    edu.szu.agent.domain.Campus campus = edu.szu.agent.domain.Campus.valueOf(campusName.toUpperCase());
                    resolvedCampusCode = EhallSportVenueClient.campusCode(campus);
                } catch (IllegalArgumentException e) {
                    // Not an enum name, treat as raw code
                    resolvedCampusCode = campusName;
                }
            } else {
                throw new BookingException(ErrorCode.INVALID_REQUEST,
                    "Either --campus or --campus-code must be provided");
            }

            // Resolve sport: --sport-code takes precedence
            if (sportCode != null && !sportCode.isBlank()) {
                resolvedSportCode = sportCode;
            } else if (sportName != null && !sportName.isBlank()) {
                // Try Chinese name mapping first
                String chineseCode = resolveChineseSport(sportName);
                if (chineseCode != null) {
                    resolvedSportCode = chineseCode;
                } else {
                    // Try parsing as enum name
                    try {
                        edu.szu.agent.domain.Campus campus = edu.szu.agent.domain.Campus.valueOf(
                            campusName != null ? campusName.toUpperCase() : "YUEHAI");
                        edu.szu.agent.domain.Sport sport = edu.szu.agent.domain.Sport.of(campus, sportName.toUpperCase());
                        resolvedSportCode = EhallSportVenueClient.sportCode(sport);
                    } catch (IllegalArgumentException e) {
                        // Not an enum name, treat as raw code
                        resolvedSportCode = sportName;
                    }
                }
            } else {
                throw new BookingException(ErrorCode.INVALID_REQUEST,
                    "Either --sport or --sport-code must be provided");
            }

            date = LocalDate.parse(dateValue);
        } catch (IllegalArgumentException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(), e.getMessage(),
                traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        } catch (BookingException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                e.code().name(), e.getMessage(), traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(e.code());
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
                List<TimeSlotOption> slots = api.getTimeSlots(resolvedCampusCode, resolvedSportCode, date, yylx);

                ObjectNode result = JSON.createObjectNode();
                result.put("traceId", traceId);
                result.put("username", username);
                result.put("campusCode", resolvedCampusCode);
                result.put("sportCode", resolvedSportCode);
                result.put("yylx", yylx);
                result.put("date", date.toString());
                ArrayNode array = result.putArray("slots");
                for (TimeSlotOption s : slots) {
                    ObjectNode node = array.addObject();
                    node.put("wid", s.wid());
                    node.put("code", s.code());
                    node.put("text", s.text());
                    node.put("disabled", s.disabled());
                    node.put("stateExplain", s.stateExplain());
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

    /**
     * Resolves Chinese sport name to ehall sport code.
     */
    private String resolveChineseSport(String chinese) {
        if (chinese.contains("羽毛球")) return "001";
        if (chinese.contains("足球")) return "002";
        if (chinese.contains("排球")) return "003";
        if (chinese.contains("网球")) return "004";
        if (chinese.contains("篮球")) return "005";
        if (chinese.contains("壁球")) return "006";
        if (chinese.contains("健身")) return "007";
        if (chinese.contains("游泳")) return "009";
        if (chinese.contains("乒乓球")) return "013";
        if (chinese.contains("舞蹈")) return "015";
        if (chinese.contains("桌球")) return "016";
        if (chinese.contains("瑜伽")) return "021";
        return null;
    }
}

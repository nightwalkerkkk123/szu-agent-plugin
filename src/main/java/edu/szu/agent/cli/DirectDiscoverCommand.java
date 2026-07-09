package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.client.http.CampusHttpClient;
import edu.szu.agent.client.http.CookieJar;
import edu.szu.agent.client.http.EhallSportVenueClient;
import edu.szu.agent.client.session.HttpSession;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.Campus;
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
 * {@code direct-discover} subcommand — one-step discovery of available venues.
 *
 * <p>Combines sport-venue discovery, date listing, and time slot listing into
 * a single command. Accepts campus/sport names or codes, auto-resolves to
 * ehall wire format, and returns all available time slots for the specified date.
 *
 * <p>Example:
 * <pre>{@code
 * direct-discover --campus 粤海 --sport 网球 --date 2026-07-10
 * }</pre>
 *
 * <p>// Design Pattern: Adapter (CLI wrapper around direct HTTP discovery)
 * // 编程技术: 注解 / Builder / try-with-resources
 *
 * @since 0.6.0
 * @author 王子豪
 */
@Command(
    name = "direct-discover",
    description = "One-step discovery: find available time slots for campus/sport/date",
    mixinStandardHelpOptions = true
)
public class DirectDiscoverCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--campus"}, description = "Campus name (YUEHAI/LIHU/粤海/丽湖) or code; auto-resolves", required = true)
    private String campusInput;

    @Option(names = {"--sport"}, description = "Sport name (TENNIS/BASKETBALL/网球/篮球) or code; auto-resolves", required = true)
    private String sportInput;

    @Option(names = {"--date"}, description = "Booking date (ISO 8601); defaults to tomorrow", required = false)
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

        // Default to tomorrow
        LocalDate date;
        if (dateValue != null && !dateValue.isBlank()) {
            try {
                date = LocalDate.parse(dateValue);
            } catch (IllegalArgumentException e) {
                long elapsed = System.currentTimeMillis() - startMs;
                out.println(CommandOutput.formatResult(false, null,
                    ErrorCode.INVALID_REQUEST.name(), "Invalid date: " + dateValue,
                    traceId, elapsed, "json"));
                return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
            }
        } else {
            date = LocalDate.now().plusDays(1);
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

                // Step 1: Resolve campus name to code
                String campusCode = resolveCampusCode(campusInput);
                String campusDisplayName = resolveCampusDisplayName(campusInput, campusCode);

                // Step 2: Resolve sport name to code
                String sportCode = resolveSportCode(sportInput, campusInput);
                String sportDisplayName = resolveSportDisplayName(sportInput, sportCode);

                // Step 3: Check if date is available
                List<String> availableDates = api.getAvailableDates();
                if (!availableDates.contains(date.toString())) {
                    throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                        "Date " + date + " is not open for booking; available: " + availableDates);
                }

                // Step 4: Get available time slots
                List<EhallSportVenueClient.TimeSlotOption> slots =
                    api.getTimeSlots(campusCode, sportCode, date, yylx);

                // Build response
                ObjectNode result = JSON.createObjectNode();
                result.put("traceId", traceId);
                result.put("username", username);

                ObjectNode campusInfo = result.putObject("campus");
                campusInfo.put("input", campusInput);
                campusInfo.put("code", campusCode);
                campusInfo.put("displayName", campusDisplayName);

                ObjectNode sportInfo = result.putObject("sport");
                sportInfo.put("input", sportInput);
                sportInfo.put("code", sportCode);
                sportInfo.put("displayName", sportDisplayName);

                result.put("date", date.toString());
                result.put("yylx", yylx);

                ArrayNode slotsArray = result.putArray("availableSlots");
                for (EhallSportVenueClient.TimeSlotOption slot : slots) {
                    ObjectNode slotNode = slotsArray.addObject();
                    slotNode.put("slot", slot.code());
                    slotNode.put("status", slot.text());
                    slotNode.put("available", !slot.disabled());
                    slotNode.put("state", slot.stateExplain());
                }

                // Summary
                long availableCount = slots.stream().filter(s -> !s.disabled()).count();
                result.put("summary", String.format("%s %s %s: %d/%d slots available",
                    campusDisplayName, sportDisplayName, date, availableCount, slots.size()));

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
     * Resolves campus input to ehall campus code.
     * Accepts: YUEHAI, LIHU, 粤海, 丽湖, 1, 2
     */
    private String resolveCampusCode(String input) {
        if (input == null || input.isBlank()) {
            throw new BookingException(ErrorCode.INVALID_REQUEST, "Campus is required");
        }
        String normalized = input.trim().toUpperCase();
        // Try enum names
        try {
            Campus campus = Campus.valueOf(normalized);
            return EhallSportVenueClient.campusCode(campus);
        } catch (IllegalArgumentException ignored) {}
        // Try Chinese names
        if (normalized.contains("粤") || normalized.contains("海")) {
            return "1";
        }
        if (normalized.contains("丽") || normalized.contains("湖")) {
            return "2";
        }
        // Try numeric codes
        if (normalized.equals("1")) return "1";
        if (normalized.equals("2")) return "2";
        throw new BookingException(ErrorCode.INVALID_REQUEST,
            "Unknown campus: " + input + " (use YUEHAI/LIHU/粤海/丽湖/1/2)");
    }

    private String resolveCampusDisplayName(String input, String code) {
        if (code.equals("1")) return "粤海校区";
        if (code.equals("2")) return "丽湖校区";
        return input;
    }

    /**
     * Resolves sport input to ehall sport code.
     * Accepts: TENNIS, BASKETBALL, 网球, 篮球, 001, 004, etc.
     */
    private String resolveSportCode(String input, String campusInput) {
        if (input == null || input.isBlank()) {
            throw new BookingException(ErrorCode.INVALID_REQUEST, "Sport is required");
        }
        String normalized = input.trim().toUpperCase();

        // Determine campus for enum lookup
        Campus campus = Campus.YUEHAI;
        if (campusInput != null) {
            try {
                campus = Campus.valueOf(campusInput.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Try Chinese names
                if (campusInput.contains("丽") || campusInput.contains("湖") || campusInput.equals("2")) {
                    campus = Campus.LIHU;
                }
            }
        }

        // Try enum names (English sport names)
        try {
            edu.szu.agent.domain.Sport sport = edu.szu.agent.domain.Sport.of(campus, normalized);
            return EhallSportVenueClient.sportCode(sport);
        } catch (IllegalArgumentException ignored) {}

        // Try Chinese names
        String chineseCode = chineseSportToCode(normalized, campus);
        if (chineseCode != null) return chineseCode;

        // Try numeric codes (assume it's already a code)
        if (normalized.matches("\\d+")) {
            return normalized;
        }

        throw new BookingException(ErrorCode.INVALID_REQUEST,
            "Unknown sport: " + input + " (use TENNIS/BASKETBALL/网球/篮球 or sport code)");
    }

    private String resolveSportDisplayName(String input, String code) {
        // Map common codes to display names
        return switch (code) {
            case "001" -> "羽毛球";
            case "002" -> "足球";
            case "003" -> "排球";
            case "004" -> "网球";
            case "005" -> "篮球";
            case "006" -> "壁球";
            case "007" -> "一楼重量型健身";
            case "008" -> "二楼有氧健身";
            case "009" -> "游泳";
            case "013" -> "乒乓球";
            case "015" -> "舞蹈";
            case "016" -> "桌球";
            case "017" -> "骑行";
            case "018" -> "魔镜";
            case "019" -> "桌游";
            case "020" -> "健身房";
            case "021" -> "瑜伽";
            case "024" -> "智能健身房";
            case "030" -> "匹克球";
            case "034" -> "毽球";
            default -> input;
        };
    }

    private String chineseSportToCode(String chinese, Campus campus) {
        // Common Chinese sport names mapped to codes
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

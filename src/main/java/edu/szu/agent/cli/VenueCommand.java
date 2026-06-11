package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.client.VenueBookingClient;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.Sport;
import edu.szu.agent.domain.TimeSlot;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.Callable;

/**
 * {@code booking venue} subcommand — P0 core demo path.
 *
 * <p>Per ADR-0001 D1/D2: {@code java -jar szu-agent-plugin.jar booking venue
 * --campus YUEHAI --sport TENNIS --date 0 --time-slot 19:00-20:00}
 * is the end-to-end demo command that drives Playwright to book a venue.
 *
 * <p>Per PRD §5.1: all parameters come via command-line flags (no stdin).
 * Per PRD §5.3: exit codes 0/1/2/3/4 correspond to
 * success / business-failure / param-error / env-error / browser-error.
 *
 * <p>Output is JSON per PRD §5.2 schema:
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": { ... },
 *   "errorCode": null,
 *   "errorMessage": null,
 *   "traceId": "20260612-ABC123",
 *   "elapsedMs": 4321
 * }
 * }</pre>
 *
 * // Design Pattern: Command (picocli @Command)
 * // 编程技术: 注解 / 枚举 / Lambda / record
 *
 * @since 0.1.0
 * @author 王子豪
 */
@Command(
    name = "venue",
    description = "Book a sports venue on SZU ehall",
    mixinStandardHelpOptions = true
)
public class VenueCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID (e.g. 2023150090)")
    private String username;

    @Option(names = {"-c", "--campus"}, description = "Campus name (YUEHAI)", required = true)
    private String campus;

    @Option(names = {"-s", "--sport"}, description = "Sport name (TENNIS)", required = true)
    private String sport;

    @Option(names = {"-d", "--date"}, description = "Date offset from today (0=today, 1=tomorrow)", defaultValue = "0")
    private int dateOffset;

    @Option(names = {"-t", "--time-slot"}, description = "Time slot (e.g. 19:00-20:00)", required = true)
    private String timeSlot;

    @Option(names = {"-f", "--format"}, description = "Output format: json or human", defaultValue = "json")
    private String format;

    @Option(names = {"-e", "--env-file"}, description = "Path to .env file for credentials")
    private String envFile;

    @Option(names = {"--dry-run"}, description = "Dry-run mode (unit test fixture only, not for demo)")
    private boolean dryRun;

    @Option(names = {"-v", "--preferred-venue"}, description = "1-based venue preference index", defaultValue = "1")
    private int preferredVenueIndex;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        long startMs = System.currentTimeMillis();
        String traceId = Tracer.getInstance().generateTraceId();

        try {
            // Per ADR-0005 D1: --env-file loads credentials before business logic
            if (envFile != null) {
                Path envPath = Path.of(envFile);
                if (!Files.exists(envPath)) {
                    long elapsed = System.currentTimeMillis() - startMs;
                    out.println(formatResult(false, null,
                        "INVALID_REQUEST", "env file not found: " + envFile,
                        traceId, elapsed));
                    return 3; // env error
                }
                ConfigManager.getInstance().loadEnvFile(envPath);
            }

            // TODO: load config, resolve credentials, construct BookingRequest,
            //       call VenueBookingClient in subsequent slices.
            // For now: --dry-run returns a stub success.

            if (dryRun) {
                ObjectNode data = JSON.createObjectNode();
                data.put("venueName", "dry-run-stub");
                data.put("confirmation", "DRY-RUN");

                long elapsed = System.currentTimeMillis() - startMs;
                out.println(formatResult(true, data, null, null, traceId, elapsed));
                return 0;
            }

            // Real booking flow
            BookingRequest bookingRequest = BookingRequest.builder()
                .username(username)
                .campus(Campus.valueOf(campus.toUpperCase()))
                .sport(Sport.valueOf(sport.toUpperCase()))
                .date(LocalDate.now().plusDays(dateOffset))
                .timeSlot(parseTimeSlot(timeSlot))
                .preferredVenueIndex(preferredVenueIndex)
                .build();

            ConfigManager.getInstance().load();
            VenueBookingClient bookingClient = new VenueBookingClient(
                ConfigManager.getInstance().browser(),
                edu.szu.agent.retry.RetryPolicies.defaultBooking());

            BookingResult result = bookingClient.book(bookingRequest);

            long elapsed = System.currentTimeMillis() - startMs;
            return formatAndOutput(out, result, traceId, elapsed);
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
        if (data != null && data.has("venueName")) {
            sb.append("Venue: ").append(data.get("venueName").asText()).append('\n');
        }
        if (data != null && data.has("confirmation")) {
            sb.append("Confirmation: ").append(data.get("confirmation").asText()).append('\n');
        }
        if (errorCode != null) {
            sb.append("Error: ").append(errorCode).append('\n');
            sb.append("Detail: ").append(errorMessage).append('\n');
        }
        sb.append("Trace: ").append(traceId).append('\n');
        sb.append("Elapsed: ").append(elapsedMs).append("ms");
        return sb.toString();
    }

    private int formatAndOutput(PrintWriter out, BookingResult result,
                                String traceId, long elapsedMs) {
        if (result instanceof BookingResult.Success s) {
            ObjectNode data = JSON.createObjectNode();
            data.put("venueName", s.venueName());
            data.put("confirmation", s.confirmation());
            out.println(formatResult(true, data, null, null, traceId, elapsedMs));
            return 0;
        } else if (result instanceof BookingResult.Failure f) {
            out.println(formatResult(false, null,
                f.code().name(), f.message(), traceId, elapsedMs));
            return exitCodeFor(f.code());
        }
        out.println(formatResult(false, null, "UNKNOWN", "unexpected result type",
            traceId, elapsedMs));
        return 1;
    }

    private static TimeSlot parseTimeSlot(String raw) {
        if (raw == null || !raw.contains("-")) {
            throw new IllegalArgumentException(
                "Invalid time-slot format (expected HH:mm-HH:mm): " + raw);
        }
        String[] parts = raw.split("-", 2);
        return new TimeSlot(parts[0].trim(), parts[1].trim());
    }

    private static int exitCodeFor(ErrorCode code) {
        return switch (code.severity()) {
            case LOW -> 2;      // param error
            case MEDIUM -> 1;   // business failure
            case HIGH -> switch (code) {
                case BROWSER_CRASH -> 4;
                default -> 1;
            };
            case CRITICAL -> 3; // env / account error
        };
    }
}

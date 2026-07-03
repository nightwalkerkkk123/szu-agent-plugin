package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.BookingFlowLauncher;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.Sport;
import edu.szu.agent.domain.TimeSlot;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicies;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code booking venue} subcommand — P0 core demo path.
 *
 * <p>Per ADR-0001 D1/D2: {@code java -jar szu-agent-plugin.jar booking venue
 * --campus YUEHAI --sport TENNIS --date today --time-slot 19:00-20:00}
 * is the end-to-end demo command that drives Playwright to book a venue.
 *
 * <p>Per PRD §5.1: all parameters come via command-line flags (no stdin).
 * Per PRD §5.3: exit codes 0/1/2/3/4 correspond to
 * success / business-failure / param-error / env-error / browser-error.
 *
 * <p>Uses picocli's {@code @Command} annotation as a CLI dispatch mechanism;
 * picocli is the framework, not a project-level design pattern (per ADR-0007 D1
 * the project commits to 4 patterns: Builder / Singleton / Strategy / Adapter).
 *
 * // 编程技术: 注解 / 枚举 / Lambda / record
 *
 * @since 0.6.0
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

    @Option(names = {"-d", "--date"},
        description = "Booking date: 0/today/今天 or 1/tomorrow/明天",
        defaultValue = "0",
        converter = DateOffsetConverter.class)
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
            if (dryRun) {
                ObjectNode data = JSON.createObjectNode();
                data.put("venueName", "dry-run-stub");
                data.put("confirmation", "DRY-RUN");
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

            Campus campusEnum = Campus.valueOf(campus.toUpperCase());
            BookingRequest bookingRequest = BookingRequest.builder()
                .username(username)
                .campus(campusEnum)
                .sport(Sport.of(campusEnum, sport.toUpperCase()))
                .date(LocalDate.now().plusDays(dateOffset))
                .timeSlot(TimeSlot.of(timeSlot))
                .preferredVenueIndex(preferredVenueIndex)
                .build();

            ConfigManager.getInstance().load();
            BookingFlowLauncher launcher = new BookingFlowLauncher(
                ConfigManager.getInstance().browser(),
                RetryPolicies.defaultBooking());

            BookingResult result = launcher.launch(bookingRequest, account);

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

    private int formatAndOutput(PrintWriter out, BookingResult result,
                                String traceId, long elapsedMs) {
        if (result instanceof BookingResult.Success s) {
            ObjectNode data = JSON.createObjectNode();
            data.put("venueName", s.venueName());
            data.put("confirmation", s.confirmation());
            out.println(formatAndPrint(true, data, null, null, traceId, elapsedMs));
            return 0;
        } else if (result instanceof BookingResult.Failure f) {
            out.println(formatAndPrint(false, null,
                f.code().name(), f.message(), traceId, elapsedMs));
            return CommandOutput.exitCodeFor(f.code());
        }
        out.println(formatAndPrint(false, null, "UNKNOWN",
            "unexpected result type", traceId, elapsedMs));
        return 1;
    }

    private String formatAndPrint(boolean success, ObjectNode data,
                                  String errorCode, String errorMessage,
                                  String traceId, long elapsedMs) {
        if ("human".equalsIgnoreCase(format)) {
            return formatHuman(success, data, errorCode, errorMessage, traceId, elapsedMs);
        }
        return CommandOutput.formatResult(success, data, errorCode, errorMessage,
            traceId, elapsedMs, format);
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

    static TimeSlot parseTimeSlot(String raw) {
        return TimeSlot.of(raw);
    }
}

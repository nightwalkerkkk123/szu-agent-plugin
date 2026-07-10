package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.http.EhallSessionManager;
import edu.szu.agent.client.http.RawBookingRequest;
import edu.szu.agent.client.http.VenueBookingService;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.TimeSlot;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.Callable;

/**
 * {@code direct-book} subcommand — direct HTTP venue booking.
 *
 * <p>Loads a persisted CAS session and calls the ehall sport-venue APIs
 * directly, without Playwright. Supports both enum-based campus/sport selection
 * and raw {@code --campus-code}/{@code --sport-code} values discovered via
 * {@code direct-sports}.
 *
 * <p>// Design Pattern: Adapter (CLI wrapper around direct HTTP booking client)
 * // 编程技术: 注解 / Builder / try-with-resources
 *
 * @since 0.6.0
 * @author 王子豪
 */
@Command(
    name = "direct-book",
    description = "Direct HTTP venue booking (any mapped campus/sport)",
    mixinStandardHelpOptions = true
)
public class DirectBookCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--name"}, description = "Display name used in booking form; defaults to resolved account name")
    private String displayName;

    @Option(names = {"--campus"}, description = "Campus: YUEHAI or LIHU")
    private String campusName;

    @Option(names = {"--campus-code"}, description = "Raw campus code (XQDM); overrides --campus")
    private String campusCode;

    @Option(names = {"--sport"}, description = "Sport enum name")
    private String sportName;

    @Option(names = {"--sport-code"}, description = "Raw sport code (XMDM); overrides --sport")
    private String sportCode;

    @Option(names = {"--date"}, description = "Booking date (ISO 8601)", required = true)
    private String dateValue;

    @Option(names = {"--slot"}, description = "Time slot HH:mm-HH:mm", required = true)
    private String slotValue;

    @Option(names = {"--preferred-venue"}, description = "1-based index among available venues", defaultValue = "1")
    private int preferredVenue;

    @Option(names = {"--session-home"}, description = "Directory under which .szu-agent/sessions is created",
        defaultValue = "${sys:user.home}")
    private String sessionHome;

    @Option(names = {"--trust-all"}, description = "Disable TLS certificate validation (dev/internal only)")
    private boolean trustAll;

    @Option(names = {"-e", "--env-file"}, description = "Path to .env file for display-name resolution")
    private String envFile;

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
        } catch (IllegalArgumentException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(), e.getMessage(), traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }

        CampusSportCodeResolver.Resolution code;
        try {
            code = CampusSportCodeResolver.resolve(campusName, campusCode, sportName, sportCode);
        } catch (IllegalArgumentException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(), e.getMessage(), traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        } catch (BookingException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                e.code().name(), e.getMessage(), traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(e.code());
        }

        Account account = resolveAccount();
        if (account == null) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(),
                "Could not resolve credential for " + username
                    + " (set SZU_PASSWORD_" + username + ")", traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }

        try {
            SessionStore store = new SessionStore(Path.of(sessionHome), username);
            EhallSessionManager sessionManager = new EhallSessionManager(
                account.studentId(), account.password(), trustAll);
            VenueBookingService service = new VenueBookingService(account, store, sessionManager);

            RawBookingRequest request = new RawBookingRequest(
                code.campusCode(), code.sportCode(), date, slot, preferredVenue, yylx);
            String dhid = service.book(request);

            ObjectNode data = JSON.createObjectNode();
            data.put("traceId", traceId);
            data.put("username", username);
            data.put("campus", code.campusDisplayName());
            data.put("campusCode", code.campusCode());
            data.put("sport", code.sportDisplayName());
            data.put("sportCode", code.sportCode());
            data.put("date", date.toString());
            data.put("slot", slot.slotId());
            data.put("preferredVenue", preferredVenue);
            data.put("dhid", dhid);
            data.put("durationMs", System.currentTimeMillis() - startMs);

            out.println(CommandOutput.formatResult(true, data, null, null,
                traceId, data.get("durationMs").asLong(), "json"));
            return 0;
        } catch (BookingException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                e.code().name(), e.getMessage(), traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(e.code());
        } catch (RuntimeException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.UNKNOWN.name(), "Unexpected error: " + e.getMessage(),
                traceId, elapsed, "json"));
            return 1;
        }
    }

    private Account resolveAccount() {
        try {
            Account resolved = (envFile != null)
                ? AccountResolver.resolve(username, System.getenv(), Path.of(envFile))
                : AccountResolver.resolve(username, System.getenv(), null);
            if (displayName != null && !displayName.isBlank()) {
                return new Account(resolved.studentId(), resolved.password(), displayName);
            }
            return resolved;
        } catch (AccountResolutionException e) {
            return null;
        }
    }
}

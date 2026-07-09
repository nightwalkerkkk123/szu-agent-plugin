package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.http.CampusHttpClient;
import edu.szu.agent.client.http.CookieJar;
import edu.szu.agent.client.http.EhallSportVenueClient;
import edu.szu.agent.client.http.EhallSportVenueClient.BookingForm;
import edu.szu.agent.client.http.EhallSportVenueClient.TimeSlotOption;
import edu.szu.agent.client.http.EhallSportVenueClient.VenueOption;
import edu.szu.agent.client.session.HttpSession;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.Sport;
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

        String resolvedCampusCode;
        String resolvedSportCode;
        String sportDisplayName;
        String campusDisplayName;
        try {
            if (campusCode != null && !campusCode.isBlank()) {
                resolvedCampusCode = campusCode;
                campusDisplayName = campusCode;
            } else if (campusName != null && !campusName.isBlank()) {
                Campus campus = Campus.valueOf(campusName.toUpperCase());
                resolvedCampusCode = EhallSportVenueClient.campusCode(campus);
                campusDisplayName = campus.displayName();
            } else {
                throw new BookingException(ErrorCode.INVALID_REQUEST,
                    "Either --campus or --campus-code must be provided");
            }

            if (sportCode != null && !sportCode.isBlank()) {
                resolvedSportCode = sportCode;
                sportDisplayName = sportCode;
            } else if (sportName != null && !sportName.isBlank()) {
                Campus campus = Campus.valueOf(campusName != null ? campusName.toUpperCase() : "YUEHAI");
                Sport sport = Sport.of(campus, sportName.toUpperCase());
                resolvedSportCode = EhallSportVenueClient.sportCode(sport);
                sportDisplayName = sport.displayName();
            } else {
                throw new BookingException(ErrorCode.INVALID_REQUEST,
                    "Either --sport or --sport-code must be provided");
            }
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
            String bookerName = resolveDisplayName();

            try (CampusHttpClient http = CampusHttpClient.builder()
                    .trustAll(trustAll)
                    .cookieJar(jar)
                    .build()) {
                EhallSportVenueClient api = new EhallSportVenueClient(http);

                List<String> dates = api.getAvailableDates();
                if (!dates.contains(date.toString())) {
                    throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                        "Date " + date + " is not open for booking; available: " + dates);
                }

                List<TimeSlotOption> slots = api.getTimeSlots(resolvedCampusCode, resolvedSportCode, date);
                TimeSlotOption chosenSlot = slots.stream()
                    .filter(s -> s.code().equals(slot.slotId()))
                    .findFirst()
                    .orElseThrow(() -> new BookingException(ErrorCode.ELEMENT_NOT_FOUND,
                        "Time slot " + slot.slotId() + " not found"));
                if (chosenSlot.disabled()) {
                    throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                        "Time slot " + slot.slotId() + " is not bookable");
                }

                List<VenueOption> venues = api.getOpeningRooms(resolvedCampusCode, resolvedSportCode, date, slot);
                List<VenueOption> available = venues.stream()
                    .filter(v -> !v.disabled())
                    .toList();
                if (available.isEmpty()) {
                    throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                        "No available venue for " + sportDisplayName + " " + date + " " + slot.slotId());
                }
                if (preferredVenue < 1 || preferredVenue > available.size()) {
                    throw new BookingException(ErrorCode.INVALID_REQUEST,
                        "preferred-venue must be between 1 and " + available.size()
                            + ", got " + preferredVenue);
                }
                VenueOption venue = available.get(preferredVenue - 1);

                BookingForm form = new BookingForm(
                    username, bookerName, resolvedCampusCode, resolvedSportCode,
                    venue.venueGroupCode(), venue.wid(), date, slot, "1.0", "");
                String dhid = api.book(form);

                ObjectNode data = JSON.createObjectNode();
                data.put("traceId", traceId);
                data.put("username", username);
                data.put("campus", campusDisplayName);
                data.put("campusCode", resolvedCampusCode);
                data.put("sport", sportDisplayName);
                data.put("sportCode", resolvedSportCode);
                data.put("date", date.toString());
                data.put("slot", slot.slotId());
                data.put("venue", venue.name());
                data.put("preferredVenue", preferredVenue);
                data.put("dhid", dhid);
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

    private String resolveDisplayName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        try {
            Account account;
            if (envFile != null) {
                account = AccountResolver.resolve(username, System.getenv(), Path.of(envFile));
            } else {
                account = AccountResolver.resolve(username, System.getenv(), null);
            }
            return account.displayName();
        } catch (AccountResolutionException e) {
            return username;
        }
    }
}

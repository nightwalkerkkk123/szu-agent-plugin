package edu.szu.agent.client.http;

import edu.szu.agent.account.Account;
import edu.szu.agent.client.session.HttpSession;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.TimeSlot;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.util.SimpleRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * High-level service that turns a resolved venue-booking request into an ehall
 * confirmation number, transparently refreshing the CAS/ehall session when
 * needed.
 *
 * <p>This is the single place where session lifecycle (load → validate →
 * refresh → persist) and business API calls meet. Callers such as
 * {@link edu.szu.agent.cli.DirectBookCommand} only need to supply a resolved
 * {@link RawBookingRequest} and an {@link Account}; the service handles the
 * rest.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Dependencies are constructor-injected and immutable.</li>
 *   <li>The {@link CampusHttpClient} is opened with try-with-resources so the
 *       underlying {@code HttpURLConnection} is always disconnected.</li>
 *   <li>Session persistence is best-effort: a write failure is logged but does
 *       not fail the booking.</li>
 * </ul>
 *
 * // Design Pattern: Service (orchestrates clients into a use-case)
 * // 编程技术: 构造器注入 / try-with-resources / 日志结构化
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class VenueBookingService {

    private static final Logger log = LoggerFactory.getLogger(VenueBookingService.class);

    // Throttling tuned for ehall sports-venue backend:
    // - read calls (dates/slots/venues) are cheap but still tracked;
    // - the final booking POST is the most expensive and rate-limited action.
    private static final double QUERY_PERMITS_PER_SECOND = 2.0;
    private static final double BOOK_PERMITS_PER_SECOND = 0.2;
    private static final long RATE_LIMIT_COOLDOWN_BASE_MS = 5_000;
    private static final long RATE_LIMIT_COOLDOWN_JITTER_MS = 2_000;
    private static final int RATE_LIMIT_MAX_RETRIES = 1;

    private final Account account;
    private final SessionStore sessionStore;
    private final EhallSessionManager sessionManager;
    private final SimpleRateLimiter queryLimiter;
    private final SimpleRateLimiter bookLimiter;

    /**
     * Creates a service for the given account and session infrastructure.
     *
     * @param account       the account performing the booking
     * @param sessionStore  persistent session store for this account
     * @param sessionManager session manager that refreshes CAS/ehall sessions
     * @since 0.6.0
     * @author 王子豪
     */
    public VenueBookingService(Account account, SessionStore sessionStore,
                                EhallSessionManager sessionManager) {
        this(account, sessionStore, sessionManager,
            new SimpleRateLimiter(QUERY_PERMITS_PER_SECOND),
            new SimpleRateLimiter(BOOK_PERMITS_PER_SECOND));
    }

    /**
     * Creates a fully configured service with injectable rate limiters.
     *
     * @param account        the account performing the booking
     * @param sessionStore   persistent session store for this account
     * @param sessionManager session manager that refreshes CAS/ehall sessions
     * @param queryLimiter   rate limiter for read-only ehall queries
     * @param bookLimiter    rate limiter for the booking submission
     * @since 0.7.0
     * @author 王子豪
     */
    public VenueBookingService(Account account, SessionStore sessionStore,
                                EhallSessionManager sessionManager,
                                SimpleRateLimiter queryLimiter,
                                SimpleRateLimiter bookLimiter) {
        this.account = Objects.requireNonNull(account, "account");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.queryLimiter = Objects.requireNonNull(queryLimiter, "queryLimiter");
        this.bookLimiter = Objects.requireNonNull(bookLimiter, "bookLimiter");
    }

    /**
     * Books a venue according to the resolved request.
     *
     * @param request resolved booking parameters (raw wire codes, date, slot, …)
     * @return the ehall confirmation number ({@code DHID})
     * @throws BookingException if the session cannot be established, the slot
     *                          is unavailable, or the booking is rejected
     * @since 0.6.0
     * @author 王子豪
     */
    public String book(RawBookingRequest request) {
        Objects.requireNonNull(request, "request");

        CookieJar jar = loadOrCreateJar();
        try (CampusHttpClient http = sessionManager.ensureSession(jar)) {
            persistSession(http.cookieJar());
            EhallSportVenueClient api = new EhallSportVenueClient(http);
            return executeBooking(api, request);
        }
    }

    private CookieJar loadOrCreateJar() {
        if (!sessionStore.exists()) {
            log.info("No persisted state for {}, starting fresh", account.studentId());
            return new CookieJar();
        }
        try {
            HttpSession session = HttpSession.read(sessionStore);
            log.debug("Loaded persisted state for {} saved at {}",
                account.studentId(), session.savedAt());
            return new CookieJar(session.cookies());
        } catch (IOException e) {
            log.warn("Failed to load persisted state for {}: {}. Starting fresh.",
                account.studentId(), e.getMessage());
            return new CookieJar();
        }
    }

    private void persistSession(CookieJar jar) {
        try {
            HttpSession.write(sessionStore, jar);
            log.info("Persisted refreshed state for {}", account.studentId());
        } catch (IOException e) {
            log.warn("Failed to persist state for {}: {}. Booking succeeded but next run may re-login.",
                account.studentId(), e.getMessage());
        }
    }

    private String executeBooking(EhallSportVenueClient api, RawBookingRequest request) {
        return executeBooking(api, request, 0);
    }

    private String executeBooking(EhallSportVenueClient api, RawBookingRequest request,
                                   int attempt) {
        try {
            queryLimiter.acquire();
            validateDate(api, request.date());

            queryLimiter.acquire();
            EhallSportVenueClient.TimeSlotOption chosenSlot = resolveTimeSlot(api, request);
            if (chosenSlot.disabled()) {
                throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                    "Time slot " + request.timeSlot().slotId() + " is not bookable");
            }

            queryLimiter.acquire();
            EhallSportVenueClient.VenueOption venue = resolveVenue(api, request);

            EhallSportVenueClient.BookingForm form = new EhallSportVenueClient.BookingForm(
                account.studentId(),
                account.displayName(),
                request.campusCode(),
                request.sportCode(),
                venue.venueGroupCode(),
                venue.wid(),
                request.date(),
                request.timeSlot(),
                request.yylx(),
                ""
            );

            bookLimiter.acquire();
            return api.book(form);
        } catch (BookingException e) {
            if (e.code() == ErrorCode.RATE_LIMITED && attempt < RATE_LIMIT_MAX_RETRIES) {
                cooldown();
                return executeBooking(api, request, attempt + 1);
            }
            throw e;
        }
    }

    private void cooldown() {
        long jitter = ThreadLocalRandom.current().nextLong(RATE_LIMIT_COOLDOWN_JITTER_MS);
        long delay = RATE_LIMIT_COOLDOWN_BASE_MS + jitter;
        log.info("Rate limited on booking for {}, cooling down {} ms before retry",
            account.studentId(), delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BookingException(ErrorCode.RATE_LIMITED,
                "Booking cooldown interrupted for " + account.studentId(), e);
        }
    }

    private void validateDate(EhallSportVenueClient api, LocalDate date) {
        List<String> dates = api.getAvailableDates();
        if (!dates.contains(date.toString())) {
            throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                "Date " + date + " is not open for booking; available: " + dates);
        }
    }

    private EhallSportVenueClient.TimeSlotOption resolveTimeSlot(EhallSportVenueClient api,
                                                                  RawBookingRequest request) {
        List<EhallSportVenueClient.TimeSlotOption> slots = api.getTimeSlots(
            request.campusCode(), request.sportCode(), request.date(), request.yylx());
        return slots.stream()
            .filter(s -> s.code().equals(request.timeSlot().slotId()))
            .findFirst()
            .orElseThrow(() -> new BookingException(ErrorCode.ELEMENT_NOT_FOUND,
                "Time slot " + request.timeSlot().slotId() + " not found"));
    }

    private EhallSportVenueClient.VenueOption resolveVenue(EhallSportVenueClient api,
                                                            RawBookingRequest request) {
        List<EhallSportVenueClient.VenueOption> venues = api.getOpeningRooms(
            request.campusCode(), request.sportCode(), request.date(),
            request.timeSlot(), null, request.yylx());
        List<EhallSportVenueClient.VenueOption> available = venues.stream()
            .filter(v -> !v.disabled())
            .toList();
        if (available.isEmpty()) {
            throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                "No available venue for " + request.sportCode() + " "
                    + request.date() + " " + request.timeSlot().slotId());
        }
        int index = request.preferredVenueIndex();
        if (index < 1 || index > available.size()) {
            throw new BookingException(ErrorCode.INVALID_REQUEST,
                "preferred-venue must be between 1 and " + available.size()
                    + ", got " + index);
        }
        return available.get(index - 1);
    }
}

package edu.szu.agent.client;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.Sport;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Venue booking orchestrator — drives the browser through the SZU ehall
 * booking flow.
 *
 * <p>Per PRD §3.1 F2.2: the flow is login → select campus → select sport →
 * select time slot → select venue → confirm. Each step maps to one or more
 * {@link BrowserLifecycle} calls.
 *
 * <p>Per ADR-0001 D9: retry is driven by {@link RetryPolicy} wrapping the
 * entire flow; per-exception retryability comes from
 * {@link ErrorCode#isRetryable()} on the wrapped exception.
 *
 * <p>Per ADR-0007 D4: failure recording uses
 * {@link Tracer#recordFailure(ErrorCode, String, Optional)} —
 * this class does NOT pass {@code Throwable} to the tracer.
 *
 * <p>CSS selectors are defined as package-private constants so they can be
 * updated when ehall changes its page structure without touching the
 * orchestration logic.
 *
 * // Design Pattern: Strategy consumer (uses RetryPolicy + BrowserLifecycle)
 * // 编程技术: 枚举 / Lambda / 不可变构造器注入 / try-with-resources 风格
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class VenueBookingClient {

    private static final Logger log = LoggerFactory.getLogger(VenueBookingClient.class);

    // ---- ehall CSS selectors (per system-map.md §1, update when page changes) ----
    static final String SEL_CAMPUS_DROPDOWN = "select#campus";
    static final String SEL_SPORT_DROPDOWN = "select#sport";
    static final String SEL_TIME_SLOT_AREA = ".time-slot-list";
    static final String SEL_VENUE_LIST = ".venue-list";
    static final String SEL_CONFIRM_BUTTON = "#confirm-btn";
    static final String EHALL_BASE_URL = "https://ehall.szu.edu.cn";

    private final BrowserLifecycle browser;
    private final RetryPolicy retryPolicy;

    /**
     * @param browser      the browser adapter (injected by ConfigManager.browser())
     * @param retryPolicy  retry policy for the booking flow
     */
    public VenueBookingClient(BrowserLifecycle browser, RetryPolicy retryPolicy) {
        this.browser = Objects.requireNonNull(browser, "browser");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    /**
     * Executes the full booking flow.
     *
     * <p>The caller is responsible for closing the browser after this method
     * returns (success or failure). This method guarantees
     * {@link BrowserLifecycle#close()} is called in a finally block.
     *
     * @param request the booking request (non-null, fully populated)
     * @return {@link BookingResult.Success} on confirmation,
     *         {@link BookingResult.Failure} on business failure
     */
    public BookingResult book(BookingRequest request) {
        Objects.requireNonNull(request, "BookingRequest must not be null");
        try {
            browser.open();
            return retryPolicy.execute(() -> executeFlow(request));
        } catch (BookingException e) {
            // Per ADR-0007 D4: screenshot decision is made here (closest to failure site)
            Optional<java.nio.file.Path> screenshotPath = Optional.empty();
            if (e.code().shouldScreenshot()) {
                try {
                    java.nio.file.Path path = java.nio.file.Path.of(
                        System.getProperty("java.io.tmpdir"),
                        "ehall-error-" + System.currentTimeMillis() + ".png");
                    browser.screenshot(path.toString());
                    screenshotPath = Optional.of(path);
                } catch (Exception screenshotEx) {
                    log.warn("Failed to take screenshot: {}", screenshotEx.getMessage());
                }
            }
            // Per ADR-0007 D4: Tracer does NOT take Throwable
            Tracer.getInstance().recordFailure(e.code(), e.getMessage(), screenshotPath);
            return new BookingResult.Failure(e.code(), e.getMessage());
        } finally {
            try {
                browser.close();
            } catch (Exception e) {
                log.warn("Failed to close browser: {}", e.getMessage());
            }
        }
    }

    /**
     * Single attempt at the booking flow — called inside
     * {@link RetryPolicy#execute(java.util.function.Supplier)}.
     */
    private BookingResult executeFlow(BookingRequest request) {
        log.info("Starting booking flow: campus={} sport={} date={} slot={}",
            request.campus().displayName(),
            request.sport().displayName(),
            request.date(),
            request.timeSlot());

        // Step 1: Navigate to ehall
        browser.navigateTo(EHALL_BASE_URL + "/booking");

        // Step 2: Select campus
        browser.fill(SEL_CAMPUS_DROPDOWN, request.campus().ehallCode());

        // Step 3: Select sport
        browser.fill(SEL_SPORT_DROPDOWN, request.sport().ehallCode());

        // Step 4: Select time slot
        selectTimeSlot(request);

        // Step 5: Select venue
        String venueName = selectVenue(request);

        // Step 6: Confirm
        browser.click(SEL_CONFIRM_BUTTON);

        log.info("Booking confirmed: venue={}", venueName);
        return new BookingResult.Success(venueName, "CONFIRMED-" + System.currentTimeMillis());
    }

    private void selectTimeSlot(BookingRequest request) {
        // Wait for time slot list to be visible
        if (!browser.isVisible(SEL_TIME_SLOT_AREA)) {
            throw new BookingException(ErrorCode.ELEMENT_NOT_FOUND,
                "Time slot area not visible");
        }
        // Click the matching time slot
        String slotLabel = request.timeSlot().start() + "-" + request.timeSlot().end();
        // Use text content to find the right slot
        var texts = browser.allTextOf(SEL_TIME_SLOT_AREA + " li");
        boolean found = texts.stream().anyMatch(t -> t.contains(slotLabel));
        if (!found) {
            throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                "Time slot not available: " + slotLabel);
        }
        browser.click(SEL_TIME_SLOT_AREA + " li:has-text('" + slotLabel + "')");
    }

    private String selectVenue(BookingRequest request) {
        if (!browser.isVisible(SEL_VENUE_LIST)) {
            throw new BookingException(ErrorCode.ELEMENT_NOT_FOUND,
                "Venue list not visible");
        }
        var venues = browser.allTextOf(SEL_VENUE_LIST + " li");
        if (venues.isEmpty()) {
            throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                "No venues available for the selected time slot");
        }
        // Select venue by index (1-based)
        int idx = Math.min(request.preferredVenueIndex() - 1, venues.size() - 1);
        String venueName = venues.get(idx);
        browser.click(SEL_VENUE_LIST + " li:nth-child(" + (idx + 1) + ")");
        return venueName;
    }
}

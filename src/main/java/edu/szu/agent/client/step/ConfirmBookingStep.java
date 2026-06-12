package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step 6 — confirm booking.
 *
 * <p>Reads the venue name from {@link BookingContext#selectedVenue()}
 * (set by {@link SelectVenueStep}) and constructs the final
 * {@link BookingResult.Success}.
 *
 * // Design Pattern: Strategy
 * // 编程技术: record
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class ConfirmBookingStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(ConfirmBookingStep.class);

    static final String SEL_CONFIRM_BUTTON = "#confirm-btn";

    @Override
    public String name() {
        return "CONFIRM_BOOKING";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        String venueName = ctx.selectedVenue();
        log.info("Confirming booking for venue: {}", venueName);
        browser.click(SEL_CONFIRM_BUTTON);
        String confirmation = "CONFIRMED-" + System.currentTimeMillis();
        return new BookingResult.Success(venueName, confirmation);
    }
}
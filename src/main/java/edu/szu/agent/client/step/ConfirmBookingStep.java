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
 * @since 0.6.0
 * @author 王子豪
 */
public final class ConfirmBookingStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(ConfirmBookingStep.class);

    /**
     * The page renders two large buttons: 取消 (white) and 提交预约 (red).
     * Match by visible text — class set is identical for both.
     */
    static final String SEL_CONFIRM_BUTTON =
        "button.bh-btn.bh-btn-large:has-text(\"提交预约\")";

    @Override
    public String name() {
        return "CONFIRM_BOOKING";
    }

    @Override
    public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
        String venueName = ctx.selectedVenue();
        log.info("Confirming booking for venue: {}", venueName);
        browser.click(SEL_CONFIRM_BUTTON);
        // This is the last step; returning Continue lets VenueBookingClient
        // construct the final Success result with a fresh confirmation id.
        return new StepOutcome.Continue(ctx);
    }
}

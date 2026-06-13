package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Step 5 — select venue.
 *
 * <p>Stores the selected venue name into {@link BookingContext#selectedVenue(String)}
 * so subsequent steps (e.g. {@link ConfirmBookingStep}) can use it.
 *
 * // Design Pattern: Strategy
 * // 编程技术: Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class SelectVenueStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(SelectVenueStep.class);

    static final String SEL_VENUE_LIST = ".venue-list";

    @Override
    public String name() {
        return "SELECT_VENUE";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        if (!browser.isVisible(SEL_VENUE_LIST)) {
            return new BookingResult.Failure(ErrorCode.ELEMENT_NOT_FOUND,
                "Venue list not visible");
        }

        List<String> venues = browser.allTextOf(SEL_VENUE_LIST + " li");
        if (venues.isEmpty()) {
            return new BookingResult.Failure(ErrorCode.NO_AVAILABLE_VENUE,
                "No venues available for the selected time slot");
        }

        int idx = Math.min(ctx.request().preferredVenueIndex() - 1, venues.size() - 1);
        String venueName = venues.get(idx);
        log.info("Selecting venue (1-based index {}): {}",
            ctx.request().preferredVenueIndex(), venueName);
        browser.click(SEL_VENUE_LIST + " li:nth-child(" + (idx + 1) + ")");
        ctx.selectedVenue(venueName);
        return null;
    }
}
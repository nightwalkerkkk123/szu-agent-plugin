package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.error.BookingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step 5 — select venue (specific court or capacity item within campus + sport).
 *
 * <p>The actual selection logic is delegated to the {@link VenueSelector}
 * bound to the current {@link edu.szu.agent.domain.Sport}. Court-style sports
 * use {@link CourtListSelector}; capacity-style sports (gym) use
 * {@link CapacityVenueSelector}.
 *
 * <p>// Design Pattern: Strategy (delegated to Sport-bound VenueSelector)
 * <p>// 编程技术: 多态 / 异常转业务结果
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class SelectVenueStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(SelectVenueStep.class);

    @Override
    public String name() {
        return "SELECT_VENUE";
    }

    @Override
    public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
        try {
            String venueName = ctx.request().sport().venueSelector().selectAndClick(browser, ctx);
            log.info("Selected venue: {}", venueName);
            return new StepOutcome.Continue(ctx.withSelectedVenue(venueName));
        } catch (BookingException e) {
            return new StepOutcome.Failure(new BookingResult.Failure(e.code(), e.getMessage()));
        }
    }
}

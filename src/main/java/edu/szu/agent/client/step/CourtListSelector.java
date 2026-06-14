package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Venue selector for court-style sports.
 *
 * <p>The page renders a list of courts such as
 * {@code 北区网球1号场(可预约)}. This implementation picks the N-th
 * {@code (可预约)} court (1-based via
 * {@link edu.szu.agent.domain.BookingRequest#preferredVenueIndex()}),
 * falling back to the last available court when fewer than N are present.
 *
 * <p>// Design Pattern: Strategy
 * <p>// 编程技术: Lambda / Stream
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class CourtListSelector implements VenueSelector {

    private static final Logger log = LoggerFactory.getLogger(CourtListSelector.class);

    /** All court labels (any state) — used to discover names. */
    private static final String SEL_COURT_LABEL_ALL = "label:has(div.element)";

    /** Only labels with {@code (可预约)} in the inner element text. */
    static final String SEL_COURT_LABEL_AVAILABLE =
        "label:has(div.element:has-text(\"可预约\"))";

    @Override
    public String selectAndClick(BrowserLifecycle browser, BookingContext ctx)
        throws BookingException {
        if (!SelectTimeSlotStep.waitForVisible(browser, SEL_COURT_LABEL_AVAILABLE,
                SelectTimeSlotStep.labelWaitMs())) {
            throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                "No bookable courts (可预约) on the page");
        }

        List<String> texts = browser.allTextOf(SEL_COURT_LABEL_AVAILABLE + " div.element");
        if (texts.isEmpty()) {
            throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                "No 可预约 courts read from page");
        }

        int requested = ctx.request().preferredVenueIndex();
        int idx = Math.min(Math.max(requested, 1), texts.size()) - 1;
        String courtLabel = texts.get(idx);
        log.info("Selecting court (1-based index {} of {} available): {}",
            requested, texts.size(), courtLabel);

        String clickSelector = ":nth-match(" + SEL_COURT_LABEL_AVAILABLE + ", " + (idx + 1) + ")";
        browser.click(clickSelector);

        int paren = courtLabel.indexOf('(');
        return paren > 0 ? courtLabel.substring(0, paren) : courtLabel;
    }
}

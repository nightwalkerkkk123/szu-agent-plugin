package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Step 5 — select venue (specific court within campus + sport).
 *
 * <p>After a time slot is picked, the page renders {@code 选择场地} radios:
 * {@code <label for="UUID"><div class="element">北区网球1号场(可预约)</div></label>}.
 * Status is encoded in the trailing parens — only {@code (可预约)} is bookable.
 *
 * <p>Strategy: pick the N-th {@code (可预约)} court (1-based via
 * {@link edu.szu.agent.domain.BookingRequest#preferredVenueIndex()}). If
 * fewer than N are available, click the last available one.
 *
 * // Design Pattern: Strategy
 * // 编程技术: Lambda / Stream
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class SelectVenueStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(SelectVenueStep.class);

    /** All court labels (any state) — used to discover names. */
    static final String SEL_COURT_LABEL_ALL = "label:has(div.element)";

    /** Only labels with {@code (可预约)} in the inner element text. */
    static final String SEL_COURT_LABEL_AVAILABLE =
        "label:has(div.element:has-text(\"可预约\"))";

    @Override
    public String name() {
        return "SELECT_VENUE";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        // Venue panel renders only after a time slot is picked — poll until
        // at least one (可预约) label appears (or timeout).
        if (!SelectTimeSlotStep.waitForVisible(browser, SEL_COURT_LABEL_AVAILABLE,
                SelectTimeSlotStep.labelWaitMs())) {
            return new BookingResult.Failure(ErrorCode.NO_AVAILABLE_VENUE,
                "No bookable courts (可预约) on the page");
        }

        // Read all court names so we can log + remember which one we picked.
        List<String> texts = browser.allTextOf(SEL_COURT_LABEL_AVAILABLE
            + " div.element");
        if (texts.isEmpty()) {
            return new BookingResult.Failure(ErrorCode.NO_AVAILABLE_VENUE,
                "No 可预约 courts read from page");
        }

        int requested = ctx.request().preferredVenueIndex();
        int idx = Math.min(Math.max(requested, 1), texts.size()) - 1;
        String courtLabel = texts.get(idx);
        log.info("Selecting court (1-based index {} of {} available): {}",
            requested, texts.size(), courtLabel);

        // Click the N-th matching :nth-match() variant (Playwright-specific).
        // We use :nth-match(SELECTOR, N) to scope the index.
        String clickSelector = ":nth-match(" + SEL_COURT_LABEL_AVAILABLE
            + ", " + (idx + 1) + ")";
        browser.click(clickSelector);

        // Strip trailing "(可预约)" before recording venue name.
        int paren = courtLabel.indexOf('(');
        ctx.selectedVenue(paren > 0 ? courtLabel.substring(0, paren) : courtLabel);
        return null;
    }
}

package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static edu.szu.agent.client.step.CourtListSelector.venueWaitMs;

/**
 * Venue selector for capacity-style sports such as gym.
 *
 * <p>The page renders a single item like {@code 二楼健身房(42/50)} under
 * the {@code 选择场地} section. This implementation scopes the search to
 * that section (so it never matches the time-slot list above), checks the
 * remaining capacity, clicks the venue, and returns the name with the
 * capacity suffix stripped.
 *
 * <p>// Design Pattern: Strategy
 * <p>// 编程技术: 正则 / 作用域选择器 / sealed-interface 实现
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class CapacityVenueSelector implements VenueSelector {

    private static final Logger log = LoggerFactory.getLogger(CapacityVenueSelector.class);

    /** Section header text that precedes the venue list on ehall. */
    private static final String VENUE_SECTION_HEADER = "选择场地";

    /**
     * Locates the div immediately following the {@code 选择场地} header div.
     * The header itself is a {@code div.text-wrapper-2} containing the text;
     * the venue list lives in its next sibling.
     */
    static final String SEL_VENUE_SECTION =
        "div.text-wrapper-2:has-text(\"" + VENUE_SECTION_HEADER + "\") + div";

    /** Labels inside the venue section whose text contains a capacity pattern. */
    static final String SEL_VENUE_LABEL =
        SEL_VENUE_SECTION + " label";

    /** Pattern for remaining/total capacity, e.g. {@code (42/50)}. */
    private static final Pattern CAPACITY_PATTERN = Pattern.compile("(\\d+)/(\\d+)");

    @Override
    public String selectAndClick(BrowserLifecycle browser, BookingContext ctx)
        throws BookingException {
        if (!browser.waitForVisible(SEL_VENUE_LABEL, venueWaitMs())) {
            throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                "No venue section (" + VENUE_SECTION_HEADER + ") rendered on the page");
        }

        List<String> texts = browser.allTextOf(SEL_VENUE_LABEL + " div.element");
        if (texts.isEmpty()) {
            throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                "No venues found under " + VENUE_SECTION_HEADER);
        }

        int requested = ctx.request().preferredVenueIndex();
        int idx = Math.min(Math.max(requested, 1), texts.size()) - 1;
        String venueLabel = texts.get(idx);
        log.info("Selecting capacity venue (1-based index {} of {}): {}",
            requested, texts.size(), venueLabel);

        int remaining = parseRemainingCapacity(venueLabel);
        if (remaining <= 0) {
            throw new BookingException(ErrorCode.NO_AVAILABLE_VENUE,
                "Venue is full: " + venueLabel);
        }

        String clickSelector = SEL_VENUE_SECTION
            + " label:has(div.element:has-text(\"" + escapeQuotes(venueLabel) + "\"))";
        browser.click(clickSelector);

        int paren = venueLabel.indexOf('(');
        return paren > 0 ? venueLabel.substring(0, paren).trim() : venueLabel.trim();
    }

    /**
     * Escapes double quotes in a label so it can be safely embedded in a
     * Playwright CSS selector string.
     */
    private static String escapeQuotes(String label) {
        return label.replace("\"", "\\\"");
    }

    /**
     * Extracts the remaining-capacity number from a label such as
     * {@code 二楼健身房(42/50)}.
     *
     * @param label the venue label text
     * @return the first integer in a {@code N/M} capacity expression, or 0 if absent
     */
    private static int parseRemainingCapacity(String label) {
        Matcher matcher = CAPACITY_PATTERN.matcher(label);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

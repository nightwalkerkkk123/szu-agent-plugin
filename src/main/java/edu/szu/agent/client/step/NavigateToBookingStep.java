package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step 1 — navigate to ehall booking page.
 *
 * // Design Pattern: Strategy
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class NavigateToBookingStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(NavigateToBookingStep.class);

    static final String EHALL_BOOKING_URL =
        "https://ehall.szu.edu.cn/qljfwapp/sys/lwSzuCgyy/index.do#/sportVenue";

    @Override
    public String name() {
        return "NAVIGATE_TO_BOOKING";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        log.info("Navigating to ehall booking page");
        browser.navigateTo(EHALL_BOOKING_URL);
        return null;
    }
}
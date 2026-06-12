package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step 3 — select sport.
 *
 * // Design Pattern: Strategy
 * // 编程技术: 枚举
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class SelectSportStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(SelectSportStep.class);

    static final String SEL_SPORT_DROPDOWN = "select#sport";

    @Override
    public String name() {
        return "SELECT_SPORT";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        var sport = ctx.request().sport();
        log.info("Selecting sport: {} ({})", sport.displayName(), sport.ehallCode());
        browser.fill(SEL_SPORT_DROPDOWN, sport.ehallCode());
        return null;
    }
}
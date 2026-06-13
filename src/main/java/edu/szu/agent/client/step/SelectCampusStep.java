package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step 2 — select campus.
 *
 * // Design Pattern: Strategy
 * // 编程技术: 枚举
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class SelectCampusStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(SelectCampusStep.class);

    static final String SEL_CAMPUS_DROPDOWN = "select#campus";

    @Override
    public String name() {
        return "SELECT_CAMPUS";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        var campus = ctx.request().campus();
        log.info("Selecting campus: {} ({})", campus.displayName(), campus.ehallCode());
        browser.fill(SEL_CAMPUS_DROPDOWN, campus.ehallCode());
        return null;
    }
}
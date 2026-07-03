package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step 2 — select campus.
 *
 * // Design Pattern: Strategy
 * // 编程技术: 枚举
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class SelectCampusStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(SelectCampusStep.class);

    /**
     * Campus selection page renders two button-like {@code <div>}s
     * (粤海校区 / 丽湖校区). Click by visible text.
     */
    static final String SEL_CAMPUS_BUTTON_TEMPLATE =
        "div.bh-btn.bh-btn-primary:has-text(\"%s\")";

    @Override
    public String name() {
        return "SELECT_CAMPUS";
    }

    @Override
    public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
        var campus = ctx.request().campus();
        log.info("Selecting campus: {} ({})", campus.displayName(), campus.ehallCode());
        String selector = String.format(SEL_CAMPUS_BUTTON_TEMPLATE, campus.displayName());
        browser.click(selector);
        return new StepOutcome.Continue(ctx);
    }
}

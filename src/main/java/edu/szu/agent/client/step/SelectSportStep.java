package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step 3 — select sport.
 *
 * // Design Pattern: Strategy
 * // 编程技术: 枚举
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class SelectSportStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(SelectSportStep.class);

    /**
     * Sport tile rendered as {@code <div class="frame-4">} containing
     * {@code <div class="text-wrapper-7">网球</div>}. We match the tile by
     * its inner sport name. Pattern: {@code %s} → displayName like 网球.
     */
    static final String SEL_SPORT_TILE_TEMPLATE =
        "div.frame-4:has(div.text-wrapper-7:has-text(\"%s\"))";

    @Override
    public String name() {
        return "SELECT_SPORT";
    }

    @Override
    public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
        var sport = ctx.request().sport();
        log.info("Selecting sport: {} ({})", sport.displayName(), sport.ehallCode());
        String selector = String.format(SEL_SPORT_TILE_TEMPLATE, sport.displayName());
        browser.click(selector);
        return new StepOutcome.Continue(ctx);
    }
}

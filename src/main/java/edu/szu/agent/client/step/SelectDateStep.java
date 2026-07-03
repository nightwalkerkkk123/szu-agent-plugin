package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

/**
 * Step 3.5 — select the booking date on the ehall page.
 *
 * <p>After a sport is selected, the page renders date radio buttons such as
 * {@code <input type="radio" id="2026-06-15">}. This step clicks the
 * {@code label[for="YYYY-MM-DD"]} matching {@link edu.szu.agent.domain.BookingRequest#date()}.
 * Clicking an already-selected date is idempotent, so no pre-check is needed.
 *
 * <p>// Design Pattern: Strategy
 * <p>// 编程技术: 日期格式化 / 不可变请求对象读取
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class SelectDateStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(SelectDateStep.class);

    @Override
    public String name() {
        return "SELECT_DATE";
    }

    @Override
    public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
        LocalDate date = ctx.request().date();
        // ehall renders dates as <input type="radio" id="YYYY-MM-DD"> wrapped in
        // a <label for="YYYY-MM-DD">. The radio is display:none, so we click the
        // label (which is what the user sees anyway). Playwright's click waits
        // for actionability — no pre-check needed.
        String selector = String.format("label[for=\"%s\"]", date);
        log.info("Selecting date: {}", date);
        browser.click(selector);
        return new StepOutcome.Continue(ctx);
    }
}

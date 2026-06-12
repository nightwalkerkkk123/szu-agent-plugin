package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Step 4 — select time slot.
 *
 * // Design Pattern: Strategy
 * // 编程技术: Lambda / 枚举
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class SelectTimeSlotStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(SelectTimeSlotStep.class);

    static final String SEL_TIME_SLOT_AREA = ".time-slot-list";

    @Override
    public String name() {
        return "SELECT_TIME_SLOT";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        if (!browser.isVisible(SEL_TIME_SLOT_AREA)) {
            return new BookingResult.Failure(ErrorCode.ELEMENT_NOT_FOUND,
                "Time slot area not visible");
        }

        var timeSlot = ctx.request().timeSlot();
        String slotLabel = timeSlot.start() + "-" + timeSlot.end();
        List<String> texts = browser.allTextOf(SEL_TIME_SLOT_AREA + " li");
        boolean found = texts.stream().anyMatch(t -> t.contains(slotLabel));
        if (!found) {
            return new BookingResult.Failure(ErrorCode.NO_AVAILABLE_VENUE,
                "Time slot not available: " + slotLabel);
        }

        log.info("Clicking time slot: {}", slotLabel);
        browser.click(SEL_TIME_SLOT_AREA + " li:has-text('" + slotLabel + "')");
        return null;
    }
}
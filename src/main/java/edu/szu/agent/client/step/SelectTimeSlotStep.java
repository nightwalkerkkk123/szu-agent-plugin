package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step 4 — select time slot.
 *
 * <p>Each time slot is a {@code <label for="HH:mm-HH:mm">} radio whose
 * inner text reads e.g. {@code "19:00-20:00(可预约)"} or
 * {@code "19:00-20:00(已满员)"} / {@code "(无开放场地)"}. We click the
 * label whose inner {@code .element} text starts with the requested slot
 * AND contains "(可预约)".
 *
 * <p>The slot panel is rendered after SELECT_SPORT clicks the sport
 * tile, so this step polls {@code isVisible} for up to
 * {@link #LABEL_WAIT_MS} milliseconds before declaring the slot missing.
 *
 * // Design Pattern: Strategy
 * // 编程技术: Lambda / 枚举 / 静态轮询(Thread.sleep)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class SelectTimeSlotStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(SelectTimeSlotStep.class);

    /** Pattern: {@code %s} → {@code "19:00-20:00"}. Matches any state. */
    static final String SEL_SLOT_LABEL_TEMPLATE =
        "label[for=\"%s\"]";

    /** Pattern for the available-only variant. */
    static final String SEL_SLOT_AVAILABLE_TEMPLATE =
        "label[for=\"%s\"]:has(div.element:has-text(\"可预约\"))";

    /**
     * Max time to wait for the slot panel to render. Tunable via the
     * {@code szu.agent.slot-wait-ms} system property — production keeps
     * the 8s default, unit tests set a small value to keep the negative
     * path fast.
     */
    static long labelWaitMs() {
        return Long.getLong("szu.agent.slot-wait-ms", 8_000L);
    }

    /** Poll interval for the slot panel render wait. */
    static final long POLL_MS = 250L;

    @Override
    public String name() {
        return "SELECT_TIME_SLOT";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        var timeSlot = ctx.request().timeSlot();
        String slotId = timeSlot.slotId();
        String labelSelector = String.format(SEL_SLOT_LABEL_TEMPLATE, slotId);

        if (!waitForVisible(browser, labelSelector, labelWaitMs())) {
            return new BookingResult.Failure(ErrorCode.ELEMENT_NOT_FOUND,
                "Time slot label not found: " + slotId);
        }

        // Reject 已满员 / 无开放场地 — only "可预约" is bookable.
        String availableSelector = String.format(SEL_SLOT_AVAILABLE_TEMPLATE, slotId);
        if (!browser.isVisible(availableSelector)) {
            return new BookingResult.Failure(ErrorCode.NO_AVAILABLE_VENUE,
                "Time slot not bookable (已满员 or 无开放场地): " + slotId);
        }

        log.info("Clicking time slot: {}", slotId);
        browser.click(availableSelector);
        return null;
    }

    /**
     * Polls {@link BrowserLifecycle#isVisible(String)} until the selector
     * resolves to a visible element or {@code timeoutMs} elapses.
     *
     * <p>Visible-for-test: {@code static} so test can short-circuit the
     * loop by feeding an immediate-true {@code isVisible} mock. Returning
     * {@code true} on the first poll means total elapsed time is &lt; 1ms,
     * keeping unit tests fast.
     */
    static boolean waitForVisible(BrowserLifecycle browser, String selector, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            if (browser.isVisible(selector)) {
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}

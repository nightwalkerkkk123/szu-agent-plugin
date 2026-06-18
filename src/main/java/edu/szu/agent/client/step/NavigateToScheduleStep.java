package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;

/**
 * Step that navigates to the SZU ehall schedule page (xskcb) and waits for
 * the {@code table.wut_table} grid to render.
 *
 * <p>The hash route {@code #/xskcb} is the page key; query parameters
 * (e.g. {@code t_s}, {@code _sec_version_}, {@code gid_}) are session-derived
 * and injected by ehall itself on first navigation.
 *
 * // Design Pattern: Strategy (concrete step in schedule pipeline)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class NavigateToScheduleStep implements BookingStep {

    /** ehall schedule page URL — the {@code #/xskcb} hash is required. */
    public static final String EHALL_SCHEDULE_URL =
        "https://ehall.szu.edu.cn/jwapp/sys/wdkb/*default/index.do#/xskcb";

    /** CSS selector for the schedule grid table. */
    static final String SEL_SCHEDULE_TABLE = "table.wut_table";

    @Override
    public String name() {
        return "NAVIGATE_TO_SCHEDULE";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        browser.navigateTo(EHALL_SCHEDULE_URL);
        if (!browser.isVisible(SEL_SCHEDULE_TABLE)) {
            throw new BookingException(ErrorCode.SCHEDULE_PAGE_LOAD_FAILED,
                "schedule table not visible on " + EHALL_SCHEDULE_URL);
        }
        return null;
    }
}

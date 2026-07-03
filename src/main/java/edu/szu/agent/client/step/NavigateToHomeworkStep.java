package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;

/**
 * Step that navigates to the LMS user index page where the todo list lives.
 *
 * // Design Pattern: Strategy (concrete step in homework pipeline)
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class NavigateToHomeworkStep implements BookingStep {

    public static final String LMS_USER_INDEX_URL = "https://lms.szu.edu.cn/user/index";
    static final String SEL_TODO_LIST = ".todo-list-container";

    @Override
    public String name() {
        return "NAVIGATE_TO_HOMEWORK";
    }

    @Override
    public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
        browser.navigateTo(LMS_USER_INDEX_URL);
        if (!browser.isVisible(SEL_TODO_LIST)) {
            throw new BookingException(ErrorCode.HOMEWORK_PAGE_LOAD_FAILED,
                "todo list container not visible on " + LMS_USER_INDEX_URL);
        }
        return new StepOutcome.Continue(ctx);
    }
}

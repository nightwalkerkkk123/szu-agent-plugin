package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;

import java.util.Objects;

/**
 * Step that navigates to the LMS homework detail page identified by
 * {@link BookingContext#homeworkId()}.
 *
 * <p>The LMS detail page is reached via AngularJS hash routing:
 * {@code https://lms.szu.edu.cn/user/index#/<homeworkId>}. The
 * {@code courseId} is not required — the LMS routes the hash
 * fragment to the correct course view server-side. We treat
 * {@link BrowserLifecycle#navigateTo(String)} (which auto-waits
 * for the load state) as the load-complete signal. The presence
 * (or absence) of {@code .attachment-row} is left to
 * {@code ParseAttachmentsStep} — a homework with no attachments
 * is a valid state, not a load failure.
 *
 * <p>Reads {@link BookingContext#homeworkId()}; throws
 * {@link BookingException} with {@code HOMEWORK_PAGE_LOAD_FAILED}
 * if the id is missing on the context.
 *
 * // Design Pattern: Strategy (concrete step in homework-download pipeline)
 * // 编程技术: 接口 / 异常 / 字符串拼接(URL)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class NavigateToHomeworkDetailStep implements BookingStep {

    /** LMS user index host used as the base for the hash-routed detail URL. */
    public static final String LMS_USER_INDEX_URL = "https://lms.szu.edu.cn/user/index";

    @Override
    public String name() {
        return "NAVIGATE_TO_HOMEWORK_DETAIL";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        String homeworkId = ctx.homeworkId();
        if (homeworkId == null || homeworkId.isBlank()) {
            throw new BookingException(ErrorCode.HOMEWORK_PAGE_LOAD_FAILED,
                "homeworkId is missing on BookingContext");
        }
        String url = LMS_USER_INDEX_URL + "#/" + homeworkId;
        browser.navigateTo(url);
        return null;
    }
}

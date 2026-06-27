package edu.szu.agent.client.exam;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.error.ExamListException;
import com.microsoft.playwright.Page;

import java.util.Objects;

/**
 * Playwright-based implementation of {@link ExamFetchProvider} that navigates
 * to the SZU ehall exam schedule page and extracts the HTML content.
 *
 * <p>The exam schedule page is behind CAS login; this implementation assumes
 * the browser already has an authenticated session (from previous login
 * and session persistence).
 *
 * <p>// 编程技术: 依赖注入 / 不可变 / 异常处理
 *
 * @since 0.4.0
 * @author 王子豪
 */
public class PlaywrightExamFetchProvider implements ExamFetchProvider {

    /**
     * The ehall exam query URL (requires CAS authentication).
     * Final after construction.
     */
    private final String examUrl;

    /**
     * The CSS selector for the exam table container.
     * Final after construction.
     */
    private final String tableSelector;

    /**
     * Browser lifecycle instance (authenticated session from persistent storage).
     * Final after construction.
     */
    private final BrowserLifecycle browser;

    /**
     * Production constructor with default URL and selector.
     *
     * @param browser  authenticated browser lifecycle (must not be null)
     */
    public PlaywrightExamFetchProvider(BrowserLifecycle browser) {
        this(browser,
            "https://ehall.szu.edu.cn/gsapp/sys/jzxk-cx-wd/default/ksap.wsxapp",
            "#table > tbody");
    }

    /**
     * Full constructor for testing with custom URL/selector.
     *
     * @param browser        authenticated browser lifecycle (must not be null)
     * @param examUrl        the URL to navigate to
     * @param tableSelector  CSS selector for the exam table container
     */
    public PlaywrightExamFetchProvider(BrowserLifecycle browser,
                                       String examUrl,
                                       String tableSelector) {
        this.browser = Objects.requireNonNull(browser, "browser must not be null");
        this.examUrl = Objects.requireNonNull(examUrl, "examUrl must not be null");
        this.tableSelector = Objects.requireNonNull(tableSelector, "tableSelector must not be null");
    }

    @Override
    public String fetchHtml() {
        try (Page page = browser.newPage()) {
            page.navigate(examUrl);
            page.waitForSelector(tableSelector);
            return page.content();
        } catch (RuntimeException e) {
            throw new ExamListException(ErrorCode.EXAM_FETCH_FAILED,
                "Failed to fetch exam schedule: " + e.getMessage(), e);
        }
    }
}

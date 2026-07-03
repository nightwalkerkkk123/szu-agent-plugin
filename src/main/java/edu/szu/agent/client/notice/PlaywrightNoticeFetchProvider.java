package edu.szu.agent.client.notice;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Playwright-backed {@link NoticeFetchProvider} that navigates to
 * {@code https://www1.szu.edu.cn/board/} and returns the rendered HTML.
 *
 * <p>The board page is publicly accessible (no CAS login required), so the
 * probe selector was calibrated directly against the live HTML: the
 * content root is a {@code fieldset} for each category, each notice row
 * is a {@code tr} with a link to the detail page.
 *
 * <p>// Design Pattern: Strategy
 * <p>// 编程技术: 接口实现 / Lambda / 不可变组合
 *
 * @since 0.6.0
 * @author 王子豪
 */
public class PlaywrightNoticeFetchProvider implements NoticeFetchProvider {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightNoticeFetchProvider.class);

    /** Public board list URL — no login required. */
    public static final String BOARD_URL = "https://www1.szu.edu.cn/board/";

    /**
     * Probe selector to wait for — any {@code fieldset} means at least one
     * category is loaded, which implies the page is ready.
     */
    private static final String PROBE_SELECTOR = "fieldset";

    private final BrowserLifecycle browser;
    private final long timeoutMs;

    /**
     * @param browser   the shared browser lifecycle (one Playwright session
     *                  per call, closed in a try-with-resources)
     * @param timeoutMs navigation timeout; defaults to 30s when null
     */
    public PlaywrightNoticeFetchProvider(BrowserLifecycle browser, Long timeoutMs) {
        this.browser = Objects.requireNonNull(browser, "browser");
        this.timeoutMs = timeoutMs == null ? 30_000L : timeoutMs;
    }

    @Override
    public String fetchHtml() {
        log.info("Navigating to board list page {}", BOARD_URL);
        try (var page = browser.newPage()) {
            page.navigate(BOARD_URL);
            page.waitForSelector(PROBE_SELECTOR);
            return page.content();
        } catch (RuntimeException e) {
            throw new NoticeFetchException(ErrorCode.NOTICE_FETCH_FAILED,
                "Unexpected error navigating to " + BOARD_URL, e);
        }
    }
}

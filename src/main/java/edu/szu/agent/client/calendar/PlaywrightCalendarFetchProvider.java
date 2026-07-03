package edu.szu.agent.client.calendar;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Playwright-backed {@link CalendarFetchProvider} that navigates to the SZU
 * official academic-calendar page and returns its rendered HTML.
 *
 * <p>The page is publicly accessible (no CAS login required). It currently
 * renders the calendar as PNG images rather than parseable text, so this
 * provider's primary value is **liveness probing** + **forward-compat**: if
 * the page ever gains HTML text content, parsing will produce events; until
 * then the resilient wrapper falls back to the static 2025-2026 spring data.
 *
 * <p>// Design Pattern: Strategy
 * <p>// 编程技术: 接口实现 / Lambda / 不可变组合
 *
 * @since 0.6.0
 * @author 王子豪
 */
public class PlaywrightCalendarFetchProvider implements CalendarFetchProvider {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightCalendarFetchProvider.class);

    /** Public SZU calendar page — no login required. */
    public static final String CALENDAR_URL = "https://www.szu.edu.cn/xxgk/xl.htm";

    /**
     * Probe selector to wait for — {@code h3} with the Chinese word
     * "校历" confirms the page is rendered (rather than 404 or redirect).
     */
    private static final String PROBE_SELECTOR = "h3";

    private final BrowserLifecycle browser;
    private final long timeoutMs;

    /**
     * @param browser   the shared browser lifecycle (one Playwright session
     *                  per call, closed in a try-with-resources)
     * @param timeoutMs navigation timeout; defaults to 30s when null
     */
    public PlaywrightCalendarFetchProvider(BrowserLifecycle browser, Long timeoutMs) {
        this.browser = Objects.requireNonNull(browser, "browser");
        this.timeoutMs = timeoutMs == null ? 30_000L : timeoutMs;
    }

    @Override
    public String fetchHtml() {
        log.info("Navigating to official calendar page {}", CALENDAR_URL);
        try (var page = browser.newPage()) {
            page.navigate(CALENDAR_URL);
            page.waitForSelector(PROBE_SELECTOR);
            return page.content();
        } catch (RuntimeException e) {
            throw new CalendarFetchException(ErrorCode.CALENDAR_FETCH_FAILED,
                "Unexpected error navigating to " + CALENDAR_URL, e);
        }
    }
}
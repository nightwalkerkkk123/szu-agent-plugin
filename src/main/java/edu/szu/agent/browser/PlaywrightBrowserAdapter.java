package edu.szu.agent.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;

import java.util.Objects;

/**
 * Playwright-backed implementation of {@link BrowserLifecycle}.
 *
 * <p>Per ADR-0002 D3: receives a {@link Playwright} instance via constructor;
 * does not own SDK lifecycle. Per D4: launches headless by default.
 *
 * <p>Per ADR-0002 D2: maps Playwright exceptions to {@link ErrorCode}:
 * <ul>
 *   <li>{@code playwright.TimeoutError} → {@link ErrorCode#NETWORK_TIMEOUT}</li>
 *   <li>exception with "selector" / "locator" in message → {@link ErrorCode#ELEMENT_NOT_FOUND}</li>
 *   <li>anything else → {@link ErrorCode#BROWSER_CRASH}</li>
 * </ul>
 *
 * // Design Pattern: Adapter (concrete)
 * // 编程技术: 不可变构造器注入 + 显式状态管理(browser / page 字段)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class PlaywrightBrowserAdapter implements BrowserLifecycle {

    private final Playwright playwright;
    private Browser browser;
    private Page page;

    /**
     * @param playwright the SDK entry point; must not be null
     */
    public PlaywrightBrowserAdapter(Playwright playwright) {
        this.playwright = Objects.requireNonNull(playwright, "playwright");
    }

    @Override
    public void open() {
        try {
            browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true));
            page = browser.newPage();
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    @Override
    public void close() {
        try {
            if (page != null) {
                page.close();
                page = null;
            }
            if (browser != null) {
                browser.close();
                browser = null;
            }
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    @Override
    public void navigateTo(String url) {
        Objects.requireNonNull(url, "url");
        try {
            page.navigate(url);
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    @Override
    public void click(String selector) {
        Objects.requireNonNull(selector, "selector");
        try {
            page.locator(selector).click();
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    @Override
    public void fill(String selector, String value) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(value, "value");
        try {
            page.locator(selector).fill(value);
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    @Override
    public boolean isVisible(String selector) {
        Objects.requireNonNull(selector, "selector");
        try {
            return page.locator(selector).isVisible();
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    /**
     * Maps a Playwright exception to a {@link BookingException} with
     * a canonical {@link ErrorCode}. Package-private for testability.
     *
     * <p>Mapping rules (per ADR-0002 D2):
     * <ul>
     *   <li>{@code com.microsoft.playwright.TimeoutError} → {@code NETWORK_TIMEOUT}</li>
     *   <li>exception with {@code "selector"} or {@code "locator"} in message → {@code ELEMENT_NOT_FOUND}</li>
     *   <li>anything else → {@code BROWSER_CRASH}</li>
     * </ul>
     *
     * @param e the caught Playwright exception
     * @return the mapped BookingException
     */
    static BookingException mapException(Exception e) {
        if (e instanceof com.microsoft.playwright.TimeoutError) {
            return new BookingException(ErrorCode.NETWORK_TIMEOUT, e.getMessage(), e);
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("selector") || msg.contains("locator")) {
            return new BookingException(ErrorCode.ELEMENT_NOT_FOUND, e.getMessage(), e);
        }
        return new BookingException(ErrorCode.BROWSER_CRASH, e.getMessage(), e);
    }
}

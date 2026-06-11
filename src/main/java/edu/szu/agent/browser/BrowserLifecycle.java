package edu.szu.agent.browser;

import java.util.List;

/**
 * Browser lifecycle abstraction — thin facade over a real browser SDK
 * (currently Playwright) used by the booking flow.
 *
 * <p>Per ADR-0002 D1: 10 methods, exposing only what the booking flow
 * needs. Other browser capabilities (hover, drag, keyboard, etc.) are
 * YAGNI for P0.
 *
 * <p>Per ADR-0002 D2: methods throw {@link edu.szu.agent.error.BookingException}
 * with a mapped {@link edu.szu.agent.error.ErrorCode} on failure.
 *
 * <p>Per ADR-0002 D3: lifecycle is owned by the caller — the adapter
 * does not close the underlying browser SDK on {@link #close()}, only
 * the page and browser it opened.
 *
 * // Design Pattern: Adapter (target interface)
 * // 编程技术: 接口 + Java 21 sealed 候选(本接口暂不 sealed,等 Phase 3 FakeBrowser 加入再评估)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public interface BrowserLifecycle {

    /**
     * Opens a headless browser and creates a default page.
     */
    void open();

    /**
     * Closes the page and browser opened by {@link #open()}.
     * Safe to call when {@link #open()} was never called.
     */
    void close();

    /**
     * Navigates the current page to {@code url}, waiting for the load
     * state to complete.
     *
     * @param url absolute URL (e.g. "https://ehall.szu.edu.cn")
     * @throws edu.szu.agent.error.BookingException with NETWORK_TIMEOUT
     *         on navigation timeout, ELEMENT_NOT_FOUND if a frame/selector
     *         is involved, or BROWSER_CRASH for other failures
     */
    void navigateTo(String url);

    /**
     * Clicks the element matching {@code selector}. Waits for the
     * element to be visible and clickable.
     *
     * @param selector CSS selector (e.g. "#submit-btn")
     * @throws edu.szu.agent.error.BookingException with NETWORK_TIMEOUT
     *         on click timeout, ELEMENT_NOT_FOUND if the selector
     *         resolves to 0 elements, or BROWSER_CRASH for other failures
     */
    void click(String selector);

    /**
     * Fills the input matching {@code selector} with {@code value},
     * clearing any prior content first.
     *
     * @param selector CSS selector for the input element
     * @param value    text to type
     * @throws edu.szu.agent.error.BookingException with NETWORK_TIMEOUT
     *         on fill timeout, ELEMENT_NOT_FOUND if the selector
     *         resolves to 0 elements, or BROWSER_CRASH for other failures
     */
    void fill(String selector, String value);

    /**
     * Returns whether the element matching {@code selector} is visible
     * in the DOM. Does not wait — returns immediately based on current
     * state. Returns {@code false} (without throwing) if the element
     * exists but is hidden.
     *
     * @param selector CSS selector
     * @return {@code true} if visible, {@code false} if hidden or absent
     * @throws edu.szu.agent.error.BookingException with NETWORK_TIMEOUT
     *         on auto-wait timeout (element not in DOM), or BROWSER_CRASH
     *         for other failures
     */
    boolean isVisible(String selector);

    /**
     * Returns the text content of the first element matching
     * {@code selector}. Waits for the element to appear.
     *
     * @param selector CSS selector
     * @return the element's text content (never null; empty string if none)
     * @throws edu.szu.agent.error.BookingException with NETWORK_TIMEOUT
     *         on auto-wait timeout, ELEMENT_NOT_FOUND if the selector
     *         resolves to 0 elements, or BROWSER_CRASH for other failures
     */
    String textOf(String selector);

    /**
     * Returns the text content of all elements matching {@code selector},
     * in DOM order. Used for listing venues / time slots / options.
     *
     * @param selector CSS selector
     * @return list of text contents (never null, possibly empty)
     * @throws edu.szu.agent.error.BookingException with NETWORK_TIMEOUT
     *         on auto-wait timeout, ELEMENT_NOT_FOUND if the selector
     *         is invalid, or BROWSER_CRASH for other failures
     */
    List<String> allTextOf(String selector);

    /**
     * Returns the current page URL. Used by business code to detect
     * CAS-redirect landing (e.g. "did we end up at ehall after login?").
     *
     * @return the current URL (e.g. "https://ehall.szu.edu.cn/booking")
     */
    String currentUrl();

    // Phase 2 Cycle 9 will add: screenshot — one method per TDD cycle.
}

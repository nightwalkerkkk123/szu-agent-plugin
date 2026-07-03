package edu.szu.agent.browser;

import com.microsoft.playwright.Page;
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
 * @since 0.6.0
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

    /**
     * Evaluates {@code script} in the current page context and returns
     * the result as a String. Used for DOM manipulations that aren't
     * covered by click/fill (e.g. removing a {@code readonly} attribute
     * the ehall CAS form puts on the password field).
     *
     * @param script JavaScript source to evaluate
     * @return the script's return value, coerced to String
     * @throws edu.szu.agent.error.BookingException with BROWSER_CRASH
     *         on evaluation failure
     */
    String evaluate(String script);

    /**
     * Saves a screenshot of the current page to {@code absolutePath}.
     * Invoked when {@link edu.szu.agent.error.ErrorCode#shouldScreenshot()}
     * returns true (per ADR-0002 D2 mapping).
     *
     * @param absolutePath absolute filesystem path (e.g. "/tmp/ehall-error.png")
     * @throws edu.szu.agent.error.BookingException with NETWORK_TIMEOUT
     *         on page-unresponsive timeout, BROWSER_CRASH on disk-write
     *         failure or other Playwright errors
     */
    void screenshot(String absolutePath);

    /**
     * Loads cookies + localStorage from a Playwright storageState JSON file.
     * Missing or invalid file silently returns {@code false}; callers should
     * fall back to re-login.
     *
     * @param storageStateFile path to a Playwright storageState JSON; must not be null
     * @return {@code true} if the file existed and was parsed, {@code false} otherwise
     * @since 0.6.0
     * @author 王子豪
     */
    boolean importStorageState(java.nio.file.Path storageStateFile);

    /**
     * Saves current cookies + localStorage to a Playwright storageState JSON file.
     * Overwrites any existing file at the same path.
     *
     * @param storageStateFile path to write to; must not be null
     * @throws edu.szu.agent.error.BookingException with SESSION_WRITE_FAILED on disk-write error
     * @since 0.6.0
     * @author 王子豪
     */
    void exportStorageState(java.nio.file.Path storageStateFile);

    /**
     * Opens a new page in the current browser context.
     *
     * <p>Used by fetchers that need to open a fresh page without leaving
     * the old page open (e.g. notice list fetcher, homework attachment downloader).
     *
     * @return the new Page object (caller closes it when done)
     * @throws edu.szu.agent.error.BookingException with BROWSER_CRASH on failure
     * @since 0.6.0
     * @author 王子豪
     */
    Page newPage();

    /**
     * Downloads a single file from a CAS-protected URL using the current
     * browser context's cookies / signed URL, writing bytes to {@code target}
     * via a {@code .tmp} + atomic move.
     *
     * <p>Accepts any URL: LMS API endpoints (cookie auth) or
     * {@code media2.szu.edu.cn} signed URLs (token in query). The Playwright
     * request context shares cookies with the page context, so LMS
     * authentication is inherited automatically.
     *
     * @param url    absolute URL to fetch; must not be null
     * @param target absolute path to write the file to; parent dir must
     *               exist; must not be null
     * @return number of bytes written
     * @throws BookingException with ATTACHMENT_DOWNLOAD_FAILED on HTTP / IO
     *         errors, NETWORK_TIMEOUT on timeout, BROWSER_CRASH otherwise
     * @since 0.6.0
     * @author 王子豪
     */
    long downloadAttachment(String url, java.nio.file.Path target);

    /**
     * Returns the full HTML content of the current page.
     *
     * @return the complete document HTML markup
     * @throws edu.szu.agent.error.BookingException with BROWSER_CRASH
     *         if evaluation fails
     * @since 0.6.0
     * @author 王子豪
     */
    String content();

    /**
     * Polls {@link #isVisible(String)} until the selector resolves to a
     * visible element or {@code timeoutMs} elapses.
     *
     * <p>Default implementation is a 250ms poll loop; adapters may override
     * with SDK-native waits for better efficiency.
     *
     * @param selector  CSS selector
     * @param timeoutMs maximum time to wait, in milliseconds
     * @return {@code true} if the element became visible, {@code false} if the
     *         timeout elapsed
     */
    default boolean waitForVisible(String selector, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long pollMs = 250L;
        while (true) {
            if (isVisible(selector)) {
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(pollMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
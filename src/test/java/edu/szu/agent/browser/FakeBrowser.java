package edu.szu.agent.browser;

import java.util.ArrayList;
import java.util.List;
import com.microsoft.playwright.Page;

/**
 * Test double for {@link BrowserLifecycle} — fakes a successful
 * ehall booking flow without opening a real browser.
 *
 * <p>Behavior:
 * <ul>
 *   <li>{@link #open()} / {@link #close()} — no-op, track state
 *   <li>{@link #navigateTo(String)} — no-op, record URL
 *   <li>{@link #fill(String, String)} — record the field
 *   <li>{@link #click(String)} — record the click
 *   <li>{@link #isVisible(String)} — always returns {@code true}
 *   <li>{@link #allTextOf(String)} — returns the configured option
 *       list for the matching selector prefix. The 7-step pipeline
 *       uses two distinct selector families:
 *       <ul>
 *         <li>time-slot selectors (containing {@code "time"}) → {@link #timeSlots}
 *         <li>venue selectors (containing {@code "venue"} or {@code "li"} alone) → {@link #venueOptions}
 *       </ul>
 *       Override either constructor to customize.
 *   <li>{@link #textOf(String)} / {@link #currentUrl()} — return placeholder
 *   <li>{@link #screenshot(String)} — no-op
 * </ul>
 *
 * <p>Use this in tests that want to drive the entire
 * {@code VenueBookingClient.book()} pipeline without a real
 * Playwright instance. To simulate failures, override individual
 * methods in an anonymous subclass.
 *
 * // 编程技术: 抽象类 / 不可变
 *
 * @since 0.6.0
 * @author 王子豪
 */
public class FakeBrowser implements BrowserLifecycle {

    private final List<String> venueOptions;
    private final List<String> timeSlots;
    private final List<String> clicked = new ArrayList<>();
    private final List<String> filled = new ArrayList<>();
    private boolean opened;
    private boolean loaded;
    private boolean saved;
    private java.nio.file.Path loadedPath;
    private java.nio.file.Path savedPath;

    public FakeBrowser() {
        this(List.of("网球场1号", "网球场2号", "网球场3号"),
             List.of("19:00-20:00", "20:00-21:00"));
    }

    public FakeBrowser(List<String> venueOptions) {
        this(venueOptions, List.of("19:00-20:00", "20:00-21:00"));
    }

    public FakeBrowser(List<String> venueOptions, List<String> timeSlots) {
        this.venueOptions = List.copyOf(venueOptions);
        this.timeSlots = List.copyOf(timeSlots);
    }

    @Override
    public void open() {
        opened = true;
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public void navigateTo(String url) {
        // no-op; could record url if tests need it
    }

    @Override
    public void click(String selector) {
        clicked.add(selector);
    }

    @Override
    public String evaluate(String script) {
        // no-op stub: tests don't assert on script content
        return "";
    }

    @Override
    public void fill(String selector, String value) {
        filled.add(selector + "=" + value);
    }

    @Override
    public boolean isVisible(String selector) {
        return true;
    }

    @Override
    public String textOf(String selector) {
        return "stub-text";
    }

    @Override
    public List<String> allTextOf(String selector) {
        // Dispatch by selector content: time-slot selectors return timeSlots;
        // everything else (venue selectors, generic li queries) returns venueOptions.
        if (selector != null && (selector.contains("time") || selector.contains("slot"))) {
            return timeSlots;
        }
        return venueOptions;
    }

    @Override
    public String currentUrl() {
        return "https://ehall.szu.edu.cn/fake";
    }

    @Override
    public void screenshot(String absolutePath) {
        // no-op
    }

    @Override
    public boolean importStorageState(java.nio.file.Path storageStateFile) {
        this.loadedPath = storageStateFile;
        this.loaded = storageStateFile != null;
        return loaded;
    }

    @Override
    public void exportStorageState(java.nio.file.Path storageStateFile) {
        this.savedPath = storageStateFile;
        this.saved = storageStateFile != null;
    }

    /** Recorded by {@link #downloadAttachment(String, java.nio.file.Path)} (US-008). */
    private final java.util.List<DownloadCall> downloadCalls = new java.util.ArrayList<>();
    /** Optional byte payload to return from {@link #downloadAttachment}; tests set this. */
    private byte[] downloadPayload;
    /** Optional exception to throw from {@link #downloadAttachment}; tests set this. */
    private RuntimeException downloadError;

    @Override
    public long downloadAttachment(String url, java.nio.file.Path target) {
        downloadCalls.add(new DownloadCall(url, target));
        if (downloadError != null) {
            throw downloadError;
        }
        byte[] payload = downloadPayload != null ? downloadPayload : new byte[0];
        try {
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.write(target, payload);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return payload.length;
    }

    public void setDownloadPayload(byte[] payload) {
        this.downloadPayload = payload;
    }

    public void setDownloadError(RuntimeException error) {
        this.downloadError = error;
    }

    public java.util.List<DownloadCall> getDownloadCalls() {
        return java.util.List.copyOf(downloadCalls);
    }

    /** Recorded argument pair for download calls. */
    public record DownloadCall(String url, java.nio.file.Path target) {}

    // Test introspection helpers

    public boolean isOpened() {
        return opened;
    }

    public List<String> getClicked() {
        return List.copyOf(clicked);
    }

    public List<String> getFilled() {
        return List.copyOf(filled);
    }

    public boolean isLoaded() { return loaded; }
    public boolean isSaved() { return saved; }
    public java.nio.file.Path loadedPath() { return loadedPath; }
    public java.nio.file.Path savedPath() { return savedPath; }

    @Override
    public Page newPage() {
        throw new UnsupportedOperationException("FakeBrowser does not support newPage()");
    }

    @Override
    public String content() {
        // Booking-flow tests don't need real page HTML; tests that exercise
        // HTML-parsing paths should use a Playwright-backed fake or mock
        // the BrowserLifecycle instead.
        return "<html><body>fake</body></html>";
    }
}

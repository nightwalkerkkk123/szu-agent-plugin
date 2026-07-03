package edu.szu.agent.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 * @since 0.6.0
 * @author 王子豪
 */
public final class PlaywrightBrowserAdapter implements BrowserLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowserAdapter.class);

    private final Playwright playwright;
    private final boolean headless;
    private final boolean ownsPlaywright;
    private final Optional<String> cdpUrl;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    /**
     * @param playwright the SDK entry point; must not be null
     */
    public PlaywrightBrowserAdapter(Playwright playwright) {
        this(playwright, resolveHeadless(), null);
    }

    /**
     * @param playwright the SDK entry point; must not be null
     * @param headless   if {@code false}, browser window is shown (useful for
     *                   manual debugging / captcha solving)
     */
    public PlaywrightBrowserAdapter(Playwright playwright, boolean headless) {
        this(playwright, headless, null);
    }

    /**
     * Creates an adapter that connects Playwright to an existing CDP endpoint,
     * typically the auto-managed Obscura daemon.
     *
     * @param playwright the SDK entry point; must not be null
     * @param cdpUrl     WebSocket CDP endpoint; must not be blank
     * @since 0.6.0
     * @author 王子豪
     */
    public PlaywrightBrowserAdapter(Playwright playwright, String cdpUrl) {
        this(playwright, true, Objects.requireNonNull(cdpUrl, "cdpUrl"), true);
    }

    private PlaywrightBrowserAdapter(Playwright playwright, boolean headless, String cdpUrl) {
        this(playwright, headless, cdpUrl, false);
    }

    private PlaywrightBrowserAdapter(Playwright playwright, boolean headless, String cdpUrl,
                                     boolean ownsPlaywright) {
        this.playwright = Objects.requireNonNull(playwright, "playwright");
        this.headless = headless;
        this.cdpUrl = Optional.ofNullable(cdpUrl).filter(v -> !v.isBlank());
        this.ownsPlaywright = ownsPlaywright;
    }

    /**
     * Resolves the headless flag from the {@code SZU_HEADLESS} env var and
     * the {@code szu.agent.headless} system property. Defaults to {@code true}
     * (matches ADR-0002 D4). Any value other than {@code "false"} / {@code "0"}
     * (case-insensitive) keeps headless enabled.
     */
    static boolean resolveHeadless() {
        String env = System.getenv("SZU_HEADLESS");
        String prop = System.getProperty("szu.agent.headless");
        String raw = (prop != null && !prop.isBlank()) ? prop : env;
        if (raw == null) {
            return true;
        }
        String v = raw.trim().toLowerCase();
        return !("false".equals(v) || "0".equals(v));
    }

    @Override
    public void open() {
        try {
            if (cdpUrl.isPresent()) {
                ObscuraLauncher.ensureRunning();
                browser = playwright.chromium().connectOverCDP(cdpUrl.get());
                context = browser.contexts().isEmpty()
                    ? browser.newContext()
                    : browser.contexts().get(0);
                page = context.pages().isEmpty()
                    ? context.newPage()
                    : context.pages().get(0);
            } else {
                browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(headless));
                context = browser.newContext();
                page = context.newPage();
            }
            // ehall pages are heavy (CAS redirect + Angular SPA + iframe); give
            // navigation a generous budget. 60s default unless overridden via
            // -Dszu.agent.nav-timeout-ms=NNN.
            long navTimeout = Long.getLong("szu.agent.nav-timeout-ms", 60_000L);
            page.setDefaultNavigationTimeout(navTimeout);
            page.setDefaultTimeout(navTimeout);
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
            if (context != null) {
                context.close();
                context = null;
            }
            if (browser != null) {
                browser.close();
                browser = null;
            }
            // Close the Playwright driver only when the adapter constructed it
            // itself (OBSCURA ctor). The legacy 2-arg / 3-arg ctors receive a
            // caller-owned Playwright and must not close it.
            if (ownsPlaywright) {
                playwright.close();
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

    @Override
    public String textOf(String selector) {
        Objects.requireNonNull(selector, "selector");
        try {
            String text = page.locator(selector).textContent();
            return text == null ? "" : text;
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    @Override
    public List<String> allTextOf(String selector) {
        Objects.requireNonNull(selector, "selector");
        try {
            return page.locator(selector).allTextContents();
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    @Override
    public String currentUrl() {
        return page.url();
    }

    @Override
    public String evaluate(String script) {
        Objects.requireNonNull(script, "script");
        try {
            Object result = page.evaluate(script);
            return result == null ? "" : result.toString();
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    @Override
    public void screenshot(String absolutePath) {
        Objects.requireNonNull(absolutePath, "absolutePath");
        try {
            page.screenshot(
                new Page.ScreenshotOptions().setPath(Paths.get(absolutePath)));
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

    @Override
    public boolean importStorageState(java.nio.file.Path storageStateFile) {
        Objects.requireNonNull(storageStateFile, "storageStateFile");
        if (!Files.exists(storageStateFile)) {
            return false;
        }
        if (browser == null) {
            log.warn("import requested before open(); skipping");
            return false;
        }
        try {
            // Playwright requires storageState at context-creation time. Tear
            // down the just-opened blank context (and its page) and rebuild a
            // new one seeded with the persisted file.
            if (page != null) {
                page.close();
                page = null;
            }
            if (context != null) {
                context.close();
                context = null;
            }
            context = browser.newContext(
                new Browser.NewContextOptions().setStorageStatePath(storageStateFile));
            page = context.newPage();
            long navTimeout = Long.getLong("szu.agent.nav-timeout-ms", 60_000L);
            page.setDefaultNavigationTimeout(navTimeout);
            page.setDefaultTimeout(navTimeout);
            return true;
        } catch (Exception e) {
            log.warn("Failed to import persisted state: {}", e.getMessage());
            // Best-effort fallback: rebuild a blank context so subsequent calls
            // do not blow up on a missing context.
            try {
                if (context == null) {
                    context = browser.newContext();
                    page = context.newPage();
                }
            } catch (Exception ignored) {
                // give up; caller will see the false return and re-login
            }
            return false;
        }
    }

    @Override
    public void exportStorageState(java.nio.file.Path storageStateFile) {
        Objects.requireNonNull(storageStateFile, "storageStateFile");
        if (context == null) {
            throw new BookingException(ErrorCode.SESSION_WRITE_FAILED,
                "no browser context to export");
        }
        try {
            Path parent = storageStateFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            context.storageState(
                new BrowserContext.StorageStateOptions().setPath(storageStateFile));
        } catch (Exception e) {
            throw new BookingException(ErrorCode.SESSION_WRITE_FAILED,
                "export failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Page newPage() {
        if (context == null) {
            context = browser.newContext();
        }
        return context.newPage();
    }

    @Override
    public long downloadAttachment(String url, java.nio.file.Path target) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(target, "target");
        if (context == null) {
            throw new BookingException(ErrorCode.BROWSER_CRASH,
                "downloadAttachment called before open()");
        }
        Path parent = target.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (Exception e) {
                throw new BookingException(ErrorCode.OUTPUT_DIR_INVALID,
                    "cannot create parent dir for " + target + ": " + e.getMessage(), e);
            }
        }
        // Write to .tmp, then atomic move to target.
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        var response = context.request().get(url);
        try {
            if (!response.ok()) {
                throw new BookingException(ErrorCode.ATTACHMENT_DOWNLOAD_FAILED,
                    "HTTP " + response.status() + " fetching " + url);
            }
            byte[] body = response.body();
            Files.write(tmp, body);
            try {
                java.nio.file.Files.move(tmp, target,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException amns) {
                // Fall back to non-atomic move on filesystems that don't support it.
                java.nio.file.Files.move(tmp, target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return body.length;
        } catch (BookingException e) {
            throw e;
        } catch (Exception e) {
            // Best-effort cleanup of the tmp file
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            throw new BookingException(ErrorCode.ATTACHMENT_DOWNLOAD_FAILED,
                "download failed for " + url + ": " + e.getMessage(), e);
        } finally {
            try { response.dispose(); } catch (Exception ignored) {}
        }
    }

    @Override
    public String content() {
        try {
            return page.content();
        } catch (Exception e) {
            throw mapException(e);
        }
    }
}

package edu.szu.agent.client.session;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Probes whether a freshly-imported session is still valid by navigating
 * to a known-protected URL and checking for a logged-in indicator.
 *
 * <p>// Design Pattern: Strategy (concrete probe)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class SessionProbe {

    private static final Logger log = LoggerFactory.getLogger(SessionProbe.class);

    /**
     * Max time to wait for the alive indicator to render. The probe target is
     * an SPA whose indicator is injected by post-load JS/XHR, so an instant
     * check races the render (especially on slow networks). 15s is generous
     * enough to ride out a laggy CAS SSO redirect + SPA hydration.
     */
    private static final long ALIVE_TIMEOUT_MS = 15_000L;

    private final String probeUrl;
    private final String aliveSelector;

    /**
     * Creates a probe targeting a specific URL and DOM selector.
     *
     * @param probeUrl protected URL that requires authentication
     * @param aliveSelector CSS selector that is only visible when logged in
     * @since 0.1.0
     * @author 王子豪
     */
    public SessionProbe(String probeUrl, String aliveSelector) {
        this.probeUrl = Objects.requireNonNull(probeUrl, "probeUrl");
        this.aliveSelector = Objects.requireNonNull(aliveSelector, "aliveSelector");
    }

    /**
     * Navigates to the probe URL and inspects the alive indicator.
     *
     * @param browser browser lifecycle adapter
     * @return {@link SessionResult.Fresh} when the indicator is visible,
     *         {@link SessionResult.Stale} otherwise
     * @since 0.1.0
     * @author 王子豪
     */
    public SessionResult isAlive(BrowserLifecycle browser) {
        Objects.requireNonNull(browser, "browser");
        try {
            browser.navigateTo(probeUrl);
            // Wait for the SPA-injected indicator instead of an instant check:
            // navigateTo only awaits 'load', but the indicator renders later.
            if (browser.waitForVisible(aliveSelector, ALIVE_TIMEOUT_MS)) {
                return new SessionResult.Fresh();
            }
            return new SessionResult.Stale("indicator not visible after navigate");
        } catch (BookingException e) {
            log.info("probe navigate failed: {}", e.getMessage());
            return new SessionResult.Stale("navigate failed: " + e.getMessage());
        }
    }
}

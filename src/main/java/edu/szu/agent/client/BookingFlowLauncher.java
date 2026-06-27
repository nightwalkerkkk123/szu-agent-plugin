package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.client.step.CasLoginStep;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.retry.RetryPolicy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * High-level seam between CLI / Skill / MCP callers and the actual booking flow.
 *
 * <p>Owns the {@link BrowserLifecycle} and {@link RetryPolicy} wiring so that
 * callers (e.g. {@link edu.szu.agent.cli.VenueCommand}) only need to supply a
 * resolved {@link Account} and a populated {@link BookingRequest}.
 *
 * <p>Per ADR-0001 D1/D5: this keeps the CLI as a thin shell — parameter
 * parsing and output formatting live in the command class; business execution
 * lives here.
 *
 * <p>Per the session-reuse design (2026-06): builds a per-user
 * {@link SessionStore} from the resolved account so a single manual MFA pass
 * (headed browser) is persisted and reused on subsequent headless runs.
 *
 * // Design Pattern: Adapter (caller-facing seam)
 * // 编程技术: 不可变构造器注入
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class BookingFlowLauncher {

    /**
     * Session-alive probe selector: the 粤海校区 campus button, always rendered
     * on the authenticated ehall booking page. Must match exactly ONE element —
     * a broad selector like {@code .bh-btn} matches both campus buttons and
     * trips Playwright strict mode, so the probe would throw instead of
     * reporting the session alive.
     */
    private static final String SEL_LOGGED_IN = "div.bh-btn.bh-btn-primary:has-text(\"粤海校区\")";
    /** Persisted session is considered usable for 30 days (matches homework flow). */
    private static final Duration SESSION_TTL = Duration.ofDays(30);

    private final BrowserLifecycle browser;
    private final RetryPolicy retryPolicy;

    /**
     * @param browser     the browser adapter (fresh instance per launch expectation)
     * @param retryPolicy retry policy for the booking flow
     */
    public BookingFlowLauncher(BrowserLifecycle browser, RetryPolicy retryPolicy) {
        this.browser = Objects.requireNonNull(browser, "browser");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    /**
     * Executes the booking flow for the given request and resolved account.
     *
     * @param request the booking request
     * @param account resolved credentials
     * @return the booking result
     */
    public BookingResult launch(BookingRequest request, Account account) {
        return clientFor(account).book(request, account);
    }

    /**
     * Builds a session-aware {@link VenueBookingClient} keyed to the given
     * account, bound to this launcher's default {@link #browser}. Single
     * source of truth for the per-user session wiring, reused by both the
     * CLI ({@link #launch}) and the MCP/daemon path
     * ({@link edu.szu.agent.task.BookingTask}).
     *
     * <p>The session store is keyed by student ID so one manual MFA pass
     * (headed) is persisted and reused on later headless runs. The probe
     * validates a restored session by reloading the ehall booking page and
     * checking the logged-in indicator is visible.
     *
     * @param account resolved credentials (supplies the student ID key)
     * @return a session-aware booking client for this account
     * @since 0.3.0
     * @author 王子豪
     */
    public VenueBookingClient clientFor(Account account) {
        return clientFor(account.studentId(), this.browser);
    }

    /**
     * Builds a session-aware {@link VenueBookingClient} bound to a specific
     * browser. Used by the headed-fallback path in
     * {@link edu.szu.agent.task.BookingTask}: when no credential can be
     * resolved, a fresh headed browser is built and a new client is created
     * against it, with the same session/probe wiring so the user can log in
     * manually and have the resulting session persisted.
     *
     * @param username the student ID key for the session store
     * @param browser  the browser to bind the client to (typically headed)
     * @return a session-aware booking client bound to {@code browser}
     * @since 0.5.0
     * @author 王子豪
     */
    public VenueBookingClient clientFor(String username, BrowserLifecycle browser) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(browser, "browser");
        SessionStore store = new SessionStore(
            Path.of(System.getProperty("user.home")), username);
        SessionProbe probe = new SessionProbe(CasLoginStep.EHALL_VENUE_URL, SEL_LOGGED_IN);
        return new VenueBookingClient(browser, retryPolicy, store, probe, SESSION_TTL);
    }
}

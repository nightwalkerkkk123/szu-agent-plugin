package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.retry.RetryPolicy;

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
 * // Design Pattern: Adapter (caller-facing seam)
 * // 编程技术: 不可变构造器注入
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class BookingFlowLauncher {

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
        VenueBookingClient client = new VenueBookingClient(browser, retryPolicy);
        return client.book(request, account);
    }
}

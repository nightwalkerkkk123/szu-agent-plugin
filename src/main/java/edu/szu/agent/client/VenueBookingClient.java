package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.client.step.*;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Venue booking orchestrator — drives the browser through the SZU ehall
 * booking flow using a {@link BookingStep} pipeline.
 *
 * <p>Per PRD §3.1 F2.2: the flow is login → select campus → select sport →
 * select time slot → select venue → confirm. Each step maps to one or more
 * {@link BrowserLifecycle} calls.
 *
 * <p>Per ADR-0001 D9: retry is driven by {@link RetryPolicy} wrapping the
 * entire flow; per-exception retryability comes from
 * {@link ErrorCode#isRetryable()} on the wrapped exception.
 *
 * <p>Per ADR-0007 D4: failure recording uses
 * {@link Tracer#recordFailure(ErrorCode, String, Optional)} —
 * this class does NOT pass {@code Throwable} to the tracer.
 *
 * // Design Pattern: Strategy (pipeline of BookingSteps) + Adapter
 * // 编程技术: 枚举 / Lambda / 不可变构造器注入 / try-with-resources 风格
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class VenueBookingClient {

    private static final Logger log = LoggerFactory.getLogger(VenueBookingClient.class);

    private final BrowserLifecycle browser;
    private final RetryPolicy retryPolicy;
    private final List<BookingStep> steps;

    /**
     * Production constructor — uses the default 8-step pipeline.
     *
     * @param browser      the browser adapter (injected by ConfigManager.browser())
     * @param retryPolicy  retry policy for the booking flow
     */
    public VenueBookingClient(BrowserLifecycle browser, RetryPolicy retryPolicy) {
        this(browser, retryPolicy, List.of(
            new CasLoginStep(),
            new NavigateToBookingStep(),
            new SelectCampusStep(),
            new SelectSportStep(),
            new SelectDateStep(),
            new SelectTimeSlotStep(),
            new SelectVenueStep(),
            new ConfirmBookingStep()
        ));
    }

    /**
     * Session-aware constructor — same pipeline as the default one, plus
     * {@link RestoreSessionStep} at the front and {@link PersistSessionStep}
     * right after {@code SELECT_CAMPUS}.
     *
     * <p>Placement rationale: {@code CAS_LOGIN} cannot confirm success at submit
     * time (MFA is completed later), so {@code SELECT_CAMPUS} is the first step
     * that proves authentication. Persisting immediately after it captures the
     * session even when a later step fails (e.g. slot full at
     * {@code SELECT_TIME_SLOT}) — the pipeline stops on the first failure, so a
     * tail-placed persist would never run in that case.
     *
     * @param browser     the browser adapter
     * @param retryPolicy retry policy for the booking flow
     * @param store       session storage (keyed by username)
     * @param probe       alive-check probe for a restored session
     * @param ttl         maximum age allowed for a persisted session
     * @since 0.3.0
     * @author 王子豪
     */
    public VenueBookingClient(BrowserLifecycle browser,
                              RetryPolicy retryPolicy,
                              SessionStore store,
                              SessionProbe probe,
                              Duration ttl) {
        this(browser, retryPolicy, List.of(
            new RestoreSessionStep(store, probe, ttl),
            new CasLoginStep(),
            new NavigateToBookingStep(),
            new SelectCampusStep(),
            // Persist here: SELECT_CAMPUS proves auth; later failures must not
            // lose the freshly-authenticated session. Unconditional (ctx->true).
            new PersistSessionStep(store, ctx -> true),
            new SelectSportStep(),
            new SelectDateStep(),
            new SelectTimeSlotStep(),
            new SelectVenueStep(),
            new ConfirmBookingStep()
        ));
    }

    /**
     * Full DI constructor — for testing with custom step lists.
     *
     * @param browser      the browser adapter
     * @param retryPolicy  retry policy for the booking flow
     * @param steps        the booking steps to execute in order
     */
    VenueBookingClient(BrowserLifecycle browser,
                        RetryPolicy retryPolicy,
                        List<BookingStep> steps) {
        this.browser = Objects.requireNonNull(browser, "browser");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.steps = List.copyOf(steps);
    }

    /**
     * @return the ordered step names of this pipeline (for tests / diagnostics)
     */
    List<String> stepNames() {
        return steps.stream().map(BookingStep::name).toList();
    }

    /**
     * Executes the full booking flow.
     *
     * <p>The caller is responsible for closing the browser after this method
     * returns (success or failure). This method guarantees
     * {@link BrowserLifecycle#close()} is called in a finally block.
     *
     * @param request the booking request (non-null, fully populated)
     * @param account resolved credentials (from AccountResolver)
     * @return {@link BookingResult.Success} on confirmation,
     *         {@link BookingResult.Failure} on business failure
     */
    public BookingResult book(BookingRequest request, Account account) {
        Objects.requireNonNull(request, "BookingRequest must not be null");
        Objects.requireNonNull(account, "Account must not be null; resolve credentials with AccountResolver first");
        BookingContext ctx = new BookingContext(request, account);
        ctx.username(account.studentId()); // for session-store keying + persist logs
        try {
            browser.open();
            return retryPolicy.execute(() -> executePipeline(ctx));
        } catch (BookingException e) {
            // Per ADR-0007 D4: screenshot decision is made here (closest to failure site)
            Optional<Path> screenshotPath = Optional.empty();
            if (e.code().shouldScreenshot()) {
                try {
                    Path path = Path.of(System.getProperty("java.io.tmpdir"),
                        "ehall-error-" + System.currentTimeMillis() + ".png");
                    browser.screenshot(path.toString());
                    screenshotPath = Optional.of(path);
                } catch (Exception screenshotEx) {
                    log.warn("Failed to take screenshot: {}", screenshotEx.getMessage());
                }
            }
            Tracer.getInstance().recordFailure(e.code(), e.getMessage(), screenshotPath);
            return new BookingResult.Failure(e.code(), e.getMessage());
        } finally {
            try {
                browser.close();
            } catch (Exception e) {
                log.warn("Failed to close browser: {}", e.getMessage());
            }
        }
    }

    /**
     * Runs all steps in sequence. Stops on first {@link StepOutcome.Failure}.
     */
    private BookingResult executePipeline(BookingContext ctx) {
        log.info("Starting booking flow: campus={} sport={} date={} slot={}",
            ctx.request().campus().displayName(),
            ctx.request().sport().displayName(),
            ctx.request().date(),
            ctx.request().timeSlot());

        BookingContext current = ctx;
        for (BookingStep step : steps) {
            log.info("Executing step: {}", step.name());
            StepOutcome outcome = step.execute(browser, current);
            if (outcome instanceof StepOutcome.Failure f) {
                log.warn("Step {} failed: {}", step.name(), f.result().message());
                return f.result();
            }
            if (outcome instanceof StepOutcome.Continue c) {
                current = c.nextContext();
            }
        }

        String venueName = current.selectedVenue() != null
            ? current.selectedVenue()
            : "unknown";
        log.info("Booking confirmed: venue={}", venueName);
        return new BookingResult.Success(venueName, "CONFIRMED-" + System.currentTimeMillis());
    }
}

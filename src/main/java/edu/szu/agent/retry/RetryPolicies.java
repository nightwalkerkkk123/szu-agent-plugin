package edu.szu.agent.retry;

import java.time.Duration;

/**
 * Factory for common retry policies.
 *
 * <p>Per ADR-0006 §3.9: three preset methods. ConfigManager reads these
 * at startup; business code never calls {@code new FixedDelay(...)} directly.
 *
 * // 编程技术: 工厂模式(静态方法,非设计模式条目;仅做代码组织)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class RetryPolicies {

    private RetryPolicies() {
    }

    /**
     * Default policy for a full booking flow — single attempt.
     *
     * <p>Why no retry by default: ehall is a stateful SPA where each step
     * builds on the page state of the previous (login session, selected
     * campus, expanded sport panel, …). If we re-run the pipeline from
     * step 0 after a mid-flow failure, the page is no longer in the
     * "fresh" state the early steps assume — login selectors won't match
     * because we're already logged in, and the browser may be navigating
     * mid-retry. ADR-0001 D9 originally specified
     * {@link ExponentialBackoff}; this was revised after live testing
     * revealed the pipeline-restart problem.
     *
     * <p>Per-step retries (e.g. retrying a single click on a transient
     * network blip) should be added inside {@code BookingStep}
     * implementations using {@link FixedDelay} on a narrow scope.
     */
    public static RetryPolicy defaultBooking() {
        return NoRetry.INSTANCE;
    }

    /**
     * Legacy policy — 3 exponential-backoff attempts with 2s base.
     * Retained for stateless flows that genuinely tolerate full restart;
     * not used by the booking pipeline. See {@link #defaultBooking()}.
     */
    public static RetryPolicy exponentialBackoff() {
        return new ExponentialBackoff(3, Duration.ofSeconds(2));
    }

    /**
     * Login retry — fixed 2s delay, 3 attempts. Login is quick and
     * exponential backoff would just delay the user.
     */
    public static RetryPolicy login() {
        return new FixedDelay(3, Duration.ofSeconds(2));
    }

    /**
     * Quick fix — no retry. For idempotent probes that should fail fast.
     */
    public static RetryPolicy quickFix() {
        return NoRetry.INSTANCE;
    }
}

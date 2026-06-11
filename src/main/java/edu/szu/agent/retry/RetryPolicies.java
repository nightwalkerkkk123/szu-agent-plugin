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
     * Default policy for a full booking flow — 3 exponential-backoff
     * attempts with 2s base.
     */
    public static RetryPolicy defaultBooking() {
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

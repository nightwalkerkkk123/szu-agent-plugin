package edu.szu.agent.retry;

import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Exponential backoff retry policy — delay grows by a multiplier, capped
 * at a maximum.
 *
 * <p>Per ADR-0006 §3.4: two constructors. The simple form defaults
 * {@code maxDelay = base × 16} and {@code multiplier = 2.0}. The full
 * form exposes every knob.
 *
 * <p>Attempt {@code n} waits {@code min(base × multiplier^(n-1), maxDelay)}.
 *
 * // Design Pattern: Strategy (concrete)
 * // 编程技术: 重载(6 技术之一,两个构造器)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class ExponentialBackoff implements RetryPolicy {

    private final int maxAttempts;
    private final Duration base;
    private final Duration maxDelay;
    private final double multiplier;

    /**
     * Simple form: {@code maxDelay = base × 16}, {@code multiplier = 2.0}.
     *
     * @param maxAttempts total attempts &gt;= 1
     * @param base       initial delay &gt;= 0
     */
    public ExponentialBackoff(int maxAttempts, Duration base) {
        this(maxAttempts, base, base.multipliedBy(16), 2.0);
    }

    /**
     * Full form.
     *
     * @param maxAttempts total attempts &gt;= 1
     * @param base       initial delay &gt;= 0
     * @param maxDelay   upper bound on computed delay
     * @param multiplier growth factor per attempt (typically 2.0)
     */
    public ExponentialBackoff(int maxAttempts, Duration base, Duration maxDelay, double multiplier) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, got: " + maxAttempts);
        }
        if (base == null || base.isNegative()) {
            throw new IllegalArgumentException("base must be non-negative, got: " + base);
        }
        if (maxDelay == null || maxDelay.isNegative()) {
            throw new IllegalArgumentException("maxDelay must be non-negative, got: " + maxDelay);
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0, got: " + multiplier);
        }
        this.maxAttempts = maxAttempts;
        this.base = base;
        this.maxDelay = maxDelay;
        this.multiplier = multiplier;
    }

    @Override
    public <T> T execute(Supplier<T> action) {
        BookingException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (BookingException e) {
                if (!e.code().isRetryable()) {
                    throw e;
                }
                last = e;
                if (attempt < maxAttempts) {
                    sleep(computeDelay(attempt));
                }
            }
        }
        throw new BookingException(
            ErrorCode.NETWORK_TIMEOUT,
            "ExponentialBackoff 重试 " + maxAttempts + " 次耗尽",
            last);
    }

    /**
     * Computes the delay for the next attempt. Package-private for testing.
     *
     * @param attempt the attempt that just failed (1-based)
     * @return the wait before attempt {@code attempt + 1}
     */
    Duration computeDelay(int attempt) {
        double factor = Math.pow(multiplier, attempt - 1);
        long millis = (long) (base.toMillis() * factor);
        long capped = Math.min(millis, maxDelay.toMillis());
        return Duration.ofMillis(Math.max(0, capped));
    }

    private static void sleep(Duration d) {
        if (d.isZero()) {
            return;
        }
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BookingException(
                ErrorCode.NETWORK_TIMEOUT,
                "ExponentialBackoff 等待时被中断",
                ie);
        }
    }
}

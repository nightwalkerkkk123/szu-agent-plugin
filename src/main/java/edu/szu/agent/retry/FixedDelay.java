package edu.szu.agent.retry;

import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Fixed-delay retry policy — waits a constant {@code delay} between attempts.
 *
 * <p>Per ADR-0006 §3.2: simple counter, throws {@code NETWORK_TIMEOUT}
 * after {@code maxAttempts} failures.
 *
 * // Design Pattern: Strategy (concrete)
 * // 编程技术: 不可变 record 风格 + 重载
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class FixedDelay implements RetryPolicy {

    private final int maxAttempts;
    private final Duration delay;

    /**
     * @param maxAttempts total attempts including the first try (must be &gt;= 1)
     * @param delay       wait between attempts (must be non-negative)
     */
    public FixedDelay(int maxAttempts, Duration delay) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, got: " + maxAttempts);
        }
        if (delay == null || delay.isNegative()) {
            throw new IllegalArgumentException("delay must be non-negative, got: " + delay);
        }
        this.maxAttempts = maxAttempts;
        this.delay = delay;
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
                    sleep(delay);
                }
            }
        }
        throw new BookingException(
            ErrorCode.NETWORK_TIMEOUT,
            "FixedDelay 重试 " + maxAttempts + " 次耗尽",
            last);
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
                "FixedDelay 等待时被中断",
                ie);
        }
    }
}

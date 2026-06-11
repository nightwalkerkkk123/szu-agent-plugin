package edu.szu.agent.retry;

import java.util.function.Supplier;

/**
 * No-retry policy — single attempt, propagate exception as-is.
 *
 * <p>Per ADR-0006 §3.7: singleton instance. Use {@link #INSTANCE} rather
 * than constructing a new one.
 *
 * // Design Pattern: Strategy (concrete) + Singleton
 * // 编程技术: 不可变单例(public static final)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class NoRetry implements RetryPolicy {

    /** Singleton instance — no state to construct. */
    public static final NoRetry INSTANCE = new NoRetry();

    private NoRetry() {
    }

    @Override
    public <T> T execute(Supplier<T> action) {
        return action.get();
    }
}

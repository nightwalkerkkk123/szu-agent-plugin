package edu.szu.agent.retry;

import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;

import java.util.function.Supplier;

/**
 * Retry policy — re-attempts a failed operation according to its rules.
 *
 * <p>Per ADR-0006 §3.1: a {@code @FunctionalInterface} with a single
 * {@link #execute(Supplier)} method. Per §3.3: a default
 * {@link #orElse(RetryPolicy)} for chaining (e.g. exponential backoff
 * then a different strategy).
 *
 * <p>Retry decisions are driven by the wrapped exception's
 * {@link ErrorCode#isRetryable()} metadata — the retry layer never
 * references specific {@code ErrorCode} constants (ADR-0006 §3.10).
 *
 * <p>Per ADR-0006 §3.5: when the policy is exhausted, it always throws
 * a {@link BookingException} with {@link ErrorCode#NETWORK_TIMEOUT},
 * regardless of the last exception's code. The semantics: "we used to
 * be able to retry; now we can't."
 *
 * // Design Pattern: Strategy
 * // 编程技术: 泛型 / @FunctionalInterface / Lambda(默认方法 orElse 返回匿名内部类 —
 * 不可用 Lambda 因为 SAM 是泛型方法 <T> T execute(Supplier<T>),Lambda 推断不到 T)
 *
 * @since 0.6.0
 * @author 王子豪
 */
@FunctionalInterface
public interface RetryPolicy {

    /**
     * Executes {@code action}, retrying on retryable exceptions until
     * the policy gives up.
     *
     * @param action the work to perform
     * @param <T>    return type
     * @return the action's result on success
     * @throws BookingException with {@code NETWORK_TIMEOUT} if retries exhausted
     */
    <T> T execute(Supplier<T> action);

    /**
     * Chains two policies: try {@code this} first, fall back to
     * {@code next} if {@code this} exhausts.
     *
     * <p>Per ADR-0006 §3.3: enables scenarios like
     * <pre>{@code
     *   policy1.orElse(policy2)  // try 3× exponential, then 1× with different account
     * }</pre>
     *
     * <p>Implemented as an anonymous class (not a lambda) because
     * {@link RetryPolicy}'s SAM is a generic method
     * ({@code <T> T execute(Supplier<T>)}), and Java's lambda type
     * inference cannot bind T for a generic-method SAM without an
     * explicit target type.
     *
     * @param next the fallback policy, invoked once with the same action
     * @return a composite policy
     */
    default RetryPolicy orElse(RetryPolicy next) {
        return new RetryPolicy() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> action) {
                try {
                    return RetryPolicy.this.execute(action);
                } catch (BookingException primaryExhausted) {
                    try {
                        return next.execute(action);
                    } catch (BookingException fallbackExhausted) {
                        // Preserve the primary failure chain; the fallback
                        // failure is still available via getSuppressed().
                        primaryExhausted.addSuppressed(fallbackExhausted);
                        throw primaryExhausted;
                    }
                }
            }
        };
    }
}

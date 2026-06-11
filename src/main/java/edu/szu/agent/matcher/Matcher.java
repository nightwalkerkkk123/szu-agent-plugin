package edu.szu.agent.matcher;

import java.util.Objects;

/**
 * Matcher — predicate over a candidate value.
 *
 * <p>Per ADR-0006 §4.1: generic {@code <T>} {@code @FunctionalInterface}.
 * Per §4.2: four default combinators ({@code and}, {@code or}, {@code negate},
 * {@code andNot}) so business code can compose without
 * {@code CompositeMatcher} wrapper classes.
 *
 * <p>Per §4.7: the empty conjunction is vacuously true ({@code all()} with
 * zero matchers matches everything); the empty disjunction is vacuously
 * false ({@code any()} with zero matchers matches nothing).
 *
 * // Design Pattern: Strategy
 * // 编程技术: @FunctionalInterface + 4 默认方法(Lambda 链式组合)
 *
 * @param <T> the type of value being matched
 * @since 0.1.0
 * @author 王子豪
 */
@FunctionalInterface
public interface Matcher<T> {

    /**
     * Tests whether {@code candidate} matches this matcher's rule.
     *
     * @param candidate the value to test
     * @return {@code true} if the value matches
     */
    boolean matches(T candidate);

    /** Conjunction — both matchers must match. */
    default Matcher<T> and(Matcher<T> other) {
        Objects.requireNonNull(other, "other must not be null");
        return candidate -> this.matches(candidate) && other.matches(candidate);
    }

    /** Disjunction — either matcher matches. */
    default Matcher<T> or(Matcher<T> other) {
        Objects.requireNonNull(other, "other must not be null");
        return candidate -> this.matches(candidate) || other.matches(candidate);
    }

    /** Negation — this matcher does not match. */
    default Matcher<T> negate() {
        return candidate -> !this.matches(candidate);
    }

    /** Anti-conjunction — this matches but the other does not. */
    default Matcher<T> andNot(Matcher<T> other) {
        Objects.requireNonNull(other, "other must not be null");
        return candidate -> this.matches(candidate) && !other.matches(candidate);
    }
}

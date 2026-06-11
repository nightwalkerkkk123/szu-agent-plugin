package edu.szu.agent.matcher;

import java.util.Objects;

/**
 * Abstract base for {@link Matcher} implementations — provides a
 * {@code description} field and a useful {@code toString}.
 *
 * <p>Per ADR-0006 §4.3: 4 concrete matchers inherit from this base; bound
 * to {@code <String>} because P0 only matches strings.
 *
 * // 编程技术: 抽象类(6 技术之一,带 description + toString)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public abstract class AbstractMatcher implements Matcher<String> {

    private final String description;

    protected AbstractMatcher(String description) {
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    /** Human-readable description — useful in logs and {@code toString}. */
    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + description + "}";
    }
}

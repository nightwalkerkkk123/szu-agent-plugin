package edu.szu.agent.matcher;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Factory for common {@link Matcher} presets.
 *
 * <p>Per ADR-0006 §4.7: 6 static methods. {@code all(...)} with zero
 * matchers returns vacuously-true; {@code any(...)} with zero matchers
 * returns vacuously-false.
 *
 * // 编程技术: 工厂模式(静态方法,非设计模式条目;仅做代码组织)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class Matchers {

    private Matchers() {
    }

    /** Exact text match. */
    public static Matcher<String> exact(String target) {
        return new ExactMatcher(target);
    }

    /** Substring match (case-sensitive). */
    public static Matcher<String> contains(String substring) {
        return new ContainsMatcher(substring);
    }

    /** Substring match (case-insensitive). */
    public static Matcher<String> containsIgnoreCase(String substring) {
        return new ContainsMatcher(substring, true);
    }

    /** Regex match — compiles a new {@link Pattern}. */
    public static Matcher<String> regex(String regex) {
        return new RegexMatcher(regex);
    }

    /** Regex match — reuses a precompiled {@link Pattern}. */
    public static Matcher<String> regex(Pattern pattern) {
        return new RegexMatcher(pattern);
    }

    /** Venue index match — see {@link VenueIndexMatcher}. */
    public static Matcher<String> venueIndex(int index) {
        return new VenueIndexMatcher(index);
    }

    /**
     * Conjunction of all matchers. Empty list → vacuously true.
     */
    @SafeVarargs
    public static Matcher<String> all(Matcher<String>... matchers) {
        List<Matcher<String>> snapshot = List.of(matchers);
        return candidate -> snapshot.stream().allMatch(m -> m.matches(candidate));
    }

    /**
     * Disjunction of all matchers. Empty list → vacuously false.
     */
    @SafeVarargs
    public static Matcher<String> any(Matcher<String>... matchers) {
        List<Matcher<String>> snapshot = List.of(matchers);
        return candidate -> snapshot.stream().anyMatch(m -> m.matches(candidate));
    }
}

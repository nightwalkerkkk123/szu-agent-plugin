package edu.szu.agent.matcher;

import java.util.regex.Pattern;

/**
 * Regex matcher — precompiled {@link Pattern} tested against the candidate.
 *
 * <p>Per ADR-0006 §4.6: a constructor accepting a precompiled {@code Pattern}
 * is exposed so callers can share a {@code Pattern} across multiple matchers.
 *
 * <p>Per ADR-0006 §4.8: invalid regex throws {@code IllegalArgumentException}
 * at construction time, not at {@code matches} time.
 *
 * // Design Pattern: Strategy (concrete)
 * // 编程技术: 不可变预编译 Pattern(类加载时一次性编译)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class RegexMatcher extends AbstractMatcher {

    private final Pattern pattern;

    public RegexMatcher(String regex) {
        this(compileOrThrow(regex));
    }

    public RegexMatcher(Pattern pattern) {
        super("regex:" + pattern.pattern());
        this.pattern = pattern;
    }

    @Override
    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        return pattern.matcher(candidate).find();
    }

    private static Pattern compileOrThrow(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid regex: " + regex, e);
        }
    }
}

package edu.szu.agent.matcher;

/**
 * Substring matcher — {@code candidate} contains the target.
 *
 * <p>Optional {@code ignoreCase} flag (default {@code false}) for matching
 * English ehall labels case-insensitively.
 *
 * // Design Pattern: Strategy (concrete)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class ContainsMatcher extends AbstractMatcher {

    private final String substring;
    private final boolean ignoreCase;

    public ContainsMatcher(String substring) {
        this(substring, false);
    }

    public ContainsMatcher(String substring, boolean ignoreCase) {
        super("contains:" + substring + (ignoreCase ? "(ignoreCase)" : ""));
        this.substring = substring;
        this.ignoreCase = ignoreCase;
    }

    @Override
    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        return ignoreCase
            ? candidate.toLowerCase().contains(substring.toLowerCase())
            : candidate.contains(substring);
    }
}

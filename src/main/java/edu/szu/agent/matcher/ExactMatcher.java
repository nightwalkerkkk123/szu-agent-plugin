package edu.szu.agent.matcher;

/**
 * Exact-match matcher — case-sensitive string equality.
 *
 * // Design Pattern: Strategy (concrete)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class ExactMatcher extends AbstractMatcher {

    private final String target;

    public ExactMatcher(String target) {
        super("exact:" + target);
        this.target = target;
    }

    @Override
    public boolean matches(String candidate) {
        return target.equals(candidate);
    }
}

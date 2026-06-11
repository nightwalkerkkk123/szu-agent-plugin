package edu.szu.agent.matcher;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Venue-index matcher — recognizes ehall's 4 venue numbering styles.
 *
 * <p>For a target index {@code N} (1-based), the matcher returns true if
 * the input contains any of:
 * <ul>
 *   <li>{@code N号}      (e.g. "1号")</li>
 *   <li>{@code 第N场}    (e.g. "第1场")</li>
 *   <li>{@code (N)}      (e.g. "(1)")</li>
 *   <li>bare {@code N}    (e.g. "1", with word boundaries to avoid "10" matching "1")</li>
 * </ul>
 *
 * <p>Per ADR-0006 §4.5: 4 precompiled {@code Pattern}s held in a static
 * final list, so each {@code matches} call is O(1) (4 regex tests).
 *
 * // Design Pattern: Strategy (concrete)
 * // 编程技术: 不可变 Pattern 集合(类加载时一次性编译)+ 字面量 Pattern 缓存
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class VenueIndexMatcher extends AbstractMatcher {

    private final int index;
    private final List<Pattern> patterns;

    public VenueIndexMatcher(int index) {
        super("venueIndex:" + index);
        if (index < 1) {
            throw new IllegalArgumentException("VenueIndexMatcher index must be >= 1, got: " + index);
        }
        this.index = index;
        this.patterns = buildPatterns(index);
    }

    private static List<Pattern> buildPatterns(int n) {
        return List.of(
            Pattern.compile("\\b" + n + "号\\b"),
            Pattern.compile("第" + n + "场"),
            Pattern.compile("\\(" + n + "\\)"),
            Pattern.compile("\\b" + n + "\\b")
        );
    }

    @Override
    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        for (Pattern p : patterns) {
            if (p.matcher(candidate).find()) {
                return true;
            }
        }
        return false;
    }

    /** Exposed for testing — the 1-based index this matcher targets. */
    public int index() {
        return index;
    }
}

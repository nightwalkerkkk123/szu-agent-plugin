package edu.szu.agent.domain;

import java.util.List;
import java.util.Objects;

/**
 * A set of weeks (周次) in which a course meets.
 *
 * <p>Encoded as a sorted, distinct list of week numbers plus the original
 * raw text. Supports queries like "is week 8 in this range?" for filtering
 * the current week's courses.
 *
 * <p>Use {@link #parse(String)} to construct from ehall's textual format.
 * See {@code WeekRangeParser} for the supported syntax.
 *
 * <p>Immutable value object.
 *
 * // 编程技术: record(不可变值对象) + 不可变 List
 *
 * @param weeks sorted, distinct list of week numbers
 * @param raw   the original ehall text, e.g. {@code "1-17周"} or {@code "1-8,10-17周(单)"}
 * @since 0.6.0
 * @author 王子豪
 */
public record WeekRange(List<Integer> weeks, String raw) {

    public WeekRange {
        Objects.requireNonNull(weeks, "weeks");
        weeks = List.copyOf(weeks);
        Objects.requireNonNull(raw, "raw");
    }

    /**
     * Parses an ehall week-expression string into a {@code WeekRange}.
     *
     * <p>Supported formats (see {@code WeekRangeParser} for details):
     * <ul>
     *   <li>{@code "1-17周"}        — contiguous range</li>
     *   <li>{@code "1-8,10-17周"}   — multi-segment with gaps</li>
     *   <li>{@code "1-17周(单)"}    — odd weeks only</li>
     *   <li>{@code "1-17周(双)"}    — even weeks only</li>
     * </ul>
     *
     * @param s the raw text (without the trailing {@code 周} required)
     * @return a parsed range; returns a single-week range for digits-only input
     * @throws IllegalArgumentException if the input cannot be parsed
     * @since 0.6.0
     */
    public static WeekRange parse(String s) {
        return edu.szu.agent.client.schedule.WeekRangeParser.parse(s);
    }

    /**
     * Checks whether this range contains the given week number.
     *
     * @param week 1-based week number
     * @return {@code true} if {@code week} is in {@link #weeks()}
     * @since 0.6.0
     */
    public boolean contains(int week) {
        return weeks.contains(week);
    }

    /**
     * Returns a compact display form (e.g. {@code "1-17"} or {@code "1-8,10-17"}).
     *
     * <p>If the weeks are contiguous, returns {@code "first-last"}. Otherwise
     * joins segments with commas, each segment as {@code "first-last"} when
     * length &gt; 1, else as a single number.
     *
     * @return the compact display string
     * @since 0.6.0
     */
    public String compact() {
        if (weeks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < weeks.size()) {
            int start = weeks.get(i);
            int end = start;
            while (i + 1 < weeks.size() && weeks.get(i + 1) == end + 1) {
                i++;
                end = weeks.get(i);
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            if (start == end) {
                sb.append(start);
            } else {
                sb.append(start).append('-').append(end);
            }
            i++;
        }
        return sb.toString();
    }
}

package edu.szu.agent.client.schedule;

import edu.szu.agent.domain.WeekRange;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the ehall week-expression text into a {@link WeekRange}.
 *
 * <p>Supported syntax (after the trailing {@code 周} has been stripped):
 * <ul>
 *   <li>{@code "1-17"}         — contiguous range</li>
 *   <li>{@code "1-8,10-17"}    — multi-segment with gaps</li>
 *   <li>{@code "1-17(单)"}     — odd weeks only</li>
 *   <li>{@code "1-17(双)"}     — even weeks only</li>
 *   <li>{@code "5"}            — single week</li>
 * </ul>
 *
 * <p>The {@code 周} suffix is optional — the parser accepts both
 * {@code "1-17周"} and {@code "1-17"}.
 *
 * // 编程技术: 不可变 + 正则表达式 + 静态工具类
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class WeekRangeParser {

    private static final Pattern SEGMENT = Pattern.compile("(\\d+)(?:-(\\d+))?");

    private WeekRangeParser() {
    }

    /**
     * Parses the given raw text and returns a {@link WeekRange}.
     *
     * @param raw ehall text such as {@code "1-17周"} or {@code "1-8,10-17周(单)"}
     * @return the parsed range
     * @throws IllegalArgumentException if the input is blank or cannot be parsed
     * @since 0.1.0
     */
    public static WeekRange parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("week range text is null");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("week range text is blank");
        }

        // Detect odd/even modifier on the overall text
        boolean odd = false;
        boolean even = false;
        String body = trimmed;
        if (body.endsWith("(单)") || body.endsWith("单周") || body.endsWith("(奇)")) {
            odd = true;
            body = body.substring(0, body.length() - 3);
        } else if (body.endsWith("(双)") || body.endsWith("双周") || body.endsWith("(偶)")) {
            even = true;
            body = body.substring(0, body.length() - 3);
        }
        if (body.endsWith("周")) {
            body = body.substring(0, body.length() - 1);
        }
        body = body.trim();

        List<Integer> weeks = new ArrayList<>();
        for (String segment : body.split(",")) {
            String seg = segment.trim();
            if (seg.isEmpty()) {
                continue;
            }
            // Each segment may carry a trailing 周 (e.g. "1-8周" in "1-8周,10-17周")
            if (seg.endsWith("周")) {
                seg = seg.substring(0, seg.length() - 1);
            }
            Matcher m = SEGMENT.matcher(seg);
            if (!m.matches()) {
                throw new IllegalArgumentException("invalid week segment: " + seg);
            }
            int start = Integer.parseInt(m.group(1));
            int end = m.group(2) != null ? Integer.parseInt(m.group(2)) : start;
            if (start < 1 || end < start) {
                throw new IllegalArgumentException(
                    "invalid week range: " + start + "-" + end);
            }
            for (int w = start; w <= end; w++) {
                if (odd && w % 2 == 0) {
                    continue;
                }
                if (even && w % 2 == 1) {
                    continue;
                }
                if (!weeks.contains(w)) {
                    weeks.add(w);
                }
            }
        }
        if (weeks.isEmpty()) {
            throw new IllegalArgumentException("no weeks parsed from: " + raw);
        }
        java.util.Collections.sort(weeks);
        return new WeekRange(weeks, raw.trim());
    }
}

package edu.szu.agent.client.calendar;

import edu.szu.agent.domain.calendar.AcademicEvent;
import edu.szu.agent.domain.calendar.AcademicEventType;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort parser for the SZU official calendar page
 * ({@code https://www.szu.edu.cn/xxgk/xl.htm}). The page currently renders
 * the calendar as PNG images, so this parser is permissive: it scans the
 * HTML for any recognizable date + description text and emits
 * {@link AcademicEvent}s.
 *
 * <p>If the page contains no parseable text (the current real-world case),
 * this returns an empty list — the resilient wrapper then falls back to the
 * static 2025-2026 spring data. This is the desired behavior: the parser
 * is forward-compatible with any future HTML-ification of the page, while
 * the static fallback keeps the Skill always available.
 *
 * // 编程技术: 正则表达式 / 不可变 List.copyOf
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class CalendarPageParser {

    /** "M月D日" or "M/D" — Chinese short date. */
    private static final Pattern SHORT_DATE = Pattern.compile(
        "(\\d{1,2})\\s*[月/]\\s*(\\d{1,2})\\s*[日号]?");

    /** "2026年3月4日" — absolute Chinese date with year. */
    private static final Pattern LONG_DATE = Pattern.compile(
        "(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?");

    /** Default year for short dates (current SZU spring semester). */
    static final int DEFAULT_YEAR = 2026;

    /** Tag used for events parsed from the official page. */
    static final String PAGE_SEMESTER_TAG = "OFFICIAL-PAGE";

    private CalendarPageParser() {
    }

    /**
     * Parse the calendar page HTML. Returns an empty list if no parseable
     * date events are found.
     *
     * @param html raw HTML body; may be null or empty
     * @return list of events (never null, possibly empty)
     */
    public static List<AcademicEvent> parse(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        // Strip HTML tags to plain text — regex is fine for our purpose
        // since we only need to find date substrings.
        String text = html
            .replaceAll("(?is)<script.*?</script>", " ")
            .replaceAll("(?is)<style.*?</style>", " ")
            .replaceAll("(?is)<[^>]+>", " ");

        List<AcademicEvent> events = new ArrayList<>();
        for (String segment : text.split("[\\n\\r;。]+")) {
            String trimmed = segment.trim();
            if (trimmed.length() < 6) {
                continue;
            }
            AcademicEvent ev = tryParseEvent(trimmed);
            if (ev != null && !isDuplicate(events, ev)) {
                events.add(ev);
            }
        }
        return List.copyOf(events);
    }

    private static AcademicEvent tryParseEvent(String segment) {
        Matcher longM = LONG_DATE.matcher(segment);
        if (longM.find()) {
            int year = Integer.parseInt(longM.group(1));
            int month = Integer.parseInt(longM.group(2));
            int day = Integer.parseInt(longM.group(3));
            String description = segment.substring(longM.end()).trim();
            if (description.length() < 3) {
                return null;
            }
            return AcademicEvent.of(
                LocalDate.of(year, month, day),
                classify(description),
                truncate(description, 80),
                PAGE_SEMESTER_TAG);
        }
        Matcher shortM = SHORT_DATE.matcher(segment);
        if (shortM.find()) {
            int month = Integer.parseInt(shortM.group(1));
            int day = Integer.parseInt(shortM.group(2));
            String description = segment.substring(shortM.end()).trim();
            if (description.length() < 3) {
                return null;
            }
            return AcademicEvent.of(
                MonthDay.of(month, day).atYear(DEFAULT_YEAR),
                classify(description),
                truncate(description, 80),
                PAGE_SEMESTER_TAG);
        }
        return null;
    }

    /**
     * Heuristic event-type classification from the description text. Mirrors
     * the categories used in the static data so the two sources look
     * consistent to downstream consumers.
     */
    private static AcademicEventType classify(String description) {
        String d = description.toLowerCase();
        if (d.contains("考试") || d.contains("缓考") || d.contains("补考")) {
            return AcademicEventType.EXAM_WEEK;
        }
        if (d.contains("暑假") || d.contains("寒假") || d.contains("假")) {
            return AcademicEventType.BREAK;
        }
        if (d.contains("节") || d.contains("日") || d.contains("典礼")) {
            return AcademicEventType.HOLIDAY;
        }
        return AcademicEventType.SEMESTER_START;
    }

    private static boolean isDuplicate(List<AcademicEvent> existing, AcademicEvent candidate) {
        for (AcademicEvent ev : existing) {
            if (ev.date().equals(candidate.date())
                && ev.description().equals(candidate.description())) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
package edu.szu.agent.client.schedule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.schedule.WeekRangeParser;
import edu.szu.agent.domain.CourseEntry;
import edu.szu.agent.domain.Period;
import edu.szu.agent.domain.WeekRange;
import edu.szu.agent.domain.Weekday;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the SZU ehall schedule grid into {@link CourseEntry} records.
 *
 * <p>The page is an 8×8 HTML table ({@code table.wut_table}). Cells with
 * courses contain a {@code div.mtt_arrange_item} carrying course name,
 * teacher, and a composite {@code roomText} string. This extractor:
 * <ol>
 *   <li>Injects a JS script that walks the grid, collects raw cell data,
 *       and returns a JSON array via {@code JSON.stringify}.</li>
 *   <li>Deserializes the JSON into {@link RawCourse} via Jackson.</li>
 *   <li>Parses each raw cell into a {@link CourseEntry} by:
 *       <ul>
 *         <li>Splitting {@code roomText} on commas into
 *             {@code weeks / weekday / period / room} segments.</li>
 *         <li>Resolving weekday via {@link Weekday#of(int)}.</li>
 *         <li>Resolving period via {@link PeriodMapping}.</li>
 *         <li>Parsing the week expression via {@link WeekRangeParser}.</li>
 *         <li>Extracting the teaching section from the {@code [NN]} suffix.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * // Design Pattern: Strategy (selectable extraction implementation)
 * // 编程技术: Lambda / Jackson 反序列化 / 正则表达式
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class ScheduleListExtractor {

    private static final Logger log = LoggerFactory.getLogger(ScheduleListExtractor.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    static final String SEL_GRID_CELL = "td[data-role=\"item\"]";
    static final String SEL_ARRANGE_ITEM = ".mtt_arrange_item";
    static final String SEL_KCMC = ".mtt_item_kcmc";
    static final String SEL_JXBMC = ".mtt_item_jxbmc";
    static final String SEL_ROOM = ".mtt_item_room";
    static final String SEL_TZK = ".mtt_item_tzkcicon";

    /** Matches {@code "课程名[NN]"} — course name with optional section. */
    private static final Pattern COURSE_SECTION =
        Pattern.compile("^(?<name>.+?)(?:\\[(?<section>\\d+)\\])?\\s*$");

    private ScheduleListExtractor() {
        // utility class
    }

    /**
     * Extracts course entries from the ehall schedule page.
     *
     * @param browser the browser adapter, currently on the schedule page
     * @return a non-null, immutable list of course entries (may be empty)
     * @throws BookingException if extraction fails or returns invalid data
     * @since 0.1.0
     */
    public static List<CourseEntry> extract(BrowserLifecycle browser) {
        Objects.requireNonNull(browser, "browser");

        String rawJson = browser.evaluate(buildExtractionScript());
        if (rawJson == null || rawJson.isBlank()) {
            throw new BookingException(ErrorCode.SCHEDULE_PAGE_LOAD_FAILED,
                "schedule extraction returned empty result");
        }

        List<RawCourse> raws;
        try {
            raws = JSON.readValue(rawJson, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new BookingException(ErrorCode.SCHEDULE_PARSE_FAILED,
                "failed to parse schedule JSON: " + e.getMessage());
        }
        if (raws == null) {
            return List.of();
        }

        List<CourseEntry> out = new ArrayList<>(raws.size());
        for (RawCourse r : raws) {
            out.add(toCourseEntry(r));
        }
        log.info("Extracted {} course entries", out.size());
        return List.copyOf(out);
    }

    /**
     * Builds the JavaScript that extracts structured data from the ehall grid.
     */
    public static String buildExtractionScript() {
        return """
            (function() {
              var cells = Array.from(document.querySelectorAll('%s'));
              var result = [];
              cells.forEach(function(td) {
                var item = td.querySelector('%s');
                if (!item) return;
                var kcmc = item.querySelector('%s');
                var jxbmc = item.querySelector('%s');
                var room = item.querySelector('%s');
                var tzk = item.querySelector('%s');
                result.push({
                  courseName: kcmc ? kcmc.textContent.trim() : null,
                  teacher:    jxbmc ? jxbmc.textContent.trim() : null,
                  roomText:   room ? room.textContent.trim() : null,
                  isAdjusted: tzk ? (tzk.textContent.trim().length > 0) : false,
                  weekday:    parseInt(td.dataset.week, 10),
                  beginUnit:  parseInt(td.dataset.beginUnit, 10),
                  endUnit:    parseInt(td.dataset.endUnit, 10)
                });
              });
              return JSON.stringify(result);
            })()
            """.formatted(
            SEL_GRID_CELL,
            SEL_ARRANGE_ITEM,
            SEL_KCMC,
            SEL_JXBMC,
            SEL_ROOM,
            SEL_TZK
        ).replaceAll("\\R\\s*", " ");
    }

    private static CourseEntry toCourseEntry(RawCourse r) {
        Weekday weekday;
        try {
            weekday = Weekday.of(r.weekday);
        } catch (IllegalArgumentException e) {
            throw new BookingException(ErrorCode.SCHEDULE_PARSE_FAILED,
                "invalid weekday code: " + r.weekday);
        }
        Period period;
        try {
            period = Period.of(r.beginUnit, r.endUnit);
        } catch (IllegalArgumentException e) {
            throw new BookingException(ErrorCode.SCHEDULE_PARSE_FAILED,
                "unknown period for " + r.beginUnit + "-" + r.endUnit);
        }
        ParsedCourse pc = parseCourseName(r.courseName);
        ParsedRoom pr = parseRoom(r.roomText);
        return new CourseEntry(
            pc.name,
            pc.section,
            r.teacher,
            pr.room,
            weekday,
            period,
            pr.weekRange,
            r.isAdjusted
        );
    }

    private static ParsedCourse parseCourseName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BookingException(ErrorCode.SCHEDULE_PARSE_FAILED,
                "course name is empty");
        }
        String trimmed = raw.trim();
        Matcher m = COURSE_SECTION.matcher(trimmed);
        if (m.matches() && m.group("section") != null) {
            return new ParsedCourse(m.group("name").trim(), m.group("section"));
        }
        return new ParsedCourse(trimmed, null);
    }

    private static ParsedRoom parseRoom(String roomText) {
        if (roomText == null || roomText.isBlank()) {
            throw new BookingException(ErrorCode.SCHEDULE_PARSE_FAILED,
                "room text is empty");
        }
        // "1-17周,星期3,1-2节,致理楼L1-601"
        String[] parts = roomText.split(",", -1);
        if (parts.length < 4) {
            throw new BookingException(ErrorCode.SCHEDULE_PARSE_FAILED,
                "room text has fewer than 4 segments: " + roomText);
        }
        WeekRange weekRange = WeekRangeParser.parse(parts[0]);
        String room = parts[3].trim();
        return new ParsedRoom(room, weekRange);
    }

    /** Raw cell data captured by the JS extraction script. */
    public static final class RawCourse {
        public String courseName;
        public String teacher;
        public String roomText;
        public boolean isAdjusted;
        public int weekday;
        public int beginUnit;
        public int endUnit;
    }

    private static final class ParsedCourse {
        final String name;
        final String section;

        ParsedCourse(String name, String section) {
            this.name = name;
            this.section = section;
        }
    }

    private static final class ParsedRoom {
        final String room;
        final WeekRange weekRange;

        ParsedRoom(String room, WeekRange weekRange) {
            this.room = room;
            this.weekRange = weekRange;
        }
    }
}

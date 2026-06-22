package edu.szu.agent.client.exam;

import edu.szu.agent.domain.exam.ExamSchedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for the static exam schedule HTML snapshot.
 *
 * <p>Extracts exam entries from the "我的考试安排" section.
 * The snapshot contains a table-like structure with columns:
 * 日期 | 星期 | 课程名称 | 课程编号 | 考试时间 | 地点 | 监考教师
 *
 * // 编程技术: 正则表达式 / Lambda
 *
 * @since 0.4.0
 * @author 王子豪
 */
public final class ExamListParser {

    private static final Pattern ROW_PATTERN = Pattern.compile(
        "<tr[^>]*>\\s*" +
        "<td[^>]*>([^<]*)</td>\\s*" +       // date
        "<td[^>]*>([^<]*)</td>\\s*" +       // weekday
        "<td[^>]*>([^<]*)</td>\\s*" +       // course name
        "<td[^>]*>([^<]*)</td>\\s*" +       // course code
        "<td[^>]*>([^<]*)</td>\\s*" +       // exam time
        "<td[^>]*>([^<]*)</td>\\s*" +       // venue
        "<td[^>]*>([^<]*)</td>\\s*" +       // invigilator
        "</tr>",
        Pattern.DOTALL
    );

    private static final Pattern TIME_PATTERN = Pattern.compile(
        "(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2}:\\d{2})-(\\d{1,2}:\\d{2})"
    );

    private static final Pattern COURSE_CODE_PATTERN = Pattern.compile(
        "\\[(\\d+)\\]"
    );

    private ExamListParser() {}

    /**
     * Parse exam schedules from HTML snapshot.
     *
     * @param html       the static HTML snapshot
     * @param defaultYear the year to use when parsing dates
     * @return list of parsed exam schedules
     */
    public static List<ExamSchedule> parse(String html, int defaultYear) {
        List<ExamSchedule> results = new ArrayList<>();
        Matcher rowMatcher = ROW_PATTERN.matcher(html);

        while (rowMatcher.find()) {
            String date = strip(rowMatcher.group(1));
            String weekday = strip(rowMatcher.group(2));
            String courseName = strip(rowMatcher.group(3));
            String rawCourseCode = strip(rowMatcher.group(4));
            String examTime = strip(rowMatcher.group(5));
            String venue = strip(rowMatcher.group(6));
            String invigilator = strip(rowMatcher.group(7));

            // Extract course code from brackets
            String courseCode = extractCourseCode(rawCourseCode);

            // Parse exam date and time
            Matcher timeMatcher = TIME_PATTERN.matcher(examTime);
            if (timeMatcher.find()) {
                String yearText = timeMatcher.group(1);
                int year = (yearText != null) ? Integer.parseInt(yearText) : defaultYear;
                int month = Integer.parseInt(timeMatcher.group(2));
                int day = Integer.parseInt(timeMatcher.group(3));
                int startHour = Integer.parseInt(timeMatcher.group(4).split(":")[0]);
                int startMin = Integer.parseInt(timeMatcher.group(4).split(":")[1]);
                int endHour = Integer.parseInt(timeMatcher.group(5).split(":")[0]);
                int endMin = Integer.parseInt(timeMatcher.group(5).split(":")[1]);

                LocalDate examDate = LocalDate.of(year, month, day);
                LocalTime startTime = LocalTime.of(startHour, startMin);
                LocalTime endTime = LocalTime.of(endHour, endMin);

                results.add(new ExamSchedule(
                    date, weekday, courseName, courseCode,
                    examDate, startTime, endTime, venue, invigilator
                ));
            }
        }

        return results;
    }

    private static String strip(String s) {
        return s == null ? "" : s.trim();
    }

    private static String extractCourseCode(String raw) {
        Matcher m = COURSE_CODE_PATTERN.matcher(raw);
        return m.find() ? m.group(1) : raw;
    }
}
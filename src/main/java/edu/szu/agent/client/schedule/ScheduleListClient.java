package edu.szu.agent.client.schedule;

import edu.szu.agent.domain.CourseEntry;
import edu.szu.agent.domain.Period;
import edu.szu.agent.domain.ScheduleListResult;
import edu.szu.agent.domain.WeekRange;
import edu.szu.agent.domain.Weekday;
import edu.szu.agent.error.ErrorCode;

import java.time.Instant;
import java.util.List;

/**
 * Schedule list client — static MVP.
 *
 * <p>Returns hardcoded course entries for the current semester.
 * A future version can replace this with an HTTP fetch through
 * {@code PlaywrightBrowserAdapter} after CAS login.
 *
 * // 设计模式: Simple Factory (static data factory)
 * // 编程技术: Lambda
 *
 * @since 0.4.0
 * @author 王子豪
 */
public class ScheduleListClient {

    private final List<CourseEntry> courses;

    /**
     * Default constructor — returns embedded static courses.
     */
    public ScheduleListClient() {
        this(STATIC_COURSES);
    }

    /**
     * Test constructor — inject custom course list.
     */
    public ScheduleListClient(List<CourseEntry> courses) {
        this.courses = List.copyOf(courses);
    }

    /**
     * Returns schedule from static data.
     */
    public ScheduleListResult list() {
        try {
            return new ScheduleListResult.Success(courses, Instant.now());
        } catch (Exception e) {
            return new ScheduleListResult.Failure(
                ErrorCode.SCHEDULE_PAGE_LOAD_FAILED,
                "Failed to build schedule: " + e.getMessage()
            );
        }
    }

    // Design Pattern: Simple Factory — static course catalog
    // 编程技术: 静态初始化块 / record
    private static final List<CourseEntry> STATIC_COURSES = buildStaticCourses();

    private static List<CourseEntry> buildStaticCourses() {
        record Triple(String courseName, String section, String teacher,
                      String room, int weekday,
                      int beginUnit, int endUnit,
                      String startTime, String endTime,
                      String weekRange) {}
        Triple[] entries = {
            // 操作系统 — 周三第1-2节 + 3-4节
            new Triple("操作系统", "05", "杜智华", "致理楼L1-601", 3, 1, 2, "08:00", "09:40", "1-17"),
            new Triple("操作系统", "05", "杜智华", "致腾楼240",   3, 3, 4, "10:00", "11:40", "1-17"),
            // 多媒体系统导论 — 周二第7-8节 + 9-10节
            new Triple("多媒体系统导论", "02", "方山城", "致理楼L1-711", 2, 7,  8,  "14:00", "15:40", "1-17"),
            new Triple("多媒体系统导论", "02", "方山城", "致腾楼240",   2, 9, 10, "16:00", "17:40", "1-17"),
            // 计算机游戏开发 — 周三第7-8节 + 9-10节
            new Triple("计算机游戏开发", "01", "储颖", "致理楼L1-706", 3, 7,  8,  "14:00", "15:40", "1-17"),
            new Triple("计算机游戏开发", "01", "储颖", "致腾楼324",   3, 9, 10, "16:00", "17:40", "1-17"),
            // 面向对象高级编程专题 — 周三第11-12节 + 13-14节
            new Triple("面向对象高级编程专题", "01", "徐鹏飞", "致理楼L1-201", 3, 11, 12, "19:00", "20:40", "1-17"),
            new Triple("面向对象高级编程专题", "01", "徐鹏飞", "致腾楼328",   3, 13, 14, "21:00", "22:40", "1-17"),
        };
        return java.util.Arrays.stream(entries).map(t -> new CourseEntry(
            t.courseName(), t.section(), t.teacher(), t.room(),
            Weekday.of(t.weekday()),
            new Period(t.beginUnit(), t.endUnit(),
                      java.time.LocalTime.parse(t.startTime()),
                      java.time.LocalTime.parse(t.endTime())),
            WeekRange.parse(t.weekRange()),
            false
        )).toList();
    }
}
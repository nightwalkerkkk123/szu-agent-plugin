package edu.szu.agent.domain.exam;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A single exam schedule entry from SZU academic affairs system.
 *
 * <p>Corresponds to the exam arrangement page (考试安排) with fields:
 * date, weekday, course name, course code, exam time, venue, and invigilator.
 *
 * // 编程技术: record
 *
 * @param date         exam date (e.g. 7月14日)
 * @param weekday      day of week (e.g. 星期二)
 * @param courseName   course name (e.g. 操作系统)
 * @param courseCode   course code in brackets (e.g. [1500110002])
 * @param examDate     full exam date (year-month-day)
 * @param startTime    exam start time
 * @param endTime      exam end time
 * @param venue        exam venue (e.g.致理楼L1-601)
 * @param invigilator  invigilator name (e.g. 杜智华)
 * @since 0.4.0
 * @author 王子豪
 */
public record ExamSchedule(
    String date,
    String weekday,
    String courseName,
    String courseCode,
    LocalDate examDate,
    LocalTime startTime,
    LocalTime endTime,
    String venue,
    String invigilator
) {
}
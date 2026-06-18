package edu.szu.agent.domain;

/**
 * A single occurrence of a course on the SZU schedule grid.
 *
 * <p>One course that meets on multiple weekdays (e.g. Mon/Wed) is represented
 * as multiple {@code CourseEntry} instances — one per grid cell. Callers that
 * need a course-level view should group by {@code (courseName, section)}.
 *
 * <p>Immutable value object. Fields are extracted from the ehall DOM:
 * <ul>
 *   <li>{@code courseName} — from {@code .mtt_item_kcmc}, with the trailing
 *       {@code [section]} stripped</li>
 *   <li>{@code section} — from the {@code [section]} suffix of {@code .mtt_item_kcmc},
 *       or {@code null} if absent</li>
 *   <li>{@code teacher} — from {@code .mtt_item_jxbmc}</li>
 *   <li>{@code room} — from {@code .mtt_item_room}, after stripping the
 *       {@code "周次,星期,节次,"} prefix</li>
 *   <li>{@code weekday} / {@code period} / {@code weekRange} — parsed from
 *       {@code data-week} / {@code data-begin-unit} / {@code data-end-unit} /
 *       the {@code roomText}</li>
 *   <li>{@code isAdjusted} — {@code true} when {@code .mtt_item_tzkcicon}
 *       carries non-empty text (调/停课 marker)</li>
 * </ul>
 *
 * // 编程技术: record(不可变值对象)
 *
 * @param courseName course name without the section suffix
 * @param section    teaching section number, e.g. {@code "05"}, or {@code null}
 * @param teacher    teacher name
 * @param room       classroom text, e.g. {@code "致理楼L1-601"}
 * @param weekday    day of the week
 * @param period     period (节次) with mapped clock times
 * @param weekRange  weeks in which this entry meets
 * @param isAdjusted {@code true} if the entry is marked as adjusted/cancelled
 * @since 0.1.0
 * @author 王子豪
 */
public record CourseEntry(
    String courseName,
    String section,
    String teacher,
    String room,
    Weekday weekday,
    Period period,
    WeekRange weekRange,
    boolean isAdjusted
) {
}

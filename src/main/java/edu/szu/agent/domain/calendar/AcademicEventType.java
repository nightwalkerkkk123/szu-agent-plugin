package edu.szu.agent.domain.calendar;

/**
 * Type classification for a single academic-calendar event.
 *
 * <p>Per PRD §3.2.4, the calendar service exposes four coarse event
 * categories.  New types can be added without breaking existing callers
 * because the field is a stable enum.
 *
 * // 编程技术: 枚举
 *
 * @since 0.3.0
 * @author 王子豪
 */
public enum AcademicEventType {
    /** 学期开始 / 学生报到注册 / 开学. */
    SEMESTER_START,
    /** 节假日 / 法定休假日 / 社会实践周. */
    HOLIDAY,
    /** 考试周(期中/期末/补考/缓考). */
    EXAM_WEEK,
    /** 长假 / 暑假 / 寒假. */
    BREAK
}

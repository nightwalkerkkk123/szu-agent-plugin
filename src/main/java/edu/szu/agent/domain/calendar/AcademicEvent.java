package edu.szu.agent.domain.calendar;

import java.time.LocalDate;

/**
 * A single event on the SZU academic calendar.
 *
 * <p>Per PRD §3.2.4, each event carries a concrete date, a type, a
 * human-readable description, the semester it belongs to, and the
 * week-of-term when applicable.  For range events the task emits one
 * record per day so that callers can filter by exact date.
 *
 * // 编程技术: record / 泛型
 *
 * @param date        the event date (ISO-8601, single day)
 * @param type        event classification
 * @param description human-readable description
 * @param semester    semester tag, e.g. "2025-2026-SPRING"
 * @param weekOfTerm  1-based week of term, or {@code null} if not applicable
 * @since 0.6.0
 * @author 王子豪
 */
public record AcademicEvent(LocalDate date,
                            AcademicEventType type,
                            String description,
                            String semester,
                            Integer weekOfTerm) {

    /**
     * Convenience factory for events whose week-of-term is unknown.
     */
    public static AcademicEvent of(LocalDate date,
                                   AcademicEventType type,
                                   String description,
                                   String semester) {
        return new AcademicEvent(date, type, description, semester, null);
    }
}

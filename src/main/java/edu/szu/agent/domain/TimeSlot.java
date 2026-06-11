package edu.szu.agent.domain;

/**
 * TimeSlot record — start and end times in {@code HH:mm} format.
 *
 * <p>Per ADR-0006 §一.5: only null check + chronological order enforced.
 * Business rules ("must be 1 hour", "must be on the hour") are intentionally
 * NOT enforced here because they may change; let the upper layer decide.
 *
 * <p>Chronological order is checked via {@link String#compareTo} which works
 * for zero-padded {@code HH:mm} strings ("19:00" < "20:00").
 *
 * // 编程技术: Record(Java 16+)
 *
 * @param start start time in {@code HH:mm} format
 * @param end   end time in {@code HH:mm} format, must be strictly after {@code start}
 * @since 0.1.0
 * @author 王子豪
 */
public record TimeSlot(String start, String end) {

    /**
     * Compact constructor with non-null and chronological-order validation.
     *
     * @throws IllegalArgumentException if start/end is null, or start &gt;= end
     */
    public TimeSlot {
        if (start == null) {
            throw new IllegalArgumentException("TimeSlot.start must not be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("TimeSlot.end must not be null");
        }
        if (start.compareTo(end) >= 0) {
            throw new IllegalArgumentException(
                "TimeSlot.start must be strictly before end: start=" + start + ", end=" + end);
        }
    }
}

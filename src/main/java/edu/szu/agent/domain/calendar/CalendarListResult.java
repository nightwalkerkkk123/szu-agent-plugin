package edu.szu.agent.domain.calendar;

import edu.szu.agent.error.ErrorCode;

import java.time.Instant;
import java.util.List;

/**
 * Sealed result for {@code calendar_get}: either a {@link Success} carrying
 * the parsed events + snapshot timestamp, or a {@link Failure} carrying an
 * {@link ErrorCode} and message. Mirrors {@code NoticeListResult} / {@code
 * ScheduleListResult} so all three P1 list-fetch Skills share the same
 * caller-facing shape.
 *
 * // 编程技术: 密封类型 / record / 模式匹配
 *
 * @since 0.4.0
 * @author 王子豪
 */
public sealed interface CalendarListResult {

    /**
     * @param events parsed events
     * @param snapshotAt when the snapshot was taken (fetch time or fallback time)
     */
    record Success(List<AcademicEvent> events, Instant snapshotAt) implements CalendarListResult {
        public Success {
            events = List.copyOf(events);
        }
    }

    /**
     * @param code canonical error code
     * @param message human-readable explanation
     */
    record Failure(ErrorCode code, String message) implements CalendarListResult {
    }
}
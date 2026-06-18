package edu.szu.agent.domain;

import edu.szu.agent.error.ErrorCode;

import java.time.Instant;
import java.util.List;

/**
 * Result type for the ehall schedule list query.
 *
 * <p>Sealed closed hierarchy mirroring {@link HomeworkListResult} and
 * {@link edu.szu.agent.domain.BookingResult}. The CLI / Skill layer
 * pattern-matches on this to produce the JSON envelope.
 *
 * // 编程技术: sealed interface + record
 *
 * @since 0.1.0
 * @author 王子豪
 */
public sealed interface ScheduleListResult {

    /**
     * Successful query — carries the list of course entries and a snapshot
     * timestamp indicating when the page was scraped.
     *
     * @param courses     list of course entries (may be empty)
     * @param snapshotAt  UTC instant the schedule was scraped
     */
    record Success(List<CourseEntry> courses, Instant snapshotAt) implements ScheduleListResult {
        public Success {
            courses = List.copyOf(courses);
            snapshotAt = snapshotAt == null ? Instant.now() : snapshotAt;
        }
    }

    /**
     * Failed query — carries the error code and human-readable message.
     *
     * @param code    the error code
     * @param message human-readable failure detail
     */
    record Failure(ErrorCode code, String message) implements ScheduleListResult {
    }
}

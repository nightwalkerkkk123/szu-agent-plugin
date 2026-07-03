package edu.szu.agent.domain.notice;

import edu.szu.agent.error.ErrorCode;

import java.time.Instant;
import java.util.List;

/**
 * Result type for the {@code notice_list} Skill.
 *
 * <p>Sealed closed hierarchy mirroring {@link edu.szu.agent.domain.ScheduleListResult}.
 * The CLI / Skill layer pattern-matches on this to produce the JSON envelope
 * and map error codes to exit codes.
 *
 * // 编程技术: sealed interface + record
 *
 * @since 0.6.0
 * @author 王子豪
 */
public sealed interface NoticeListResult {

    /**
     * Successful query — carries the parsed notices and a snapshot timestamp.
     *
     * @param notices    parsed notice list (may be empty)
     * @param snapshotAt UTC instant the source HTML was scraped
     */
    record Success(List<Notice> notices, Instant snapshotAt) implements NoticeListResult {
        public Success {
            notices = List.copyOf(notices);
            snapshotAt = snapshotAt == null ? Instant.now() : snapshotAt;
        }
    }

    /**
     * Failed query — carries the error code and human-readable message.
     *
     * @param code    the error code
     * @param message human-readable failure detail
     */
    record Failure(ErrorCode code, String message) implements NoticeListResult {
    }
}

package edu.szu.agent.domain;

import edu.szu.agent.error.ErrorCode;

import java.util.List;

/**
 * Result type for the Chaoxing homework list query.
 *
 * <p>Sealed closed hierarchy mirroring {@link BookingResult}.
 * The CLI / Skill layer pattern-matches on this to produce the JSON envelope.
 *
 * // 编程技术: sealed interface + record
 *
 * @since 0.6.0
 * @author 王子豪
 */
public sealed interface HomeworkListResult {

    /**
     * Successful query — carries the list of homework items.
     *
     * @param homeworks list of homework items (may be empty)
     */
    record Success(List<Homework> homeworks) implements HomeworkListResult {
    }

    /**
     * Failed query — carries the error code and message.
     *
     * @param code    the error code
     * @param message human-readable failure detail
     */
    record Failure(ErrorCode code, String message) implements HomeworkListResult {
    }
}

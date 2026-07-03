package edu.szu.agent.domain;

import edu.szu.agent.error.ErrorCode;

import java.util.List;

/**
 * Outcome of a {@code homework download} operation.
 *
 * <p>Sealed to give callers a finite set of cases to handle:
 * <ul>
 *   <li>{@link Success} — at least one attachment downloaded</li>
 *   <li>{@link Empty} — homework has no attachments (NOT an error,
 *       Agents should not retry)</li>
 *   <li>{@link Failure} — operation failed (Agent may retry depending
 *       on the {@link ErrorCode#isRetryable()} flag)</li>
 * </ul>
 *
 * <p>// 编程技术: sealed interface + record
 *
 * @since 0.6.0
 * @author 王子豪
 */
public sealed interface HomeworkDownloadResult
    permits HomeworkDownloadResult.Success, HomeworkDownloadResult.Empty,
            HomeworkDownloadResult.Failure {

    /** At least one attachment was downloaded successfully. */
    record Success(List<HomeworkAttachment> attachments) implements HomeworkDownloadResult {}

    /** Homework has no attachments — legitimate outcome, not an error. */
    record Empty(String homeworkId) implements HomeworkDownloadResult {}

    /** Operation failed with the given error code and message. */
    record Failure(ErrorCode code, String message) implements HomeworkDownloadResult {}
}

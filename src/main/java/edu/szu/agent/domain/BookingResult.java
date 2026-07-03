package edu.szu.agent.domain;

import edu.szu.agent.error.ErrorCode;

/**
 * Booking result — strict 2-state sealed type.
 *
 * <p>Per ADR-0006 §一.3: timeouts are expressed as
 * {@link Failure} + {@code ErrorCode.NETWORK_TIMEOUT}, not a third
 * {@code Timeout} subtype. This keeps the retry decision in the
 * {@code retry/} package rather than the type system.
 *
 * // 编程技术: Sealed Interface(Java 17+,permits 限制实现)
 * // 编程技术: Record(Java 16+,值类型)
 *
 * @since 0.6.0
 * @author 王子豪
 */
public sealed interface BookingResult permits BookingResult.Success, BookingResult.Failure {

    /**
     * Successful booking — ehall confirmed a venue.
     *
     * @param venueName    human-readable venue name (e.g. "网球1号场")
     * @param confirmation ehall confirmation number
     * @since 0.6.0
     */
    record Success(String venueName, String confirmation) implements BookingResult {
    }

    /**
     * Failed booking — carries the {@link ErrorCode} so the caller can
     * decide whether to retry, switch account, or report to the user.
     *
     * @param code    error code with severity / retryable / hint metadata
     * @param message human-readable description of what went wrong
     * @since 0.6.0
     */
    record Failure(ErrorCode code, String message) implements BookingResult {
    }
}

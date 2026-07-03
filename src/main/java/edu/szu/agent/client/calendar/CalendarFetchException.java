package edu.szu.agent.client.calendar;

import edu.szu.agent.error.ErrorCode;

/**
 * Domain exception for calendar fetch failures — carries a canonical
 * {@link ErrorCode} so the resilient wrapper can map to
 * {@code CalendarListResult.Failure} with proper severity / hint.
 *
 * // 编程技术: 不可变异常(cause + code)
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class CalendarFetchException extends RuntimeException {

    private final ErrorCode code;

    public CalendarFetchException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public CalendarFetchException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
package edu.szu.agent.error;

import java.util.Objects;

/**
 * Booking exception — runtime exception carrying an {@link ErrorCode}.
 *
 * <p>Per ADR-0006 §二.4: unchecked, business code catches {@code Exception}
 * and wraps it into {@code BookingException} before propagating.
 *
 * <p>Per ADR-0006 §二.5: the {@code retry/} layer catches
 * {@code BookingException} and inspects {@link ErrorCode#isRetryable()}
 * to decide whether to re-attempt.
 *
 * // 编程技术: 异常(RuntimeException + 携带 ErrorCode 元数据)
 *
 * @since 0.6.0
 * @author 王子豪
 */
public class BookingException extends RuntimeException {

    private final ErrorCode code;

    /**
     * @param code    error code (required, non-null)
     * @param message human-readable description
     */
    public BookingException(ErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "BookingException.code must not be null");
    }

    /**
     * @param code    error code (required, non-null)
     * @param message human-readable description
     * @param cause   underlying cause (typically the Playwright / network exception)
     */
    public BookingException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "BookingException.code must not be null");
    }

    /** The error code driving retry / log / screenshot decisions. */
    public ErrorCode code() {
        return code;
    }
}

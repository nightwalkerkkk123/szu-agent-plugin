package edu.szu.agent.error;

/**
 * Exception thrown when the exam list fetch or parsing fails.
 *
 * <p>// 编程技术:  unchecked 异常 / 错误码携带
 *
 * @since 0.4.0
 * @author 王子豪
 */
public class ExamListException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Create a new exception with the given error code and message.
     *
     * @param errorCode the error code classification
     * @param message   human-readable message
     */
    public ExamListException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create a new exception with the given error code, message, and cause.
     *
     * @param errorCode the error code classification
     * @param message   human-readable message
     * @param cause     the underlying cause
     */
    public ExamListException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the error code for this exception.
     *
     * @return the error code
     */
    public ErrorCode errorCode() {
        return errorCode;
    }
}

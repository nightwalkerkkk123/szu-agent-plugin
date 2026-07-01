package edu.szu.agent.client.notice;

import edu.szu.agent.error.ErrorCode;

/**
 * Thrown by {@link NoticeFetchProvider} implementations when a real
 * fetch fails (network / timeout / page-not-loaded / selector-miss).
 *
 * <p>Carries an {@link ErrorCode} so the resilient wrapper can map the
 * failure into {@code NoticeListResult.Failure} without losing the
 * diagnostic category (e.g. {@link ErrorCode#NOTICE_TIMEOUT} vs
 * {@link ErrorCode#NOTICE_FETCH_FAILED}).
 *
 * // 编程技术: 不可变 record / 枚举元数据
 *
 * @since 0.4.0
 * @author 王子豪
 */
public final class NoticeFetchException extends RuntimeException {

    private final ErrorCode code;

    public NoticeFetchException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public NoticeFetchException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}

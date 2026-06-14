package edu.szu.agent.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ErrorCode} enum metadata.
 *
 * <p>Per ADR-0006 §二.1: 12 values. Per §二.2: 5 metadata fields per constant.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("ErrorCode enum")
class ErrorCodeTest {

    @Test
    @DisplayName("has exactly 14 constants")
    void hasFourteenConstants() {
        assertThat(ErrorCode.values()).hasSize(14);
    }

    @Test
    @DisplayName("NETWORK_TIMEOUT is retryable but not screenshot-worthy")
    void networkTimeoutMetadata() {
        ErrorCode code = ErrorCode.NETWORK_TIMEOUT;

        assertThat(code.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(code.isRetryable()).isTrue();
        assertThat(code.shouldSwitchAccount()).isFalse();
        assertThat(code.shouldScreenshot()).isFalse();
        assertThat(code.hint()).isEqualTo("网络超时");
    }

    @Test
    @DisplayName("PASSWORD_INCORRECT is non-retryable and triggers account switch")
    void passwordIncorrectMetadata() {
        ErrorCode code = ErrorCode.PASSWORD_INCORRECT;

        assertThat(code.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(code.isRetryable()).isFalse();
        assertThat(code.shouldSwitchAccount()).isTrue();
        assertThat(code.shouldScreenshot()).isTrue();
    }

    @Test
    @DisplayName("INVALID_REQUEST is not retryable (user error, must fix input)")
    void invalidRequestMetadata() {
        ErrorCode code = ErrorCode.INVALID_REQUEST;

        assertThat(code.severity()).isEqualTo(Severity.LOW);
        assertThat(code.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("all constants expose non-blank hint")
    void allConstantsHaveHint() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.hint())
                .as("hint for %s", code.name())
                .isNotBlank();
            assertThat(code.severity())
                .as("severity for %s", code.name())
                .isNotNull();
        }
    }
}

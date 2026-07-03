package edu.szu.agent.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BookingException}.
 *
 * @since 0.6.0
 * @author 王子豪
 */
@DisplayName("BookingException")
class BookingExceptionTest {

    @Test
    @DisplayName("carries ErrorCode and message")
    void carriesCodeAndMessage() {
        BookingException ex = new BookingException(ErrorCode.NETWORK_TIMEOUT, "Playwright 30s 超时");

        assertThat(ex.code()).isEqualTo(ErrorCode.NETWORK_TIMEOUT);
        assertThat(ex.getMessage()).isEqualTo("Playwright 30s 超时");
    }

    @Test
    @DisplayName("carries underlying cause")
    void carriesCause() {
        RuntimeException cause = new RuntimeException("connection reset");
        BookingException ex = new BookingException(ErrorCode.NETWORK_TIMEOUT, "wrapped", cause);

        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("is a RuntimeException (unchecked)")
    void isUnchecked() {
        BookingException ex = new BookingException(ErrorCode.UNKNOWN, "x");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("rejects null ErrorCode")
    void rejectsNullCode() {
        assertThatThrownBy(() -> new BookingException(null, "x"))
            .isInstanceOf(NullPointerException.class);
    }
}

package edu.szu.agent.retry;

import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link NoRetry}.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("NoRetry")
class NoRetryTest {

    @Test
    @DisplayName("INSTANCE is a singleton")
    void instanceIsSingleton() {
        assertThat(NoRetry.INSTANCE).isSameAs(NoRetry.INSTANCE);
    }

    @Test
    @DisplayName("returns action result on success")
    void returnsOnSuccess() {
        String result = NoRetry.INSTANCE.execute(() -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("propagates exception as-is (no retry)")
    void propagatesException() {
        assertThatThrownBy(() -> NoRetry.INSTANCE.execute(
            () -> { throw new BookingException(ErrorCode.NETWORK_TIMEOUT, "fail"); }))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.NETWORK_TIMEOUT);
    }
}

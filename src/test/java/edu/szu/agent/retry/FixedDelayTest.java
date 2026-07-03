package edu.szu.agent.retry;

import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link FixedDelay}.
 *
 * @since 0.6.0
 * @author 王子豪
 */
@DisplayName("FixedDelay")
class FixedDelayTest {

    @Test
    @DisplayName("returns the result on first success")
    void returnsOnFirstSuccess() {
        var policy = new FixedDelay(3, Duration.ZERO);
        AtomicInteger calls = new AtomicInteger();

        String result = policy.execute(() -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("retries on retryable exception and eventually succeeds")
    void retriesThenSucceeds() {
        var policy = new FixedDelay(3, Duration.ofMillis(1));
        AtomicInteger calls = new AtomicInteger();

        String result = policy.execute(() -> {
            int n = calls.incrementAndGet();
            if (n < 2) {
                throw new BookingException(ErrorCode.NETWORK_TIMEOUT, "transient");
            }
            return "recovered";
        });

        assertThat(result).isEqualTo("recovered");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("throws NETWORK_TIMEOUT after max attempts")
    void exhaustsToNetworkTimeout() {
        var policy = new FixedDelay(2, Duration.ofMillis(1));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> policy.execute(() -> {
            calls.incrementAndGet();
            throw new BookingException(ErrorCode.NETWORK_TIMEOUT, "always fails");
        }))
            .isInstanceOf(BookingException.class)
            .hasMessageContaining("重试 2 次耗尽")
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.NETWORK_TIMEOUT);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("does not retry non-retryable exception")
    void doesNotRetryNonRetryable() {
        var policy = new FixedDelay(3, Duration.ofMillis(1));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> policy.execute(() -> {
            calls.incrementAndGet();
            throw new BookingException(ErrorCode.INVALID_REQUEST, "user error");
        }))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.INVALID_REQUEST);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("propagates non-BookingException as-is")
    void propagatesRawException() {
        var policy = new FixedDelay(3, Duration.ofMillis(1));

        assertThatThrownBy(() -> policy.execute(() -> {
            throw new IllegalStateException("boom");
        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("boom");
    }

    @Test
    @DisplayName("rejects invalid maxAttempts")
    void rejectsInvalidMaxAttempts() {
        assertThatThrownBy(() -> new FixedDelay(0, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedDelay(-1, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

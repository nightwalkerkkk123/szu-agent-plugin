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
 * Tests for {@link ExponentialBackoff}.
 *
 * @since 0.6.0
 * @author 王子豪
 */
@DisplayName("ExponentialBackoff")
class ExponentialBackoffTest {

    @Test
    @DisplayName("computeDelay grows by multiplier and caps at maxDelay")
    void computeDelayGrowsAndCaps() {
        var policy = new ExponentialBackoff(
            5, Duration.ofMillis(100), Duration.ofMillis(500), 2.0);

        assertThat(policy.computeDelay(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.computeDelay(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.computeDelay(3)).isEqualTo(Duration.ofMillis(400));
        // attempt 4: 100 × 2^3 = 800 → cap 500
        assertThat(policy.computeDelay(4)).isEqualTo(Duration.ofMillis(500));
        // attempt 5: 100 × 2^4 = 1600 → cap 500
        assertThat(policy.computeDelay(5)).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    @DisplayName("retries on retryable exception and eventually succeeds")
    void retriesThenSucceeds() {
        var policy = new ExponentialBackoff(
            3, Duration.ofMillis(1), Duration.ofMillis(10), 2.0);
        AtomicInteger calls = new AtomicInteger();

        String result = policy.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new BookingException(ErrorCode.NETWORK_TIMEOUT, "transient");
            }
            return "recovered";
        });

        assertThat(result).isEqualTo("recovered");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("throws NETWORK_TIMEOUT after max attempts")
    void exhaustsToNetworkTimeout() {
        var policy = new ExponentialBackoff(
            2, Duration.ofMillis(1), Duration.ofMillis(10), 2.0);

        assertThatThrownBy(() -> policy.execute(() -> {
            throw new BookingException(ErrorCode.NETWORK_TIMEOUT, "fails");
        }))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.NETWORK_TIMEOUT);
    }

    @Test
    @DisplayName("simple constructor defaults maxDelay=base×16, multiplier=2.0")
    void simpleConstructorDefaults() {
        var policy = new ExponentialBackoff(5, Duration.ofMillis(100));
        // maxDelay = 100 × 16 = 1600ms; attempt 5: 100 × 2^4 = 1600 (just at cap)
        assertThat(policy.computeDelay(5)).isEqualTo(Duration.ofMillis(1600));
    }

    @Test
    @DisplayName("rejects multiplier < 1.0")
    void rejectsSmallMultiplier() {
        assertThatThrownBy(() ->
            new ExponentialBackoff(3, Duration.ofMillis(1), Duration.ofMillis(10), 0.5))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

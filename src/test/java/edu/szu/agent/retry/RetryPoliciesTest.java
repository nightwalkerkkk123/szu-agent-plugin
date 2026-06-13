package edu.szu.agent.retry;

import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RetryPolicies} factory and {@link RetryPolicy#orElse}
 * chaining.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("RetryPolicies factory + orElse chaining")
class RetryPoliciesTest {

    @Test
    @DisplayName("defaultBooking returns NoRetry (single attempt — pipeline can't restart)")
    void defaultBookingIsNoRetry() {
        assertThat(RetryPolicies.defaultBooking()).isSameAs(NoRetry.INSTANCE);
    }

    @Test
    @DisplayName("exponentialBackoff factory still available for stateless flows")
    void exponentialBackoffStillAvailable() {
        assertThat(RetryPolicies.exponentialBackoff()).isInstanceOf(ExponentialBackoff.class);
    }

    @Test
    @DisplayName("login returns a FixedDelay")
    void loginIsFixedDelay() {
        assertThat(RetryPolicies.login()).isInstanceOf(FixedDelay.class);
    }

    @Test
    @DisplayName("quickFix returns NoRetry.INSTANCE")
    void quickFixIsNoRetry() {
        assertThat(RetryPolicies.quickFix()).isSameAs(NoRetry.INSTANCE);
    }

    @Test
    @DisplayName("orElse chains two policies (first exhausts, then second runs)")
    void orElseChainsPolicies() {
        // Primary always throws NETWORK_TIMEOUT (simulates exhausted retry).
        // Anonymous class (not lambda) because RetryPolicy's SAM is a
        // generic method — see RetryPolicy.orElse() Javadoc for the same
        // restriction.
        RetryPolicy primary = new RetryPolicy() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> action) {
                throw new BookingException(
                    ErrorCode.NETWORK_TIMEOUT,
                    "primary exhausted");
            }
        };
        RetryPolicy fallback = NoRetry.INSTANCE;

        String result = primary.orElse(fallback).execute(() -> "from-fallback");

        assertThat(result).isEqualTo("from-fallback");
    }
}

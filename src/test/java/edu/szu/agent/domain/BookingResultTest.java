package edu.szu.agent.domain;

import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BookingResult} sealed hierarchy.
 *
 * <p>Per ADR-0006 §一.3: strictly 2-state (Success, Failure).
 * Timeouts are expressed as Failure + ErrorCode.NETWORK_TIMEOUT.
 *
 * @since 0.6.0
 * @author 王子豪
 */
@DisplayName("BookingResult sealed type")
class BookingResultTest {

    @Test
    @DisplayName("Success carries venueName and confirmation")
    void successCarriesVenueAndConfirmation() {
        BookingResult.Success success =
            new BookingResult.Success("网球1号场", "BOOKED-20260612-001");

        assertThat(success.venueName()).isEqualTo("网球1号场");
        assertThat(success.confirmation()).isEqualTo("BOOKED-20260612-001");
    }

    @Test
    @DisplayName("Failure carries ErrorCode and human-readable message")
    void failureCarriesCodeAndMessage() {
        BookingResult.Failure failure =
            new BookingResult.Failure(ErrorCode.NETWORK_TIMEOUT, "Playwright 超时");

        assertThat(failure.code()).isEqualTo(ErrorCode.NETWORK_TIMEOUT);
        assertThat(failure.message()).isEqualTo("Playwright 超时");
    }

    @Test
    @DisplayName("sealed hierarchy permits only Success and Failure")
    void sealedHierarchyIsClosed() {
        Class<?>[] permitted = BookingResult.class.getPermittedSubclasses();
        assertThat(permitted)
            .as("BookingResult must have exactly 2 permitted subtypes")
            .hasSize(2);
    }
}

package edu.szu.agent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BookingRequest} Builder pattern and validation.
 *
 * <p>Per ADR-0006 §一.4: 6-field Builder, build() validates 4 required
 * non-null fields + preferredVenueIndex &gt;= 1.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("BookingRequest Builder")
class BookingRequestTest {

    private static final Campus CAMPUS = Campus.YUEHAI;
    private static final Sport SPORT = Sport.TENNIS;
    private static final LocalDate DATE = LocalDate.of(2026, 6, 12);
    private static final TimeSlot SLOT = new TimeSlot("19:00", "20:00");

    @Test
    @DisplayName("Builder builds valid request")
    void buildsValidRequest() {
        BookingRequest request = BookingRequest.builder()
            .username("2023150090")
            .campus(CAMPUS)
            .sport(SPORT)
            .date(DATE)
            .timeSlot(SLOT)
            .preferredVenueIndex(1)
            .build();

        assertThat(request.username()).isEqualTo("2023150090");
        assertThat(request.campus()).isEqualTo(CAMPUS);
        assertThat(request.sport()).isEqualTo(SPORT);
        assertThat(request.date()).isEqualTo(DATE);
        assertThat(request.timeSlot()).isEqualTo(SLOT);
        assertThat(request.preferredVenueIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects missing campus")
    void rejectsMissingCampus() {
        assertThatThrownBy(() -> BookingRequest.builder()
            .sport(SPORT)
            .date(DATE)
            .timeSlot(SLOT)
            .preferredVenueIndex(1)
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("campus");
    }

    @Test
    @DisplayName("rejects missing sport")
    void rejectsMissingSport() {
        assertThatThrownBy(() -> BookingRequest.builder()
            .campus(CAMPUS)
            .date(DATE)
            .timeSlot(SLOT)
            .preferredVenueIndex(1)
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("sport");
    }

    @Test
    @DisplayName("rejects missing date")
    void rejectsMissingDate() {
        assertThatThrownBy(() -> BookingRequest.builder()
            .campus(CAMPUS)
            .sport(SPORT)
            .timeSlot(SLOT)
            .preferredVenueIndex(1)
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("date");
    }

    @Test
    @DisplayName("rejects missing timeSlot")
    void rejectsMissingTimeSlot() {
        assertThatThrownBy(() -> BookingRequest.builder()
            .campus(CAMPUS)
            .sport(SPORT)
            .date(DATE)
            .preferredVenueIndex(1)
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("timeSlot");
    }

    @Test
    @DisplayName("rejects preferredVenueIndex < 1 (ehall 1-based)")
    void rejectsVenueIndexBelowOne() {
        assertThatThrownBy(() -> BookingRequest.builder()
            .campus(CAMPUS)
            .sport(SPORT)
            .date(DATE)
            .timeSlot(SLOT)
            .preferredVenueIndex(0)
            .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("preferredVenueIndex");

        assertThatThrownBy(() -> BookingRequest.builder()
            .campus(CAMPUS)
            .sport(SPORT)
            .date(DATE)
            .timeSlot(SLOT)
            .preferredVenueIndex(-3)
            .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("preferredVenueIndex");
    }

    @Test
    @DisplayName("username is optional (filled by AccountResolver later)")
    void usernameIsOptional() {
        BookingRequest request = BookingRequest.builder()
            .campus(CAMPUS)
            .sport(SPORT)
            .date(DATE)
            .timeSlot(SLOT)
            .preferredVenueIndex(2)
            .build();

        assertThat(request.username()).isNull();
    }
}

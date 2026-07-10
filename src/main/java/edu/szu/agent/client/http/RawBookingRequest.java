package edu.szu.agent.client.http;

import edu.szu.agent.domain.TimeSlot;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Resolved, wire-ready booking request used by {@link VenueBookingService}.
 *
 * <p>Unlike {@link edu.szu.agent.domain.BookingRequest}, which uses the
 * {@code Campus}/{@code Sport} domain enums, this record already carries the
 * raw ehall codes ({@code XQDM}, {@code XMDM}) and the booking type
 * ({@code YYLX}) that the HTTP client needs. It is produced by the CLI layer
 * after it has normalized user input.
 *
 * // 编程技术: record(不可变值类型) / 参数校验
 *
 * @param campusCode         campus wire code ({@code XQDM})
 * @param sportCode          sport wire code ({@code XMDM})
 * @param date               booking date
 * @param timeSlot           booking time slot
 * @param preferredVenueIndex 1-based index among available venues
 * @param yylx               booking type: {@code 1.0} (package) or {@code 2.0} (dismissal)
 * @since 0.6.0
 * @author 王子豪
 */
public record RawBookingRequest(
    String campusCode,
    String sportCode,
    LocalDate date,
    TimeSlot timeSlot,
    int preferredVenueIndex,
    String yylx
) {

    /**
     * Compact constructor with non-null validation.
     */
    public RawBookingRequest {
        Objects.requireNonNull(campusCode, "campusCode must not be null");
        Objects.requireNonNull(sportCode, "sportCode must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(timeSlot, "timeSlot must not be null");
        Objects.requireNonNull(yylx, "yylx must not be null");
        if (campusCode.isBlank()) {
            throw new IllegalArgumentException("campusCode must not be blank");
        }
        if (sportCode.isBlank()) {
            throw new IllegalArgumentException("sportCode must not be blank");
        }
        if (yylx.isBlank()) {
            throw new IllegalArgumentException("yylx must not be blank");
        }
        if (preferredVenueIndex < 1) {
            throw new IllegalArgumentException(
                "preferredVenueIndex must be >= 1, got " + preferredVenueIndex);
        }
    }
}

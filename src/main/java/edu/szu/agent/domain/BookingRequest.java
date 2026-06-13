package edu.szu.agent.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable booking request — what a caller wants the agent to book.
 *
 * <p>Per ADR-0006 §一.4: 6 fields, Builder construction, validation on
 * {@link Builder#build()}:
 * <ul>
 *   <li>4 fields required non-null: {@code campus}, {@code sport}, {@code date}, {@code timeSlot}</li>
 *   <li>1 field with constraint: {@code preferredVenueIndex} &gt;= 1 (ehall 1-based)</li>
 *   <li>1 field optional: {@code username}</li>
 * </ul>
 *
 * // Design Pattern: Builder
 * // 编程技术: 不可变类(全部字段 final) + 静态内部 Builder
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class BookingRequest {

    private final String username;
    private final Campus campus;
    private final Sport sport;
    private final LocalDate date;
    private final TimeSlot timeSlot;
    private final int preferredVenueIndex;

    private BookingRequest(Builder b) {
        this.username = b.username;
        this.campus = b.campus;
        this.sport = b.sport;
        this.date = b.date;
        this.timeSlot = b.timeSlot;
        this.preferredVenueIndex = b.preferredVenueIndex;
    }

    /** Username of the booker; may be null if resolved by {@code AccountResolver} later. */
    public String username() {
        return username;
    }

    public Campus campus() {
        return campus;
    }

    public Sport sport() {
        return sport;
    }

    public LocalDate date() {
        return date;
    }

    public TimeSlot timeSlot() {
        return timeSlot;
    }

    /** 1-based venue index, per ehall numbering. */
    public int preferredVenueIndex() {
        return preferredVenueIndex;
    }

    /** Creates a new {@link Builder} for chaining. */
    public static Builder builder() {
        return new Builder();
    }

    // 编程技术: 静态内部 Builder 类(6 字段链式构造)
    /**
     * Builder for {@link BookingRequest}. All setters return {@code this} for chaining.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private String username;
        private Campus campus;
        private Sport sport;
        private LocalDate date;
        private TimeSlot timeSlot;
        private int preferredVenueIndex;

        private Builder() {
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder campus(Campus campus) {
            this.campus = campus;
            return this;
        }

        public Builder sport(Sport sport) {
            this.sport = sport;
            return this;
        }

        public Builder date(LocalDate date) {
            this.date = date;
            return this;
        }

        public Builder timeSlot(TimeSlot timeSlot) {
            this.timeSlot = timeSlot;
            return this;
        }

        public Builder preferredVenueIndex(int preferredVenueIndex) {
            this.preferredVenueIndex = preferredVenueIndex;
            return this;
        }

        /**
         * Validates and builds the {@link BookingRequest}.
         *
         * @return the immutable request
         * @throws IllegalStateException if any required field is missing or
         *                               {@code preferredVenueIndex < 1}
         */
        public BookingRequest build() {
            Objects.requireNonNull(campus, "BookingRequest.campus must not be null");
            Objects.requireNonNull(sport, "BookingRequest.sport must not be null");
            Objects.requireNonNull(date, "BookingRequest.date must not be null");
            Objects.requireNonNull(timeSlot, "BookingRequest.timeSlot must not be null");
            if (sport.campus() != campus) {
                throw new IllegalStateException(
                    "BookingRequest.sport (" + sport + ") belongs to campus "
                        + sport.campus() + " but campus parameter is " + campus
                        + ". Use Sport.of(campus, name) to route correctly.");
            }
            if (preferredVenueIndex < 1) {
                throw new IllegalStateException(
                    "BookingRequest.preferredVenueIndex must be >= 1 (ehall 1-based), got: "
                        + preferredVenueIndex);
            }
            return new BookingRequest(this);
        }
    }
}

package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.client.VenueBookingClient;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.Sport;
import edu.szu.agent.domain.TimeSlot;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * {@code booking_venue} CampusTask — the P0 realized implementation
 * of {@link CampusTask}.
 *
 * <p>Per ADR-0001 D10: thin adapter that translates {@link TaskInput}
 * → {@link BookingRequest}, delegates to {@link VenueBookingClient},
 * and unwraps the result.
 *
 * <p>Parameter keys (string contract, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code username} (required) — student ID
 *   <li>{@code campus} (required) — Campus enum name (e.g. YUEHAI)
 *   <li>{@code sport} (required) — Sport enum name (e.g. TENNIS)
 *   <li>{@code date} (required) — ISO 8601 date (e.g. 2026-06-13)
 *   <li>{@code timeSlot} (required) — "HH:mm-HH:mm" (e.g. 19:00-20:00)
 *   <li>{@code preferredVenue} (optional, default 1) — 1-based index
 * </ul>
 *
 * // 编程技术: 泛型 / 枚举 / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class BookingTask implements CampusTask<BookingResult> {

    private final VenueBookingClient client;
    private final Account account;

    public BookingTask(VenueBookingClient client, Account account) {
        this.client = client;
        this.account = account;
    }

    @Override
    public String name() {
        return "booking_venue";
    }

    @Override
    public String description() {
        return "体育场馆定时预约";
    }

    @Override
    public BookingResult execute(TaskInput input) {
        // Parse — fail fast with IllegalArgumentException (CLI maps to exit 2)
        Campus campus = Campus.valueOf(input.require("campus").toUpperCase());
        Sport sport = Sport.valueOf(input.require("sport").toUpperCase());
        LocalDate date;
        try {
            date = LocalDate.parse(input.require("date"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date must be ISO 8601: " + e.getMessage());
        }
        TimeSlot slot = parseTimeSlot(input.require("timeSlot"));

        int preferredVenue = input.getInt("preferredVenue", 1);

        BookingRequest request = BookingRequest.builder()
            .username(input.require("username"))
            .campus(campus)
            .sport(sport)
            .date(date)
            .timeSlot(slot)
            .preferredVenueIndex(preferredVenue)
            .build();

        return client.book(request);
    }

    /**
     * Parses "HH:mm-HH:mm" into a {@link TimeSlot}. Reused from
     * {@code VenueCommand.parseTimeSlot} semantics so CLI and Skill
     * layers share one validation path.
     *
     * @since 0.1.0
     */
    private static TimeSlot parseTimeSlot(String raw) {
        if (raw == null || !raw.contains("-")) {
            throw new IllegalArgumentException(
                "Invalid time-slot format (expected HH:mm-HH:mm): " + raw);
        }
        String[] parts = raw.split("-", 2);
        return new TimeSlot(parts[0].trim(), parts[1].trim());
    }
}

package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.VenueBookingClient;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.Sport;
import edu.szu.agent.domain.TimeSlot;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * {@code booking_venue} CampusTask — the P0 realized implementation
 * of {@link CampusTask}.
 *
 * <p>Per ADR-0001 D10: thin adapter that translates {@link TaskInput}
 * → {@link BookingRequest}, resolves the account via
 * {@link AccountResolver}, delegates to {@link VenueBookingClient},
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

    private final Function<Account, VenueBookingClient> clientFactory;
    private final Function<String, Account> accountResolver;

    /**
     * Production constructor — uses {@link AccountResolver#resolve(String)}.
     *
     * <p>The {@code clientFactory} builds a session-aware client per resolved
     * account (e.g. {@code BookingFlowLauncher::clientFor}) so a single manual
     * MFA pass is reused on later headless runs — the same path the CLI uses.
     *
     * @param clientFactory builds a {@link VenueBookingClient} for a resolved account
     */
    public BookingTask(Function<Account, VenueBookingClient> clientFactory) {
        this(clientFactory, AccountResolver::resolve);
    }

    /**
     * Test constructor — inject a custom client factory and account resolver.
     */
    BookingTask(Function<Account, VenueBookingClient> clientFactory,
                Function<String, Account> accountResolver) {
        this.clientFactory = clientFactory;
        this.accountResolver = accountResolver;
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
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> username = new LinkedHashMap<>();
        username.put("type", "string");
        username.put("description", "学号");
        properties.put("username", username);

        Map<String, Object> campus = new LinkedHashMap<>();
        campus.put("type", "string");
        campus.put("description", "校区(YUEHAI/LIHU)");
        properties.put("campus", campus);

        Map<String, Object> sport = new LinkedHashMap<>();
        sport.put("type", "string");
        sport.put("description", "运动项目(TENNIS/BADMINTON/...)");
        properties.put("sport", sport);

        Map<String, Object> date = new LinkedHashMap<>();
        date.put("type", "string");
        date.put("format", "date");
        date.put("description", "ISO 8601 日期,例如 2026-06-13");
        properties.put("date", date);

        Map<String, Object> timeSlot = new LinkedHashMap<>();
        timeSlot.put("type", "string");
        timeSlot.put("description", "预约时段 HH:mm-HH:mm(1 小时窗口),例如 19:00-20:00");
        properties.put("timeSlot", timeSlot);

        Map<String, Object> preferredVenue = new LinkedHashMap<>();
        preferredVenue.put("type", "integer");
        preferredVenue.put("description", "1-based 场地序号,默认 1");
        preferredVenue.put("default", 1);
        properties.put("preferredVenue", preferredVenue);

        schema.put("properties", properties);
        schema.put("required", List.of("username", "campus", "sport", "date", "timeSlot"));
        return schema;
    }

    @Override
    public BookingResult execute(TaskInput input) {
        // Parse — fail fast with IllegalArgumentException (CLI maps to exit 2)
        Campus campus = Campus.valueOf(input.require("campus").toUpperCase());
        Sport sport = Sport.of(campus, input.require("sport").toUpperCase());
        LocalDate date;
        try {
            date = LocalDate.parse(input.require("date"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date must be ISO 8601: " + e.getMessage());
        }
        TimeSlot slot = resolveTimeSlot(input);

        int preferredVenue = input.getInt("preferredVenue", 1);
        String username = input.require("username");

        BookingRequest request = BookingRequest.builder()
            .username(username)
            .campus(campus)
            .sport(sport)
            .date(date)
            .timeSlot(slot)
            .preferredVenueIndex(preferredVenue)
            .build();

        Account account = accountResolver.apply(username);
        return clientFactory.apply(account).book(request, account);
    }

    /**
     * Resolves the {@code timeSlot} parameter, tolerating both wire shapes.
     *
     * <p>The canonical form is a flat {@code "HH:mm-HH:mm"} string (CLI
     * {@code --time-slot}, the declared MCP schema). A caller may still send
     * a nested object {@code {"start":"19:00","end":"20:00"}}; the MCP layer
     * flattens that to dotted keys {@code timeSlot.start} / {@code timeSlot.end}
     * (see {@code MCPToolCallHandler#flatten}). Accepting both removes the
     * schema-vs-implementation drift that previously surfaced as a spurious
     * "Missing required parameter: timeSlot".
     *
     * @param input the task input
     * @return the parsed {@link TimeSlot}
     * @throws IllegalArgumentException if neither shape is present, or the
     *         slot is not a valid 1-hour boundary
     * @since 0.1.0
     */
    private static TimeSlot resolveTimeSlot(TaskInput input) {
        // 编程技术: 重载 — TimeSlot.of(String) vs TimeSlot.parse(start, end)
        String flat = input.get("timeSlot");
        if (flat != null && !flat.isBlank()) {
            return TimeSlot.of(flat);
        }
        String start = input.get("timeSlot.start");
        String end = input.get("timeSlot.end");
        if (start != null && !start.isBlank() && end != null && !end.isBlank()) {
            return TimeSlot.parse(start, end);
        }
        throw new IllegalArgumentException("Missing required parameter: timeSlot");
    }
}

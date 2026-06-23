package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.VenueBookingClient;
import edu.szu.agent.config.ConfigManager;
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

    private final VenueBookingClient client;
    private final Function<String, Account> accountResolver;

    /**
     * Production constructor — uses {@link AccountResolver#resolve(String)}.
     */
    public BookingTask(VenueBookingClient client) {
        this(client, AccountResolver::resolve);
    }

    /**
     * Test constructor — inject a custom account resolver.
     */
    BookingTask(VenueBookingClient client, Function<String, Account> accountResolver) {
        this.client = client;
        this.accountResolver = accountResolver;
    }

    @Override
    public String name() {
        return "booking_venue";
    }

    @Override
    public String description() {
        return "体育场馆定时预约。调用前请先阅读 docs/tools/booking-venue.md;真实预约会占用实际名额,调用前必须获得用户明确确认。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> username = new LinkedHashMap<>();
        username.put("type", "string");
        username.put("description", "学号;若未提供,默认使用环境变量 SZU_USERNAME 配置的账号");
        properties.put("username", username);

        Map<String, Object> campus = new LinkedHashMap<>();
        campus.put("type", "string");
        campus.put("description", "校区(YUEHAI/LIHU)");
        properties.put("campus", campus);

        Map<String, Object> sport = new LinkedHashMap<>();
        sport.put("type", "string");
        sport.put("description", "运动项目枚举名。粤海校区: BADMINTON/FOOTBALL/VOLLEYBALL/TENNIS/BASKETBALL/SQUASH/GYM_HEAVY(一楼重量型健身)/GYM_AEROBIC(二楼有氧健身)/SWIMMING; 丽湖校区: BADMINTON/VOLLEYBALL/TENNIS/BASKETBALL/SWIMMING/TABLE_TENNIS/DANCE/POOL/CYCLING/MAGIC_MIRROR/BOARD_GAME/GYM(健身房)/YOGA/PICKLEBALL/SHUTTLECOCK");
        properties.put("sport", sport);

        Map<String, Object> date = new LinkedHashMap<>();
        date.put("type", "string");
        date.put("format", "date");
        date.put("description", "ISO 8601 日期,例如 2026-06-13");
        properties.put("date", date);

        Map<String, Object> timeSlot = new LinkedHashMap<>();
        timeSlot.put("type", "string");
        timeSlot.put("description", "预约时段,HH:mm-HH:mm 格式,例如 12:00-13:00 或 19:00-20:00。系统只支持整点 1 小时时段(08:00-22:00)");
        properties.put("timeSlot", timeSlot);

        Map<String, Object> preferredVenue = new LinkedHashMap<>();
        preferredVenue.put("type", "integer");
        preferredVenue.put("description", "1-based 偏好序号,默认 1。对球场类项目指第几个可预约球场;对健身房类容量项目指第几个可用容量时段/区域");
        preferredVenue.put("default", 1);
        properties.put("preferredVenue", preferredVenue);

        schema.put("properties", properties);
        schema.put("required", List.of("campus", "sport", "date", "timeSlot"));
        return schema;
    }

    private String resolveUsername(TaskInput input) {
        String fromInput = input.get("username");
        if (fromInput != null && !fromInput.isBlank()) {
            return fromInput;
        }
        String fromEnv = ConfigManager.getInstance().get("SZU_USERNAME");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        throw new IllegalArgumentException("Missing required parameter: username");
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
        TimeSlot slot = TimeSlot.of(input.require("timeSlot"));

        int preferredVenue = input.getInt("preferredVenue", 1);
        String username = resolveUsername(input);

        BookingRequest request = BookingRequest.builder()
            .username(username)
            .campus(campus)
            .sport(sport)
            .date(date)
            .timeSlot(slot)
            .preferredVenueIndex(preferredVenue)
            .build();

        return client.book(request, accountResolver.apply(username));
    }
}

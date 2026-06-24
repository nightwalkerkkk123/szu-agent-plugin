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
        return """
            深圳大学体育场馆定时预约。重要约束(必须遵守,否则调用会失败):
            1. 真实预约会占用实际名额,调用前必须获得用户明确确认。
            2. campus 必须是枚举值之一:YUEHAI(粤海校区)或 LIHU(丽湖校区)。
            3. sport 必须是枚举值之一,且要与 campus 匹配:
               - 粤海校区(YUEHAI)可选:BADMINTON(羽毛球),FOOTBALL(足球),VOLLEYBALL(排球),TENNIS(网球),BASKETBALL(篮球),SQUASH(壁球),GYM_HEAVY(一楼重量型健身/一楼健身房),GYM_AEROBIC(二楼有氧健身/二楼健身房),SWIMMING(游泳)。
               - 丽湖校区(LIHU)可选:BADMINTON(羽毛球),VOLLEYBALL(排球),TENNIS(网球),BASKETBALL(篮球),SWIMMING(游泳),TABLE_TENNIS(乒乓球),DANCE(舞蹈),POOL(桌球),CYCLING(骑行),MAGIC_MIRROR(魔镜),BOARD_GAME(桌游),GYM(健身房),YOGA(瑜伽),PICKLEBALL(匹克球),SHUTTLECOCK(毽球)。
            4. date 必须是 ISO 8601 日期字符串,例如 2026-06-24。
            5. timeSlot 必须是字符串,格式 HH:mm-HH:mm,只支持整点 1 小时时段,例如 16:00-17:00。不要传对象,不要写 4-5 点 等口语。
            6. 若用户未提供 username,默认使用环境变量 SZU_USERNAME 配置的账号(当前默认 2023150090)。
            7. 用户说 明天 时,date 取明天的 ISO 日期;用户说 4-5点/下午4点到5点 时,timeSlot 必须转换为 16:00-17:00。
            8. 调用前必须先向用户复述校区、项目、日期、时段,得到明确同意后再执行。
            """;
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> username = new LinkedHashMap<>();
        username.put("type", "string");
        username.put("description", "学号,例如 2023150090。若未提供,默认使用环境变量 SZU_USERNAME 配置的账号");
        properties.put("username", username);

        Map<String, Object> campus = new LinkedHashMap<>();
        campus.put("type", "string");
        campus.put("enum", List.of("YUEHAI", "LIHU"));
        campus.put("description", "校区枚举名:YUEHAI(粤海)或 LIHU(丽湖)");
        properties.put("campus", campus);

        Map<String, Object> sport = new LinkedHashMap<>();
        sport.put("type", "string");
        sport.put("enum", List.of(
            "BADMINTON", "FOOTBALL", "VOLLEYBALL", "TENNIS", "BASKETBALL", "SQUASH",
            "GYM_HEAVY", "GYM_AEROBIC", "SWIMMING", "TABLE_TENNIS", "DANCE", "POOL",
            "CYCLING", "MAGIC_MIRROR", "BOARD_GAME", "GYM", "YOGA", "PICKLEBALL", "SHUTTLECOCK"
        ));
        sport.put("description", "运动项目枚举名。必须与 campus 匹配:粤海校区用 GYM_HEAVY(一楼健身房)/GYM_AEROBIC(二楼健身房),不要用 GYM;丽湖校区用 GYM(健身房)。详见 description 中的完整映射。");
        properties.put("sport", sport);

        Map<String, Object> date = new LinkedHashMap<>();
        date.put("type", "string");
        date.put("format", "date");
        date.put("description", "ISO 8601 日期,例如 2026-06-24");
        properties.put("date", date);

        Map<String, Object> timeSlot = new LinkedHashMap<>();
        timeSlot.put("type", "string");
        timeSlot.put("pattern", "^([01]?[0-9]|2[0-3]):00-([01]?[0-9]|2[0-3]):00$");
        timeSlot.put("description", "预约时段,HH:mm-HH:mm 格式,只支持整点 1 小时时段(08:00-22:00),例如 16:00-17:00。禁止传对象。");
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

        Account account = accountResolver.apply(username);
        return clientFactory.apply(account).book(request, account);
    }
}

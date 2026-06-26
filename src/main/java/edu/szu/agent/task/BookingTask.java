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

        Map<String, Object> username = TaskInputSchema.property("string",
            "学号,例如 2023150090。若未提供,默认使用环境变量 SZU_USERNAME 配置的账号。",
            Map.of("pattern", "^20\\d{9}$", "examples", List.of("2023150090")));
        properties.put("username", username);

        Map<String, Object> campus = TaskInputSchema.enumProperty(
            "校区枚举名:YUEHAI(粤海)或 LIHU(丽湖)。",
            List.of("YUEHAI", "LIHU"),
            Map.of("examples", List.of("YUEHAI", "LIHU")));
        properties.put("campus", campus);

        Map<String, Object> sport = TaskInputSchema.enumProperty(
            "运动项目枚举名。必须与 campus 匹配:粤海校区用 GYM_HEAVY/GYM_AEROBIC,丽湖校区用 GYM。详见 description 中的完整映射。",
            List.of(
                "BADMINTON", "FOOTBALL", "VOLLEYBALL", "TENNIS", "BASKETBALL", "SQUASH",
                "GYM_HEAVY", "GYM_AEROBIC", "SWIMMING", "TABLE_TENNIS", "DANCE", "POOL",
                "CYCLING", "MAGIC_MIRROR", "BOARD_GAME", "GYM", "YOGA", "PICKLEBALL", "SHUTTLECOCK"
            ),
            Map.of("examples", List.of("TENNIS", "GYM_HEAVY", "GYM")));
        properties.put("sport", sport);

        Map<String, Object> date = TaskInputSchema.property("string",
            "ISO 8601 日期,例如 2026-06-24。",
            Map.of("format", "date", "examples", List.of("2026-06-24")));
        properties.put("date", date);

        Map<String, Object> timeSlot = TaskInputSchema.property("string",
            "预约时段,HH:mm-HH:mm 格式,只支持整点 1 小时时段(08:00-22:00),例如 16:00-17:00。禁止传对象。",
            Map.of(
                "pattern", "^([01]?[0-9]|2[0-3]):00-([01]?[0-9]|2[0-3]):00$",
                "examples", List.of("16:00-17:00", "19:00-20:00")
            ));
        properties.put("timeSlot", timeSlot);

        Map<String, Object> preferredVenue = TaskInputSchema.property("integer",
            "1-based 偏好序号,默认 1。对球场类项目指第几个可预约球场;对健身房类容量项目指第几个可用容量时段/区域。",
            Map.of("default", 1, "minimum", 1, "examples", List.of(1, 2)));
        properties.put("preferredVenue", preferredVenue);

        schema.put("properties", properties);
        schema.put("required", List.of("campus", "sport", "date", "timeSlot"));
        return schema;
    }

    @Override
    public ToolAnnotations annotations() {
        Map<String, Object> ex1 = new LinkedHashMap<>();
        ex1.put("username", "2023150090");
        ex1.put("campus", "YUEHAI");
        ex1.put("sport", "TENNIS");
        ex1.put("date", "2026-06-24");
        ex1.put("timeSlot", "19:00-20:00");
        ex1.put("preferredVenue", 1);

        Map<String, Object> ex2 = new LinkedHashMap<>();
        ex2.put("campus", "YUEHAI");
        ex2.put("sport", "GYM_HEAVY");
        ex2.put("date", "2026-06-24");
        ex2.put("timeSlot", "16:00-17:00");

        Map<String, Object> ex3 = new LinkedHashMap<>();
        ex3.put("campus", "LIHU");
        ex3.put("sport", "GYM");
        ex3.put("date", "2026-06-25");
        ex3.put("timeSlot", "20:00-21:00");

        Map<String, Object> ex4 = new LinkedHashMap<>();
        ex4.put("username", "2023150090");
        ex4.put("campus", "YUEHAI");
        ex4.put("sport", "BADMINTON");
        ex4.put("date", "2026-06-27");
        ex4.put("timeSlot", "14:00-15:00");
        ex4.put("preferredVenue", 2);

        Map<String, Object> ex5 = new LinkedHashMap<>();
        ex5.put("campus", "LIHU");
        ex5.put("sport", "BASKETBALL");
        ex5.put("date", "2026-06-28");
        ex5.put("timeSlot", "18:00-19:00");

        Map<String, Object> ex6 = new LinkedHashMap<>();
        ex6.put("campus", "YUEHAI");
        ex6.put("sport", "SWIMMING");
        ex6.put("date", "2026-06-29");
        ex6.put("timeSlot", "09:00-10:00");

        Map<String, Object> ex7 = new LinkedHashMap<>();
        ex7.put("campus", "LIHU");
        ex7.put("sport", "YOGA");
        ex7.put("date", "2026-06-30");
        ex7.put("timeSlot", "07:00-08:00");

        return ToolAnnotations.builder()
            .example(ex1)
            .example(ex2)
            .example(ex3)
            .example(ex4)
            .example(ex5)
            .example(ex6)
            .example(ex7)
            .resultShape("""
                BookingResult (sealed):
                - Success { request: BookingRequest, venueName: String, confirmationNo: String, message: String }
                - Failure { code: ErrorCode, message: String }
                BookingRequest 字段: username, campus, sport, date, timeSlot, preferredVenueIndex。
                注意:调用成功会真实占用预约名额,调用前必须获得用户明确确认。""")
            .commonError("sport 与 campus 不匹配(如 LIHU + GYM_HEAVY)→ INVALID_REQUEST;按 description 的校区映射修正")
            .commonError("timeSlot 传对象或口语 \"4-5点\" → INVALID_REQUEST;必须转为 \"16:00-17:00\"")
            .commonError("未注入账号凭证/会话过期 → ACCOUNT_RESOLUTION_FAILED 或 SESSION_EXPIRED;需 env/--env-file 或 headed 登录刷新")
            .build();
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

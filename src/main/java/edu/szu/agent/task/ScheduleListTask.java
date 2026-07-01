package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.EhallScheduleClient;
import edu.szu.agent.client.schedule.ResilientScheduleClient;
import edu.szu.agent.client.schedule.ScheduleListClient;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.ScheduleListResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * {@code schedule_list} CampusTask — queries the ehall schedule grid.
 *
 * <p>Parameter contract (string keys, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code username} (required) — student ID
 * </ul>
 *
 * <p>Routing (per PLAN-p1-real-fetch.md §4 阶段 1): 动态判断
 * <ul>
 *   <li>Default: real-fetch via {@link EhallScheduleClient} (Playwright +
 *       session reuse); on any failure (no session, CAS expired, page change,
 *       network) automatically falls back to the embedded static course list.
 *   <li>Env {@code SZU_SCHEDULE_REAL=0}: force the static path (skip the
 *       real client entirely; no Playwright, no network).
 * </ul>
 *
 * <p>Note: the default is "real with static fallback", not "static only".
 * The {@code SZU_SCHEDULE_REAL} switch is an explicit *opt-out*, not an
 * opt-in. See {@link #description()} for the LLM-facing contract.
 *
 * <p>// 编程技术: 泛型 / 枚举 / Lambda / 依赖注入
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class ScheduleListTask implements CampusTask<ScheduleListResult> {

    private final Function<Account, EhallScheduleClient> realClientFactory;
    private final Function<String, Account> accountResolver;
    private final boolean staticOnly;

    /**
     * No-arg constructor — kept for binary compatibility with callers that
     * predate the real-fetch path. The provided {@code staticOnly} client is
     * <strong>not</strong> retained: {@link #execute(TaskInput)} always
     * constructs a fresh {@link ScheduleListClient()} when running in
     * static-only mode, so all data served from this ctor is the embedded
     * snapshot regardless of what instance is passed in. New code should
     * use {@link #ScheduleListTask(Function)} so the real-fetch path is
     * honored.
     */
    public ScheduleListTask() {
        this(new ScheduleListClient());
    }

    /**
     * Production constructor — wires real-fetch (per-account) + static fallback.
     * Real client is invoked per {@link #execute(TaskInput)} call so the
     * resolved account's session is honored (mirrors {@link BookingTask}).
     */
    public ScheduleListTask(Function<Account, EhallScheduleClient> realClientFactory) {
        this(realClientFactory, AccountResolver::resolve,
            "0".equals(ConfigManager.getInstance().get("SZU_SCHEDULE_REAL")));
    }

    /**
     * Test constructor — inject custom resolver and force flag.
     */
    ScheduleListTask(Function<Account, EhallScheduleClient> realClientFactory,
                     Function<String, Account> accountResolver,
                     boolean staticOnly) {
        this.realClientFactory = Objects.requireNonNull(realClientFactory, "realClientFactory");
        this.accountResolver = Objects.requireNonNull(accountResolver, "accountResolver");
        this.staticOnly = staticOnly;
    }

    /**
     * Backward-compatible static-only constructor — kept so the existing
     * test suite and any pre-P1 callers continue to compile. The supplied
     * {@code staticOnly} client is <strong>not</strong> retained: the
     * field is only used as a null-check sentinel. {@link #execute(TaskInput)}
     * always constructs a fresh {@link ScheduleListClient()} in static-only
     * mode, so the snapshot served is always the embedded 8-course list
     * and never the caller's instance. Prefer
     * {@link #ScheduleListTask(Function)} for new code.
     *
     * @deprecated since 0.4.0 — the ctor argument is silently ignored. Use
     *     {@link #ScheduleListTask(Function)} for real-fetch or construct a
     *     {@link ScheduleListClient} directly and call {@link #list()} on it.
     */
    @Deprecated
    public ScheduleListTask(ScheduleListClient staticOnly) {
        this(account -> {
            throw new IllegalStateException(
                "static-only ScheduleListTask cannot build a real client");
        }, username -> {
            throw new IllegalStateException(
                "static-only ScheduleListTask has no account resolver");
        }, true);
        Objects.requireNonNull(staticOnly,
            "staticOnly client must not be null (argument is otherwise ignored)");
    }

    @Override
    public String name() {
        return "schedule_list";
    }

    @Override
    public String description() {
        return """
            查询学生本学期课表,返回周课表 grid(课程名、教师、时间地点)。
            重要约束(必须遵守,否则调用会失败或返回错误):
            1. 路由策略: 默认走真实抓取路径(ehall + Playwright + 30 天会话复用);真实路径失败时自动回退到静态 MVP(8 条硬编码课程)。设置环境变量 SZU_SCHEDULE_REAL=0 强制走静态路径(不发起浏览器请求)。
            2. username 是必填,深大学号格式 20XXXXXXX(11 位数字)。不要传中文姓名或昵称。
            3. 真实路径会调用 VenueBookingClient 同款的 AccountResolver(走进程 env / --env-file / Skill 注入三层查找)。当前 MCP daemon 模式下若没有 SZU_PASSWORD_<id> 注入会抛 AccountResolutionException,这是预期的 — 不要绕过凭证流。
            4. 当前实现只返回本学期(2025-2026 春季)的课表,不支持按周、按学期筛选;若需历史课表当前版本不支持,返回 8 条静态数据。
            5. 不传 username 会抛 IllegalArgumentException(MCPToolCallHandler 映射为 INVALID_REQUEST 错误码)。
            6. 真实路径可能因为 ehall 登录态过期而失败 — 错误响应中 errorCode 会含 SESSION_EXPIRED,需要用户先 headed 跑一次 booking 流程重新注入 session。
            7. 适合回答"我下周一上什么课?"、"某老师什么时候上课?"等问题(对返回的 courses 数组做 client-side 过滤)。
            """;
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> username = TaskInputSchema.property("string",
            "深大学号,11 位数字,例如 2023150090。必填。",
            Map.of(
                "pattern", "^20\\d{9}$",
                "examples", List.of("2023150090")
            ));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("username", username);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("username"));
        return schema;
    }

    @Override
    public ToolAnnotations annotations() {
        Map<String, Object> ex1 = new LinkedHashMap<>();
        ex1.put("username", "2023150090");

        // 同样的合法输入,演示 11 位数字;LLM 错传学号格式时第一行就是反例
        Map<String, Object> ex2 = new LinkedHashMap<>();
        ex2.put("username", "2030200100");

        return ToolAnnotations.builder()
            .example(ex1)
            .example(ex2)
            .resultShape("""
                ScheduleListResult (sealed):
                - Success { courses: List<CourseEntry>, snapshotAt: LocalDateTime }
                - Failure { errorCode: ErrorCode, message: String }
                CourseEntry 字段: courseName, teacher, weekday(1-7), period(1-12),
                weeks(Set<Integer>,例如 {1,2,3,...,17}), location(教学楼+教室)。""")
            .commonError("MCP daemon 模式 + 未注入凭证 → ACCOUNT_RESOLUTION_FAILED;需 Skill wrapper 传 --env-file")
            .commonError("传中文姓名或学号格式错(非 11 位数字)→ INVALID_REQUEST,errorMessage 含 \"username\"")
            .commonError("真实路径 SESSION_EXPIRED → 用户需 headed 跑 booking 流程刷新 session")
            .build();
    }

    @Override
    public ScheduleListResult execute(TaskInput input) {
        input.require("username");
        String username = input.get("username");
        ScheduleListClient fallback = new ScheduleListClient();
        if (staticOnly) {
            return fallback.list();
        }
        Account account = accountResolver.apply(username);
        EhallScheduleClient real = realClientFactory.apply(account);
        return new ResilientScheduleClient(real, fallback).list();
    }
}

package edu.szu.agent.task;

import edu.szu.agent.client.notice.NoticeListClient;
import edu.szu.agent.client.notice.ResilientNoticeClient;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.notice.Notice;
import edu.szu.agent.domain.notice.NoticeCategory;
import edu.szu.agent.domain.notice.NoticeListResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@code notice_list} CampusTask — lists SZU board (公文通) notices.
 *
 * <p>Parameter contract (string keys, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code username} (required) — student ID
 *   <li>{@code category} (optional) — ANNOUNCEMENT / LECTURE /
 *       COMPETITION / PUBLICITY
 *   <li>{@code daysBack} (optional, default 30) — only return notices
 *       published within the last N days
 * </ul>
 *
 * <p>Routing (per PLAN-p1-real-fetch.md §5 阶段 2): 动态判断
 * <ul>
 *   <li>Default: real-fetch via Playwright + the public board list page;
 *       on any failure (no HAR calibrated, network down, page change)
 *       automatically falls back to the embedded static snapshot.
 *   <li>Env {@code SZU_NOTICE_REAL=0}: force the static path (skip the
 *       real client entirely; no Playwright, no network).
 * </ul>
 *
 * <p>Note: the default is "real with static fallback", not "static only".
 * The {@code SZU_NOTICE_REAL} switch is an explicit *opt-out*, not an
 * opt-in. See {@link #description()} for the LLM-facing contract.
 *
 * <p>// 编程技术: 泛型 / 枚举 / Lambda / 依赖注入
 *
 * @since 0.6.0
 * @author 王子豪
 */
public class NoticeTask implements CampusTask<NoticeListResult> {

    private static final int DEFAULT_DAYS_BACK = 30;

    private final Supplier<NoticeListClient> realClientSupplier;
    private final Supplier<NoticeListClient> fallbackSupplier;
    private final boolean staticOnly;

    /**
     * No-arg constructor — kept for binary compatibility with callers that
     * predate the real-fetch path. Uses static-only mode (no Playwright,
     * no real fetch).
     */
    public NoticeTask() {
        this(() -> {
            throw new IllegalStateException(
                "static-only NoticeTask cannot build a real client");
        }, NoticeListClient::new, true);
    }

    /**
     * Production constructor — wires real-fetch + static fallback. Both
     * suppliers are invoked per {@link #execute(TaskInput)} call so the
     * fresh provider is honored (mirrors {@link ScheduleListTask}).
     *
     * @param realClientSupplier builds a {@link NoticeListClient} bound
     *     to a fresh Playwright-backed provider
     * @param fallbackSupplier   builds the static fallback client (snapshot)
     */
    public NoticeTask(Supplier<NoticeListClient> realClientSupplier,
                      Supplier<NoticeListClient> fallbackSupplier) {
        this(realClientSupplier, fallbackSupplier,
            "0".equals(ConfigManager.getInstance().get("SZU_NOTICE_REAL")));
    }

    /**
     * Full constructor — used by tests to inject custom suppliers and
     * force the static-only flag.
     */
    NoticeTask(Supplier<NoticeListClient> realClientSupplier,
               Supplier<NoticeListClient> fallbackSupplier,
               boolean staticOnly) {
        this.realClientSupplier = Objects.requireNonNull(realClientSupplier, "realClientSupplier");
        this.fallbackSupplier = Objects.requireNonNull(fallbackSupplier, "fallbackSupplier");
        this.staticOnly = staticOnly;
    }

    /**
     * Backward-compatible constructor — used by the existing test suite
     * that supplies a pre-built static client. The supplied
     * {@code staticOnly} client is <strong>not</strong> retained: the
     * field is only used as a sentinel. {@link #execute(TaskInput)} always
     * constructs a fresh {@link NoticeListClient()} in static-only mode,
     * so the snapshot served is always the embedded one and never the
     * caller's instance. Prefer
     * {@link #NoticeTask(Supplier, Supplier)} for new code.
     *
     * @deprecated since 0.4.0 — the ctor argument is silently ignored.
     *     Use {@link #NoticeTask(Supplier, Supplier)} for real-fetch.
     */
    @Deprecated
    public NoticeTask(NoticeListClient staticOnly) {
        this(() -> {
            throw new IllegalStateException(
                "static-only NoticeTask cannot build a real client");
        }, NoticeListClient::new, true);
        Objects.requireNonNull(staticOnly,
            "staticOnly client must not be null (argument is otherwise ignored)");
    }

    @Override
    public String name() {
        return "notice_list";
    }

    @Override
    public String description() {
        return """
            查询深圳大学公文通通知列表,返回公告、讲座、竞赛、公示/生活服务等通知摘要。
            重要约束(必须遵守,否则调用会失败或返回空):
            1. 路由策略: 默认走真实抓取路径(https://www1.szu.edu.cn/board/ + Playwright,公开页无需登录);
               真实路径失败时自动回退到静态 MVP 快照解析。设置环境变量 SZU_NOTICE_REAL=0 强制走静态路径(不发起浏览器请求)。
               当前 PlaywrightNoticeFetchProvider 占位实现,等用户提供 https://www1.szu.edu.cn/board/ 的真实 HAR 后
               校准 selector 才会有真实数据返回;无 HAR 时真实路径会抛 NOTICE_FETCH_FAILED 并自动回退到 snapshot。
            2. username 是必填字段,用于保持与未来真实抓取路径一致。当前静态 MVP 不会登录、不发起浏览器请求,
               但仍要求传学号以避免外部 Agent 形成错误习惯。
            3. category 可选,枚举值固定 4 个: ANNOUNCEMENT(教务教学/科研动态/党务行政)、LECTURE(学术讲座)、
               COMPETITION(竞赛/活动征集)、PUBLICITY(学生工作/校园生活/后勤服务)。必须使用大写英文枚举。
            4. daysBack 可选,默认 30,必须 > 0。它按 publishedAt 与当前日期过滤最近 N 天通知。
            5. 当前实现使用内置 HTML 快照解析,不会实时访问公文通,因此结果可能不是最新公告。
            6. 返回每条 Notice 的 id、title、category、publishedAt、url、hasAttachment。hasAttachment 由标题关键词
               (如 附件/下载/申请表)启发式判断,不保证完全准确。
            7. 如果用户只问"最近有什么通知",传 username + daysBack 即可,不要臆造 category。
            8. 如果用户问"讲座"、"竞赛"、"公示"等明确类型,再传对应 category 过滤。
            """;
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> username = TaskInputSchema.property("string",
            "深大学号,11 位数字,例如 2023150090。必填。",
            Map.of("pattern", "^20\\d{9}$", "examples", List.of("2023150090")));
        Map<String, Object> category = TaskInputSchema.enumProperty(
            "可选分类过滤。ANNOUNCEMENT=公告/教务,LECTURE=讲座,COMPETITION=竞赛,PUBLICITY=公示/生活服务。",
            List.of("ANNOUNCEMENT", "LECTURE", "COMPETITION", "PUBLICITY"),
            Map.of("examples", List.of("LECTURE", "COMPETITION")));
        Map<String, Object> daysBack = TaskInputSchema.property("integer",
            "查询最近 N 天,默认 30。必须 > 0。",
            Map.of("default", 30, "minimum", 1, "examples", List.of(7, 30)));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("username", username);
        properties.put("category", category);
        properties.put("daysBack", daysBack);

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
        ex1.put("daysBack", 7);
        Map<String, Object> ex2 = new LinkedHashMap<>();
        ex2.put("username", "2023150090");
        ex2.put("category", "LECTURE");
        ex2.put("daysBack", 30);
        Map<String, Object> ex3 = new LinkedHashMap<>();
        ex3.put("username", "2023150090");
        ex3.put("category", "COMPETITION");

        return ToolAnnotations.builder()
            .example(ex1)
            .example(ex2)
            .example(ex3)
            .resultShape("""
                NoticeListResult (sealed):
                - Success { notices: List<Notice>, snapshotAt: Instant }
                - Failure { errorCode: ErrorCode, message: String }
                Notice 字段: id, title, category(ANNOUNCEMENT/LECTURE/COMPETITION/PUBLICITY),
                publishedAt(LocalDate), url, hasAttachment(boolean)。""")
            .commonError("缺 username → INVALID_REQUEST;即使静态 MVP 也必须传学号")
            .commonError("category=\"讲座\" → INVALID_REQUEST;应传 \"LECTURE\"")
            .commonError("daysBack=0 或负数 → INVALID_REQUEST(\"daysBack must be positive\")")
            .commonError("PlaywrightNoticeFetchProvider 无 HAR 校准 → NOTICE_FETCH_FAILED "
                + "(已自动回退到 snapshot,不会冒泡到 CLI)")
            .build();
    }

    @Override
    public NoticeListResult execute(TaskInput input) {
        input.require("username");

        final NoticeCategory categoryFilter;
        String categoryValue = input.get("category");
        if (categoryValue != null && !categoryValue.isBlank()) {
            try {
                categoryFilter = NoticeCategory.valueOf(categoryValue.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "Invalid category: " + categoryValue + ". Valid: ANNOUNCEMENT, LECTURE, COMPETITION, PUBLICITY");
            }
        } else {
            categoryFilter = null;
        }

        int daysBack = input.getInt("daysBack", DEFAULT_DAYS_BACK);
        if (daysBack <= 0) {
            throw new IllegalArgumentException("daysBack must be positive");
        }

        NoticeListResult raw = fetch();
        if (raw instanceof NoticeListResult.Failure f) {
            return f;
        }
        NoticeListResult.Success s = (NoticeListResult.Success) raw;

        LocalDate cutoff = LocalDate.now().minusDays(daysBack);
        List<Notice> filtered = s.notices().stream()
            .filter(n -> n.publishedAt().isAfter(cutoff.minusDays(1)))
            .filter(n -> categoryFilter == null || n.category() == categoryFilter)
            .toList();
        return new NoticeListResult.Success(filtered, Instant.now());
    }

    /**
     * Runs the resilient fetch: real client first, then static fallback.
     * Never returns null; on dual failure (real throws and static parse
     * fails) returns the static parse failure.
     */
    private NoticeListResult fetch() {
        NoticeListClient fallback = fallbackSupplier.get();
        if (staticOnly) {
            return fallback.list();
        }
        NoticeListClient real = realClientSupplier.get();
        return new ResilientNoticeClient(real, fallback).list();
    }
}

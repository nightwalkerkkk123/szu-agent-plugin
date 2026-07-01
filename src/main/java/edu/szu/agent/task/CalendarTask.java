package edu.szu.agent.task;

import edu.szu.agent.client.calendar.ResilientCalendarClient;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.calendar.AcademicEvent;
import edu.szu.agent.domain.calendar.CalendarListResult;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@code calendar_get} CampusTask — returns the SZU academic calendar.
 *
 * <p>Parameter contract (string keys, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code academicYear} (optional) — e.g. "2025-2026"; defaults to
 *       the current academic year inferred from the system date</li>
 * </ul>
 *
 * <p>Routing (per PLAN-p1-real-fetch.md §5 阶段 3): 动态判断
 * <ul>
 *   <li>Default: real-fetch via Playwright on the public SZU official
 *       calendar page ({@code https://www.szu.edu.cn/xxgk/xl.htm}); any
 *       failure (network, timeout, selector mismatch, or empty parse result
 *       since the page currently renders PNGs) automatically falls back to
 *       the embedded 2025-2026 spring static snapshot.
 *   <li>Env {@code SZU_CALENDAR_REAL=0}: force the static path (no
 *       Playwright, no network).
 * </ul>
 *
 * <p>The default is "real with static fallback", not "static only".
 * The {@code SZU_CALENDAR_REAL} switch is an explicit *opt-out*.
 *
 * <p>// 编程技术: 泛型 / 枚举 / Lambda / 依赖注入 / 密封类型模式匹配
 *
 * @since 0.3.0
 * @author 王子豪
 */
public class CalendarTask implements CampusTask<CalendarListResult> {

    private static final String DEFAULT_YEAR = "2025-2026";
    private static final String SEMESTER_TAG = "2025-2026-SPRING";

    private final Supplier<List<AcademicEvent>> realSupplier;
    private final Supplier<List<AcademicEvent>> fallbackSupplier;
    private final boolean staticOnly;

    /**
     * No-arg constructor — kept for binary compatibility with callers that
     * predate the real-fetch path. Uses static-only mode.
     */
    public CalendarTask() {
        this(() -> {
            throw new IllegalStateException(
                "static-only CalendarTask cannot build a real supplier");
        }, CalendarTask::spring2026Events, true);
    }

    /**
     * Production constructor — wires real-fetch + static fallback. Both
     * suppliers are invoked per {@link #execute(TaskInput)} call so the
     * fresh provider is honored (mirrors {@link NoticeTask} / {@link
     * ScheduleListTask}).
     *
     * @param realSupplier     returns parsed events from the live page
     *                         (may throw RuntimeException)
     * @param fallbackSupplier returns the static snapshot (must not throw)
     */
    public CalendarTask(Supplier<List<AcademicEvent>> realSupplier,
                        Supplier<List<AcademicEvent>> fallbackSupplier) {
        this(realSupplier, fallbackSupplier,
            "0".equals(ConfigManager.getInstance().get("SZU_CALENDAR_REAL")));
    }

    /**
     * Full constructor — used by tests to inject custom suppliers and
     * force the static-only flag.
     */
    CalendarTask(Supplier<List<AcademicEvent>> realSupplier,
                 Supplier<List<AcademicEvent>> fallbackSupplier,
                 boolean staticOnly) {
        this.realSupplier = Objects.requireNonNull(realSupplier, "realSupplier");
        this.fallbackSupplier = Objects.requireNonNull(fallbackSupplier, "fallbackSupplier");
        this.staticOnly = staticOnly;
    }

    @Override
    public String name() {
        return "calendar_get";
    }

    @Override
    public String description() {
        return """
            查询深圳大学校历,返回本学期的关键时间节点(开学、节假日、考试周、毕业、暑假等)。
            重要约束(必须遵守,否则调用会失败或返回空):
            1. 路由策略: 默认走真实抓取路径(https://www.szu.edu.cn/xxgk/xl.htm + Playwright,公开页无需登录);
               真实路径失败或解析为空(当前页面渲染为 PNG 图像,无可解析文本)时自动回退到 2025-2026 春季学期静态 MVP。
               设置环境变量 SZU_CALENDAR_REAL=0 强制走静态路径(不发起浏览器请求)。
            2. academicYear 可选,默认按当前系统日期推断(1-7 月取上一学年,8-12 月取本学年)。传其他值(如 "2024-2025")会返回空列表,而非报错 — 调用方应自行判断是否需要重试。
            3. 学年格式严格为 "YYYY-YYYY",中间用半角连字符,例如 "2025-2026"。不要传 "2025" 或 "2025-2026 学年"。
            4. 返回的事件类型 (type) 固定为下列枚举之一:SEMESTER_START / SEMESTER_END / EXAM_WEEK / BREAK / HOLIDAY。LLM 不应臆造新类型。
            5. 不需要 username、密码或浏览器 cookie — 这是公开页静态查询工具。
            6. 调用前无需用户授权,因为不涉及任何账号行为或写操作。
            7. 适合回答"这学期什么时候开学?"、"什么时候放暑假?"、"期末考试第几周?"等问题。
            """;
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> yearProp = TaskInputSchema.property("string",
            "学年,格式 YYYY-YYYY,例如 2025-2026。可选,不传则按当前日期推断。",
            Map.of(
                "pattern", "^\\d{4}-\\d{4}$",
                "examples", List.of("2025-2026", "2024-2025")
            ));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("academicYear", yearProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    @Override
    public ToolAnnotations annotations() {
        Map<String, Object> ex1 = new LinkedHashMap<>();
        ex1.put("academicYear", "2025-2026");

        Map<String, Object> ex2 = new LinkedHashMap<>();
        ex2.put("academicYear", "2024-2025");

        Map<String, Object> ex3 = new LinkedHashMap<>();
        // ex3 intentionally empty — demonstrates "use inferred year" path

        Map<String, Object> ex4 = new LinkedHashMap<>();
        ex4.put("academicYear", "2023-2024");

        Map<String, Object> ex5 = new LinkedHashMap<>();
        ex5.put("academicYear", "2026-2027");

        return ToolAnnotations.builder()
            .example(ex1)
            .example(ex2)
            .example(ex3)
            .example(ex4)
            .example(ex5)
            .resultShape("""
                CalendarListResult (sealed):
                - Success { events: List<AcademicEvent>, snapshotAt: Instant }
                - Failure { errorCode: ErrorCode, message: String }
                AcademicEvent 字段: date(LocalDate ISO-8601,例如 "2026-03-04"),
                type(枚举 SEMESTER_START/SEMESTER_END/EXAM_WEEK/BREAK/HOLIDAY),
                description(中文一句话), semester(学期标记,例如 "2025-2026-SPRING")。
                真实路径成功时包含 OFFICIAL-PAGE 标记的事件;回退静态时全部标记为 2025-2026-SPRING。""")
            .commonError("传 academicYear=\"2025\"(缺后缀)→ 返回 [];应改为 \"2025-2026\"")
            .commonError("问\"明天有什么校历事件\"→ calendar_get 不支持单日查询;用 date 过滤结果")
            .commonError("传不存在的学年(如 \"2099-2100\")→ 返回 [] 而非报错;调用方应自行 fallback 到默认年")
            .build();
    }

    @Override
    public CalendarListResult execute(TaskInput input) {
        String year = input.get("academicYear");
        if (year == null || year.isBlank()) {
            year = defaultAcademicYear();
        }
        if (!DEFAULT_YEAR.equals(year)) {
            // MVP: only the 2025-2026 calendar is embedded; other years
            // intentionally return an empty success (matches the previous
            // behavior).
            return new CalendarListResult.Success(List.of(), java.time.Instant.now());
        }

        CalendarListResult raw = fetch();
        if (raw instanceof CalendarListResult.Success s) {
            return s;
        }
        return raw;
    }

    /**
     * Infers the current academic year from the system date. Between January
     * and July the current year is the spring semester of the previous/fall
     * year; between August and December it is the fall semester of the
     * current year.
     */
    public static String defaultAcademicYear() {
        LocalDate now = LocalDate.now();
        if (now.getMonthValue() <= 7) {
            return (now.getYear() - 1) + "-" + now.getYear();
        }
        return now.getYear() + "-" + (now.getYear() + 1);
    }

    /**
     * Runs the resilient fetch: real supplier first, then static fallback.
     * Never returns null; on dual failure (real throws and static returns
     * empty) returns a Success with the static snapshot.
     *
     * <p>The fallback supplier is evaluated lazily — at most once per call,
     * only when the static snapshot is actually needed (the resilient wrapper
     * invokes it on real-failure; staticOnly invokes it directly).
     */
    private CalendarListResult fetch() {
        if (staticOnly) {
            return new CalendarListResult.Success(
                fallbackSupplier.get(), java.time.Instant.now());
        }
        return new ResilientCalendarClient(realSupplier, fallbackSupplier).list();
    }

    /**
     * Static events parsed from 《深圳大学 2025-2026 学年第二学期校历说明》.
     * Public so {@link edu.szu.agent.cli.CalendarCommand#defaultTask()} can
     * use it as a fallback supplier method reference.
     */
    public static List<AcademicEvent> spring2026Events() {
        List<AcademicEvent> events = new java.util.ArrayList<>();

        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 4),
            edu.szu.agent.domain.calendar.AcademicEventType.SEMESTER_START,
            "2025-2026学年第二学期起：3月4日至7月17日",
            SEMESTER_TAG));

        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 4),
            edu.szu.agent.domain.calendar.AcademicEventType.SEMESTER_START,
            "全体教职工上班报到（星期三）",
            SEMESTER_TAG));

        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 5),
            edu.szu.agent.domain.calendar.AcademicEventType.SEMESTER_START,
            "学生报到、注册（星期四）；开学时间",
            SEMESTER_TAG));

        for (int day = 4; day <= 8; day++) {
            events.add(AcademicEvent.of(
                LocalDate.of(2026, 3, day),
                edu.szu.agent.domain.calendar.AcademicEventType.EXAM_WEEK,
                "本科生缓考、补考；研究生缓考",
                SEMESTER_TAG));
        }

        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 9),
            edu.szu.agent.domain.calendar.AcademicEventType.SEMESTER_START,
            "开始上课（星期一）",
            SEMESTER_TAG));

        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 9),
            edu.szu.agent.domain.calendar.AcademicEventType.SEMESTER_START,
            "本科生上课周数：第一至十七周（3月9日~7月3日）",
            SEMESTER_TAG));
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 7, 3),
            edu.szu.agent.domain.calendar.AcademicEventType.SEMESTER_START,
            "本科生第十七周上课结束",
            SEMESTER_TAG));

        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 9),
            edu.szu.agent.domain.calendar.AcademicEventType.SEMESTER_START,
            "研究生部分课程上课周数：第一至十二周（3月9日~5月29日）",
            SEMESTER_TAG));
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 5, 29),
            edu.szu.agent.domain.calendar.AcademicEventType.SEMESTER_START,
            "研究生部分课程第十二周上课结束",
            SEMESTER_TAG));

        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 9),
            edu.szu.agent.domain.calendar.AcademicEventType.SEMESTER_START,
            "研究生部分课程上课周数：第一至十六周（3月9日~6月26日）",
            SEMESTER_TAG));
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 6, 26),
            edu.szu.agent.domain.calendar.AcademicEventType.SEMESTER_START,
            "研究生部分课程第十六周上课结束",
            SEMESTER_TAG));

        for (int day = 6; day <= 17; day++) {
            events.add(AcademicEvent.of(
                LocalDate.of(2026, 7, day),
                edu.szu.agent.domain.calendar.AcademicEventType.EXAM_WEEK,
                "本科生期末考试（第十八、十九周）",
                SEMESTER_TAG));
        }

        events.add(AcademicEvent.of(
            LocalDate.of(2026, 6, 27),
            edu.szu.agent.domain.calendar.AcademicEventType.EXAM_WEEK,
            "研究生期末考试：课程结束两周内进行（示例起始日）",
            SEMESTER_TAG));

        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 9),
            edu.szu.agent.domain.calendar.AcademicEventType.SEMESTER_START,
            "本科生毕业论文及答辩：第1~10周（3月9日~5月14日）",
            SEMESTER_TAG));
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 5, 15),
            edu.szu.agent.domain.calendar.AcademicEventType.SEMESTER_START,
            "研究生学位论文答辩：第10周以前完成（5月15日前）",
            SEMESTER_TAG));

        events.add(AcademicEvent.of(
            LocalDate.of(2026, 6, 26),
            edu.szu.agent.domain.calendar.AcademicEventType.HOLIDAY,
            "毕业典礼安排",
            SEMESTER_TAG));

        events.add(AcademicEvent.of(
            LocalDate.of(2026, 7, 18),
            edu.szu.agent.domain.calendar.AcademicEventType.BREAK,
            "暑假开始：7月18日~8月27日",
            SEMESTER_TAG));
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 8, 27),
            edu.szu.agent.domain.calendar.AcademicEventType.BREAK,
            "暑假结束",
            SEMESTER_TAG));

        events.add(AcademicEvent.of(
            LocalDate.of(2026, 5, 1),
            edu.szu.agent.domain.calendar.AcademicEventType.HOLIDAY,
            "五一社会实践周、法定休假日等具体安排另行通知",
            SEMESTER_TAG));

        return List.copyOf(events);
    }
}
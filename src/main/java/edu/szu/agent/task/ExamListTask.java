package edu.szu.agent.task;

import edu.szu.agent.client.exam.ExamListClient;
import edu.szu.agent.client.exam.ResilientExamListClient;
import edu.szu.agent.client.exam.PlaywrightExamFetchProvider;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.exam.ExamSchedule;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@code exam_list} CampusTask — lists SZU exam schedules.
 *
 * <p>Routing (per P1 real-fetch plan):
 * <ul>
 *   <li>Default: real-fetch via Playwright on the authenticated ehall exam page
 *       ({@code https://ehall.szu.edu.cn/gsapp/sys/jzxk-cx-wd/default/ksap.wsx});
 *       any failure (network, timeout, selector mismatch, session expired, or
 *       empty parse result) automatically falls back to the embedded static
 *       snapshot that ships with the build.</li>
 *   <li>Env {@code SZU_EXAM_REAL=0}: force the static path (no Playwright,
 *       no network, uses the embedded snapshot only).</li>
 * </ul>
 *
 * <p>The default is "real with static fallback", not "static only".
 * The {@code SZU_EXAM_REAL} switch is an explicit *opt-out*.
 *
 * <p>Parameter contract (string keys, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code username} (required) — student ID</li>
 *   <li>{@code status} (optional) — exam status filter (待开始考试/已结束)</li>
 * </ul>
 *
 * <p>// 编程技术: 泛型 / 枚举 / Lambda / 依赖注入 / 不可变性
 *
 * @since 0.6.0
 * @author 王子豪
 */
public class ExamListTask implements CampusTask<List<ExamSchedule>> {

    private final ExamListClient staticClient;
    private final Supplier<List<ExamSchedule>> realSupplier;
    private final Supplier<List<ExamSchedule>> fallbackSupplier;
    private final boolean staticOnly;
    private final Clock clock;

    /**
     * No-arg constructor — kept for binary compatibility. Uses static-only mode
     * and the system clock.
     */
    public ExamListTask() {
        this(new ExamListClient());
    }

    /**
     * Original constructor for static-only mode — kept for backward
     * compatibility with existing tests and callers.
     *
     * @param staticClient the static client to use exclusively
     */
    public ExamListTask(ExamListClient staticClient) {
        this(staticClient, Clock.systemDefaultZone());
    }

    /**
     * Static-only constructor with an injectable clock for deterministic
     * tests.
     *
     * @param staticClient the static client to use exclusively
     * @param clock        clock used to decide "pending" vs. "finished"
     */
    public ExamListTask(ExamListClient staticClient, Clock clock) {
        this.staticClient = Objects.requireNonNull(staticClient, "staticClient");
        this.realSupplier = () -> {
            throw new IllegalStateException(
                "static-only ExamListTask cannot build a real supplier");
        };
        this.fallbackSupplier = staticClient::list;
        this.staticOnly = true;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Production constructor — wires real-fetch + static fallback. Both
     * suppliers are invoked per {@link #execute(TaskInput)} call so the
     * fresh provider is honored (mirrors {@link CalendarTask} / {@link
     * NoticeTask}).
     *
     * @param realSupplier     returns parsed exams from the live page
     *                         (may throw RuntimeException)
     * @param fallbackSupplier returns the static snapshot (must not throw)
     */
    public ExamListTask(Supplier<List<ExamSchedule>> realSupplier,
                        Supplier<List<ExamSchedule>> fallbackSupplier) {
        this(realSupplier, fallbackSupplier,
            "0".equals(ConfigManager.getInstance().get("SZU_EXAM_REAL")),
            Clock.systemDefaultZone());
    }

    /**
     * Full constructor — used by tests to inject custom suppliers and
     * force the static-only flag.
     */
    ExamListTask(Supplier<List<ExamSchedule>> realSupplier,
                 Supplier<List<ExamSchedule>> fallbackSupplier,
                 boolean staticOnly) {
        this(realSupplier, fallbackSupplier, staticOnly, Clock.systemDefaultZone());
    }

    /**
     * Full constructor with clock injection.
     */
    ExamListTask(Supplier<List<ExamSchedule>> realSupplier,
                 Supplier<List<ExamSchedule>> fallbackSupplier,
                 boolean staticOnly,
                 Clock clock) {
        this.staticClient = null; // not used in this path
        this.realSupplier = Objects.requireNonNull(realSupplier, "realSupplier");
        this.fallbackSupplier = Objects.requireNonNull(fallbackSupplier, "fallbackSupplier");
        this.staticOnly = staticOnly;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return "exam_list";
    }

    @Override
    public String description() {
        return """
            查询深圳大学考试安排列表,返回课程考试日期、时间、地点、课程代码和监考教师。
            重要约束(必须遵守,否则调用会失败或返回空):
            1. 路由策略: 默认走真实抓取路径(已登录 ehall 考试安排页 + Playwright,需要有效的 CAS 会话);
               真实路径失败(会话过期/网络超时/选择器不匹配)或解析为空时自动回退到内置静态快照。
               设置环境变量 SZU_EXAM_REAL=0 强制走静态路径(不发起浏览器请求)。
            2. username 是必填字段,用于会话选择(对应 ehall 中存储的个人考试安排)。
            3. status 可选,枚举值固定两个中文字符串:"待开始考试" 或 "已结束"。不要传英文 PENDING、FINISHED,
               也不要传"未开始"、"已考完"等同义词。
            4. 不传 status 表示返回全部考试安排;传 status 后按 examDate 与当前日期比较过滤。
            5. 返回的 ExamSchedule 包含 date(原始中文月日)、weekday、courseName、courseCode、examDate、
               startTime、endTime、venue、invigilator。调用方可自行按课程名/日期做二次筛选。
            6. 静态回退快照不保证实时更新,也不支持按学期、课程名或周次作为服务器端参数过滤。
            7. 适合回答"我有哪些考试?"、"操作系统考试在哪?"、"还有哪些未开始考试?"等问题。
            8. 如果用户只说"考试安排",只传 username;不要为了过滤而臆造 status。
            """;
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> username = TaskInputSchema.property("string",
            "深大学号,11 位数字,例如 2023150090。必填。",
            Map.of("pattern", "^20\\d{9}$", "examples", List.of("2023150090")));
        Map<String, Object> status = TaskInputSchema.enumProperty(
            "考试状态筛选。不传则返回全部。只能是中文枚举: 待开始考试 / 已结束。",
            List.of("待开始考试", "已结束"),
            Map.of("examples", List.of("待开始考试", "已结束")));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("username", username);
        properties.put("status", status);

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

        Map<String, Object> ex2 = new LinkedHashMap<>();
        ex2.put("username", "2023150090");
        ex2.put("status", "待开始考试");

        Map<String, Object> ex3 = new LinkedHashMap<>();
        ex3.put("username", "2023150090");
        ex3.put("status", "已结束");

        Map<String, Object> ex4 = new LinkedHashMap<>();
        ex4.put("username", "2030200100");

        Map<String, Object> ex5 = new LinkedHashMap<>();
        ex5.put("username", "2023150090");
        ex5.put("status", "已结束");
        // 演示:同一 username + 不同 status,LLM 必须用 status 而不是别的字段过滤

        return ToolAnnotations.builder()
            .example(ex1)
            .example(ex2)
            .example(ex3)
            .example(ex4)
            .example(ex5)
            .resultShape("""
                List<ExamSchedule>:
                - date: 原始月日文本,例如 "7月14日"
                - weekday: 星期文本,例如 "星期二"
                - courseName/courseCode: 课程名与课程代码
                - examDate: LocalDate(ISO 8601)
                - startTime/endTime: LocalTime
                - venue: 考试地点
                - invigilator: 监考教师
                真实路径成功时使用实时数据;会话失效或网络失败时返回静态快照数据。""")
            .commonError("status=\"未开始\" → 当前实现不会过滤;应传 \"待开始考试\"")
            .commonError("缺 username → INVALID_REQUEST;必须传学号")
            .commonError("用户按课程名查询 → exam_list 不支持 courseName 参数;先取列表再由调用方过滤")
            .commonError("会话过期/无持久化登录态 → 真实抓取失败,自动回退到静态快照")
            .build();
    }

    @Override
    public List<ExamSchedule> execute(TaskInput input) {
        input.require("username");

        String status = input.get("status");

        List<ExamSchedule> all = fetch();

        return all.stream()
            .filter(e -> status == null || status.isBlank() || matchesStatus(e, status))
            .toList();
    }

    /**
     * Runs the resilient fetch: real supplier first, then static fallback.
     * Never returns null; on dual failure (real throws and static returns
     * empty) returns the static snapshot list.
     *
     * <p>The fallback supplier is evaluated lazily — at most once per call,
     * only when the static snapshot is actually needed.
     */
    private List<ExamSchedule> fetch() {
        if (staticOnly) {
            return fallbackSupplier.get();
        }
        return new ResilientExamListClient(realSupplier, fallbackSupplier).list();
    }

    private boolean matchesStatus(ExamSchedule exam, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return switch (status.trim()) {
            case "待开始考试" -> exam.examDate().isAfter(LocalDate.now(clock).minusDays(1));
            case "已结束" -> exam.examDate().isBefore(LocalDate.now(clock));
            default -> true;
        };
    }
}
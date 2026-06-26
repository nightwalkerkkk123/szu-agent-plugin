package edu.szu.agent.task;

import edu.szu.agent.client.exam.ExamListClient;
import edu.szu.agent.domain.exam.ExamSchedule;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code exam_list} CampusTask — lists SZU exam schedules.
 *
 * <p>This MVP implementation ships with a static snapshot of the exam
 * schedule page and parses it without launching a browser. The snapshot
 * can later be replaced by an HTTP fetch after CAS login.
 *
 * <p>Parameter contract (string keys, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code username} (required) — student ID</li>
 *   <li>{@code status} (optional) — exam status filter (待开始考试/已结束)</li>
 * </ul>
 *
 * // 编程技术: 泛型 / Lambda
 *
 * @since 0.4.0
 * @author 王子豪
 */
public class ExamListTask implements CampusTask<List<ExamSchedule>> {

    private final ExamListClient client;

    public ExamListTask() {
        this(new ExamListClient());
    }

    /**
     * Test constructor — inject a custom client.
     */
    public ExamListTask(ExamListClient client) {
        this.client = client;
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
            1. username 是必填字段。当前实现使用静态 HTML 快照解析,不登录教务系统,但保留 username 以对齐
               未来真实抓取路径和 MCP/CLI 参数契约。
            2. status 可选,枚举值固定两个中文字符串:"待开始考试" 或 "已结束"。不要传英文 PENDING、FINISHED,
               也不要传"未开始"、"已考完"等同义词。
            3. 不传 status 表示返回全部考试安排;传 status 后按 examDate 与当前日期比较过滤。
            4. 返回的 ExamSchedule 包含 date(原始中文月日)、weekday、courseName、courseCode、examDate、
               startTime、endTime、venue、invigilator。调用方可自行按课程名/日期做二次筛选。
            5. 当前静态 MVP 不保证实时更新,也不支持按学期、课程名或周次作为服务器端参数过滤。
            6. 适合回答"我有哪些考试?"、"操作系统考试在哪?"、"还有哪些未开始考试?"等问题。
            7. 如果用户只说"考试安排",只传 username;不要为了过滤而臆造 status。
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
                - invigilator: 监考教师""")
            .commonError("status=\"未开始\" → 当前实现不会过滤;应传 \"待开始考试\"")
            .commonError("缺 username → INVALID_REQUEST;必须传学号")
            .commonError("用户按课程名查询 → exam_list 不支持 courseName 参数;先取列表再由调用方过滤")
            .build();
    }

    @Override
    public List<ExamSchedule> execute(TaskInput input) {
        input.require("username");

        String status = input.get("status");

        List<ExamSchedule> all = client.list();

        return all.stream()
            .filter(e -> status == null || status.isBlank() || matchesStatus(e, status))
            .toList();
    }

    private boolean matchesStatus(ExamSchedule exam, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return switch (status.trim()) {
            case "待开始考试" -> exam.examDate().isAfter(LocalDate.now().minusDays(1));
            case "已结束" -> exam.examDate().isBefore(LocalDate.now());
            default -> true;
        };
    }
}
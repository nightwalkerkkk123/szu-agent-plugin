package edu.szu.agent.task;

import edu.szu.agent.client.exam.ExamListClient;
import edu.szu.agent.domain.exam.ExamSchedule;

import java.time.LocalDate;
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
        return "查询深大考试安排列表";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return TaskInputSchema.schemaWithOptional(
            "username", "学号",
            Map.of("status", Map.of(
                "type", "string",
                "description", "考试状态筛选: 待开始考试/已结束"))
        );
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
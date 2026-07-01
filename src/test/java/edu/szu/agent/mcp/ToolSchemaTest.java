package edu.szu.agent.mcp;

import edu.szu.agent.cli.Main;
import edu.szu.agent.skill.Skills;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ToolSchema} — JSON Schema emitter for MCP
 * {@code tools/list}.
 *
 * <p>// 编程技术: 泛型 / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
class ToolSchemaTest {

    @BeforeEach
    void registerSkills() {
        Skills.reset();
        Main.registerDefaultSkills();
    }

    @AfterEach
    void cleanup() {
        Skills.reset();
    }

    @Test
    @DisplayName("ToolSchema.toolsList returns schemaVersion + tools[]")
    void listToolsShape() {
        Map<String, Object> response = ToolSchema.toolsList(Skills.getInstance().all());
        assertThat(response).containsKeys("schemaVersion", "tools");
        assertThat(response.get("schemaVersion")).isEqualTo("1.3");
        assertThat((List<?>) response.get("tools")).hasSize(8);
    }

    @Test
    @DisplayName("all built-in tools have long descriptions and top-level examples")
    void allBuiltInToolsHaveExamples() {
        List<Map<String, Object>> tools = tools();

        assertThat(tools).hasSize(8);
        assertThat(tools)
            .allSatisfy(tool -> {
                assertThat(tool.get("description").toString().length()).isGreaterThan(50);
                assertThat(tool).containsKey("examples");
                assertThat((List<?>) tool.get("examples")).isNotEmpty();
            });
    }

    @Test
    @DisplayName("all multi-param tools have >= 3 examples; single-param tools have >= 2")
    void examplesCountByToolShape() {
        // Per-tool minimum is keyed by the count of input properties.
        // Single-param tools (schedule_list, homework_list) cap at 2-3 because
        // the only meaningful variation is the username value itself.
        Map<String, Integer> minExamples = Map.of(
            "booking_venue", 5,
            "calendar_get", 4,
            "kb_query", 5,
            "schedule_list", 2,
            "notice_list", 3,
            "exam_list", 4,
            "homework_list", 2,
            "homework_download", 4
        );

        assertThat(minExamples).hasSize(8);
        tools().forEach(tool -> {
            String name = (String) tool.get("name");
            int actual = ((List<?>) tool.get("examples")).size();
            int minimum = minExamples.getOrDefault(name, 3);
            assertThat(actual)
                .as("tool %s should have >= %d examples (actual=%d)", name, minimum, actual)
                .isGreaterThanOrEqualTo(minimum);
        });
    }

    @Test
    @DisplayName("booking_venue tool has documented schema, enums and examples")
    void bookingVenueSchema() {
        Map<String, Object> booking = tool("booking_venue");

        assertThat(booking.get("description").toString())
            .startsWith("深圳大学体育场馆定时预约")
            .contains("真实预约会占用实际名额", "YUEHAI", "GYM_HEAVY(一楼重量型健身/一楼健身房)", "16:00-17:00");
        assertHasExamples(booking);

        Map<String, Object> inputSchema = inputSchema(booking);
        assertThat(inputSchema.get("type")).isEqualTo("object");
        Map<String, Object> properties = properties(inputSchema);
        assertThat(properties).containsKeys(
            "username", "campus", "sport", "date", "timeSlot", "preferredVenue");
        assertThat(required(inputSchema)).contains("campus", "sport", "date", "timeSlot");
        assertThat(required(inputSchema)).doesNotContain("username");
        assertThat(enumValues(properties, "campus")).contains("YUEHAI", "LIHU");
        assertThat(enumValues(properties, "sport")).contains("GYM_HEAVY", "GYM_AEROBIC", "GYM");
        assertThat(property(properties, "date").get("format")).isEqualTo("date");
    }

    @Test
    @DisplayName("kb_query tool has documented schema, category enum and examples")
    void kbQuerySchema() {
        Map<String, Object> kb = tool("kb_query");

        assertThat(kb.get("description").toString())
            .startsWith("查询深大校园知识库")
            .contains("CAMPUS_BASICS", "LIBRARY", "limit");
        assertHasExamples(kb);

        Map<String, Object> inputSchema = inputSchema(kb);
        assertThat(inputSchema.get("type")).isEqualTo("object");
        Map<String, Object> properties = properties(inputSchema);
        assertThat(properties).containsKeys("query", "limit", "category");
        assertThat(required(inputSchema)).containsExactly("query");
        assertThat(enumValues(properties, "category"))
            .containsExactly("CAMPUS_BASICS", "DINING", "LIBRARY", "ACADEMICS", "FAQ");
        assertThat(property(properties, "limit")).containsEntry("default", 5).containsEntry("minimum", 1);
    }

    @Test
    @DisplayName("calendar_get tool has documented schema and examples")
    void calendarGetSchema() {
        Map<String, Object> calendar = tool("calendar_get");

        assertThat(calendar.get("description").toString())
            .startsWith("查询深圳大学校历")
            .contains("2025-2026", "academicYear", "SEMESTER_START");
        assertHasExamples(calendar);

        Map<String, Object> inputSchema = inputSchema(calendar);
        assertThat(inputSchema.get("type")).isEqualTo("object");
        Map<String, Object> properties = properties(inputSchema);
        assertThat(properties).containsKeys("academicYear");
        assertThat(property(properties, "academicYear").get("pattern")).isEqualTo("^\\d{4}-\\d{4}$");
    }

    @Test
    @DisplayName("notice_list tool has documented schema, category enum and examples")
    void noticeListSchema() {
        Map<String, Object> notice = tool("notice_list");

        assertThat(notice.get("description").toString())
            .startsWith("查询深圳大学公文通通知列表")
            .contains("ANNOUNCEMENT", "LECTURE", "daysBack",
                "SZU_NOTICE_REAL=0", "Playwright",
                "https://www1.szu.edu.cn/board/", "NOTICE_FETCH_FAILED");
        assertHasExamples(notice);

        Map<String, Object> inputSchema = inputSchema(notice);
        assertThat(inputSchema.get("type")).isEqualTo("object");
        Map<String, Object> properties = properties(inputSchema);
        assertThat(properties).containsKeys("username", "category", "daysBack");
        assertThat(required(inputSchema)).containsExactly("username");
        assertThat(enumValues(properties, "category"))
            .containsExactly("ANNOUNCEMENT", "LECTURE", "COMPETITION", "PUBLICITY");
        assertThat(property(properties, "daysBack")).containsEntry("default", 30).containsEntry("minimum", 1);
    }

    @Test
    @DisplayName("schedule_list tool has documented username schema and examples")
    void scheduleListSchema() {
        Map<String, Object> schedule = tool("schedule_list");

        assertThat(schedule.get("description").toString())
            .startsWith("查询学生本学期课表")
            .contains("SZU_SCHEDULE_REAL=0", "AccountResolver", "username");
        assertHasExamples(schedule);

        Map<String, Object> inputSchema = inputSchema(schedule);
        Map<String, Object> properties = properties(inputSchema);
        assertThat(properties).containsKeys("username");
        assertThat(required(inputSchema)).containsExactly("username");
        assertThat(property(properties, "username").get("pattern")).isEqualTo("^20\\d{9}$");
    }

    @Test
    @DisplayName("exam_list tool has documented status enum and examples")
    void examListSchema() {
        Map<String, Object> exam = tool("exam_list");

        assertThat(exam.get("description").toString())
            .startsWith("查询深圳大学考试安排列表")
            .contains("待开始考试", "已结束", "ExamSchedule");
        assertHasExamples(exam);

        Map<String, Object> inputSchema = inputSchema(exam);
        Map<String, Object> properties = properties(inputSchema);
        assertThat(properties).containsKeys("username", "status");
        assertThat(required(inputSchema)).containsExactly("username");
        assertThat(enumValues(properties, "status")).containsExactly("待开始考试", "已结束");
    }

    @Test
    @DisplayName("homework_list tool has documented username schema and examples")
    void homeworkListSchema() {
        Map<String, Object> homework = tool("homework_list");

        assertThat(homework.get("description").toString())
            .startsWith("查询深圳大学畅课")
            .contains("AccountResolver", "homework_download", "HomeworkListResult");
        assertHasExamples(homework);

        Map<String, Object> inputSchema = inputSchema(homework);
        Map<String, Object> properties = properties(inputSchema);
        assertThat(properties).containsKeys("username");
        assertThat(required(inputSchema)).isEmpty();
        assertThat(property(properties, "username").get("pattern")).isEqualTo("^20\\d{9}$");
    }

    @Test
    @DisplayName("homework_download tool has documented required fields and examples")
    void homeworkDownloadSchema() {
        Map<String, Object> download = tool("homework_download");

        assertThat(download.get("description").toString())
            .startsWith("下载深圳大学畅课")
            .contains("homeworkId", "outputDir", "HomeworkDownloadResult");
        assertHasExamples(download);

        Map<String, Object> inputSchema = inputSchema(download);
        Map<String, Object> properties = properties(inputSchema);
        assertThat(properties).containsKeys("username", "homeworkId", "outputDir", "throttleMs", "maxRetries");
        assertThat(required(inputSchema)).containsExactly("homeworkId", "outputDir");
        assertThat(property(properties, "homeworkId").get("pattern")).isEqualTo("^\\d+$");
        assertThat(property(properties, "outputDir").get("format")).isEqualTo("uri-reference");
        assertThat(property(properties, "throttleMs")).containsEntry("default", 500).containsEntry("minimum", 0);
        assertThat(property(properties, "maxRetries")).containsEntry("default", 2).containsEntry("minimum", 0);
    }

    @Test
    @DisplayName("forSkill on an unknown skill returns a permissive schema")
    void unknownSkillPermissive() {
        edu.szu.agent.skill.Skill<?> dummy = new edu.szu.agent.skill.Skill<>(
            "test_dummy", "test",
            new edu.szu.agent.task.CampusTask<>() {
                @Override
                public String name() { return "test_dummy"; }
                @Override
                public String description() { return "test"; }
                @Override
                public String execute(edu.szu.agent.task.TaskInput input) { return ""; }
            });

        Map<String, Object> tool = ToolSchema.forSkill(dummy);
        assertThat(tool.get("name")).isEqualTo("test_dummy");
        assertThat(tool).containsKey("inputSchema");
        assertThat(tool).doesNotContainKey("examples");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> tools() {
        Map<String, Object> response = ToolSchema.toolsList(Skills.getInstance().all());
        return (List<Map<String, Object>>) response.get("tools");
    }

    private static Map<String, Object> tool(String name) {
        return tools().stream()
            .filter(t -> name.equals(t.get("name")))
            .findFirst()
            .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> inputSchema(Map<String, Object> tool) {
        return (Map<String, Object>) tool.get("inputSchema");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> inputSchema) {
        return (Map<String, Object>) inputSchema.get("properties");
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(Map<String, Object> inputSchema) {
        Object raw = inputSchema.get("required");
        if (raw == null) {
            return List.of();
        }
        return (List<String>) raw;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> property(Map<String, Object> properties, String name) {
        return (Map<String, Object>) properties.get(name);
    }

    @SuppressWarnings("unchecked")
    private static List<String> enumValues(Map<String, Object> properties, String name) {
        return (List<String>) property(properties, name).get("enum");
    }

    private static void assertHasExamples(Map<String, Object> tool) {
        assertThat(tool).containsKey("examples");
        assertThat((List<?>) tool.get("examples")).isNotEmpty();
    }
}

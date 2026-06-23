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
        assertThat(response.get("schemaVersion")).isEqualTo("1.2");
        assertThat((List<?>) response.get("tools")).hasSize(8);
    }

    @Test
    @DisplayName("booking_venue tool has the documented inputSchema")
    void bookingVenueSchema() {
        Map<String, Object> response = ToolSchema.toolsList(Skills.getInstance().all());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) response.get("tools");

        Map<String, Object> booking = tools.stream()
            .filter(t -> "booking_venue".equals(t.get("name")))
            .findFirst()
            .orElseThrow();

        assertThat(booking.get("description")).isEqualTo("体育场馆定时预约");

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) booking.get("inputSchema");
        assertThat(inputSchema.get("type")).isEqualTo("object");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertThat(properties).containsKeys(
            "username", "campus", "sport", "date", "timeSlot", "preferredVenue");

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) inputSchema.get("required");
        assertThat(required).contains("username", "campus", "sport", "date", "timeSlot");
    }

    @Test
    @DisplayName("kb_query tool has the documented inputSchema")
    void kbQuerySchema() {
        Map<String, Object> response = ToolSchema.toolsList(Skills.getInstance().all());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) response.get("tools");

        Map<String, Object> kb = tools.stream()
            .filter(t -> "kb_query".equals(t.get("name")))
            .findFirst()
            .orElseThrow();

        assertThat(kb.get("description")).isEqualTo("深大知识库查询");

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) kb.get("inputSchema");
        assertThat(inputSchema.get("type")).isEqualTo("object");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertThat(properties).containsKeys("query", "limit", "category");

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) inputSchema.get("required");
        assertThat(required).containsExactly("query");
    }

    @Test
    @DisplayName("calendar_get tool has the documented inputSchema")
    void calendarGetSchema() {
        Map<String, Object> response = ToolSchema.toolsList(Skills.getInstance().all());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) response.get("tools");

        Map<String, Object> calendar = tools.stream()
            .filter(t -> "calendar_get".equals(t.get("name")))
            .findFirst()
            .orElseThrow();

        assertThat(calendar.get("description")).isEqualTo("查询深大校历(静态 MVP)");

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) calendar.get("inputSchema");
        assertThat(inputSchema.get("type")).isEqualTo("object");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertThat(properties).containsKeys("academicYear");
    }

    @Test
    @DisplayName("notice_list tool has the documented inputSchema")
    void noticeListSchema() {
        Map<String, Object> response = ToolSchema.toolsList(Skills.getInstance().all());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) response.get("tools");

        Map<String, Object> notice = tools.stream()
            .filter(t -> "notice_list".equals(t.get("name")))
            .findFirst()
            .orElseThrow();

        assertThat(notice.get("description")).isEqualTo("查询深大公文通通知列表(静态 MVP)");

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) notice.get("inputSchema");
        assertThat(inputSchema.get("type")).isEqualTo("object");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertThat(properties).containsKeys("username", "category", "daysBack");

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) inputSchema.get("required");
        assertThat(required).containsExactly("username");
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
    }
}

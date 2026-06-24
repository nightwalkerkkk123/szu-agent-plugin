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
 * Tests for {@link MCPToolCallHandler} — MCP {@code tools/call} dispatch.
 *
 * <p>Covers the response envelope shape, error paths (unknown tool,
 * missing required param), and the nested-Map flattening
 * (e.g. {@code timeSlot.start} = "19:00" → flat string).
 *
 * <p>// 编程技术: 泛型 / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
class MCPToolCallHandlerTest {

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
    @DisplayName("Unknown tool name → INVALID_REQUEST, success=false")
    void unknownTool() {
        Map<String, Object> response = MCPToolCallHandler.call("no_such_tool", Map.of());
        assertThat(response.get("success")).isEqualTo(false);
        assertThat(response.get("errorCode")).isEqualTo("INVALID_REQUEST");
        assertThat((String) response.get("errorMessage")).contains("no_such_tool");
    }

    @Test
    @DisplayName("Missing required param → INVALID_REQUEST (BookingTask.require)")
    void missingRequiredParam() {
        Map<String, Object> args = Map.of(
            "campus", "YUEHAI",
            "sport", "TENNIS",
            "date", "2026-06-13",
            "username", "2023150090"
            // timeSlot is missing → BookingTask.require("timeSlot") throws
        );

        Map<String, Object> response = MCPToolCallHandler.call("booking_venue", args);
        assertThat(response.get("success")).isEqualTo(false);
        assertThat(response.get("errorCode")).isEqualTo("INVALID_REQUEST");
        assertThat((String) response.get("errorMessage")).contains("timeSlot");
    }

    @Test
    @DisplayName("Nested timeSlot Map is flattened to dotted keys and consumed by BookingTask")
    void nestedMapFlattening() {
        // Equal endpoints make TimeSlot reject the slot. We use an invalid
        // slot on purpose: a *valid* one would proceed into VenueBookingClient
        // .book() and launch a real browser inside the unit test. The
        // "19:00-19:00" error proves the nested object was flattened to
        // timeSlot.start / timeSlot.end AND that BookingTask read those
        // dotted keys — the schema-drift fix — without any browser launch.
        Map<String, Object> args = Map.of(
            "username", "2023150090",
            "campus", "YUEHAI",
            "sport", "TENNIS",
            "date", "2026-06-13",
            "timeSlot", Map.of("start", "19:00", "end", "19:00")
        );

        Map<String, Object> response = MCPToolCallHandler.call("booking_venue", args);
        assertThat(response.get("success")).isEqualTo(false);
        assertThat(response.get("errorCode")).isEqualTo("INVALID_REQUEST");
        assertThat((String) response.get("errorMessage")).contains("19:00-19:00");
        // Envelope is always the 6 PRD §5.2 fields
        assertThat(response).containsKeys("success", "data", "errorCode",
            "errorMessage", "traceId", "elapsedMs");
    }

    @Test
    @DisplayName("Response envelope always has 6 PRD §5.2 fields")
    void envelopeHasAllFields() {
        Map<String, Object> response = MCPToolCallHandler.call("no_such", Map.of());
        assertThat(response).containsOnlyKeys(
            "success", "data", "errorCode", "errorMessage", "traceId", "elapsedMs");
    }

    @Test
    @DisplayName("Empty arguments map is handled (not null-deref)")
    void emptyArgs() {
        Map<String, Object> response = MCPToolCallHandler.call("booking_venue", Map.of());
        // No required params → BookingTask.require fails on first call
        assertThat(response.get("success")).isEqualTo(false);
        assertThat(response.get("errorCode")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("Null arguments map is handled")
    void nullArgs() {
        Map<String, Object> response = MCPToolCallHandler.call("booking_venue", null);
        assertThat(response).containsKey("success");
    }

    @Test
    @DisplayName("kb_query returns knowledge results")
    void kbQuery() {
        Map<String, Object> args = Map.of(
            "query", "图书馆",
            "limit", 3
        );

        Map<String, Object> response = MCPToolCallHandler.call("kb_query", args);
        assertThat(response.get("success")).isEqualTo(true);
        assertThat(response).containsKey("data");
        assertThat(response).containsKey("traceId");
    }
}

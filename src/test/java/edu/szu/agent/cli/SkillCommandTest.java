package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.skill.Skills;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLI integration tests for {@link SkillCommand} and {@link MCPCommand}.
 *
 * <p>Runs picocli end-to-end and asserts JSON / human format output.
 * Exercises the wiring between {@code Main} → subcommands → Skills
 * registry → ToolSchema / MCPToolCallHandler.
 *
 * <p>// 编程技术: Lambda / @Nested
 *
 * @since 0.1.0
 * @author 王子豪
 */
class SkillCommandTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;

    @BeforeEach
    void resetState() {
        Skills.reset();
        Main.registerDefaultSkills();
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
    }

    @AfterEach
    void cleanup() {
        Skills.reset();
    }

    private int runCli(String... args) {
        return new picocli.CommandLine(new Main())
            .setOut(new PrintWriter(out, true))
            .setErr(new PrintWriter(err, true))
            .execute(args);
    }

    @Test
    @DisplayName("skill list — JSON output has count + skills array")
    void skillListJson() throws Exception {
        int exit = runCli("skill", "list");
        assertThat(exit).isEqualTo(0);
        JsonNode root = MAPPER.readTree(out.toString().trim());
        assertThat(root.get("count").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(root.get("skills").isArray()).isTrue();
        boolean hasBooking = false;
        for (JsonNode s : root.get("skills")) {
            if ("booking_venue".equals(s.get("name").asText())) {
                hasBooking = true;
            }
        }
        assertThat(hasBooking).isTrue();
    }

    @Test
    @DisplayName("skill list --format human — output is human-readable")
    void skillListHuman() {
        int exit = runCli("skill", "list", "--format", "human");
        assertThat(exit).isEqualTo(0);
        String text = out.toString();
        assertThat(text).contains("Registered Skills");
        assertThat(text).contains("booking_venue");
    }

    @Test
    @DisplayName("skill call — unknown tool → success=false INVALID_REQUEST")
    void skillCallUnknownTool() throws Exception {
        int exit = runCli("skill", "call", "no_such_tool");
        assertThat(exit).isEqualTo(1);
        JsonNode root = MAPPER.readTree(out.toString().trim());
        assertThat(root.get("success").asBoolean()).isFalse();
        assertThat(root.get("errorCode").asText()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("skill call — bad --args (no '=') → exit 2")
    void skillCallBadArgs() {
        int exit = runCli("skill", "call", "booking_venue", "--args", "no-equals");
        assertThat(exit).isEqualTo(2);
    }

    @Test
    @DisplayName("mcp list — emits schemaVersion + tools array with inputSchema")
    void mcpList() throws Exception {
        int exit = runCli("mcp", "list");
        assertThat(exit).isEqualTo(0);
        JsonNode root = MAPPER.readTree(out.toString().trim());
        assertThat(root.get("schemaVersion").asText()).isEqualTo("1.2");
        JsonNode tools = root.get("tools");
        assertThat(tools.isArray()).isTrue();
        JsonNode booking = null;
        JsonNode kb = null;
        for (JsonNode t : tools) {
            String name = t.get("name").asText();
            if ("booking_venue".equals(name)) {
                booking = t;
            } else if ("kb_query".equals(name)) {
                kb = t;
            }
        }
        assertThat(booking).isNotNull();
        assertThat(booking.get("description").asText())
            .startsWith("深圳大学体育场馆定时预约")
            .contains("真实预约会占用实际名额", "YUEHAI", "GYM_HEAVY(一楼重量型健身/一楼健身房)", "16:00-17:00");
        assertThat(booking.get("inputSchema").get("type").asText()).isEqualTo("object");
        assertThat(kb).isNotNull();
        assertThat(kb.get("description").asText()).isEqualTo("深大知识库查询");
    }

    @Test
    @DisplayName("mcp call — booking_venue with all required args (no real browser, expects failure envelope)")
    void mcpCallWithArgs() throws Exception {
        int exit = runCli("mcp", "call", "booking_venue",
            "--args", "username=2023150090",
            "--args", "campus=YUEHAI",
            "--args", "sport=TENNIS",
            "--args", "date=2026-06-13",
            "--args", "timeSlot=19:00-20:00");
        // exit is 0 because the CLI returns 0 after emitting the response.
        // Whether the response is success=true or success=false depends on
        // whether a real browser is available; we only care that the call
        // reaches the handler and the response is well-formed.
        assertThat(exit).isEqualTo(0);
        JsonNode root = MAPPER.readTree(out.toString().trim());
        assertThat(root.has("success")).isTrue();
        assertThat(root.has("traceId")).isTrue();
        assertThat(root.has("elapsedMs")).isTrue();
    }
}

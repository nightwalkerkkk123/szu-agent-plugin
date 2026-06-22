package edu.szu.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.cli.Main;
import edu.szu.agent.skill.Skills;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpStdioServerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Skills.reset();
        Main.registerDefaultSkills();
    }

    @AfterEach
    void tearDown() {
        Skills.reset();
    }

    @Test
    void handleInitializeReturnsServerInfo() throws IOException {
        McpStdioServer server = new McpStdioServer();
        String request = mapper.writeValueAsString(Map.of(
            "jsonrpc", "2.0",
            "id", 1,
            "method", "initialize",
            "params", Map.of()));

        String response = server.handle(request);
        Map<String, Object> map = mapper.readValue(response, Map.class);

        assertThat(map.get("jsonrpc")).isEqualTo("2.0");
        assertThat(map.get("id")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) map.get("result");
        assertThat(result).containsKey("protocolVersion");
        assertThat(result).containsKey("serverInfo");
    }

    @Test
    void handleToolsListReturnsBothSkills() throws IOException {
        McpStdioServer server = new McpStdioServer();
        String request = mapper.writeValueAsString(Map.of(
            "jsonrpc", "2.0",
            "id", 2,
            "method", "tools/list"));

        String response = server.handle(request);
        Map<String, Object> map = mapper.readValue(response, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) map.get("result");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> tools = (java.util.List<Map<String, Object>>) result.get("tools");

        assertThat(tools).hasSize(5);
        assertThat(tools.stream().map(t -> t.get("name")))
            .containsExactlyInAnyOrder("booking_venue", "kb_query",
                "homework_list", "homework_download", "schedule_list");
    }

    @Test
    void handleToolCallInvokesKbQuery() throws IOException {
        McpStdioServer server = new McpStdioServer();
        String request = mapper.writeValueAsString(Map.of(
            "jsonrpc", "2.0",
            "id", 3,
            "method", "tools/call",
            "params", Map.of(
                "name", "kb_query",
                "arguments", Map.of("query", "图书馆", "limit", 1))));

        String response = server.handle(request);
        Map<String, Object> map = mapper.readValue(response, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) map.get("result");

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result).containsKey("traceId");
    }

    @Test
    void handleUnknownMethodReturnsError() throws IOException {
        McpStdioServer server = new McpStdioServer();
        String request = mapper.writeValueAsString(Map.of(
            "jsonrpc", "2.0",
            "id", 4,
            "method", "foo/bar"));

        String response = server.handle(request);
        Map<String, Object> map = mapper.readValue(response, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) map.get("error");

        assertThat(error.get("code")).isEqualTo(-32601);
    }

    @Test
    void runReadsLengthPrefixedMessageAndResponds() throws IOException {
        String request = mapper.writeValueAsString(Map.of(
            "jsonrpc", "2.0",
            "id", 5,
            "method", "tools/list"));
        byte[] body = request.getBytes(StandardCharsets.UTF_8);
        String envelope = "Content-Length: " + body.length + "\r\n\r\n";
        ByteArrayInputStream in = new ByteArrayInputStream(
            (envelope + new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        McpStdioServer server = new McpStdioServer(in, out);
        server.run();

        String raw = out.toString(StandardCharsets.UTF_8);
        assertThat(raw).contains("Content-Length:");
        assertThat(raw).contains("\"tools\"");
    }
}

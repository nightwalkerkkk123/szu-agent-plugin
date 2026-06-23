package edu.szu.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.skill.Skills;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link McpHttpServer} — the resident HTTP daemon transport.
 *
 * <p>Verifies the four routes used by the two call surfaces: {@code /health}
 * (liveness), {@code /tools} (discovery), {@code /call} (REST for Skill curl
 * wrappers), and {@code /mcp} (JSON-RPC for MCP hosts over HTTP).
 *
 * <p>Each test binds to an ephemeral port (0) so the suite never collides
 * with a running daemon on a developer machine.
 */
class McpHttpServerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private McpHttpServer server;
    private String base;

    @BeforeEach
    void setUp() throws IOException {
        Skills.reset();
        server = new McpHttpServer(0);
        server.start();
        base = "http://127.0.0.1:" + server.boundPort();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        Skills.reset();
    }

    @Test
    void healthEndpointReturnsOk() throws Exception {
        HttpResponse<String> res = client.send(
            HttpRequest.newBuilder(URI.create(base + "/health")).GET().build(),
            HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("ok");
    }

    @Test
    void toolsEndpointListsRegisteredSkills() throws Exception {
        HttpResponse<String> res = client.send(
            HttpRequest.newBuilder(URI.create(base + "/tools")).GET().build(),
            HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("booking_venue").contains("kb_query");
    }

    @Test
    void callEndpointInvokesKbQueryInProcess() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
            "name", "kb_query",
            "arguments", Map.of("query", "图书馆", "limit", 1)));

        HttpResponse<String> res = client.send(
            HttpRequest.newBuilder(URI.create(base + "/call"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        Map<String, Object> envelope = mapper.readValue(res.body(), Map.class);
        assertThat(envelope.get("success")).isEqualTo(true);
        assertThat(envelope).containsKey("traceId");
    }

    @Test
    void callEndpointRejectsUnknownToolWithEnvelope() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
            "name", "does_not_exist",
            "arguments", Map.of()));

        HttpResponse<String> res = client.send(
            HttpRequest.newBuilder(URI.create(base + "/call"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        Map<String, Object> envelope = mapper.readValue(res.body(), Map.class);
        assertThat(envelope.get("success")).isEqualTo(false);
        assertThat(envelope.get("errorCode")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void mcpEndpointAnswersJsonRpcToolsList() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
            "jsonrpc", "2.0",
            "id", 1,
            "method", "tools/list"));

        HttpResponse<String> res = client.send(
            HttpRequest.newBuilder(URI.create(base + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        Map<String, Object> envelope = mapper.readValue(res.body(), Map.class);
        assertThat(envelope.get("jsonrpc")).isEqualTo("2.0");
        assertThat(envelope.get("id")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) envelope.get("result");
        assertThat(result).containsKey("tools");
    }

    @Test
    void callEndpointSerializesDateBearingTool() throws Exception {
        // Regression: calendar_get returns java.time.LocalDate fields; the
        // /call mapper must have JavaTimeModule registered or this 500s.
        String body = mapper.writeValueAsString(Map.of(
            "name", "calendar_get", "arguments", Map.of()));

        HttpResponse<String> res = client.send(
            HttpRequest.newBuilder(URI.create(base + "/call"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        Map<String, Object> envelope = mapper.readValue(res.body(), Map.class);
        assertThat(envelope.get("success")).isEqualTo(true);
    }

    @Test
    void mcpEndpointSerializesDateBearingTool() throws Exception {
        // Regression: same date-serialization path, exercised through the
        // shared McpStdioServer.handle() JSON-RPC dispatch.
        String body = mapper.writeValueAsString(Map.of(
            "jsonrpc", "2.0", "id", 9, "method", "tools/call",
            "params", Map.of("name", "calendar_get", "arguments", Map.of())));

        HttpResponse<String> res = client.send(
            HttpRequest.newBuilder(URI.create(base + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        Map<String, Object> envelope = mapper.readValue(res.body(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) envelope.get("result");
        // MCP CallToolResult shape: { content:[{type,text}], isError }.
        assertThat(result.get("isError")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> content =
            (java.util.List<Map<String, Object>>) result.get("content");
        String text = String.valueOf(content.get(0).get("text"));
        assertThat(text).contains("\"success\":true");
        // Dates must serialize as ISO-8601 strings, never the legacy [2026,3,4] array.
        assertThat(text).doesNotContain("[2026");
    }

    @Test
    void boundPortIsStableAfterStart() {
        assertThat(server.boundPort()).isGreaterThan(0);
    }
}

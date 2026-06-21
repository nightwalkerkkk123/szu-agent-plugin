package edu.szu.agent.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.cli.Main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP stdio server — JSON-RPC 2.0 over stdin/stdout.
 *
 * <p>Implements the Model Context Protocol stdio transport so that any
 * MCP host (Claude Code, Claude Desktop, OpenClaw, etc.) can run the
 * jar directly on Windows, macOS, or Linux without platform-specific
 * wrappers.
 *
 * <p>Supported messages:
 * <ul>
 *   <li>{@code initialize}
 *   <li>{@code notifications/initialized}
 *   <li>{@code tools/list}
 *   <li>{@code tools/call}
 * </ul>
 *
 * <p>All logging is directed to {@code System.err}; {@code System.out}
 * is reserved for the JSON-RPC stream.
 *
 * // 编程技术: 泛型 / Lambda + Stream / IO
 *
 * @since 0.2.0
 * @author 王子豪
 */
public final class McpStdioServer {

    private static final String CONTENT_LENGTH = "Content-Length: ";

    private final ObjectMapper mapper = new ObjectMapper();
    private final InputStream in;
    private final OutputStream out;

    public McpStdioServer() {
        this(System.in, System.out);
    }

    McpStdioServer(InputStream in, OutputStream out) {
        this.in = Objects.requireNonNull(in, "in");
        this.out = Objects.requireNonNull(out, "out");
    }

    /**
     * Starts the server and blocks until EOF on stdin.
     *
     * @throws IOException if reading or writing fails
     */
    public void run() throws IOException {
        Main.registerDefaultSkills();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            if (!line.startsWith(CONTENT_LENGTH)) {
                continue;
            }
            int length;
            try {
                length = Integer.parseInt(line.substring(CONTENT_LENGTH.length()).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            // Empty line before JSON body
            reader.readLine();

            char[] buffer = new char[length];
            int read = 0;
            while (read < length) {
                int n = reader.read(buffer, read, length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            String json = new String(buffer, 0, read);
            String response = handle(json);
            if (response != null) {
                send(response);
            }
        }
    }

    /**
     * Processes one JSON-RPC message and returns the response JSON, or
     * {@code null} for notifications that do not require a response.
     */
    String handle(String json) throws JsonProcessingException {
        Map<String, Object> request = mapper.readValue(json, Map.class);
        String method = String.valueOf(request.getOrDefault("method", ""));
        Object id = request.get("id");

        return switch (method) {
            case "initialize" -> response(id, initializeResult());
            case "notifications/initialized" -> null;
            case "tools/list" -> response(id, MCPToolProvider.listTools());
            case "tools/call" -> response(id, handleToolCall(request));
            default -> errorResponse(id, -32601, "Method not found: " + method);
        };
    }

    private Map<String, Object> initializeResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", "2024-11-05");

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "szu-agent-plugin");
        serverInfo.put("version", "0.2.0");
        result.put("serverInfo", serverInfo);

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        result.put("capabilities", capabilities);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolCall(Map<String, Object> request) {
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());
        String name = String.valueOf(params.getOrDefault("name", ""));
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());
        return MCPToolCallHandler.call(name, arguments);
    }

    private String response(Object id, Map<String, Object> result) throws JsonProcessingException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", id == null ? null : id);
        envelope.put("result", result);
        return mapper.writeValueAsString(envelope);
    }

    private String errorResponse(Object id, int code, String message) throws JsonProcessingException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", id == null ? null : id);

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        envelope.put("error", error);
        return mapper.writeValueAsString(envelope);
    }

    private void send(String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        synchronized (out) {
            PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8);
            writer.print(CONTENT_LENGTH + bytes.length + "\r\n\r\n");
            writer.flush();
            out.write(bytes);
            out.flush();
        }
    }
}

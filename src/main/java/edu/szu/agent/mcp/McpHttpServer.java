package edu.szu.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.szu.agent.cli.Main;
import edu.szu.agent.json.JsonMappers;
import edu.szu.agent.skill.Skills;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resident HTTP daemon transport for the MCP tool surface.
 *
 * <p>Unlike {@link McpStdioServer} (one process per host, reached over a
 * stdio pipe), this server binds a TCP port so that a <em>single</em>
 * long-lived JVM can be shared by many callers at once:
 * <ul>
 *   <li>Skill wrappers hit {@code POST /call} via {@code curl} — no JVM
 *       cold-start per invocation, only an HTTP round-trip.
 *   <li>MCP hosts (Claude Code / Desktop) speak JSON-RPC over
 *       {@code POST /mcp}, reusing the exact dispatch logic of the stdio
 *       transport.
 * </ul>
 *
 * <p>Built on the JDK's {@link com.sun.net.httpserver.HttpServer} — no
 * third-party dependency — so the same jar runs unchanged on any machine
 * with a Java 21 runtime.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code GET  /health} — liveness probe ({@code {"status":"ok"}})
 *   <li>{@code GET  /tools}  — MCP {@code tools/list} envelope
 *   <li>{@code POST /call}   — {@code {"name":..,"arguments":{..}}} → result envelope
 *   <li>{@code POST /mcp}    — raw JSON-RPC 2.0 message → JSON-RPC response
 * </ul>
 *
 * <p>All diagnostics are logged via SLF4J; standard streams are never
 * touched, so the daemon is safe to run detached.
 *
 * // Design Pattern: Adapter (HTTP transport adapts the shared MCP dispatch)
 * // 编程技术: Lambda / 泛型 / IO
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class McpHttpServer {

    private static final Logger LOG = LoggerFactory.getLogger(McpHttpServer.class);
    private static final String JSON = "application/json; charset=utf-8";
    private static final int STOP_DELAY_SECONDS = 0;

    // Centralized factory: java.time types serialize as ISO-8601 strings,
    // not numeric arrays (e.g. calendar_get / exam_list date fields).
    private final ObjectMapper mapper = JsonMappers.standard();
    /** Reused purely for its self-contained {@code handle(String)} JSON-RPC dispatch. */
    private final McpStdioServer dispatcher = new McpStdioServer();
    private final int requestedPort;

    private HttpServer server;

    /**
     * @param port TCP port to bind; {@code 0} selects an ephemeral free port
     *             (resolve the actual value with {@link #boundPort()})
     * @since 0.6.0
     */
    public McpHttpServer(int port) {
        this.requestedPort = port;
    }

    /**
     * Registers the default Skills once, binds the port, and starts serving.
     *
     * @throws IOException if the port cannot be bound
     * @since 0.6.0
     */
    public void start() throws IOException {
        Main.registerDefaultSkills();
        server = HttpServer.create(new InetSocketAddress(requestedPort), 0);
        server.createContext("/health", guarded("GET", this::health));
        server.createContext("/tools", guarded("GET", this::tools));
        server.createContext("/call", guarded("POST", this::call));
        server.createContext("/mcp", guarded("POST", this::mcp));
        server.setExecutor(null); // default per-request executor; sufficient for local use
        server.start();
        LOG.info("MCP HTTP daemon listening on port {} ({} tools registered)",
            boundPort(), Skills.getInstance().all().size());
    }

    /**
     * @return the port the server is actually bound to (meaningful after {@link #start()})
     * @since 0.6.0
     */
    public int boundPort() {
        return Objects.requireNonNull(server, "server not started").getAddress().getPort();
    }

    /**
     * Stops the server, allowing in-flight exchanges a brief grace period.
     *
     * @since 0.6.0
     */
    public void stop() {
        if (server != null) {
            server.stop(STOP_DELAY_SECONDS);
        }
    }

    // ---------- route handlers ----------

    private void health(HttpExchange ex) throws IOException {
        writeJson(ex, 200, mapper.writeValueAsString(Map.of("status", "ok")));
    }

    private void tools(HttpExchange ex) throws IOException {
        writeJson(ex, 200, mapper.writeValueAsString(
            ToolSchema.toolsList(Skills.getInstance().all())));
    }

    @SuppressWarnings("unchecked")
    private void call(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        Map<String, Object> request = body.isBlank()
            ? Map.of()
            : mapper.readValue(body, Map.class);
        String name = String.valueOf(request.getOrDefault("name", ""));
        Object rawArgs = request.get("arguments");
        Map<String, Object> arguments = rawArgs instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : Map.of();
        Map<String, Object> envelope = MCPToolCallHandler.call(name, arguments);
        writeJson(ex, 200, mapper.writeValueAsString(envelope));
    }

    private void mcp(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        String response = dispatcher.handle(body);
        if (response == null) {
            // JSON-RPC notification — no response body expected.
            ex.sendResponseHeaders(202, -1);
            ex.close();
            return;
        }
        writeJson(ex, 200, response);
    }

    // ---------- plumbing ----------

    /**
     * Wraps a handler with a method guard and a uniform error envelope so a
     * single bad request can never crash the daemon mid-demo.
     */
    private HttpHandler guarded(String method, IoHandler handler) {
        return ex -> {
            try {
                if (!method.equalsIgnoreCase(ex.getRequestMethod())) {
                    writeJson(ex, 405, mapper.writeValueAsString(
                        Map.of("error", "method not allowed; use " + method)));
                    return;
                }
                handler.handle(ex);
            } catch (Exception e) {
                LOG.error("Request to {} failed", ex.getRequestURI(), e);
                try {
                    writeJson(ex, 500, mapper.writeValueAsString(
                        Map.of("error", String.valueOf(e.getMessage()))));
                } catch (IOException ignored) {
                    ex.close();
                }
            }
        };
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void writeJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", JSON);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Local functional interface so route methods may throw {@link IOException}. */
    @FunctionalInterface
    private interface IoHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}

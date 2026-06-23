package edu.szu.agent.cli;

import edu.szu.agent.mcp.McpHttpServer;
import edu.szu.agent.mcp.McpStdioServer;
import edu.szu.agent.mcp.MCPToolCallHandler;
import edu.szu.agent.mcp.ToolSchema;
import edu.szu.agent.skill.Skills;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

/**
 * {@code mcp} subcommand — emits MCP protocol responses from the CLI.
 *
 * <p>Per ADR-0001 D5: the CLI is a thin shim around the MCP layer.
 * An external MCP host (Claude Code, OpenClaw) typically speaks the
 * protocol over stdio or HTTP; for debugging and smoke-testing, the
 * CLI offers {@code mcp list} and {@code mcp call} that emit the
 * exact same JSON shape.
 *
 * <p>Sub-actions:
 * <ul>
 *   <li>{@code mcp list} — emit the MCP {@code tools/list} response
 *       (schema version + tools array)
 *   <li>{@code mcp call <name> --args k=v} — emit the MCP
 *       {@code tools/call} response
 *   <li>{@code mcp serve} — run the stdio MCP server for external hosts
 * </ul>
 *
 * <p>picocli is a framework, not a project pattern (per ADR-0007 D1).
 *
 * // 编程技术: 注解 / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
@Command(
    name = "mcp",
    description = "MCP protocol surface (tools/list, tools/call, serve)",
    mixinStandardHelpOptions = true,
    subcommands = {
        MCPCommand.McpListAction.class,
        MCPCommand.McpCallAction.class,
        MCPCommand.McpServeAction.class
    }
)
public class MCPCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        // No sub-action specified; picocli prints usage.
        return 0;
    }

    // ---------- mcp tools/list ----------

    @Command(
        name = "list",
        description = "Emit MCP tools/list response (JSON Schema per skill)"
    )
    public static class McpListAction implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            out.println(SkillCommand.toJson(ToolSchema.toolsList(Skills.getInstance().all())));
            return 0;
        }
    }

    // ---------- mcp tools/call ----------

    @Command(
        name = "call",
        description = "Emit MCP tools/call response for a tool name with --args k=v"
    )
    public static class McpCallAction implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", description = "Tool name (e.g. booking_venue)")
        private String name;

        @Option(names = {"-a", "--args"}, description = "Argument as k=v (repeatable)")
        private List<String> args;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            try {
                Map<String, String> flat = SkillCommand.parseArgs(args);
                Map<String, Object> arguments = new LinkedHashMap<>(flat);
                out.println(SkillCommand.toJson(MCPToolCallHandler.call(name, arguments)));
                return 0;
            } catch (IllegalArgumentException e) {
                Map<String, Object> err = Map.of(
                    "success", false,
                    "errorCode", "INVALID_REQUEST",
                    "errorMessage", e.getMessage());
                out.println(SkillCommand.toJson(err));
                return 2;
            }
        }
    }

    // ---------- mcp serve ----------

    @Command(
        name = "serve",
        description = "Run the MCP server: stdio (default) for one host, or "
            + "--http for a resident daemon shared by Skill curl + MCP hosts"
    )
    public static class McpServeAction implements Callable<Integer> {

        private static final Logger LOG = LoggerFactory.getLogger(McpServeAction.class);

        /** Default daemon port; chosen to avoid common dev ports (3000/8080/8000). */
        private static final int DEFAULT_PORT = 8765;

        @Option(names = "--http",
            description = "Run as a resident HTTP daemon instead of stdio")
        private boolean http;

        @Option(names = "--port",
            description = "HTTP daemon port (default: " + DEFAULT_PORT + ")")
        private int port = DEFAULT_PORT;

        @Override
        public Integer call() {
            return http ? serveHttp() : serveStdio();
        }

        private Integer serveStdio() {
            try {
                new McpStdioServer().run();
                return 0;
            } catch (Exception e) {
                // MCP communication errors are logged to stderr so they do
                // not corrupt the stdout JSON-RPC stream.
                LOG.error("MCP server failed", e);
                return 1;
            }
        }

        private Integer serveHttp() {
            McpHttpServer daemon = new McpHttpServer(port);
            CountDownLatch shutdown = new CountDownLatch(1);
            try {
                daemon.start();
            } catch (Exception e) {
                LOG.error("Failed to start MCP HTTP daemon on port {}", port, e);
                return 1;
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                daemon.stop();
                shutdown.countDown();
            }));
            try {
                // Block until the JVM receives a termination signal.
                shutdown.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                daemon.stop();
            }
            return 0;
        }
    }
}

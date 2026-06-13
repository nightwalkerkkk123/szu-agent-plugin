package edu.szu.agent.cli;

import edu.szu.agent.mcp.MCPToolCallHandler;
import edu.szu.agent.mcp.MCPToolProvider;
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
    description = "MCP protocol surface (tools/list, tools/call)",
    mixinStandardHelpOptions = true,
    subcommands = {
        MCPCommand.McpListAction.class,
        MCPCommand.McpCallAction.class
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
            out.println(SkillCommand.toJson(MCPToolProvider.listTools()));
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
}

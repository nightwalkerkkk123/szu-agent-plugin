package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import edu.szu.agent.json.JsonMappers;
import edu.szu.agent.mcp.MCPToolCallHandler;
import edu.szu.agent.skill.Skill;
import edu.szu.agent.skill.Skills;
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
 * {@code skill} subcommand — exposes the Skill registry via the CLI.
 *
 * <p>Per ADR-0001 D5: the CLI is a thin shim around the Skill layer.
 * {@code skill list} emits JSON shaped for external Agent discovery;
 * {@code skill call} invokes a Skill by name with {@code --args k=v}.
 *
 * <p>Sub-actions:
 * <ul>
 *   <li>{@code skill list} — print registered Skills (name, description)
 *   <li>{@code skill call <name> --args k=v} — invoke a Skill
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
    name = "skill",
    description = "List or invoke registered Skills (P1 thin wrapper)",
    mixinStandardHelpOptions = true,
    subcommands = {
        SkillCommand.ListAction.class,
        SkillCommand.CallAction.class
    }
)
public class SkillCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        // No sub-action specified; picocli prints usage.
        return 0;
    }

    static final ObjectMapper JSON = JsonMappers.standard()
        .enable(SerializationFeature.INDENT_OUTPUT);

    static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON", e);
        }
    }

    static Map<String, String> parseArgs(List<String> raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        for (String kv : raw) {
            int eq = kv.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException(
                    "Bad --args (expected k=v): " + kv);
            }
            out.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        return out;
    }

    // ---------- skill list ----------

    @Command(
        name = "list",
        description = "List all registered Skills"
    )
    public static class ListAction implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = {"-f", "--format"}, description = "Output format: json or human",
                defaultValue = "json")
        private String format;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            List<Skill<?>> skills = Skills.getInstance().all();

            if ("human".equalsIgnoreCase(format)) {
                out.println("Registered Skills (" + skills.size() + "):");
                for (Skill<?> s : skills) {
                    out.println("  - " + s.name() + ": " + s.description());
                }
                return 0;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("count", skills.size());
            data.put("skills", skills.stream()
                .map(s -> Map.<String, Object>of(
                    "name", s.name(),
                    "description", s.description()))
                .toList());
            out.println(toJson(data));
            return 0;
        }
    }

    // ---------- skill call ----------

    @Command(
        name = "call",
        description = "Invoke a registered Skill by name with --args k=v"
    )
    public static class CallAction implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", description = "Skill name (e.g. booking_venue)")
        private String name;

        @Option(names = {"-a", "--args"}, description = "Argument as k=v (repeatable)")
        private List<String> args;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            try {
                Map<String, String> flat = parseArgs(args);
                Map<String, Object> arguments = new LinkedHashMap<>(flat);
                Map<String, Object> response = MCPToolCallHandler.call(name, arguments);
                out.println(toJson(response));
                return Boolean.TRUE.equals(response.get("success")) ? 0 : 1;
            } catch (IllegalArgumentException e) {
                Map<String, Object> err = Map.of(
                    "success", false,
                    "errorCode", "INVALID_REQUEST",
                    "errorMessage", e.getMessage());
                out.println(toJson(err));
                return 2;
            }
        }
    }
}

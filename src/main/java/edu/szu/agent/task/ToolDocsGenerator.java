package edu.szu.agent.task;

import edu.szu.agent.skill.Skill;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Renders MCP tool metadata into human-readable Markdown reference pages.
 *
 * <p>// Design Pattern: Static Renderer (no Builder; pure function)
 * // 编程技术: 泛型 / record / Lambda / 文本块
 *
 * @since 0.5.0
 * @author 王子豪
 */
public final class ToolDocsGenerator {

    private ToolDocsGenerator() {
    }

    /**
     * Renders one {@link Skill}'s task metadata as a Markdown document.
     *
     * @param skill the skill to render
     * @return Markdown content for {@code docs/tools/<tool>.md}
     * @since 0.5.0
     * @author 王子豪
     */
    public static String renderMarkdown(Skill<?> skill) {
        CampusTask<?> task = skill.task();
        ToolAnnotations annotations = task.annotations();
        Map<String, Object> schema = task.inputSchema();
        Map<String, Object> properties = properties(schema);
        List<String> required = required(schema);

        StringBuilder out = new StringBuilder();
        out.append("# ").append(skill.name()).append("\n\n");
        out.append(task.description().strip()).append("\n\n");
        appendParameters(out, properties, required);
        appendEnums(out, properties);
        appendExamples(out, skill.name(), annotations.examples());
        appendResultShape(out, annotations.resultShape());
        appendCommonErrors(out, annotations.commonErrors());
        appendRelatedDocs(out, skill.name());
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> schema) {
        Object raw = schema.get("properties");
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(Map<String, Object> schema) {
        Object raw = schema.get("required");
        if (raw instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static void appendParameters(StringBuilder out,
                                         Map<String, Object> properties,
                                         List<String> required) {
        out.append("## 参数\n\n");
        out.append("| 名称 | 必填 | 类型 | 说明 | 约束 |\n");
        out.append("|---|---:|---|---|---|\n");
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> prop = (Map<String, Object>) entry.getValue();
            out.append("| `").append(entry.getKey()).append("` | ")
                .append(required.contains(entry.getKey()) ? "是" : "否")
                .append(" | `").append(valueOrDash(prop.get("type"))).append("` | ")
                .append(escapeTable(valueOrDash(prop.get("description"))))
                .append(" | ").append(escapeTable(constraints(prop))).append(" |\n");
        }
        out.append("\n");
    }

    @SuppressWarnings("unchecked")
    private static void appendEnums(StringBuilder out, Map<String, Object> properties) {
        boolean hasEnum = properties.values().stream()
            .map(Map.class::cast)
            .anyMatch(prop -> prop.containsKey("enum"));
        if (!hasEnum) {
            return;
        }
        out.append("## 枚举\n\n");
        out.append("| 参数 | 可选值 |\n");
        out.append("|---|---|\n");
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> prop = (Map<String, Object>) entry.getValue();
            if (prop.containsKey("enum")) {
                List<Object> values = (List<Object>) prop.get("enum");
                out.append("| `").append(entry.getKey()).append("` | ")
                    .append(escapeTable(joinBackticked(values))).append(" |\n");
            }
        }
        out.append("\n");
    }

    private static void appendExamples(StringBuilder out,
                                       String toolName,
                                       List<Map<String, Object>> examples) {
        out.append("## 示例\n\n");
        if (examples.isEmpty()) {
            out.append("暂无示例。\n\n");
            return;
        }
        for (Map<String, Object> example : examples) {
            out.append("```json\n");
            out.append("{\n");
            out.append("  \"name\": \"").append(toolName).append("\",\n");
            out.append("  \"arguments\": ").append(toJson(example, 2)).append("\n");
            out.append("}\n");
            out.append("```\n\n");
        }
    }

    private static void appendResultShape(StringBuilder out, String resultShape) {
        out.append("## 返回值\n\n");
        if (resultShape == null || resultShape.isBlank()) {
            out.append("暂无补充说明。\n\n");
            return;
        }
        out.append("```text\n").append(resultShape.strip()).append("\n```\n\n");
    }

    private static void appendCommonErrors(StringBuilder out, List<String> commonErrors) {
        out.append("## 常见错误\n\n");
        if (commonErrors.isEmpty()) {
            out.append("暂无。\n\n");
            return;
        }
        for (String error : commonErrors) {
            out.append("- ").append(error).append("\n");
        }
        out.append("\n");
    }

    private static void appendRelatedDocs(StringBuilder out, String toolName) {
        out.append("## 相关文档\n\n");
        out.append("- [MCP 工具总览](../../MCP.md)\n");
        out.append("- 工具名: `").append(toolName).append("`\n");
    }

    /**
     * CLI helper used to generate {@code docs/tools/*.md} from task metadata.
     *
     * <p>Per the project's ArchUnit carve-out ({@code Main.main}), {@code main}
     * is the only place allowed to use {@code System.out.println} directly.
     *
     * @param args ignored
     * @since 0.5.0
     * @author 王子豪
     */
    public static void main(String[] args) {
        edu.szu.agent.cli.Main.registerDefaultSkills();
        edu.szu.agent.skill.Skills.getInstance().all().forEach(skill -> {
            System.out.println("<!-- tool-doc: " + skill.name() + " -->");
            System.out.println(renderMarkdown(skill));
        });
    }

    private static String constraints(Map<String, Object> prop) {
        StringJoiner joiner = new StringJoiner("; ");
        appendIfPresent(joiner, "enum", prop.get("enum"));
        appendIfPresent(joiner, "format", prop.get("format"));
        appendIfPresent(joiner, "pattern", prop.get("pattern"));
        appendIfPresent(joiner, "default", prop.get("default"));
        appendIfPresent(joiner, "minimum", prop.get("minimum"));
        appendIfPresent(joiner, "maximum", prop.get("maximum"));
        appendIfPresent(joiner, "examples", prop.get("examples"));
        String result = joiner.toString();
        return result.isBlank() ? "-" : result;
    }

    private static void appendIfPresent(StringJoiner joiner, String key, Object value) {
        if (value != null) {
            joiner.add(key + "=" + value);
        }
    }

    private static String joinBackticked(List<Object> values) {
        return values.stream()
            .map(String::valueOf)
            .map(value -> "`" + value + "`")
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }

    private static String valueOrDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private static String escapeTable(String value) {
        return value.replace("|", "\\|").replace("\n", "<br>");
    }

    private static String toJson(Object value, int indent) {
        return switch (value) {
            case Map<?, ?> map -> mapToJson(map, indent);
            case List<?> list -> listToJson(list, indent);
            case String s -> "\"" + escapeJson(s) + "\"";
            case Number n -> n.toString();
            case Boolean b -> b.toString();
            case null -> "null";
            default -> "\"" + escapeJson(String.valueOf(value)) + "\"";
        };
    }

    private static String mapToJson(Map<?, ?> map, int indent) {
        if (map.isEmpty()) {
            return "{}";
        }
        String spaces = " ".repeat(indent);
        String childSpaces = " ".repeat(indent + 2);
        StringJoiner joiner = new StringJoiner(",\n", "{\n", "\n" + spaces + "}");
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            joiner.add(childSpaces + "\"" + escapeJson(String.valueOf(entry.getKey()))
                + "\": " + toJson(entry.getValue(), indent + 2));
        }
        return joiner.toString();
    }

    private static String listToJson(List<?> list, int indent) {
        if (list.isEmpty()) {
            return "[]";
        }
        String spaces = " ".repeat(indent);
        String childSpaces = " ".repeat(indent + 2);
        StringJoiner joiner = new StringJoiner(",\n", "[\n", "\n" + spaces + "]");
        for (Object item : list) {
            joiner.add(childSpaces + toJson(item, indent + 2));
        }
        return joiner.toString();
    }

    private static String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n");
    }
}

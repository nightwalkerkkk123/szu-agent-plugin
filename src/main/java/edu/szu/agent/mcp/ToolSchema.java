package edu.szu.agent.mcp;

import edu.szu.agent.skill.Skill;
import edu.szu.agent.task.ToolAnnotations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ToolSchema — converts a {@link Skill} into a JSON-Schema-shaped
 * description suitable for MCP {@code tools/list}.
 *
 * <p>Per the architecture-deepening plan (改动 2): schemas are declared on
 * each {@link edu.szu.agent.task.CampusTask} via {@code inputSchema()}.
 * This class delegates to that method, eliminating the former switch
 * dispatch. External skills ({@link ExternalSkill}) continue to source
 * their schema from the skill manifest.
 *
 * <p>Schema versioning: when a task's parameter set changes,
 * bump the schema version string returned here so MCP clients can
 * detect the change.
 *
 * // 编程技术: record(Java 16+)
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class ToolSchema {

    private ToolSchema() {
    }

    /** Schema version. Bump when the tool envelope shape changes.
     *
     * <p>History:
     * <ul>
     *   <li>1.0 — initial {@code {name, description, inputSchema}}</li>
     *   <li>1.1 — inputSchema requires/optional split</li>
     *   <li>1.2 — schema declared on each {@link edu.szu.agent.task.CampusTask} (delegation)</li>
     *   <li>1.3 — top-level {@code examples} key added, sourced from
     *       {@link ToolAnnotations#examples()}. Optional; tools with no
     *       annotations emit no {@code examples} field.</li>
     * </ul>
     */
    public static final String SCHEMA_VERSION = "1.3";

    /**
     * Returns the {@code tools/list} entry for a Skill.
     *
     * <p>Per MCP.md §tools/list:
     * <pre>{@code
     * {
     *   "name": "booking_venue",
     *   "description": "体育场馆定时预约",
     *   "inputSchema": { ... },
     *   "examples": [ { "name": "...", "arguments": {...} }, ... ]
     * }
     * }</pre>
     *
     * <p>The {@code examples} key (added in schema 1.3) is sourced from
     * {@link edu.szu.agent.task.CampusTask#annotations()} — tools with no
     * declared examples emit no {@code examples} key at all, keeping the
     * envelope minimal.
     *
     * @param skill the skill to describe
     * @return an ordered map mirroring the MCP schema shape
     * @since 0.6.0
     */
    public static Map<String, Object> forSkill(Skill<?> skill) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", skill.name());
        tool.put("description", skill.description());
        tool.put("inputSchema", schemaFor(skill));
        ToolAnnotations annotations = skill.task().annotations();
        if (!annotations.examples().isEmpty()) {
            List<Map<String, Object>> examples = annotations.examples().stream()
                .map(args -> {
                    Map<String, Object> wrapper = new LinkedHashMap<>();
                    wrapper.put("name", skill.name());
                    wrapper.put("arguments", args);
                    return wrapper;
                })
                .toList();
            tool.put("examples", examples);
        }
        return tool;
    }

    /**
     * Returns the {@code tools/list} response wrapping one or more
     * skills. The wrapping is a {@code Map} (not a JSON string) so
     * the CLI / Skill layer can serialize it with Jackson without
     * re-parsing.
     *
     * @param skills the skills to include
     * @return the {@code tools/list} response map
     * @since 0.6.0
     */
    public static Map<String, Object> toolsList(List<Skill<?>> skills) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("schemaVersion", SCHEMA_VERSION);
        response.put("tools", skills.stream().map(ToolSchema::forSkill).toList());
        return response;
    }

    /**
     * Delegates to the task's own {@code inputSchema()} method.
     * External skills override that method to return their manifest schema.
     *
     * @since 0.6.0
     */
    private static Map<String, Object> schemaFor(Skill<?> skill) {
        return skill.task().inputSchema();
    }
}
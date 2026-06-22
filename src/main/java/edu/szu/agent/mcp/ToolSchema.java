package edu.szu.agent.mcp;

import edu.szu.agent.skill.Skill;

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
 * @since 0.1.0
 * @author 王子豪
 */
public final class ToolSchema {

    private ToolSchema() {
    }

    /** Schema version. Bump when inputSchema changes. */
    public static final String SCHEMA_VERSION = "1.2";

    /**
     * Returns the {@code tools/list} entry for a Skill.
     *
     * <p>Per MCP.md §tools/list:
     * <pre>{@code
     * {
     *   "name": "booking_venue",
     *   "description": "体育场馆定时预约",
     *   "inputSchema": { ... }
     * }
     * }</pre>
     *
     * @param skill the skill to describe
     * @return an ordered map mirroring the MCP schema shape
     * @since 0.1.0
     */
    public static Map<String, Object> forSkill(Skill<?> skill) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", skill.name());
        tool.put("description", skill.description());
        tool.put("inputSchema", schemaFor(skill));
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
     * @since 0.1.0
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
     * @since 0.1.0
     */
    private static Map<String, Object> schemaFor(Skill<?> skill) {
        return skill.task().inputSchema();
    }
}
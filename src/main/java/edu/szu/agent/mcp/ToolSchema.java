package edu.szu.agent.mcp;

import edu.szu.agent.skill.Skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ToolSchema — converts a {@link Skill} into a JSON-Schema-shaped
 * description suitable for MCP {@code tools/list}.
 *
 * <p>Per ADR-0001 D5: this is a thin schema emitter. The actual
 * schema is hand-curated (not runtime-generated) because MCP clients
 * are strict about schema shape, and the parameter set for
 * {@code booking_venue} is small and stable.
 *
 * <p>Schema versioning: when the Skill's parameter set changes,
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
    public static final String SCHEMA_VERSION = "1.1";

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
        tool.put("inputSchema", schemaFor(skill.name()));
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
     * Hand-curated inputSchema per Skill. Matches MCP.md §tools/list.
     * New Skills add a switch branch here.
     *
     * @since 0.1.0
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaFor(String skillName) {
        return switch (skillName) {
            case "booking_venue" -> bookingVenueSchema();
            case "kb_query" -> kbQuerySchema();
            default -> Map.of("type", "object", "additionalProperties", true);
        };
    }

    private static Map<String, Object> bookingVenueSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> username = new LinkedHashMap<>();
        username.put("type", "string");
        username.put("description", "学号");
        properties.put("username", username);

        Map<String, Object> campus = new LinkedHashMap<>();
        campus.put("type", "string");
        campus.put("description", "校区(YUEHAI/LYULAKU/...)");
        properties.put("campus", campus);

        Map<String, Object> sport = new LinkedHashMap<>();
        sport.put("type", "string");
        sport.put("description", "运动项目(TENNIS/BADMINTON/...)");
        properties.put("sport", sport);

        Map<String, Object> date = new LinkedHashMap<>();
        date.put("type", "string");
        date.put("format", "date");
        date.put("description", "ISO 8601 日期,例如 2026-06-13");
        properties.put("date", date);

        Map<String, Object> timeSlot = new LinkedHashMap<>();
        timeSlot.put("type", "object");
        timeSlot.put("description", "预约时段,例如 {\"start\": \"19:00\", \"end\": \"20:00\"}");

        Map<String, Object> tsProps = new LinkedHashMap<>();
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("type", "string");
        start.put("description", "HH:mm 格式");
        tsProps.put("start", start);
        Map<String, Object> end = new LinkedHashMap<>();
        end.put("type", "string");
        end.put("description", "HH:mm 格式");
        tsProps.put("end", end);
        timeSlot.put("properties", tsProps);
        properties.put("timeSlot", timeSlot);

        Map<String, Object> preferredVenue = new LinkedHashMap<>();
        preferredVenue.put("type", "integer");
        preferredVenue.put("description", "1-based 场地序号,默认 1");
        preferredVenue.put("default", 1);
        properties.put("preferredVenue", preferredVenue);

        schema.put("properties", properties);
        schema.put("required", List.of("username", "campus", "sport", "date", "timeSlot"));
        return schema;
    }

    private static Map<String, Object> kbQuerySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "查询关键词,例如 图书馆、食堂、选课");
        properties.put("query", query);

        Map<String, Object> limit = new LinkedHashMap<>();
        limit.put("type", "integer");
        limit.put("description", "最大返回条数,默认 5");
        limit.put("default", 5);
        properties.put("limit", limit);

        Map<String, Object> category = new LinkedHashMap<>();
        category.put("type", "string");
        category.put("description", "可选分类过滤: CAMPUS_BASICS / DINING / LIBRARY / ACADEMICS / FAQ");
        properties.put("category", category);

        schema.put("properties", properties);
        schema.put("required", List.of("query"));
        return schema;
    }
}

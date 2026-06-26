package edu.szu.agent.task;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared factory for building {@link CampusTask#inputSchema()} maps.
 *
 * <p>All five read-only tasks ({@code calendar_get}, {@code exam_list},
 * {@code kb_query}, {@code notice_list}, {@code schedule_list}) use the
 * identical {@code LinkedHashMap} + nested {@code properties} pattern.
 * This utility eliminates ~120 lines of copy-paste and ensures all
 * schemas are structurally consistent.
 *
 * <p>Two layers:
 * <ul>
 *   <li>Low-level {@link #property} / {@link #enumProperty} — build a
 *       single property fragment with extras (examples / default /
 *       format / pattern / minimum / maximum).</li>
 *   <li>Composite {@link #requiredSingle} / {@link #optionalOnly} /
 *       {@link #schemaWithOptional} — assemble the object-level schema.
 *       These delegate to the low-level helpers internally so all three
 *       factories share the same fragment-building code path.</li>
 * </ul>
 *
 * // 设计模式: Factory Method
 * // 编程技术: 泛型 / Lambda / 静态工具类
 *
 * @since 0.4.0
 * @author 王子豪
 */
public final class TaskInputSchema {

    private TaskInputSchema() {}

    /**
     * Builds a single property fragment with no constraints beyond
     * {@code type} and {@code description}.
     *
     * @param type        JSON Schema type (e.g. {@code "string"}, {@code "integer"})
     * @param description human-readable description shown in MCP tool listings
     * @return an ordered map suitable for nesting under {@code properties}
     */
    public static Map<String, Object> property(String type, String description) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        out.put("description", description);
        return out;
    }

    /**
     * Builds a single property fragment and merges arbitrary JSON-Schema
     * extras (examples / default / format / pattern / minimum / maximum).
     *
     * <p>Insertion order: {@code type} → {@code description} → all keys from
     * {@code extras} (in their iteration order). Caller-supplied keys
     * <em>cannot</em> overwrite {@code type} or {@code description} — those
     * are written last so the contract stays stable.
     *
     * @param type        JSON Schema type
     * @param description human-readable description
     * @param extras      additional schema keys; may be empty, may be null
     * @return an ordered map suitable for nesting under {@code properties}
     * @since 0.5.0
     * @author 王子豪
     */
    public static Map<String, Object> property(String type, String description,
                                               Map<String, Object> extras) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (extras != null) {
            out.putAll(extras);
        }
        out.put("type", type);
        out.put("description", description);
        return out;
    }

    /**
     * Builds a string-typed property fragment with an {@code enum}
     * constraint. Equivalent to
     * {@code property("string", description, Map.of("enum", values))}.
     *
     * @param description human-readable description
     * @param values      allowed enum values (insertion order preserved)
     * @return an ordered map with {@code type=string}, {@code description}, {@code enum}
     */
    public static Map<String, Object> enumProperty(String description, List<String> values) {
        return enumProperty(description, values, Map.of());
    }

    /**
     * Builds a string-typed property fragment with an {@code enum}
     * constraint <em>and</em> arbitrary extras (examples, default).
     *
     * <p>The {@code enum} key is written first so caller-supplied extras
     * (e.g. {@code default}) appear after the constraint — and so an
     * accidentally-supplied {@code enum} in {@code extras} is silently
     * overwritten by the canonical values list.
     *
     * @param description human-readable description
     * @param values      allowed enum values
     * @param extras      additional schema keys; may be empty, may be null
     * @return an ordered map
     * @since 0.5.0
     * @author 王子豪
     */
    public static Map<String, Object> enumProperty(String description, List<String> values,
                                                    Map<String, Object> extras) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enum", List.copyOf(values));
        if (extras != null) {
            out.putAll(extras);
        }
        out.put("type", "string");
        out.put("description", description);
        return out;
    }

    /**
     * Builds a minimal input schema with one required property.
     *
     * @param requiredFieldName name of the required field
     * @param fieldDescription description of the field
     * @return JSON-Schema-shaped map for {@link CampusTask#inputSchema()}
     */
    public static Map<String, Object> requiredSingle(String requiredFieldName,
                                                    String fieldDescription) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(requiredFieldName, property("string", fieldDescription));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(requiredFieldName));
        return schema;
    }

    /**
     * Builds an input schema with only optional properties (no required fields).
     *
     * @param optionalProperties map of field name → schema fragment
     * @return JSON-Schema-shaped map for {@link CampusTask#inputSchema()}
     */
    public static Map<String, Object> optionalOnly(Map<String, Map<String, Object>> optionalProperties) {
        Map<String, Object> properties = new LinkedHashMap<>(optionalProperties);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    /**
     * Builds an input schema with one required and multiple optional properties.
     *
     * @param requiredFieldName name of the required field
     * @param requiredDescription description of the required field
     * @param optionalProperties map of optional field name → schema fragment
     * @return JSON-Schema-shaped map for {@link CampusTask#inputSchema()}
     */
    public static Map<String, Object> schemaWithOptional(String requiredFieldName,
                                                         String requiredDescription,
                                                         Map<String, ?> optionalProperties) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (var e : optionalProperties.entrySet()) {
            properties.put(e.getKey(), Map.copyOf(
                java.util.Map.class.cast(e.getValue())));
        }
        properties.put(requiredFieldName, property("string", requiredDescription));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(requiredFieldName));
        return schema;
    }
}

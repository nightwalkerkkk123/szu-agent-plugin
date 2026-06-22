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
 * // 设计模式: Factory Method
 * // 编程技术: 泛型 / Lambda / 静态工具类
 *
 * @since 0.4.0
 * @author 王子豪
 */
public final class TaskInputSchema {

    private TaskInputSchema() {}

    /**
     * Builds a minimal input schema with one required property.
     *
     * @param requiredFieldName name of the required field
     * @param fieldDescription description of the field
     * @return JSON-Schema-shaped map for {@link CampusTask#inputSchema()}
     */
    public static Map<String, Object> requiredSingle(String requiredFieldName,
                                                    String fieldDescription) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        prop.put("description", fieldDescription);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(requiredFieldName, prop);

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
        Map<String, Object> requiredProp = new LinkedHashMap<>();
        requiredProp.put("type", "string");
        requiredProp.put("description", requiredDescription);

        Map<String, Object> properties = new LinkedHashMap<>();
        for (var e : optionalProperties.entrySet()) {
            properties.put(e.getKey(), Map.copyOf(
                java.util.Map.class.cast(e.getValue())));
        }
        properties.put(requiredFieldName, requiredProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(requiredFieldName));
        return schema;
    }
}

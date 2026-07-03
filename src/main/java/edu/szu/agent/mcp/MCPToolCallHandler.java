package edu.szu.agent.mcp;

import edu.szu.agent.error.BookingException;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.skill.Skill;
import edu.szu.agent.skill.Skills;
import edu.szu.agent.task.TaskInput;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP {@code tools/call} implementation.
 *
 * <p>Per ADR-0001 D5: this is the dispatch path. The MCP host sends
 * a {@code tools/call} request like:
 * <pre>{@code
 * {
 *   "name": "booking_venue",
 *   "arguments": {
 *     "username": "2023150090",
 *     "campus": "YUEHAI",
 *     "sport": "TENNIS",
 *     "date": "2026-06-13",
 *     "timeSlot": {"start": "19:00", "end": "20:00"}
 *   }
 * }
 * }</pre>
 *
 * <p>This class translates the JSON arguments into a {@link TaskInput},
 * looks up the registered Skill by name, invokes it, and wraps the
 * result (or thrown {@link BookingException}) into a uniform
 * response envelope with a trace_id.
 *
 * <p>Response shape (matches CLI JSON envelope in PRD §5.2):
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": { ... task result ... },
 *   "errorCode": null,
 *   "errorMessage": null,
 *   "traceId": "20260613-ABC123",
 *   "elapsedMs": 1234
 * }
 * }</pre>
 *
 * // 编程技术: 泛型 / Lambda / 枚举
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class MCPToolCallHandler {

    private MCPToolCallHandler() {
    }

    /**
     * Invokes a tool by name with the given arguments.
     *
     * @param toolName  the skill name (e.g. {@code "booking_venue"})
     * @param arguments JSON arguments from the MCP host; values may be
     *                  String, Number, Boolean, Map, List, or null.
     *                  Nested Maps (e.g. {@code timeSlot}) are
     *                  flattened to dotted keys
     *                  ({@code "timeSlot.start" = "19:00"}).
     * @return response envelope (success=true with data, or
     *         success=false with errorCode)
     * @since 0.6.0
     */
    public static Map<String, Object> call(String toolName, Map<String, Object> arguments) {
        long startMs = System.currentTimeMillis();
        String traceId = Tracer.getInstance().generateTraceId();

        Skill<?> skill = findSkill(toolName);
        if (skill == null) {
            return envelope(false, null, "INVALID_REQUEST",
                "Unknown tool: " + toolName, traceId, startMs);
        }

        try {
            Map<String, String> flat = flatten(arguments == null ? Map.of() : arguments);
            TaskInput input = new TaskInput(flat);
            Object result = skill.task().execute(input);
            return envelope(true, result, null, null, traceId, startMs);
        } catch (IllegalArgumentException e) {
            return envelope(false, null, "INVALID_REQUEST", e.getMessage(), traceId, startMs);
        } catch (BookingException e) {
            return envelope(false, null, e.code().name(), e.getMessage(), traceId, startMs);
        } catch (Exception e) {
            return envelope(false, null, "UNKNOWN", e.getMessage(), traceId, startMs);
        }
    }

    private static Skill<?> findSkill(String name) {
        return Skills.getInstance().all().stream()
            .filter(s -> s.name().equals(name))
            .findFirst()
            .orElse(null);
    }

    /**
     * Flattens nested Maps to dotted keys, coerces non-string values
     * to their {@code toString()}. Preserves insertion order via the
     * input map's iterator.
     *
     * @since 0.6.0
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> flatten(Map<String, Object> input) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : input.entrySet()) {
            flattenInto(out, e.getKey(), e.getValue());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void flattenInto(Map<String, String> out, String key, Object value) {
        if (value instanceof Map<?, ?> nested) {
            for (Map.Entry<?, ?> entry : nested.entrySet()) {
                flattenInto(out, key + "." + entry.getKey(), entry.getValue());
            }
        } else if (value != null) {
            out.put(key, value.toString());
        }
        // null values are skipped — the task can use .require() to detect
    }

    private static Map<String, Object> envelope(boolean success, Object data,
                                                String errorCode, String errorMessage,
                                                String traceId, long startMs) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("success", success);
        root.put("data", data);
        root.put("errorCode", errorCode);
        root.put("errorMessage", errorMessage);
        root.put("traceId", traceId);
        root.put("elapsedMs", System.currentTimeMillis() - startMs);
        return root;
    }
}

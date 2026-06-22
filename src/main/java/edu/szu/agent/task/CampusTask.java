package edu.szu.agent.task;

import java.util.Map;

/**
 * Generic campus task contract — every campus-bound action
 * (booking, notice, growth, ...) implements this.
 *
 * <p>Per ADR-0001 D10: the project is scoped to {@code booking} as P0
 * (the only realized implementation is {@code BookingTask}). Other
 * Campus tasks are deferred P1, but the type system is in place so
 * Skill / MCP layers can wrap a {@code CampusTask<T>} uniformly
 * without knowing its concrete type.
 *
 * <p>Generics let each task return a domain-specific result type:
 * <ul>
 *   <li>{@code BookingTask} → {@code BookingResult}
 *   <li>(future) {@code NoticeTask} → {@code List<Notice>}
 *   <li>(future) {@code GrowthTask} → {@code GrowthProgress}
 * </ul>
 *
 * // 编程技术: 泛型 / 函数式接口
 *
 * @param <T> the task's domain-specific result type
 * @since 0.1.0
 * @author 王子豪
 */
public interface CampusTask<T> {

    /**
     * Stable name used by the Skill registry and MCP tool registry.
     * Convention: snake_case, matches the CLI subcommand path
     * (e.g. {@code "booking_venue"}, {@code "notice_list"}).
     *
     * @return the task name, never {@code null} or blank
     * @since 0.1.0
     */
    String name();

    /**
     * Short human-readable description, used by {@code skill list} and
     * MCP {@code tools/list}. One sentence.
     *
     * @return the description, never {@code null}
     * @since 0.1.0
     */
    String description();

    /**
     * Executes the task. Implementations are expected to be idempotent
     * within a single business attempt — retries are handled by the
     * caller via {@link edu.szu.agent.retry.RetryPolicy}.
     *
     * @param input task-specific input
     * @return the task result, never {@code null}
     * @throws edu.szu.agent.error.BookingException on failure
     * @since 0.1.0
     */
    T execute(TaskInput input);

    /**
     * Returns the MCP {@code inputSchema} for this task.
     *
     * <p>Per the architecture-deepening plan (改动 2): schema is declared on
     * the task itself so that new skills automatically participate in
     * {@code tools/list} without adding a switch branch in {@link edu.szu.agent.mcp.ToolSchema}.
     *
     * @return a JSON-Schema-shaped map with {@code type: "object"} and
     *         {@code properties} / {@code required} keys
     * @since 0.4.0
     */
    default Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "additionalProperties", true);
    }
}

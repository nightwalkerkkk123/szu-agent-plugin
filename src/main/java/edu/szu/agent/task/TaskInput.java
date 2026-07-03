package edu.szu.agent.task;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Task input — minimal parameter bag, the only way {@link CampusTask}
 * receives data from the CLI / Skill / MCP layers.
 *
 * <p>Per ADR-0001 D5: a thin wrapper over a {@code Map<String, String>}
 * of named parameters. We don't use a per-task subclass because
 * (a) MCP calls come in as a JSON object over the wire anyway,
 * (b) the small fixed parameter set is cleaner as a map,
 * (c) it keeps the {@code CampusTask<T>} contract task-agnostic.
 *
 * <p>All values are {@code String} — the task itself parses to the
 * domain type it needs (e.g. {@code Campus.valueOf(input.get("campus"))}).
 * This avoids premature type-binding at the Skill/MCP boundary.
 *
 * // 编程技术: record(Java 16+,值类型不可变)
 *
 * @since 0.6.0
 * @author 王子豪
 */
public record TaskInput(Map<String, String> params) {

    public TaskInput {
        Objects.requireNonNull(params, "params");
        // Defensive copy: callers may mutate the original map after
        // constructing a TaskInput. We do not want surprise mutations
        // to leak into the task's view of parameters.
        params = Collections.unmodifiableMap(Map.copyOf(params));
    }

    /**
     * Returns the parameter value for {@code key}, or {@code null} if
     * absent. The CLI / Skill layer is responsible for required-param
     * validation; the task can use {@link #require(String)} for
     * required keys.
     *
     * @param key the parameter name
     * @return the value, or {@code null}
     * @since 0.6.0
     */
    public String get(String key) {
        return params.get(key);
    }

    /**
     * Returns the parameter value for {@code key}, or throws if absent.
     *
     * @param key the required parameter name
     * @return the value, never {@code null}
     * @throws IllegalArgumentException if the key is missing or blank
     * @since 0.6.0
     */
    public String require(String key) {
        String v = params.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return v;
    }

    /**
     * Returns the parameter value for {@code key} as an int, or
     * {@code defaultValue} if absent or unparseable.
     *
     * @since 0.6.0
     */
    public int getInt(String key, int defaultValue) {
        String v = params.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

package edu.szu.agent.skill;

import edu.szu.agent.task.CampusTask;

import java.util.Objects;

/**
 * Skill — a named, discoverable capability backed by a {@link CampusTask}.
 *
 * <p>Per ADR-0001 D5: a Skill is the thin wrapper that external AI
 * Agents (Claude / ChatGPT / OpenClaw) reference by name when they
 * want to invoke a campus-bound action. The Skill layer adds:
 * <ul>
 *   <li>a stable {@code name} (snake_case, the public contract)
 *   <li>a short human-readable {@code description}
 *   <li>delegation to the underlying {@link CampusTask} for execution
 * </ul>
 *
 * <p>Use {@link Skills#register} / {@link Skills#all} to manage the
 * global registry. The registry is a singleton, matching the
 * {@code ConfigManager} / {@code Tracer} pattern (per ADR-0007 D1).
 *
 * <p>This is the only Skill type the project ships — extending to
 * more skills is done by registering more {@link CampusTask}
 * implementations.
 *
 * // 编程技术: 泛型 / record(Java 16+)
 *
 * @param <T> the wrapped task's result type
 * @since 0.6.0
 * @author 王子豪
 */
public record Skill<T>(String name, String description, CampusTask<T> task) {

    public Skill {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(task, "task");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Skill name must not be blank");
        }
        if (!name.equals(task.name())) {
            throw new IllegalArgumentException(
                "Skill name '" + name + "' must match task name '" + task.name() + "'");
        }
    }

    /**
     * Creates a Skill whose {@code name} and {@code description} are both
     * derived from the task itself, making the {@link CampusTask} the
     * single source of truth.
     *
     * <p>Prefer this factory over the canonical constructor for built-in
     * skills: hand-writing the description at the registration site lets
     * it silently drift from {@link CampusTask#description()} — which is
     * what {@code tools/list} exports to the Agent. The explicit
     * constructor remains for callers whose metadata legitimately
     * originates outside the task, e.g. external skill loading from a
     * manifest.
     *
     * // Design Pattern: Static Factory Method
     *
     * @param task the backing task; supplies both name and description
     * @param <T>  the task's result type
     * @return a Skill wrapping {@code task}
     * @since 0.6.0
     * @author 王子豪
     */
    public static <T> Skill<T> of(CampusTask<T> task) {
        Objects.requireNonNull(task, "task");
        return new Skill<>(task.name(), task.description(), task);
    }
}

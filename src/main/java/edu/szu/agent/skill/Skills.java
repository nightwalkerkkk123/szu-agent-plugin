package edu.szu.agent.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Skill registry — the in-memory list of all registered Skills.
 *
 * <p>Per ADR-0007 D1: this is a Singleton (double-checked locking),
 * matching the {@code ConfigManager} / {@code Tracer} pattern.
 *
 * <p>Skills are registered at startup (typically by a static
 * initializer in {@code Main} or by a {@code ServiceLoader}). The
 * {@code skill list} CLI command reads from this registry.
 *
 * <p>Thread-safety: {@link CopyOnWriteArrayList} is used because
 * registration happens once at startup, and {@link #all()} is read
 * many times — copy-on-write gives lock-free reads at the cost of
 * a full copy on each write, which is fine for small N (≪100).
 *
 * // Design Pattern: Singleton (double-checked locking)
 * // 编程技术: 泛型 / 枚举 / Lambda
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class Skills {

    private static volatile Skills instance;

    private final List<Skill<?>> skills = new CopyOnWriteArrayList<>();

    private Skills() {
    }

    public static Skills getInstance() {
        if (instance == null) {
            synchronized (Skills.class) {
                if (instance == null) {
                    instance = new Skills();
                }
            }
        }
        return instance;
    }

    /**
     * Resets the registry — public for test isolation only. Production
     * code should never call this. Mirrors {@code Tracer.reset()}.
     *
     * @since 0.6.0
     */
    public static void reset() {
        synchronized (Skills.class) {
            if (instance != null) {
                instance.skills.clear();
            }
        }
    }

    /**
     * Registers a Skill. Duplicate names are rejected.
     *
     * @param skill the skill to register
     * @throws IllegalArgumentException if a skill with the same name
     *         is already registered
     * @since 0.6.0
     */
    public void register(Skill<?> skill) {
        Objects.requireNonNull(skill, "skill");
        synchronized (skills) {
            for (Skill<?> existing : skills) {
                if (existing.name().equals(skill.name())) {
                    throw new IllegalArgumentException(
                        "Skill already registered: " + skill.name());
                }
            }
            skills.add(skill);
        }
    }

    /**
     * Returns an immutable view of all registered skills, sorted by
     * name for stable output.
     *
     * @return sorted immutable list
     * @since 0.6.0
     */
    public List<Skill<?>> all() {
        List<Skill<?>> copy = new ArrayList<>(skills);
        copy.sort((a, b) -> a.name().compareTo(b.name()));
        return Collections.unmodifiableList(copy);
    }

    /**
     * Returns the count of registered skills.
     *
     * @since 0.6.0
     */
    public int size() {
        return skills.size();
    }
}

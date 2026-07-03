package edu.szu.agent.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Central factory for project-wide {@link ObjectMapper} instances.
 *
 * <p>Every mapper that serializes domain objects carrying {@code java.time}
 * fields — e.g. {@code AcademicEvent.date()} ({@link java.time.LocalDate}),
 * {@code ExamSchedule.startTime()} ({@link java.time.LocalTime}),
 * {@code ScheduleListResult.snapshotAt()} ({@link java.time.Instant}) — MUST
 * be obtained here.
 *
 * <p><strong>Why this exists.</strong> Registering {@link JavaTimeModule}
 * alone is <em>not</em> enough: with Jackson's default
 * {@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} still enabled, a
 * {@code LocalDate} serializes to a numeric array such as {@code [2026,3,4]}
 * rather than the ISO-8601 string {@code "2026-03-04"}. Bundling both the
 * module registration and the feature toggle in one place means no caller can
 * register the module but forget the toggle, which previously caused MCP tool
 * results to emit array-shaped dates.
 *
 * <p><strong>Backward compatibility.</strong> Only serialization (writing) is
 * affected. {@code JavaTimeModule}'s deserializers accept both ISO-8601
 * strings and the legacy numeric-array form, so cache files written before
 * this change remain readable.
 *
 * // Design Pattern: Factory Method (centralized ObjectMapper construction)
 * // 编程技术: 静态工厂 / Jackson 模块注册
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class JsonMappers {

    private JsonMappers() {
    }

    /**
     * Creates a new {@link ObjectMapper} configured for the project's JSON
     * contract: {@code java.time} support with ISO-8601 (not timestamp-array)
     * output.
     *
     * <p>A fresh, independently-mutable instance is returned on each call so
     * callers may layer on additional features (e.g.
     * {@link SerializationFeature#INDENT_OUTPUT}) without mutating shared
     * state.
     *
     * @return a configured mapper
     * @since 0.6.0
     */
    public static ObjectMapper standard() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}

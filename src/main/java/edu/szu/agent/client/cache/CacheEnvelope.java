package edu.szu.agent.client.cache;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Instant;

/**
 * JSON envelope wrapping cached payloads.
 *
 * <p>Structure:
 * <pre>{@code
 * {
 *   "fetchedAt": "2026-06-23T08:30:00Z",
 *   "schemaVersion": 1,
 *   "payload": [ ... ]
 * }
 * }</pre>
 *
 * // 编程技术: record / Jackson JSON 序列化
 *
 * @since 0.3.0
 * @author 王子豪
 */
public record CacheEnvelope<T>(
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant fetchedAt,
    int schemaVersion,
    T payload
) {
    /**
     * Creates a new envelope wrapping the given payload.
     *
     * @param payload       cached data
     * @param schemaVersion payload schema version (increment on breaking changes)
     * @since 0.3.0
     * @author 王子豪
     */
    public static <T> CacheEnvelope<T> of(T payload, int schemaVersion) {
        return new CacheEnvelope<>(Instant.now(), schemaVersion, payload);
    }

    /**
     * Casts a typed {@link TypeReference} to a wildcard so it can be passed
     * to {@link edu.szu.agent.client.step.CacheLookupStep} without triggering
     * Java's anonymous-class type-capture incompatibility.
     *
     * @param typed a type reference of the form {@code new TypeReference<T>() {}}
     * @return the same reference as {@code TypeReference<?>}
     * @since 0.3.0
     * @author 王子豪
     */
    @SuppressWarnings("unchecked")
    public static <T> TypeReference<?> envelopeType(TypeReference<CacheEnvelope<T>> typed) {
        return (TypeReference<?>) typed;
    }
}

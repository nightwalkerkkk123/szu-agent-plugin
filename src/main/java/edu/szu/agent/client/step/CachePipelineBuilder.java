package edu.szu.agent.client.step;

import com.fasterxml.jackson.core.type.TypeReference;
import edu.szu.agent.client.cache.CacheEnvelope;
import edu.szu.agent.client.cache.CacheKey;
import edu.szu.agent.client.cache.CacheStore;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.CourseEntry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Builder that assembles a {@link CacheLookupStep} + {@link CacheWriteStep}
 * pair around a cache {@link CacheStore}. Extracted from
 * {@code EhallScheduleClient.defaultStepsWithCache} so future pipelines
 * (homework, exam) can reuse the same cache-aware composition without
 * re-implementing the {@code TypeReference} + {@code CacheKey} boilerplate.
 *
 * <p>Typical usage in {@code EhallScheduleClient}:
 * <pre>{@code
 * List<BookingStep> built = new ArrayList<>();
 * built.add(new RestoreSessionStep(store, probe, ttl));
 * built.add(new CasLoginStep(EHALL_SCHEDULE_URL));
 * built.addAll(new CachePipelineBuilder()
 *     .store(cacheStore)
 *     .scope("schedule", "schedule-" + account.studentId(), 1)
 *     .populate((ctx, p) -> ctx.scheduleCourses(safeCast(p)))
 *     .extract(ctx -> ctx.scheduleCourses())
 *     .buildLookup());
 * built.add(new NavigateToScheduleStep());
 * built.add(new ParseScheduleStep());
 * built.addAll(new CachePipelineBuilder()
 *     .scope("schedule", "schedule-" + account.studentId(), 1)
 *     .extract(ctx -> ctx.scheduleCourses())
 *     .buildWrite());
 * if (store != null) built.add(new PersistSessionStep(store));
 * }</pre>
 *
 * <p>Design rationale: this builder is the single seam where the cache
 * plumbing lives. Pipeline composers do not need to know about
 * {@link TypeReference} captures, anonymous-class casts, or {@link CacheKey}
 * triple-tuple construction.
 *
 * <p>// Design Pattern: Builder + Strategy assembly
 * // 编程技术: Builder 模式 / 泛型捕获 / Lambda / 不可变中间状态
 *
 * @since 0.3.0
 * @author 王子豪
 */
public final class CachePipelineBuilder {

    private CacheStore store;
    private String scope;
    private String key;
    private int schemaVersion = 1;
    private BiConsumer<BookingContext, Object> populate;
    private Function<BookingContext, Object> extract;

    /**
     * Sets the cache store. Required for both {@link #buildLookup()} and
     * {@link #buildWrite()}.
     */
    public CachePipelineBuilder store(CacheStore store) {
        this.store = store;
        return this;
    }

    /**
     * Sets the cache key triple. {@code scope} and {@code key} must match the
     * whitelist patterns enforced by {@link CacheStore}.
     */
    public CachePipelineBuilder scope(String scope, String key, int schemaVersion) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.key = Objects.requireNonNull(key, "key");
        this.schemaVersion = schemaVersion;
        return this;
    }

    /**
     * Sets the population function used on cache hit: receive the typed payload
     * (already deserialized) and assign it to the right context slot.
     */
    public CachePipelineBuilder populate(BiConsumer<BookingContext, Object> populate) {
        this.populate = populate;
        return this;
    }

    /**
     * Sets the extraction function used on cache miss: pull the payload out of
     * the context after the pipeline populates it (e.g. after parsing).
     */
    public CachePipelineBuilder extract(Function<BookingContext, Object> extract) {
        this.extract = extract;
        return this;
    }

    /**
     * Builds a single {@link CacheLookupStep} that:
     * <ul>
     *   <li>Checks {@link CacheStore#isFresh(String, String)} (TTL looked up
     *       internally by scope)</li>
     *   <li>On hit: deserializes the envelope and invokes {@link #populate}</li>
     *   <li>On miss: passes through, allowing the next step (browser automation)
     *       to run</li>
     * </ul>
     *
     * <p>The {@code envelopeType} is fixed at {@code CacheEnvelope<List<T>>}
     * for any caller — the lookup step is generic over the payload type, and
     * the existing step implementation already erases via {@code TypeReference<?>}.
     */
    public List<BookingStep> buildLookup() {
        CacheKey cacheKey = requireKey();
        requireStore();
        Objects.requireNonNull(populate, "populate");

        @SuppressWarnings("unchecked")
        TypeReference<CacheEnvelope<List<Object>>> typedEnvelope =
            new TypeReference<>() {};
        TypeReference<?> envelopeType = typedEnvelope;

        return List.of(new CacheLookupStep(
            store, cacheKey, envelopeType, populate));
    }

    /**
     * Builds a single {@link CacheWriteStep} that writes the payload extracted
     * from the context back to the cache (no-op on cache hit).
     */
    public List<BookingStep> buildWrite() {
        CacheKey cacheKey = requireKey();
        requireStore();
        Objects.requireNonNull(extract, "extract");

        return List.of(new CacheWriteStep(
            store, cacheKey, extract, schemaVersion));
    }

    private CacheKey requireKey() {
        Objects.requireNonNull(scope, "scope (call scope() first)");
        Objects.requireNonNull(key, "key (call scope() first)");
        return new CacheKey(scope, key, schemaVersion);
    }

    private void requireStore() {
        Objects.requireNonNull(store, "store (call store() first)");
    }

    /**
     * Convenience overload: assemble the full lookup-write pair as a single
     * list when both ends are configured.
     */
    public List<BookingStep> buildLookupAndWrite() {
        List<BookingStep> steps = new ArrayList<>(2);
        steps.addAll(buildLookup());
        steps.addAll(buildWrite());
        return List.copyOf(steps);
    }

    /**
     * Helper kept for parity with the prior {@code defaultStepsWithCache}
     * shape — returns an empty list if {@code store} is null, otherwise the
     * configured lookup + write pair. Callers can spread this with
     * {@code built.addAll(...)}.
     */
    public static List<BookingStep> assembleLookupAndWrite(CacheStore cacheStore,
                                                           String scope,
                                                           String key,
                                                           int schemaVersion,
                                                           BiConsumer<BookingContext, Object> populate,
                                                           Function<BookingContext, Object> extract) {
        if (cacheStore == null) {
            return List.of();
        }
        return new CachePipelineBuilder()
            .store(cacheStore)
            .scope(scope, key, schemaVersion)
            .populate(populate)
            .extract(extract)
            .buildLookupAndWrite();
    }

    /**
     * No-op helper to keep the session-restore + persist symmetry visible
     * to callers without leaking {@link SessionStore}/{@link SessionProbe}
     * into the cache builder. Returns an empty list (the caller adds
     * {@link RestoreSessionStep} / {@link PersistSessionStep} directly).
     */
    public static List<BookingStep> noSessionBoundary() {
        return List.of();
    }

    /** Convenience overload to avoid {@code Duration} import at call sites. */
    public static List<BookingStep> sessionRestore(SessionStore store,
                                                   SessionProbe probe,
                                                   Duration ttl) {
        if (store == null || probe == null || ttl == null) {
            return List.of();
        }
        return List.of(new RestoreSessionStep(store, probe, ttl));
    }

    /** Convenience overload to avoid {@code PersistSessionStep} import at call sites. */
    public static List<BookingStep> sessionPersist(SessionStore store) {
        if (store == null) {
            return List.of();
        }
        return List.of(new PersistSessionStep(store));
    }

    /**
     * Typed schedule cache pipeline: a {@link CacheLookupStep} +
     * {@link CacheWriteStep} pair bound to {@code CacheEnvelope<List<CourseEntry>>}.
     *
     * <p>Unlike the generic {@link #buildLookup()} (which erases the payload
     * element type to {@code Object}, causing Jackson to deserialize cached
     * course rows into {@code LinkedHashMap} instead of {@link CourseEntry}),
     * this helper captures the concrete element type so round-trip
     * deserialization preserves {@link CourseEntry}.
     *
     * @param store    cache store (must have the {@code schedule} scope TTL
     *                 configured)
     * @param key      cache key within the {@code schedule} scope
     * @param populate assigns the deserialized courses to the context on a hit
     * @param extract  pulls the courses back out of the context on a miss
     * @return {@code [CacheLookupStep, CacheWriteStep]}
     * @since 0.3.0
     * @author 王子豪
     */
    public static List<BookingStep> scheduleLookupAndWrite(
            CacheStore store,
            String key,
            BiConsumer<BookingContext, List<CourseEntry>> populate,
            Function<BookingContext, List<CourseEntry>> extract) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(populate, "populate");
        Objects.requireNonNull(extract, "extract");

        CacheKey cacheKey = new CacheKey("schedule", key, 1);
        TypeReference<?> envelopeType = CacheEnvelope.envelopeType(
            new TypeReference<CacheEnvelope<List<CourseEntry>>>() {});
        BiConsumer<BookingContext, Object> populateAdapter = (ctx, payload) -> {
            @SuppressWarnings("unchecked")
            List<CourseEntry> courses = (List<CourseEntry>) payload;
            populate.accept(ctx, courses);
        };
        Function<BookingContext, Object> extractAdapter = ctx -> extract.apply(ctx);

        return List.of(
            new CacheLookupStep(store, cacheKey, envelopeType, populateAdapter),
            new CacheWriteStep(store, cacheKey, extractAdapter, 1));
    }
}
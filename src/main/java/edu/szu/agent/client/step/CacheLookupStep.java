package edu.szu.agent.client.step;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.cache.CacheEnvelope;
import edu.szu.agent.client.cache.CacheKey;
import edu.szu.agent.client.cache.CacheStore;
import edu.szu.agent.json.JsonMappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Reads from the local cache before invoking browser automation.
 *
 * <p>On a cache hit: populates the context slot and short-circuits the pipeline
 * so the browser is never launched. On a miss or stale entry: passes through
 * unchanged, allowing the caller to invoke browser automation.
 *
 * <p>// Design Pattern: Strategy
 * // 编程技术: 泛型 / Jackson TypeReference / Lambda
 *
 * @since 0.3.0
 * @author 王子豪
 */
public final class CacheLookupStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(CacheLookupStep.class);
    private static final ObjectMapper MAPPER = JsonMappers.standard();

    private final CacheStore store;
    private final CacheKey cacheKey;
    private final TypeReference<?> envelopeType;
    private final BiConsumer<BookingContext, Object> populate;

    /**
     * Creates a cache-lookup step.
     *
     * <p>TTL is read from the {@link CacheStore}'s internal scope TTL table
     * (configured at store construction time via {@link CacheStore.Builder}).
     *
     * @param store          cache store (must have scope TTL configured)
     * @param cacheKey       cache key (scope/key/schemaVersion)
     * @param envelopeType   Jackson type reference for the envelope
     * @param populate       consumer to populate the context slot on a hit
     * @since 0.3.0
     * @author 王子豪
     */
    public CacheLookupStep(CacheStore store,
                           CacheKey cacheKey,
                           TypeReference<?> envelopeType,
                           BiConsumer<BookingContext, Object> populate) {
        this.store = Objects.requireNonNull(store, "store");
        this.cacheKey = Objects.requireNonNull(cacheKey, "cacheKey");
        this.envelopeType = Objects.requireNonNull(envelopeType, "envelopeType");
        this.populate = Objects.requireNonNull(populate, "populate");
    }

    @Override
    public String name() {
        return "CacheLookup[" + cacheKey.scope() + ":" + cacheKey.key() + "]";
    }

    @Override
    public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
        if (!store.exists(cacheKey.scope(), cacheKey.key())) {
            log.debug("Cache miss (file not found): {}/{}", cacheKey.scope(), cacheKey.key());
            ctx.cacheHit(false);
            return continueWith(ctx);
        }

        if (!store.isFresh(cacheKey.scope(), cacheKey.key())) {
            log.debug("Cache stale (TTL expired): {}/{}", cacheKey.scope(), cacheKey.key());
            ctx.cacheHit(false);
            return continueWith(ctx);
        }

        try {
            String json = store.read(cacheKey.scope(), cacheKey.key());
            JavaType javaType = MAPPER.getTypeFactory()
                .constructType(envelopeType.getType());
            CacheEnvelope<?> envelope = MAPPER.readValue(json, javaType);

            if (envelope.schemaVersion() != cacheKey.schemaVersion()) {
                log.debug("Cache schema version mismatch: expected {}, got {}",
                    cacheKey.schemaVersion(), envelope.schemaVersion());
                ctx.cacheHit(false);
                return continueWith(ctx);
            }

            @SuppressWarnings("unchecked")
            CacheEnvelope<Object> typed = (CacheEnvelope<Object>) envelope;
            populate.accept(ctx, typed.payload());
            ctx.cacheHit(true);
            ctx.cacheFetchedAt(typed.fetchedAt());
            log.info("Cache hit: {}/{} (fetched {})",
                cacheKey.scope(), cacheKey.key(), typed.fetchedAt());
            // Short-circuit: data already cached, skip browser automation steps.
            return new StepOutcome.ShortCircuit(ctx);

        } catch (Exception e) {
            log.warn("Failed to read cache {}/{}, treating as miss: {}",
                cacheKey.scope(), cacheKey.key(), e.getMessage());
            ctx.cacheHit(false);
            return continueWith(ctx);
        }
    }

    private static StepOutcome continueWith(BookingContext ctx) {
        return new StepOutcome.Continue(ctx);
    }
}

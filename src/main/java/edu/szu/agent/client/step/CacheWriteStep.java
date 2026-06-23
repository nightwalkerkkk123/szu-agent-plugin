package edu.szu.agent.client.step;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.cache.CacheEnvelope;
import edu.szu.agent.client.cache.CacheKey;
import edu.szu.agent.client.cache.CacheStore;
import edu.szu.agent.json.JsonMappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * Writes to the local cache after browser automation + parsing succeeds.
 *
 * <p>This step is a no-op when {@link BookingContext#cacheHit()} is {@code true}
 * (data was already cached; no need to re-write).
 *
 * <p>// Design Pattern: Strategy
 * // 编程技术: 泛型 / Jackson TypeReference / Lambda
 *
 * @since 0.3.0
 * @author 王子豪
 */
public final class CacheWriteStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(CacheWriteStep.class);
    private static final ObjectMapper MAPPER = JsonMappers.standard();

    private final CacheStore store;
    private final CacheKey cacheKey;
    private final Function<BookingContext, Object> extract;
    private final int schemaVersion;

    /**
     * Creates a cache-write step.
     *
     * @param store        cache store
     * @param cacheKey     cache key (scope/key/schemaVersion)
     * @param extract      function to extract the payload from the context
     * @param schemaVersion payload schema version
     * @since 0.3.0
     * @author 王子豪
     */
    public CacheWriteStep(CacheStore store,
                          CacheKey cacheKey,
                          Function<BookingContext, Object> extract,
                          int schemaVersion) {
        this.store = Objects.requireNonNull(store, "store");
        this.cacheKey = Objects.requireNonNull(cacheKey, "cacheKey");
        this.extract = Objects.requireNonNull(extract, "extract");
        this.schemaVersion = schemaVersion;
    }

    @Override
    public String name() {
        return "CacheWrite[" + cacheKey.scope() + ":" + cacheKey.key() + "]";
    }

    @Override
    public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
        // No-op if we already had a cache hit (skip unnecessary write)
        if (ctx.cacheHit()) {
            log.debug("Skipping write (cache already hit): {}/{}",
                cacheKey.scope(), cacheKey.key());
            return continueWith(ctx);
        }

        Object payload = extract.apply(ctx);
        if (payload == null) {
            log.debug("No payload to cache (extract returned null): {}/{}",
                cacheKey.scope(), cacheKey.key());
            return continueWith(ctx);
        }

        try {
            CacheEnvelope<Object> envelope = CacheEnvelope.of(payload, schemaVersion);
            String json = MAPPER.writeValueAsString(envelope);
            store.write(cacheKey.scope(), cacheKey.key(), json);
            log.info("Cached: {}/{}", cacheKey.scope(), cacheKey.key());
        } catch (Exception e) {
            // Cache write failure is non-fatal — log and continue
            log.warn("Failed to write cache {}/{}: {}",
                cacheKey.scope(), cacheKey.key(), e.getMessage());
        }

        return continueWith(ctx);
    }

    private static StepOutcome continueWith(BookingContext ctx) {
        return new StepOutcome.Continue(ctx);
    }
}

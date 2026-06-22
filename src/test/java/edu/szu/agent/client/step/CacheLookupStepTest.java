package edu.szu.agent.client.step;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.cache.CacheEnvelope;
import edu.szu.agent.client.cache.CacheKey;
import edu.szu.agent.client.cache.CacheStore;
import edu.szu.agent.domain.CourseEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link CacheLookupStep}.
 *
 * @since 0.3.0
 * @author 王子豪
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CacheLookupStep")
class CacheLookupStepTest {

    @Mock
    private BrowserLifecycle browser;

    @TempDir
    Path tmp;

    private CacheStore store;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        // Configure TTL table: schedule entries fresh for 1 day
        store = CacheStore.builder(tmp)
            .ttl("schedule", Duration.ofDays(1))
            .build();
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("命中时 populate context 并设置 cacheHit=true")
    void hitPopulatesContextAndSetsCacheHitTrue() throws Exception {
        CourseEntry entry1 = new CourseEntry("课程A", "1-2节", "老师A", "教室A",
            edu.szu.agent.domain.Weekday.MONDAY,
            edu.szu.agent.domain.Period.of(1, 2), null, false);
        CourseEntry entry2 = new CourseEntry("课程B", "3-4节", "老师B", "教室B",
            edu.szu.agent.domain.Weekday.MONDAY,
            edu.szu.agent.domain.Period.of(3, 4), null, false);
        List<CourseEntry> payload = List.of(entry1, entry2);
        CacheEnvelope<List<CourseEntry>> envelope = CacheEnvelope.of(payload, 1);
        String json = mapper.writeValueAsString(envelope);
        store.write("schedule", "k1", json);

        BiConsumer<BookingContext, Object> populate = (ctx, p) -> {
            @SuppressWarnings("unchecked")
            List<CourseEntry> courses = (List<CourseEntry>) p;
            ctx.scheduleCourses(courses);
        };

        TypeReference<?> envelopeType = CacheEnvelope.envelopeType(
            new TypeReference<CacheEnvelope<List<CourseEntry>>>() {});
        CacheKey key = new CacheKey("schedule", "k1", 1);
        CacheLookupStep step = new CacheLookupStep(store, key, envelopeType, populate);

        BookingContext ctx = new BookingContext(null);
        StepOutcome.ShortCircuit result = (StepOutcome.ShortCircuit) step.execute(browser, ctx);

        assertThat(result.nextContext().cacheHit()).isTrue();
        assertThat(result.nextContext().scheduleCourses()).hasSize(2);
        assertThat(result.nextContext().cacheFetchedAt()).isNotNull();
    }

    @Test
    @DisplayName("未命中时 cacheHit=false, context 不变")
    void missSetsCacheHitFalseAndContextUnchanged() {
        // Store has no "nonexistent" scope registered in TTL table → isFresh returns false
        CacheKey key = new CacheKey("schedule", "nonexistent", 1);
        BiConsumer<BookingContext, Object> populate = (ctx, p) -> { };
        TypeReference<?> envelopeType = CacheEnvelope.envelopeType(
            new TypeReference<CacheEnvelope<List<CourseEntry>>>() {});
        CacheLookupStep step = new CacheLookupStep(store, key, envelopeType, populate);

        BookingContext ctx = new BookingContext(null);
        StepOutcome.Continue result = (StepOutcome.Continue) step.execute(browser, ctx);

        assertThat(result.nextContext().cacheHit()).isFalse();
        assertThat(result.nextContext().scheduleCourses()).isNull();
        assertThat(result.nextContext().cacheFetchedAt()).isNull();
    }

    @Test
    @DisplayName("TTL 过期时视为未命中")
    void staleTtlTreatedAsMiss() throws Exception {
        // TTL = 1 second; backdate file mtime to force expiry
        CourseEntry payload = new CourseEntry("旧课程", null, "老师", "教室",
            edu.szu.agent.domain.Weekday.MONDAY,
            edu.szu.agent.domain.Period.of(1, 2), null, false);
        CacheEnvelope<CourseEntry> envelope = CacheEnvelope.of(payload, 1);
        String json = mapper.writeValueAsString(envelope);
        Path file = store.write("schedule", "k1", json);
        Files.setLastModifiedTime(file,
            java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(86401)));

        BiConsumer<BookingContext, Object> populate = (ctx, p) -> { };
        CacheKey key = new CacheKey("schedule", "k1", 1);
        TypeReference<?> envelopeType = CacheEnvelope.envelopeType(
            new TypeReference<CacheEnvelope<CourseEntry>>() {});
        CacheLookupStep step = new CacheLookupStep(store, key, envelopeType, populate);

        BookingContext ctx = new BookingContext(null);
        StepOutcome.Continue result = (StepOutcome.Continue) step.execute(browser, ctx);

        assertThat(result.nextContext().cacheHit()).isFalse();
    }

    @Test
    @DisplayName("schemaVersion 不匹配时视为未命中")
    void schemaVersionMismatchTreatedAsMiss() throws Exception {
        CourseEntry payload = new CourseEntry("课程", null, "老师", "教室",
            edu.szu.agent.domain.Weekday.TUESDAY,
            edu.szu.agent.domain.Period.of(3, 4), null, false);
        CacheEnvelope<CourseEntry> envelope = CacheEnvelope.of(payload, 2); // version 2
        String json = mapper.writeValueAsString(envelope);
        store.write("schedule", "k1", json);

        BiConsumer<BookingContext, Object> populate = (ctx, p) -> { };
        CacheKey key = new CacheKey("schedule", "k1", 1); // expecting version 1
        TypeReference<?> envelopeType = CacheEnvelope.envelopeType(
            new TypeReference<CacheEnvelope<CourseEntry>>() {});
        CacheLookupStep step = new CacheLookupStep(store, key, envelopeType, populate);

        BookingContext ctx = new BookingContext(null);
        StepOutcome.Continue result = (StepOutcome.Continue) step.execute(browser, ctx);

        assertThat(result.nextContext().cacheHit()).isFalse();
    }

    @Test
    @DisplayName("损坏的 JSON 视为未命中（不抛异常）")
    void corruptJsonTreatedAsMiss() throws Exception {
        store.write("schedule", "k1", "not valid json {{{");

        BiConsumer<BookingContext, Object> populate = (ctx, p) -> { };
        CacheKey key = new CacheKey("schedule", "k1", 1);
        TypeReference<?> envelopeType = CacheEnvelope.envelopeType(
            new TypeReference<CacheEnvelope<String>>() {});
        CacheLookupStep step = new CacheLookupStep(store, key, envelopeType, populate);

        BookingContext ctx = new BookingContext(null);
        StepOutcome.Continue result = (StepOutcome.Continue) step.execute(browser, ctx);

        assertThat(result.nextContext().cacheHit()).isFalse();
        verifyNoInteractions(browser);
    }

    @Test
    @DisplayName("命中时浏览器自动化不会被调用")
    void browserNotInvokedOnHit() throws Exception {
        CourseEntry payload = new CourseEntry("课程", null, "老师", "教室",
            edu.szu.agent.domain.Weekday.WEDNESDAY,
            edu.szu.agent.domain.Period.of(5, 5), null, false);
        CacheEnvelope<CourseEntry> envelope = CacheEnvelope.of(payload, 1);
        String json = mapper.writeValueAsString(envelope);
        store.write("schedule", "k1", json);

        BiConsumer<BookingContext, Object> populate = (ctx, p) -> { };
        CacheKey key = new CacheKey("schedule", "k1", 1);
        TypeReference<?> envelopeType = CacheEnvelope.envelopeType(
            new TypeReference<CacheEnvelope<CourseEntry>>() {});
        CacheLookupStep step = new CacheLookupStep(store, key, envelopeType, populate);

        step.execute(browser, new BookingContext(null));

        verifyNoInteractions(browser);
    }
}

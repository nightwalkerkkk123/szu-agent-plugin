package edu.szu.agent.client.step;

import edu.szu.agent.client.cache.CacheStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.szu.agent.client.cache.CacheEnvelope;
import edu.szu.agent.domain.CourseEntry;
import edu.szu.agent.domain.Period;
import edu.szu.agent.domain.WeekRange;
import edu.szu.agent.domain.Weekday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CachePipelineBuilder}.
 *
 * @since 0.6.0
 * @author 王子豪
 */
@DisplayName("CachePipelineBuilder")
class CachePipelineBuilderTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("assembleLookupAndWrite 在 cacheStore==null 时返回空列表")
    void assembleLookupAndWriteReturnsEmptyWhenStoreIsNull() {
        List<BookingStep> steps = CachePipelineBuilder.assembleLookupAndWrite(
            null, "schedule", "k", 1, (ctx, p) -> {}, ctx -> null);
        assertThat(steps).isEmpty();
    }

    @Test
    @DisplayName("assembleLookupAndWrite 返回 [CacheLookup, CacheWrite]")
    void assembleLookupAndWriteReturnsLookupAndWrite() {
        CacheStore store = CacheStore.builder(tmp)
            .ttl("schedule", Duration.ofDays(1))
            .build();

        List<BookingStep> steps = CachePipelineBuilder.assembleLookupAndWrite(
            store, "schedule", "schedule-u1", 1,
            (ctx, p) -> {},
            ctx -> null);

        assertThat(steps).hasSize(2);
        assertThat(steps.get(0)).isInstanceOf(CacheLookupStep.class);
        assertThat(steps.get(1)).isInstanceOf(CacheWriteStep.class);
    }

    @Test
    @DisplayName("buildLookup 缺失 populate 抛 NPE")
    void buildLookupRequiresPopulate() {
        CacheStore store = CacheStore.builder(tmp)
            .ttl("schedule", Duration.ofDays(1))
            .build();

        CachePipelineBuilder builder = new CachePipelineBuilder()
            .store(store)
            .scope("schedule", "k", 1);

        assertThatThrownBy(builder::buildLookup)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("populate");
    }

    @Test
    @DisplayName("buildWrite 缺失 extract 抛 NPE")
    void buildWriteRequiresExtract() {
        CacheStore store = CacheStore.builder(tmp)
            .ttl("schedule", Duration.ofDays(1))
            .build();

        CachePipelineBuilder builder = new CachePipelineBuilder()
            .store(store)
            .scope("schedule", "k", 1);

        assertThatThrownBy(builder::buildWrite)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("extract");
    }

    @Test
    @DisplayName("buildLookup 缺失 scope 抛 NPE")
    void buildLookupRequiresScope() {
        CacheStore store = CacheStore.builder(tmp).build();
        CachePipelineBuilder builder = new CachePipelineBuilder().store(store);

        assertThatThrownBy(builder::buildLookup)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("scope");
    }

    @Test
    @DisplayName("sessionRestore 在 store/probe/ttl 任一为空时返回空列表")
    void sessionRestoreEmptyWhenAnyArgNull() {
        assertThat(CachePipelineBuilder.sessionRestore(null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("sessionPersist 在 store==null 时返回空列表")
    void sessionPersistEmptyWhenStoreNull() {
        assertThat(CachePipelineBuilder.sessionPersist(null)).isEmpty();
    }

    @Test
    @DisplayName("scheduleLookupAndWrite round-trip 保留 CourseEntry 类型")
    void scheduleLookupAndWriteRoundTripKeepsCourseEntryType() throws Exception {
        CacheStore store = CacheStore.builder(tmp)
            .ttl("schedule", Duration.ofDays(1))
            .build();
        CourseEntry course = new CourseEntry("操作系统", "05", "杜智华", "致理楼L1-601",
            Weekday.WEDNESDAY, new Period(1, 2, java.time.LocalTime.of(8, 0),
            java.time.LocalTime.of(9, 50)), WeekRange.parse("1-17周"), false);
        CacheEnvelope<List<CourseEntry>> envelope = CacheEnvelope.of(List.of(course), 1);
        String json = new ObjectMapper().registerModule(new JavaTimeModule())
            .writeValueAsString(envelope);
        store.write("schedule", "schedule-u1", json);

        List<BookingStep> steps = CachePipelineBuilder.scheduleLookupAndWrite(
            store, "schedule-u1",
            (ctx, courses) -> ctx.scheduleCourses(courses),
            BookingContext::scheduleCourses);
        BookingContext ctx = new BookingContext(null);

        StepOutcome outcome = steps.get(0).execute(null, ctx);

        assertThat(outcome).isInstanceOf(StepOutcome.ShortCircuit.class);
        assertThat(ctx.scheduleCourses()).hasSize(1);
        assertThat(ctx.scheduleCourses().get(0)).isInstanceOf(CourseEntry.class);
        assertThat(ctx.scheduleCourses().get(0).courseName()).isEqualTo("操作系统");
    }

    @Test
    @DisplayName("noSessionBoundary 返回空列表")
    void noSessionBoundaryReturnsEmpty() {
        assertThat(CachePipelineBuilder.noSessionBoundary()).isEmpty();
    }
}
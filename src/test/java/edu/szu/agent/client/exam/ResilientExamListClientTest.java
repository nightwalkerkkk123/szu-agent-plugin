package edu.szu.agent.client.exam;

import edu.szu.agent.domain.exam.ExamSchedule;
import edu.szu.agent.error.ExamListException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ResilientExamListClient} — covers the dynamic
 * "real with static fallback" routing pattern consistent with the
 * other P1 real-fetch phases.
 *
 * <p>// 编程技术: JUnit 5 / AssertJ / 函数式接口
 *
 * @since 0.4.0
 * @author 王子豪
 */
@DisplayName("ResilientExamListClient")
class ResilientExamListClientTest {

    /** A fallback that always returns one test event for assertions. */
    private static final Supplier<List<ExamSchedule>> FALLBACK = () -> List.of(
        new ExamSchedule(
            "1月1日",
            "星期三",
            "static fallback",
            "FALLBACK001",
            LocalDate.of(2099, 1, 1),
            null, null,
            "fallback room",
            "fallback prof")
    );

    /** A real-supplier that always returns one event; marks "REAL". */
    private static Supplier<List<ExamSchedule>> realWithEvents() {
        return () -> List.of(
            new ExamSchedule(
                "2月2日",
                "星期四",
                "real fetched",
                "REAL001",
                LocalDate.of(2026, 2, 2),
                null, null,
                "real room",
                "real prof")
        );
    }

    @Test
    @DisplayName("real returns non-empty list → uses real result")
    void realSuccessReturnsRealResult() {
        ResilientExamListClient client =
            new ResilientExamListClient(realWithEvents(), FALLBACK);

        List<ExamSchedule> result = client.list();

        assertThat(result)
            .hasSize(1)
            .first()
            .extracting(ExamSchedule::courseCode)
            .isEqualTo("REAL001");
    }

    @Test
    @DisplayName("real returns empty list → falls back to static")
    void realEmptyFallsBackToStatic() {
        Supplier<List<ExamSchedule>> emptyReal = List::of;
        ResilientExamListClient client =
            new ResilientExamListClient(emptyReal, FALLBACK);

        List<ExamSchedule> result = client.list();

        assertThat(result)
            .hasSize(1)
            .first()
            .extracting(ExamSchedule::courseCode)
            .isEqualTo("FALLBACK001");
    }

    @Test
    @DisplayName("real supplier throws → wrapper catches and falls back to static")
    void realThrowsFallsBackToStatic() {
        Supplier<List<ExamSchedule>> throwingReal = () -> {
            throw new ExamListException(ErrorCode.EXAM_FETCH_FAILED,
                "simulated network error");
        };
        ResilientExamListClient client =
            new ResilientExamListClient(throwingReal, FALLBACK);

        List<ExamSchedule> result = client.list();

        assertThat(result)
            .hasSize(1)
            .first()
            .extracting(ExamSchedule::courseCode)
            .isEqualTo("FALLBACK001");
    }

    @Test
    @DisplayName("real supplier returns null → falls back to static")
    void realNullFallsBackToStatic() {
        Supplier<List<ExamSchedule>> nullReal = () -> null;
        ResilientExamListClient client =
            new ResilientExamListClient(nullReal, FALLBACK);

        List<ExamSchedule> result = client.list();

        assertThat(result)
            .hasSize(1)
            .first()
            .extracting(ExamSchedule::courseCode)
            .isEqualTo("FALLBACK001");
    }

    @Test
    @DisplayName("fallback supplier is invoked at most once per list() call")
    void fallbackIsLazyAndCalledOnce() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        Supplier<List<ExamSchedule>> countingFallback = () -> {
            fallbackCalls.incrementAndGet();
            return List.of(new ExamSchedule(
                "1月1日",
                "星期三",
                "counted",
                "COUNTED",
                LocalDate.of(2099, 1, 1),
                null, null,
                "counted room",
                "counted prof"));
        };
        ResilientExamListClient client =
            new ResilientExamListClient(realWithEvents(), countingFallback);

        // Real path succeeds → fallback should never be called
        client.list();

        assertThat(fallbackCalls.get()).isZero();
    }

    @Test
    @DisplayName("constructor requires non-null real supplier (fail-fast)")
    void ctorRejectsNullReal() {
        assertThatThrownBy(() ->
            new ResilientExamListClient(null, FALLBACK))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("realSupplier");
    }

    @Test
    @DisplayName("constructor requires non-null fallback supplier (fail-fast)")
    void ctorRejectsNullFallback() {
        assertThatThrownBy(() ->
            new ResilientExamListClient(realWithEvents(), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fallbackSupplier");
    }
}

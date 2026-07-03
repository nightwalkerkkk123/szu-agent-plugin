package edu.szu.agent.client.calendar;

import edu.szu.agent.domain.calendar.AcademicEvent;
import edu.szu.agent.domain.calendar.AcademicEventType;
import edu.szu.agent.domain.calendar.CalendarListResult;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResilientCalendarClient} — covers the dynamic
 * "real with static fallback" routing defined by
 * PLAN-p1-real-fetch.md §5 阶段 3.
 *
 * <p>// 编程技术: JUnit 5 / AssertJ / sealed type pattern matching / 函数式接口
 *
 * @since 0.6.0
 * @author 王子豪
 */
@DisplayName("ResilientCalendarClient")
class ResilientCalendarClientTest {

    /** A fallback that always returns one event so tests can assert fallback used. */
    private static final Supplier<List<AcademicEvent>> FALLBACK = () -> List.of(
        AcademicEvent.of(
            LocalDate.of(2099, 1, 1),
            AcademicEventType.HOLIDAY,
            "static fallback event",
            "FALLBACK"));

    /** A real-supplier that always returns one event — marks "OFFICIAL". */
    private static Supplier<List<AcademicEvent>> realWithEvents() {
        return () -> List.of(
            AcademicEvent.of(
                LocalDate.of(2026, 3, 4),
                AcademicEventType.SEMESTER_START,
                "real fetched event",
                "OFFICIAL"));
    }

    @Test
    @DisplayName("real returns non-empty list → uses real result")
    void realSuccessReturnsRealResult() {
        ResilientCalendarClient client =
            new ResilientCalendarClient(realWithEvents(), FALLBACK);

        CalendarListResult result = client.list();

        assertThat(result).isInstanceOf(CalendarListResult.Success.class);
        assertThat(((CalendarListResult.Success) result).events())
            .hasSize(1)
            .first()
            .extracting(AcademicEvent::semester)
            .isEqualTo("OFFICIAL");
    }

    @Test
    @DisplayName("real returns empty list → falls back to static")
    void realEmptyFallsBackToStatic() {
        Supplier<List<AcademicEvent>> emptyReal = List::of;
        ResilientCalendarClient client =
            new ResilientCalendarClient(emptyReal, FALLBACK);

        CalendarListResult result = client.list();

        assertThat(result).isInstanceOf(CalendarListResult.Success.class);
        assertThat(((CalendarListResult.Success) result).events())
            .hasSize(1)
            .first()
            .extracting(AcademicEvent::semester)
            .isEqualTo("FALLBACK");
    }

    @Test
    @DisplayName("real supplier throws → wrapper catches and falls back to static")
    void realThrowsFallsBackToStatic() {
        Supplier<List<AcademicEvent>> throwingReal = () -> {
            throw new CalendarFetchException(ErrorCode.CALENDAR_FETCH_FAILED,
                "simulated network error");
        };
        ResilientCalendarClient client =
            new ResilientCalendarClient(throwingReal, FALLBACK);

        CalendarListResult result = client.list();

        assertThat(result).isInstanceOf(CalendarListResult.Success.class);
        assertThat(((CalendarListResult.Success) result).events())
            .hasSize(1)
            .first()
            .extracting(AcademicEvent::semester)
            .isEqualTo("FALLBACK");
    }

    @Test
    @DisplayName("real supplier returns null → falls back to static")
    void realNullFallsBackToStatic() {
        Supplier<List<AcademicEvent>> nullReal = () -> null;
        ResilientCalendarClient client =
            new ResilientCalendarClient(nullReal, FALLBACK);

        CalendarListResult result = client.list();

        assertThat(result).isInstanceOf(CalendarListResult.Success.class);
        assertThat(((CalendarListResult.Success) result).events())
            .hasSize(1)
            .first()
            .extracting(AcademicEvent::semester)
            .isEqualTo("FALLBACK");
    }

    @Test
    @DisplayName("fallback supplier is invoked at most once per list() call")
    void fallbackIsLazyAndCalledOnce() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        Supplier<List<AcademicEvent>> countingFallback = () -> {
            fallbackCalls.incrementAndGet();
            return List.of(AcademicEvent.of(
                LocalDate.of(2099, 1, 1),
                AcademicEventType.HOLIDAY,
                "counted",
                "FALLBACK"));
        };
        ResilientCalendarClient client =
            new ResilientCalendarClient(realWithEvents(), countingFallback);

        // Real path succeeds → fallback should never be called.
        client.list();

        assertThat(fallbackCalls.get()).isZero();
    }

    @Test
    @DisplayName("constructor requires non-null real supplier (fail-fast)")
    void ctorRejectsNullReal() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new ResilientCalendarClient(null, FALLBACK))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("realSupplier");
    }

    @Test
    @DisplayName("constructor requires non-null fallback supplier (fail-fast)")
    void ctorRejectsNullFallback() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new ResilientCalendarClient(realWithEvents(), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fallbackSupplier");
    }
}

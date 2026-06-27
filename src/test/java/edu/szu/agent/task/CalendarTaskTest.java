package edu.szu.agent.task;

import edu.szu.agent.domain.calendar.AcademicEvent;
import edu.szu.agent.domain.calendar.AcademicEventType;
import edu.szu.agent.domain.calendar.CalendarListResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CalendarTask")
class CalendarTaskTest {

    /** Helper — unwraps the sealed Success variant for assertions. */
    private static List<AcademicEvent> unwrap(CalendarListResult result) {
        assertThat(result).isInstanceOf(CalendarListResult.Success.class);
        return ((CalendarListResult.Success) result).events();
    }

    @Test
    @DisplayName("name = calendar_get")
    void nameAndDescriptionAreCorrect() {
        CalendarTask task = new CalendarTask();
        assertThat(task.name()).isEqualTo("calendar_get");
        assertThat(task.description())
            .startsWith("查询深圳大学校历")
            .contains("静态 MVP", "2025-2026", "academicYear", "SEMESTER_START");
    }

    @Test
    @DisplayName("返回 2025-2026 春季学期静态校历")
    void returnsStaticSpring2026Calendar() {
        CalendarTask task = new CalendarTask();

        List<AcademicEvent> events = unwrap(task.execute(
            new TaskInput(Map.of("academicYear", "2025-2026"))));

        assertThat(events).isNotEmpty();
        assertThat(events).allMatch(e -> "2025-2026-SPRING".equals(e.semester()));
        assertThat(events).anyMatch(e ->
            e.date().equals(LocalDate.of(2026, 3, 5)) &&
            e.type() == AcademicEventType.SEMESTER_START &&
            e.description().contains("学生报到"));
        assertThat(events).anyMatch(e ->
            e.date().equals(LocalDate.of(2026, 6, 26)) &&
            e.type() == AcademicEventType.HOLIDAY &&
            e.description().contains("毕业典礼"));
        assertThat(events).anyMatch(e ->
            e.date().equals(LocalDate.of(2026, 7, 18)) &&
            e.type() == AcademicEventType.BREAK &&
            e.description().contains("暑假开始"));
    }

    @Test
    @DisplayName("不支持的学年返回空列表")
    void unsupportedYearReturnsEmptyList() {
        CalendarTask task = new CalendarTask();

        List<AcademicEvent> events = unwrap(task.execute(
            new TaskInput(Map.of("academicYear", "2099-2100"))));

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("默认学年推断规则")
    void defaultAcademicYearInference() {
        // 当前日期在 2026-06-22，属于第二学期(春季)，默认应为 2025-2026
        String year = CalendarTask.defaultAcademicYear();
        assertThat(year).isEqualTo("2025-2026");
    }

    @Test
    @DisplayName("real supplier 抛异常时,自动回退到静态(走 ResilientCalendarClient)")
    void realSupplierThrowsFallsBackToStatic() {
        CalendarTask task = new CalendarTask(
            () -> { throw new RuntimeException("simulated network"); },
            CalendarTask::spring2026Events,
            false);  // not staticOnly — exercise the resilient path

        List<AcademicEvent> events = unwrap(task.execute(
            new TaskInput(Map.of("academicYear", "2025-2026"))));

        assertThat(events).isNotEmpty();
        assertThat(events).allMatch(e -> "2025-2026-SPRING".equals(e.semester()));
    }

    @Test
    @DisplayName("real supplier 返回空时,自动回退到静态")
    void realSupplierEmptyFallsBackToStatic() {
        CalendarTask task = new CalendarTask(
            java.util.List::of,
            CalendarTask::spring2026Events,
            false);

        List<AcademicEvent> events = unwrap(task.execute(
            new TaskInput(Map.of("academicYear", "2025-2026"))));

        assertThat(events).isNotEmpty();
    }

    @Test
    @DisplayName("real supplier 返回非空时,使用真实结果")
    void realSupplierNonEmptyUsesRealResult() {
        CalendarTask task = new CalendarTask(
            () -> java.util.List.of(AcademicEvent.of(
                java.time.LocalDate.of(2030, 1, 1),
                AcademicEventType.HOLIDAY,
                "real event",
                "OFFICIAL")),
            CalendarTask::spring2026Events,
            false);

        List<AcademicEvent> events = unwrap(task.execute(
            new TaskInput(Map.of("academicYear", "2025-2026"))));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).semester()).isEqualTo("OFFICIAL");
    }
}

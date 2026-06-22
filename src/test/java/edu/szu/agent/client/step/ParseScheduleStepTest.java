package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.schedule.ScheduleListExtractor;
import edu.szu.agent.domain.CourseEntry;
import edu.szu.agent.domain.Weekday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ParseScheduleStep")
class ParseScheduleStepTest {

    @Test
    @DisplayName("name = PARSE_SCHEDULE")
    void nameConstant() {
        assertThat(new ParseScheduleStep().name()).isEqualTo("PARSE_SCHEDULE");
    }

    @Test
    @DisplayName("execute 调 ScheduleListExtractor 写 ctx.scheduleCourses")
    void writesContext() {
        String json = """
            [{"courseName":"操作系统[05]","teacher":"杜智华",
              "roomText":"1-17周,星期3,1-2节,致理楼L1-601",
              "isAdjusted":false,"weekday":3,"beginUnit":1,"endUnit":2}]
            """;
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.evaluate(anyString())).thenReturn(json);
        BookingContext ctx = new BookingContext(null);

        StepOutcome outcome = new ParseScheduleStep().execute(browser, ctx);
        assertThat(outcome).isInstanceOf(StepOutcome.Continue.class);

        List<CourseEntry> courses = ctx.scheduleCourses();
        assertThat(courses).hasSize(1);
        assertThat(courses.get(0).courseName()).isEqualTo("操作系统");
        assertThat(courses.get(0).weekday()).isEqualTo(Weekday.WEDNESDAY);
    }

    @Test
    @DisplayName("execute 解析到空列表时 ctx.scheduleCourses 为空列表(非 null)")
    void writesEmptyList() {
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.evaluate(anyString())).thenReturn("[]");
        BookingContext ctx = new BookingContext(null);

        new ParseScheduleStep().execute(browser, ctx);
        assertThat(ctx.scheduleCourses()).isEmpty();
    }
}

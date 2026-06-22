package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.schedule.PeriodMapping;
import edu.szu.agent.client.step.BookingContext;
import edu.szu.agent.client.step.BookingStep;
import edu.szu.agent.client.step.StepOutcome;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.CourseEntry;
import edu.szu.agent.domain.ScheduleListResult;
import edu.szu.agent.domain.WeekRange;
import edu.szu.agent.domain.Weekday;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.retry.NoRetry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("EhallScheduleClient")
class EhallScheduleClientTest {

    private static final Account ACCOUNT = new Account("2023150090", "p", "x");

    @Test
    @DisplayName("list 成功路径返回 ScheduleListResult.Success")
    void happyPath() {
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        List<CourseEntry> courses = List.of(sampleCourse());
        EhallScheduleClient client = clientWith(browser, courses,
            new StubStep("noop1", null));

        ScheduleListResult r = client.list();
        assertThat(r).isInstanceOf(ScheduleListResult.Success.class);
        ScheduleListResult.Success s = (ScheduleListResult.Success) r;
        assertThat(s.courses()).hasSize(1);
        assertThat(s.snapshotAt()).isNotNull();
    }

    @Test
    @DisplayName("list 步骤失败 → Failure,带 ErrorCode")
    void stepFailure() {
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        EhallScheduleClient client = clientWith(browser, null,
            new StubStep("fail", new BookingResult.Failure(
                ErrorCode.SCHEDULE_PAGE_LOAD_FAILED, "no table")));

        ScheduleListResult r = client.list();
        assertThat(r).isInstanceOf(ScheduleListResult.Failure.class);
        ScheduleListResult.Failure f = (ScheduleListResult.Failure) r;
        assertThat(f.code()).isEqualTo(ErrorCode.SCHEDULE_PAGE_LOAD_FAILED);
        assertThat(f.message()).isEqualTo("no table");
    }

    @Test
    @DisplayName("list 课表为空 → SCHEDULE_EMPTY")
    void emptySchedule() {
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        EhallScheduleClient client = clientWith(browser, List.of(),
            new StubStep("noop", null));
        ScheduleListResult r = client.list();
        assertThat(r).isInstanceOf(ScheduleListResult.Failure.class);
        assertThat(((ScheduleListResult.Failure) r).code()).isEqualTo(ErrorCode.SCHEDULE_EMPTY);
    }

    @Test
    @DisplayName("list ctx.scheduleCourses=null → SCHEDULE_PARSE_FAILED")
    void nullScheduleCourses() {
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        EhallScheduleClient client = clientWith(browser, null,
            new StubStep("noop", null));
        ScheduleListResult r = client.list();
        assertThat(r).isInstanceOf(ScheduleListResult.Failure.class);
        assertThat(((ScheduleListResult.Failure) r).code()).isEqualTo(ErrorCode.SCHEDULE_PARSE_FAILED);
    }

    @Test
    @DisplayName("short-circuit step stops later schedule steps")
    void shortCircuitStopsLaterSteps() {
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        CourseEntry course = sampleCourse();
        BookingStep hit = new BookingStep() {
            @Override
            public String name() {
                return "cache-hit";
            }

            @Override
            public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
                ctx.scheduleCourses(List.of(course));
                return new StepOutcome.ShortCircuit(ctx);
            }
        };
        BookingStep shouldNotRun = new StubStep("should-not-run", new BookingResult.Failure(
            ErrorCode.SCHEDULE_PAGE_LOAD_FAILED, "should not run"));

        EhallScheduleClient client = clientWith(browser, null, hit, shouldNotRun);

        ScheduleListResult r = client.list();

        assertThat(r).isInstanceOf(ScheduleListResult.Success.class);
    }

    @Test
    @DisplayName("list browser.open 抛异常后仍关闭 browser")
    void browserClosedOnException() {
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(browser).open();
        EhallScheduleClient client = clientWith(browser, null, new StubStep("noop", null));
        ScheduleListResult r = client.list();
        assertThat(r).isInstanceOf(ScheduleListResult.Failure.class);
        verify(browser).close();
    }

    private static CourseEntry sampleCourse() {
        return new CourseEntry(
            "操作系统", "05", "杜智华", "致理楼L1-601",
            Weekday.WEDNESDAY, PeriodMapping.lookup(1, 2),
            WeekRange.parse("1-17周"), false);
    }

    private static EhallScheduleClient clientWith(BrowserLifecycle browser,
                                                    List<CourseEntry> expectedCourses,
                                                    BookingStep... steps) {
        List<BookingStep> list = new ArrayList<>(List.of(steps));
        if (expectedCourses != null) {
            list.add(new BookingStep() {
                @Override
                public String name() {
                    return "stub-write-courses";
                }

                @Override
                public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
                    ctx.scheduleCourses(expectedCourses);
                    return new StepOutcome.Continue(ctx);
                }
            });
        }
        return new EhallScheduleClient(ACCOUNT, browser, NoRetry.INSTANCE, list);
    }

    /** Minimal no-op / fail Step. */
    private static final class StubStep implements BookingStep {
        private final String name;
        private final BookingResult result;

        StubStep(String name, BookingResult result) {
            this.name = name;
            this.result = result;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
            if (result instanceof BookingResult.Failure f) {
                return new StepOutcome.Failure(f);
            }
            return new StepOutcome.Continue(ctx);
        }
    }
}

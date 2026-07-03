package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.schedule.ScheduleListExtractor;

/**
 * Step that parses the ehall schedule grid into {@code CourseEntry} records.
 *
 * <p>Writes the parsed list into {@link BookingContext#scheduleCourses(java.util.List)}
 * so the client can build the final
 * {@link edu.szu.agent.domain.ScheduleListResult}.
 *
 * // Design Pattern: Strategy
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class ParseScheduleStep implements BookingStep {

    @Override
    public String name() {
        return "PARSE_SCHEDULE";
    }

    @Override
    public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
        ctx.scheduleCourses(ScheduleListExtractor.extract(browser));
        return new StepOutcome.Continue(ctx);
    }
}

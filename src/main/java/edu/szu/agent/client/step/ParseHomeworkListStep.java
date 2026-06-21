package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.homework.HomeworkListExtractor;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.Homework;

import java.util.List;

/**
 * Step that parses the LMS todo list into {@link Homework} records.
 *
 * <p>Writes the parsed list into {@link BookingContext#homeworks(List)} so the
 * client can build the final {@link edu.szu.agent.domain.HomeworkListResult}.
 *
 * // Design Pattern: Strategy
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class ParseHomeworkListStep implements BookingStep {

    @Override
    public String name() {
        return "PARSE_HOMEWORK_LIST";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        ctx.homeworks(HomeworkListExtractor.extract(browser));
        return null;
    }
}

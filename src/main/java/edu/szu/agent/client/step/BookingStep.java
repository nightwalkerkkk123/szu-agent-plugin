package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * One step in the booking pipeline.
 *
 * <p>Each step is a self-contained {@link Strategy} that:
 * <ul>
 *   <li>Reads from {@link BookingContext#request()} to get its input</li>
 *   <li>Drives the {@link BrowserLifecycle} to act on ehall</li>
 *   <li>Writes intermediate results into {@link BookingContext}
 *       (e.g. {@link BookingContext#selectedVenue(String)})</li>
 *   <li>Returns {@code null} on success, or a {@link BookingResult.Failure}
 *       on terminal failure</li>
 * </ul>
 *
 * <p>Steps are composed by {@link edu.szu.agent.client.VenueBookingClient}
 * into a pipeline. Steps are tested in isolation via mock
 * {@link BrowserLifecycle}.
 *
 * // Design Pattern: Strategy
 * // 编程技术: 泛型 / 接口 / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
public interface BookingStep {

    /**
     * Step display name for logging.
     */
    String name();

    /**
     * Executes this step.
     *
     * @param browser the browser adapter
     * @param ctx     the booking context (holds request + intermediate results)
     * @return {@code null} on success, {@link BookingResult.Failure} on terminal
     *         failure, or throws to signal retryable error
     */
    BookingResult execute(BrowserLifecycle browser, BookingContext ctx);

    /**
     * Static factory for single-lambda steps.
     */
    static BookingStep of(String name,
                          BiFunction<BrowserLifecycle, BookingContext, BookingResult> body) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(body, "body");
        return new BookingStep() {
            @Override
            public String name() { return name; }
            @Override
            public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
                return body.apply(browser, ctx);
            }
        };
    }
}
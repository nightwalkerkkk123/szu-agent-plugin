package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;

/**
 * Strategy for selecting a concrete bookable venue on the ehall page.
 *
 * <p>Different sports render the venue section differently:
 * <ul>
 *   <li>Court sports (tennis, badminton, ...) expose a list of courts.</li>
 *   <li>Gym sports expose a single capacity item such as
 *       {@code 二楼健身房(42/50)}.</li>
 * </ul>
 * Each variant is encapsulated by a sealed implementation;
 * {@link edu.szu.agent.domain.Sport} binds the appropriate selector to each
 * enum constant.
 *
 * <p>// Design Pattern: Strategy
 * <p>// 编程技术: sealed interface / 异常传递业务错误
 *
 * @since 0.1.0
 * @author 王子豪
 */
public sealed interface VenueSelector permits CourtListSelector, CapacityVenueSelector {

    /**
     * Selects and clicks the venue described by the current request.
     *
     * @param browser the browser adapter
     * @param ctx     the booking context (request + intermediate results)
     * @return the human-readable venue name to record in the result
     * @throws BookingException if no bookable venue is available
     */
    String selectAndClick(BrowserLifecycle browser, BookingContext ctx) throws BookingException;
}

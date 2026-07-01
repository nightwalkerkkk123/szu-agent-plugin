package edu.szu.agent.client.calendar;

import edu.szu.agent.domain.calendar.AcademicEvent;

import java.util.List;

/**
 * Fetches the SZU academic calendar list page from a real source and returns
 * the raw HTML so the caller can parse it.
 *
 * <p>Implemented by {@link PlaywrightCalendarFetchProvider} (Playwright + page
 * navigation). The interface is intentionally narrow — just
 * {@code fetchHtml()} — so the resilient wrapper and the task can be tested
 * with a tiny stub provider.
 *
 * <p>// Design Pattern: Strategy
 * <p>// 编程技术: 接口 / Lambda / FunctionalInterface
 *
 * @since 0.4.0
 * @author 王子豪
 */
// Design Pattern: Strategy
@FunctionalInterface
public interface CalendarFetchProvider {

    /**
     * Fetches the calendar list page HTML.
     *
     * @return raw HTML body of {@code https://www.szu.edu.cn/xxgk/xl.htm}
     * @throws CalendarFetchException if navigation / loading fails. The message
     *     is surfaced as a {@code Failure} when invoked through the resilient
     *     wrapper.
     */
    String fetchHtml() throws CalendarFetchException;

    /**
     * Convenience overload — fetches and parses in one call. Default
     * implementation delegates to {@link #fetchHtml()} and
     * {@link CalendarPageParser#parse(String)}; provided so callers that
     * only need the parsed list can ignore HTML plumbing.
     *
     * @return parsed events, possibly empty if the page has no parseable text
     * @throws CalendarFetchException if fetch fails
     */
    default List<AcademicEvent> fetchAndParse() throws CalendarFetchException {
        return CalendarPageParser.parse(fetchHtml());
    }
}
package edu.szu.agent.client.notice;

import edu.szu.agent.domain.notice.Notice;

import java.util.List;

/**
 * Fetches the SZU board (公文通) list page from a real source and returns the
 * raw HTML so the caller can parse it via {@link NoticeListParser}.
 *
 * <p>Implemented by {@link PlaywrightNoticeFetchProvider} (Playwright + page
 * navigation). The interface is intentionally narrow — just {@code fetchHtml()}
 * — so the resilient wrapper and the task can be tested with a tiny
 * stub provider.
 *
 * <p>// Design Pattern: Strategy
 * <p>// 编程技术: 接口 / Lambda / FunctionalInterface
 *
 * @since 0.6.0
 * @author 王子豪
 */
// Design Pattern: Strategy
@FunctionalInterface
public interface NoticeFetchProvider {

    /**
     * Fetches the board list page HTML.
     *
     * @return raw HTML body of {@code https://www1.szu.edu.cn/board/}
     * @throws NoticeFetchException if navigation / loading / parsing-source
     *     fails. The message is surfaced as a {@code Failure} on
     *     {@code NoticeListResult} when invoked through the resilient wrapper.
     */
    String fetchHtml() throws NoticeFetchException;

    /**
     * Convenience overload — fetches and parses in one call. Default
     * implementation delegates to {@link #fetchHtml()} and
     * {@link NoticeListParser#parse(String, int)}; provided so callers that
     * only need the parsed list (e.g. legacy static-only path) can ignore
     * HTML plumbing.
     *
     * @param defaultYear year used when the page only supplies month/day
     * @return parsed notices, sorted by publishedAt descending
     * @throws NoticeFetchException if fetch fails
     */
    default List<Notice> fetchAndParse(int defaultYear) throws NoticeFetchException {
        return NoticeListParser.parse(fetchHtml(), defaultYear);
    }
}

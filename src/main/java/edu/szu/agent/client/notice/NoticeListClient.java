package edu.szu.agent.client.notice;

import edu.szu.agent.domain.notice.Notice;
import edu.szu.agent.domain.notice.NoticeListResult;
import edu.szu.agent.error.ErrorCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * SZU board (公文通) list client.
 *
 * <p>Two operating modes:
 * <ul>
 *   <li><strong>Static mode</strong> (default): parses an embedded HTML
 *       snapshot bundled at {@code /notice-snapshot.html}. The Skill is
 *       always available even with no network.
 *   <li><strong>Real-fetch mode</strong>: delegates to a
 *       {@link NoticeFetchProvider} (Playwright) and parses the live
 *       board page. Used by {@link ResilientNoticeClient} which falls
 *       back to static on any failure.
 * </ul>
 *
 * <p>Per PLAN-p1-real-fetch.md §5 阶段 2: 镜像 {@code ScheduleListClient} 的
 * 密封返回类型,让 {@code ResilientNoticeClient} 能做模式匹配。
 *
 * <p>// 编程技术: 不可变组合 / Lambda / 密封类型
 *
 * @since 0.6.0
 * @author 王子豪
 */
public class NoticeListClient {

    private static final String SNAPSHOT_RESOURCE = "/notice-snapshot.html";

    private final NoticeFetchProvider fetchProvider;
    private final String snapshotHtml;
    private final int defaultYear;

    /**
     * Default constructor — static mode, no Playwright. Used by the
     * resilient wrapper as the fallback path and by legacy callers.
     */
    public NoticeListClient() {
        this(null, loadSnapshot(), LocalDate.now().getYear());
    }

    /**
     * Real-fetch constructor — delegates to the supplied provider.
     * The static snapshot is still loaded so it can be returned on
     * provider failure (callers that want strict real-or-fail semantics
     * should check the sealed {@code Failure} variant).
     *
     * @param fetchProvider Playwright-backed provider; may be null
     *     (treated as static mode)
     */
    public NoticeListClient(NoticeFetchProvider fetchProvider) {
        this(fetchProvider, loadSnapshot(), LocalDate.now().getYear());
    }

    /**
     * Test constructor — inject custom HTML and year. Static mode
     * (fetchProvider=null).
     */
    public NoticeListClient(String snapshotHtml, int defaultYear) {
        this(null, snapshotHtml, defaultYear);
    }

    /**
     * Full constructor — used by tests that need to override both
     * the provider and the fallback HTML.
     */
    public NoticeListClient(NoticeFetchProvider fetchProvider, String snapshotHtml, int defaultYear) {
        this.fetchProvider = fetchProvider;
        this.snapshotHtml = Objects.requireNonNull(snapshotHtml, "snapshotHtml");
        this.defaultYear = defaultYear;
    }

    /**
     * Returns the underlying fetch provider, or null in static-only mode.
     */
    public NoticeFetchProvider fetchProvider() {
        return fetchProvider;
    }

    /**
     * Fetches and parses the notice list. Tries the provider first; on
     * {@link NoticeFetchException} (or any other RuntimeException) falls
     * back to the embedded snapshot. Never returns null; never throws
     * unchecked exceptions at the call site (provider exceptions are
     * caught and converted to {@link NoticeListResult.Failure} via the
     * static fallback path).
     */
    public NoticeListResult list() {
        if (fetchProvider != null) {
            try {
                List<Notice> parsed = fetchProvider.fetchAndParse(defaultYear);
                return new NoticeListResult.Success(parsed, Instant.now());
            } catch (RuntimeException e) {
                return parseSnapshot();
            }
        }
        return parseSnapshot();
    }

    private NoticeListResult parseSnapshot() {
        try {
            List<Notice> parsed = NoticeListParser.parse(snapshotHtml, defaultYear);
            return new NoticeListResult.Success(parsed, Instant.now());
        } catch (RuntimeException e) {
            return new NoticeListResult.Failure(
                ErrorCode.NOTICE_FETCH_FAILED,
                "Failed to parse notice snapshot: " + e.getMessage()
            );
        }
    }

    private static String loadSnapshot() {
        try (var in = NoticeListClient.class.getResourceAsStream(SNAPSHOT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Snapshot not found: " + SNAPSHOT_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load board snapshot", e);
        }
    }
}

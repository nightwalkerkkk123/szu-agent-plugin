package edu.szu.agent.client.notice;

import edu.szu.agent.domain.notice.Notice;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * SZU board (公文通) list client — static MVP.
 *
 * <p>The real board page requires CAS login when accessed directly.
 * For the MVP we ship a snapshot of the public list-page HTML so the
 * Skill is always available.  A future version can replace the snapshot
 * with an HTTP fetch through {@code PlaywrightBrowserAdapter} after CAS
 * login.
 *
 * @since 0.3.0
 * @author 王子豪
 */
public class NoticeListClient {

    private static final String SNAPSHOT_RESOURCE = "/notice-snapshot.html";

    private final String snapshotHtml;
    private final int defaultYear;

    /**
     * Default constructor using the embedded board snapshot.
     */
    public NoticeListClient() {
        this(loadSnapshot(), LocalDate.now().getYear());
    }

    /**
     * Test constructor — inject custom HTML and year.
     */
    public NoticeListClient(String snapshotHtml, int defaultYear) {
        this.snapshotHtml = snapshotHtml;
        this.defaultYear = defaultYear;
    }

    /**
     * Returns all notices parsed from the current snapshot, sorted by
     * publication date descending.
     */
    public List<Notice> list() {
        return NoticeListParser.parse(snapshotHtml, defaultYear);
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

package edu.szu.agent.client.exam;

import edu.szu.agent.domain.exam.ExamSchedule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Exam schedule list client — static MVP.
 *
 * <p>The real exam schedule page requires CAS login when accessed directly.
 * For the MVP we ship a snapshot of the public exam-page HTML so the
 * Skill is always available. A future version can replace the snapshot
 * with an HTTP fetch through {@code PlaywrightBrowserAdapter} after CAS
 * login.
 *
 * @since 0.6.0
 * @author 王子豪
 */
public class ExamListClient {

    private static final String SNAPSHOT_RESOURCE = "/exam-snapshot.html";

    private final String snapshotHtml;
    private final int defaultYear;

    /**
     * Default constructor using the embedded exam snapshot.
     */
    public ExamListClient() {
        this(loadSnapshot(), LocalDate.now().getYear());
    }

    /**
     * Test constructor — inject custom HTML and year.
     */
    public ExamListClient(String snapshotHtml, int defaultYear) {
        this.snapshotHtml = snapshotHtml;
        this.defaultYear = defaultYear;
    }

    /**
     * Returns all exam schedules parsed from the current snapshot.
     */
    public List<ExamSchedule> list() {
        return ExamListParser.parse(snapshotHtml, defaultYear);
    }

    private static String loadSnapshot() {
        try (var in = ExamListClient.class.getResourceAsStream(SNAPSHOT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Snapshot not found: " + SNAPSHOT_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load exam snapshot", e);
        }
    }
}
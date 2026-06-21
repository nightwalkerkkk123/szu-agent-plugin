package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.homework.attachment.FilenameSanitizer;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.HomeworkAttachment;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Step that downloads every attachment in
 * {@link BookingContext#attachments()} to the directory in
 * {@link BookingContext#outputDir()}.
 *
 * <p>Each download is throttled by a fixed delay (default 500ms, configurable
 * via the downstream client) and uses collision-safe filename renaming via
 * {@link FilenameSanitizer#uniqueName}. On success, replaces
 * {@code ctx.attachments()} with a list whose records carry
 * {@code localPath}, {@code sizeBytes} and {@code downloadedAt}.
 *
 * <p>An empty input list is a valid no-op; the step returns
 * {@code null} without raising an error.
 *
 * // Design Pattern: Strategy
 * // 编程技术: Lambda / 不可变记录替换 / NIO atomic move
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class DownloadFilesStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(DownloadFilesStep.class);

    /** Fixed throttle between downloads; same as {@code HomeworkDownloadRequest.DEFAULT_THROTTLE}. */
    private final long throttleMillis;

    public DownloadFilesStep() {
        this(500L);
    }

    public DownloadFilesStep(long throttleMillis) {
        if (throttleMillis < 0) {
            throw new IllegalArgumentException("throttleMillis must be >= 0");
        }
        this.throttleMillis = throttleMillis;
    }

    @Override
    public String name() {
        return "DOWNLOAD_FILES";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        Path outputDir = ctx.outputDir();
        if (outputDir == null) {
            throw new BookingException(ErrorCode.OUTPUT_DIR_INVALID,
                "outputDir is missing on BookingContext");
        }
        if (!Files.isDirectory(outputDir)) {
            throw new BookingException(ErrorCode.OUTPUT_DIR_INVALID,
                "outputDir is not a directory: " + outputDir);
        }
        List<HomeworkAttachment> inputs = ctx.attachments();
        if (inputs == null || inputs.isEmpty()) {
            return null;
        }

        Set<String> existing = listExistingFilenames(outputDir);
        List<HomeworkAttachment> downloaded = new ArrayList<>(inputs.size());

        for (int i = 0; i < inputs.size(); i++) {
            HomeworkAttachment a = inputs.get(i);
            String unique = FilenameSanitizer.uniqueName(outputDir, a.fileName(), existing);
            Path target = outputDir.resolve(unique);
            long size = browser.downloadAttachment(a.sourceUrl(), target);
            existing.add(unique);
            downloaded.add(new HomeworkAttachment(
                a.homeworkId(),
                unique,
                a.sourceUrl(),
                target,
                size,
                Instant.now()));
            log.info("Downloaded attachment {} ({} bytes) -> {}", unique, size, target);

            if (i < inputs.size() - 1 && throttleMillis > 0) {
                sleepQuietly(throttleMillis);
            }
        }
        ctx.attachments(downloaded);
        return null;
    }

    private static Set<String> listExistingFilenames(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.map(p -> p.getFileName().toString())
                .collect(Collectors.toCollection(HashSet::new));
        } catch (IOException e) {
            throw new BookingException(ErrorCode.OUTPUT_DIR_INVALID,
                "cannot list outputDir " + dir + ": " + e.getMessage(), e);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BookingException(ErrorCode.BROWSER_CRASH,
                "interrupted during download throttle", ie);
        }
    }
}

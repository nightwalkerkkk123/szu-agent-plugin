package edu.szu.agent.client.homework;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.HomeworkAttachment;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Extracts the LMS homework attachment list from the browser DOM via a
 * single JS evaluation.
 *
 * <p>The extraction script runs in the page context, walks every
 * {@code .attachment-row.preview-able} element, joins
 * {@code .file-name} + {@code .file-extension} to compose the local
 * filename, and pulls the download URL from the
 * {@code a[ng-href*="/api/uploads/reference/"]} link (falling back to
 * the plain {@code href} attribute if AngularJS has not yet populated
 * {@code ng-href}).
 *
 * <p>Filenames are sanitized via
 * {@link edu.szu.agent.client.homework.attachment.FilenameSanitizer}
 * before being returned, so the caller can safely pass them to
 * {@code Path.resolve}.
 *
 * // Design Pattern: Strategy (selectable extraction implementation)
 * // 编程技术: Lambda / Jackson 反序列化 / 字符串拼接
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class AttachmentListExtractor {

    private static final Logger log = LoggerFactory.getLogger(AttachmentListExtractor.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    static final String SEL_ROW = ".attachment-row.preview-able";
    static final String SEL_FILE_NAME = ".file-name";
    static final String SEL_FILE_EXT = ".file-extension";
    static final String SEL_SIZE = ".attachment-size";
    static final String SEL_DOWNLOAD_LINK =
        "a[ng-href*=\"/api/uploads/reference/\"]";

    private AttachmentListExtractor() {
        // utility class
    }

    /**
     * Extracts attachment items from the current LMS homework detail page.
     *
     * @param browser    the browser adapter, currently on the LMS detail page
     * @param homeworkId homework ID these attachments belong to; stamped
     *                   into each returned record
     * @return a non-null list (may be empty) of attachments without
     *         {@code localPath} / {@code sizeBytes} / {@code downloadedAt}
     * @throws BookingException if extraction fails or returns invalid data
     */
    public static List<HomeworkAttachment> extract(BrowserLifecycle browser, String homeworkId) {
        Objects.requireNonNull(browser, "browser");
        Objects.requireNonNull(homeworkId, "homeworkId");

        String rawJson = browser.evaluate(buildExtractionScript());
        if (rawJson == null || rawJson.isBlank()) {
            throw new BookingException(ErrorCode.HOMEWORK_PAGE_LOAD_FAILED,
                "attachment list extraction returned empty result");
        }

        try {
            List<RawAttachment> raws = JSON.readValue(rawJson, new TypeReference<>() {
            });
            List<HomeworkAttachment> out = new ArrayList<>(raws.size());
            for (RawAttachment r : raws) {
                String sanitized = edu.szu.agent.client.homework.attachment
                    .FilenameSanitizer.sanitize(r.fileName);
                out.add(new HomeworkAttachment(
                    homeworkId,
                    sanitized,
                    r.sourceUrl,
                    null,
                    0L,
                    null));
            }
            log.info("Extracted {} attachment(s) for homework {}", out.size(), homeworkId);
            return List.copyOf(out);
        } catch (IOException e) {
            throw new BookingException(ErrorCode.ELEMENT_NOT_FOUND,
                "failed to parse attachment list JSON: " + e.getMessage());
        }
    }

    /**
     * Builds the JavaScript that extracts structured data from the DOM.
     */
    public static String buildExtractionScript() {
        return """
            (function() {
              var rows = Array.from(document.querySelectorAll('%s'));
              var result = [];
              rows.forEach(function(row) {
                var nameEl = row.querySelector('%s');
                var extEl = row.querySelector('%s');
                var sizeEl = row.querySelector('%s');
                var linkEl = row.querySelector('%s');
                if (!nameEl || !linkEl) return;
                var name = nameEl.textContent.trim() || '';
                var ext = extEl ? extEl.textContent.trim() : '';
                var fileName = name + ext;
                var ngHref = linkEl.getAttribute('ng-href');
                var href = linkEl.getAttribute('href');
                var sourceUrl = ngHref || href || '';
                if (!sourceUrl) return;
                var sizeText = sizeEl ? sizeEl.textContent.trim() : '';
                result.push({
                  fileName: fileName,
                  sourceUrl: sourceUrl,
                  fileSizeText: sizeText
                });
              });
              return JSON.stringify(result);
            })()
            """.formatted(
            SEL_ROW,
            SEL_FILE_NAME,
            SEL_FILE_EXT,
            SEL_SIZE,
            SEL_DOWNLOAD_LINK
        ).replaceAll("\\R\\s*", " ");
    }

    /** Wire format used by the JS extraction script. */
    private static final class RawAttachment {
        @SuppressWarnings("unused")
        public String fileName;
        @SuppressWarnings("unused")
        public String sourceUrl;
        @SuppressWarnings("unused")
        public String fileSizeText;
    }
}

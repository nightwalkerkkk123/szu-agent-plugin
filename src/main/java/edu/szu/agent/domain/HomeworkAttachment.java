package edu.szu.agent.domain;

import java.nio.file.Path;
import java.time.Instant;

/**
 * A single attachment from a Chaoxing homework detail page.
 *
 * <p>Immutable value object. Populated by
 * {@link edu.szu.agent.client.step.ParseAttachmentsStep} (with
 * {@code localPath} / {@code sizeBytes} / {@code downloadedAt} still null
 * / 0) and enriched by
 * {@link edu.szu.agent.client.step.DownloadFilesStep} after the file
 * lands on disk. Surfaced through {@code homework download} and
 * {@code skill homework_download}.
 *
 * // 编程技术: record(不可变值对象)
 *
 * @param homeworkId   the homework this attachment belongs to
 * @param fileName     sanitized local file name (no path separators, no
 *                     platform-illegal characters)
 * @param sourceUrl    original download URL on LMS (CAS-protected)
 * @param localPath    absolute path on disk after successful download,
 *                     or {@code null} if not yet downloaded
 * @param sizeBytes    file size in bytes, or {@code 0} if unknown
 * @param downloadedAt timestamp at which the download finished, or
 *                     {@code null} if not yet downloaded
 * @since 0.6.0
 * @author 王子豪
 */
public record HomeworkAttachment(String homeworkId, String fileName, String sourceUrl,
                                 Path localPath, long sizeBytes, Instant downloadedAt) {
}

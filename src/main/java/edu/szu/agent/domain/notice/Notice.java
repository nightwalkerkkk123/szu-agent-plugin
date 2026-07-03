package edu.szu.agent.domain.notice;

import java.time.LocalDate;

/**
 * A single SZU board (公文通) notice.
 *
 * <p>Per PRD §3.2.2, each notice carries an id, title, category,
 * publication date, detail URL, and whether it has attachments.
 * The MVP parser derives {@code hasAttachment} from the title text
 * (keywords like 附件 / 下载 / 申请表) because the list page does not
 * expose an explicit attachment icon in the provided HTML snippet.
 *
 * // 编程技术: record
 *
 * @param id            board item id (numeric)
 * @param title         notice title
 * @param category      mapped category
 * @param publishedAt   publication date (year inferred from current year)
 * @param url           absolute detail URL
 * @param hasAttachment whether the notice likely has attachments
 * @since 0.6.0
 * @author 王子豪
 */
public record Notice(String id,
                     String title,
                     NoticeCategory category,
                     LocalDate publishedAt,
                     String url,
                     boolean hasAttachment) {
}

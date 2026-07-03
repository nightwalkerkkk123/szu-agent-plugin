package edu.szu.agent.client.notice;

import edu.szu.agent.domain.notice.Notice;
import edu.szu.agent.domain.notice.NoticeCategory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the SZU board (公文通) list page HTML into {@link Notice} records.
 *
 * <p>This is a static MVP parser: it works against the list-page HTML
 * structure shown in the screenshot and does not require a logged-in
 * browser.  It uses JDK regex only, so no extra HTML parser dependency
 * is added.
 *
 * // 编程技术: 正则表达式 / Lambda / 枚举
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class NoticeListParser {

    private static final String BASE_URL = "https://www1.szu.edu.cn/board/";
    private static final String SITE_BASE = "https://www1.szu.edu.cn";

    private static final Pattern FIELDSET_PATTERN = Pattern.compile(
        "<fieldset[^>]*>.*?<legend[^>]*>.*?<a[^>]*href=\"\\./infolist\\.asp\\?infotype=([^\"]+)\"[^>]*>.*?<strong>.*?>([^<]+)</font></strong>.*?</legend>.*?<table[^>]*>(.*?)</table>.*?</fieldset>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern ROW_PATTERN = Pattern.compile(
        "<tr[^>]*>.*?<a(?=[^>]*title=\"([^\"]+)\")(?=[^>]*href=\"(/board/)?(view\\.asp\\?id=(\\d+))\")[^>]*>(.*?)</a>.*?<td[^>]*>(.*?)</td>.*?</tr>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private NoticeListParser() {
    }

    /**
     * Parses the given board list HTML.
     *
     * @param html the raw HTML body
     * @param defaultYear year used when the page only supplies month/day
     * @return list of notices, sorted by published date descending
     */
    public static List<Notice> parse(String html, int defaultYear) {
        List<Notice> notices = new ArrayList<>();
        Matcher fieldsetMatcher = FIELDSET_PATTERN.matcher(html);
        while (fieldsetMatcher.find()) {
            String rawType = fieldsetMatcher.group(1).trim();
            String sectionName = fieldsetMatcher.group(2).trim();
            String tableBody = fieldsetMatcher.group(3);
            NoticeCategory category = mapCategory(sectionName, rawType);

            Matcher rowMatcher = ROW_PATTERN.matcher(tableBody);
            while (rowMatcher.find()) {
                String title = rowMatcher.group(1).trim();
                String relativeUrl = rowMatcher.group(3);
                String id = rowMatcher.group(4);
                String displayTitle = rowMatcher.group(5).trim();
                String dateText = rowMatcher.group(6).trim();

                String effectiveTitle = chooseTitle(title, displayTitle);
                LocalDate publishedAt = parseDate(dateText, defaultYear);
                String url = toAbsoluteUrl(relativeUrl);
                boolean hasAttachment = guessAttachment(effectiveTitle);

                notices.add(new Notice(id,
                    effectiveTitle,
                    category,
                    publishedAt,
                    url,
                    hasAttachment));
            }
        }
        notices.sort((a, b) -> b.publishedAt().compareTo(a.publishedAt()));
        return List.copyOf(notices);
    }

    private static NoticeCategory mapCategory(String sectionName, String infoType) {
        String key = (sectionName + " " + infoType).toLowerCase();
        if (key.contains("讲座")) {
            return NoticeCategory.LECTURE;
        }
        if (key.contains("竞赛")) {
            return NoticeCategory.COMPETITION;
        }
        if (key.contains("生活") || key.contains("学工") || key.contains("后勤")) {
            return NoticeCategory.PUBLICITY;
        }
        return NoticeCategory.ANNOUNCEMENT;
    }

    private static LocalDate parseDate(String text, int defaultYear) {
        String normalized = text.replaceAll("&nbsp;", " ").trim();
        int sep = normalized.indexOf(' ');
        if (sep > 0) {
            normalized = normalized.substring(0, sep);
        }
        String[] parts = normalized.split("/");
        int month = Integer.parseInt(parts[0]);
        int day = Integer.parseInt(parts[1]);
        return LocalDate.of(defaultYear, month, day);
    }

    private static String chooseTitle(String titleAttr, String displayText) {
        if (titleAttr == null || titleAttr.isBlank()) {
            return displayText;
        }
        // 学术讲座的 title 属性是“时间/地点/专题”，显示文本才是讲座标题
        if (titleAttr.startsWith("时间：")) {
            return displayText;
        }
        return titleAttr;
    }

    private static String toAbsoluteUrl(String relative) {
        if (relative.startsWith("http")) {
            return relative;
        }
        if (relative.startsWith("/")) {
            return SITE_BASE + relative;
        }
        return BASE_URL + relative;
    }

    private static boolean guessAttachment(String title) {
        String t = title.toLowerCase();
        return t.contains("附件") || t.contains("下载") || t.contains("申请表")
            || t.contains("报名表") || t.contains("申报书") || t.contains("材料");
    }
}

package edu.szu.agent.client.homework;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.Homework;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Extracts the LMS homework list from the browser DOM via a single JS evaluation.
 *
 * <p>The extraction script runs in the page context, filters items whose icon
 * is {@code #todo-homework}, and pulls title/status/course/deadline from the
 * user-supplied HTML structure.
 *
 * // Design Pattern: Strategy (selectable extraction implementation)
 * // 编程技术: Lambda / 正则表达式 / Jackson 反序列化
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class HomeworkListExtractor {

    private static final Logger log = LoggerFactory.getLogger(HomeworkListExtractor.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    static final String SEL_LIST_CONTAINER = ".todo-list-container";
    static final String SEL_ITEM = ".todo-item";
    static final String SEL_ICON_USE = ".todo-icon use";
    static final String SEL_TITLE = ".todo-title .text-too-long";
    static final String SEL_STATUS = ".todo-status div";
    static final String SEL_COURSE = ".todo-course .text-too-long";
    static final String SEL_DATETIME = ".todo-datetime";
    static final String SEL_ACTION_LINK = ".todo-actions a.todo-link";

    private HomeworkListExtractor() {
        // utility class
    }

    /**
     * Extracts homework items from the current LMS page.
     *
     * @param browser the browser adapter, currently on the LMS page
     * @return a non-null list of homework items (may be empty)
     * @throws BookingException if extraction fails or returns invalid data
     */
    public static List<Homework> extract(BrowserLifecycle browser) {
        Objects.requireNonNull(browser, "browser");

        String rawJson = browser.evaluate(buildExtractionScript());
        if (rawJson == null || rawJson.isBlank()) {
            throw new BookingException(ErrorCode.HOMEWORK_PAGE_LOAD_FAILED,
                "homework list extraction returned empty result");
        }

        try {
            List<Homework> homeworks = JSON.readValue(rawJson, new TypeReference<>() {
            });
            log.info("Extracted {} homework item(s)", homeworks.size());
            return List.copyOf(homeworks);
        } catch (IOException e) {
            throw new BookingException(ErrorCode.ELEMENT_NOT_FOUND,
                "failed to parse homework list JSON: " + e.getMessage());
        }
    }

    /**
     * Builds the JavaScript that extracts structured data from the DOM.
     *
     * <p>Uses {@code JSON.stringify} so the Java side only needs to deserialize.
     */
    public static String buildExtractionScript() {
        return """
            (function() {
              var items = Array.from(document.querySelectorAll('%s'));
              var result = [];
              items.forEach(function(item, index) {
                var iconUse = item.querySelector('%s');
                var hrefAttr = iconUse && iconUse.getAttribute('xlink:href');
                if (!hrefAttr) {
                  hrefAttr = iconUse && iconUse.getAttribute('href');
                }
                if (hrefAttr !== '#todo-homework') return;

                var titleEl = item.querySelector('%s');
                var statusEl = item.querySelector('%s');
                var courseEl = item.querySelector('%s');
                var datetimeEl = item.querySelector('%s');
                var linkEl = item.querySelector('%s');

                var title = titleEl ? titleEl.textContent.trim() : '';
                var status = statusEl ? statusEl.textContent.trim() : '';
                var course = courseEl ? courseEl.textContent.trim() : '';
                var datetimeText = datetimeEl
                  ? datetimeEl.textContent.replace('截止时间:', '').trim()
                  : '';
                var linkHref = linkEl ? linkEl.getAttribute('href') : '';
                var idMatch = linkHref.match(/#\\/(\\d+)/);
                var homeworkId = idMatch ? idMatch[1] : String(index);

                result.push({
                  homeworkId: homeworkId,
                  courseName: course,
                  title: title,
                  deadline: datetimeText,
                  status: status
                });
              });
              return JSON.stringify(result);
            })()
            """.formatted(
            SEL_ITEM,
            SEL_ICON_USE,
            SEL_TITLE,
            SEL_STATUS,
            SEL_COURSE,
            SEL_DATETIME,
            SEL_ACTION_LINK
        ).replaceAll("\\R\s*", " ");
    }
}

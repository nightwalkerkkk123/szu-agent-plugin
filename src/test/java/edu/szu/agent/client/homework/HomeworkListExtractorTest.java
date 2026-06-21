package edu.szu.agent.client.homework;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.Homework;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HomeworkListExtractor")
class HomeworkListExtractorTest {

    @Mock
    private BrowserLifecycle browser;

    @Test
    @DisplayName("buildExtractionScript() contains all required selectors")
    void buildScriptContainsSelectors() {
        String script = HomeworkListExtractor.buildExtractionScript();

        assertThat(script)
            .contains(".todo-item")
            .contains(".todo-icon use")
            .contains(".todo-title .text-too-long")
            .contains(".todo-status div")
            .contains(".todo-course .text-too-long")
            .contains(".todo-datetime")
            .contains(".todo-actions a.todo-link")
            .contains("#todo-homework");
    }

    @Test
    @DisplayName("extract() returns homework list from valid JSON")
    void extractReturnsHomeworkList() {
        String json = """
            [
              {"homeworkId":"169193","courseName":"操作系统","title":"综合实验二",\
"deadline":"2026.06.24 23:59","status":"待提交"},
              {"homeworkId":"177533","courseName":"面向对象高级编程专题","title":"期末大作业提交",\
"deadline":"2026.06.21 23:59","status":"待提交"}
            ]
            """;
        when(browser.evaluate(HomeworkListExtractor.buildExtractionScript())).thenReturn(json);

        List<Homework> result = HomeworkListExtractor.extract(browser);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).homeworkId()).isEqualTo("169193");
        assertThat(result.get(0).courseName()).isEqualTo("操作系统");
        assertThat(result.get(0).title()).isEqualTo("综合实验二");
        assertThat(result.get(0).deadline()).isEqualTo("2026.06.24 23:59");
        assertThat(result.get(0).status()).isEqualTo("待提交");
        verify(browser).evaluate(HomeworkListExtractor.buildExtractionScript());
    }

    @Test
    @DisplayName("extract() returns empty list for empty JSON array")
    void extractReturnsEmptyList() {
        when(browser.evaluate(HomeworkListExtractor.buildExtractionScript())).thenReturn("[]");

        List<Homework> result = HomeworkListExtractor.extract(browser);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extract() throws HOMEWORK_PAGE_LOAD_FAILED when evaluate returns blank")
    void extractThrowsWhenBlank() {
        when(browser.evaluate(HomeworkListExtractor.buildExtractionScript())).thenReturn("   ");

        assertThatThrownBy(() -> HomeworkListExtractor.extract(browser))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code())
                .isEqualTo(ErrorCode.HOMEWORK_PAGE_LOAD_FAILED));
    }

    @Test
    @DisplayName("extract() throws ELEMENT_NOT_FOUND when JSON is invalid")
    void extractThrowsWhenInvalidJson() {
        when(browser.evaluate(HomeworkListExtractor.buildExtractionScript())).thenReturn("not-json");

        assertThatThrownBy(() -> HomeworkListExtractor.extract(browser))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code())
                .isEqualTo(ErrorCode.ELEMENT_NOT_FOUND));
    }
}

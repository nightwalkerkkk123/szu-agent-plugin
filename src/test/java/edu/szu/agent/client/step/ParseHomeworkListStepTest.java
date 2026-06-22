package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.homework.HomeworkListExtractor;
import edu.szu.agent.client.step.StepOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParseHomeworkListStep")
class ParseHomeworkListStepTest {

    @Mock
    private BrowserLifecycle browser;

    @Test
    @DisplayName("execute() extracts homework and writes them into the context")
    void executeStoresHomeworks() {
        when(browser.evaluate(HomeworkListExtractor.buildExtractionScript())).thenReturn(
            "[{\"homeworkId\":\"1\",\"courseName\":\"OS\",\"title\":\"lab\","
                + "\"deadline\":\"2026.06.24 23:59\",\"status\":\"待提交\"}]"
        );
        BookingContext ctx = new BookingContext(null);

        var result = new ParseHomeworkListStep().execute(browser, ctx);

        assertThat(result).isInstanceOf(StepOutcome.Continue.class);
        assertThat(ctx.homeworks()).hasSize(1);
        assertThat(ctx.homeworks().get(0).homeworkId()).isEqualTo("1");
    }

    @Test
    @DisplayName("name() returns PARSE_HOMEWORK_LIST")
    void nameIsParseHomeworkList() {
        assertThat(new ParseHomeworkListStep().name()).isEqualTo("PARSE_HOMEWORK_LIST");
    }
}

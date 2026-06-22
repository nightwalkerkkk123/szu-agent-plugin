package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.step.StepOutcome;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("NavigateToHomeworkDetailStep")
class NavigateToHomeworkDetailStepTest {

    @Mock
    private BrowserLifecycle browser;

    @Test
    @DisplayName("execute() 拼接 homeworkId 到 hash 路由 URL 并导航")
    void executeNavigatesToHashRoutedUrl() {
        BookingContext ctx = new BookingContext(null);
        ctx.homeworkId("169193");

        new NavigateToHomeworkDetailStep().execute(browser, ctx);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(browser).navigateTo(urlCaptor.capture());
        verifyNoMoreInteractions(browser);
        assertThat(urlCaptor.getValue())
            .isEqualTo("https://lms.szu.edu.cn/user/index#/169193");
    }

    @Test
    @DisplayName("execute() 成功时返回 Continue(无附件是合法状态)")
    void executeReturnsNullOnSuccess() {
        BookingContext ctx = new BookingContext(null);
        ctx.homeworkId("185894");

        var result = new NavigateToHomeworkDetailStep().execute(browser, ctx);

        assertThat(result).isInstanceOf(StepOutcome.Continue.class);
    }

    @Test
    @DisplayName("execute() homeworkId 缺失时抛 HOMEWORK_PAGE_LOAD_FAILED")
    void executeThrowsWhenHomeworkIdMissing() {
        BookingContext ctx = new BookingContext(null);
        // ctx.homeworkId() == null

        assertThatThrownBy(() -> new NavigateToHomeworkDetailStep().execute(browser, ctx))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code())
                .isEqualTo(ErrorCode.HOMEWORK_PAGE_LOAD_FAILED));
        verifyNoMoreInteractions(browser);
    }

    @Test
    @DisplayName("execute() homeworkId 为空白字符串时抛错")
    void executeThrowsWhenHomeworkIdBlank() {
        BookingContext ctx = new BookingContext(null);
        ctx.homeworkId("   ");

        assertThatThrownBy(() -> new NavigateToHomeworkDetailStep().execute(browser, ctx))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code())
                .isEqualTo(ErrorCode.HOMEWORK_PAGE_LOAD_FAILED));
        verifyNoMoreInteractions(browser);
    }

    @Test
    @DisplayName("name() 返回 NAVIGATE_TO_HOMEWORK_DETAIL")
    void nameIsNavigateToHomeworkDetail() {
        assertThat(new NavigateToHomeworkDetailStep().name())
            .isEqualTo("NAVIGATE_TO_HOMEWORK_DETAIL");
    }
}

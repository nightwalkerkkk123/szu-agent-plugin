package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.step.StepOutcome;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NavigateToHomeworkStep")
class NavigateToHomeworkStepTest {

    @Mock
    private BrowserLifecycle browser;

    @Test
    @DisplayName("execute() navigates to LMS user index URL and probes the todo container")
    void executeNavigatesAndProbes() {
        when(browser.isVisible(NavigateToHomeworkStep.SEL_TODO_LIST)).thenReturn(true);

        new NavigateToHomeworkStep().execute(browser, new BookingContext(null));

        var inOrder = inOrder(browser);
        inOrder.verify(browser).navigateTo(NavigateToHomeworkStep.LMS_USER_INDEX_URL);
        inOrder.verify(browser).isVisible(NavigateToHomeworkStep.SEL_TODO_LIST);
    }

    @Test
    @DisplayName("execute() returns Continue on success")
    void executeReturnsNullOnSuccess() {
        when(browser.isVisible(NavigateToHomeworkStep.SEL_TODO_LIST)).thenReturn(true);
        var result = new NavigateToHomeworkStep().execute(browser, new BookingContext(null));
        assertThat(result).isInstanceOf(StepOutcome.Continue.class);
    }

    @Test
    @DisplayName("execute() throws HOMEWORK_PAGE_LOAD_FAILED when todo container is missing")
    void executeThrowsWhenTodoContainerMissing() {
        when(browser.isVisible(NavigateToHomeworkStep.SEL_TODO_LIST)).thenReturn(false);

        assertThatThrownBy(() -> new NavigateToHomeworkStep().execute(browser, new BookingContext(null)))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code())
                .isEqualTo(ErrorCode.HOMEWORK_PAGE_LOAD_FAILED));
        verify(browser).navigateTo(NavigateToHomeworkStep.LMS_USER_INDEX_URL);
    }

    @Test
    @DisplayName("name() returns NAVIGATE_TO_HOMEWORK")
    void nameIsNavigateToHomework() {
        assertThat(new NavigateToHomeworkStep().name()).isEqualTo("NAVIGATE_TO_HOMEWORK");
    }
}

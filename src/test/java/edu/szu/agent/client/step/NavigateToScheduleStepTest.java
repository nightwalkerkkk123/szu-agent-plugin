package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("NavigateToScheduleStep")
class NavigateToScheduleStepTest {

    @Test
    @DisplayName("name = NAVIGATE_TO_SCHEDULE")
    void nameConstant() {
        assertThat(new NavigateToScheduleStep().name()).isEqualTo("NAVIGATE_TO_SCHEDULE");
    }

    @Test
    @DisplayName("execute 导航到 EHALL_SCHEDULE_URL 并等待 table.wut_table 可见")
    void navigateAndWait() {
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.isVisible("table.wut_table")).thenReturn(true);
        BookingContext ctx = new BookingContext(null);
        StepOutcome outcome = new NavigateToScheduleStep().execute(browser, ctx);
        assertThat(outcome).isInstanceOf(StepOutcome.Continue.class);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(browser).navigateTo(urlCaptor.capture());
        assertThat(urlCaptor.getValue()).isEqualTo(NavigateToScheduleStep.EHALL_SCHEDULE_URL);
        assertThat(urlCaptor.getValue()).endsWith("#/xskcb");
    }

    @Test
    @DisplayName("execute 表格不可见抛 SCHEDULE_PAGE_LOAD_FAILED")
    void tableNotVisible() {
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.isVisible("table.wut_table")).thenReturn(false);
        BookingContext ctx = new BookingContext(null);
        assertThatThrownBy(() -> new NavigateToScheduleStep().execute(browser, ctx))
            .isInstanceOf(BookingException.class)
            .extracting("code").isEqualTo(ErrorCode.SCHEDULE_PAGE_LOAD_FAILED);
    }

    @Test
    @DisplayName("EHALL_SCHEDULE_URL 是 ehall 课表页 hash 路由")
    void urlContainsHash() {
        assertThat(NavigateToScheduleStep.EHALL_SCHEDULE_URL)
            .startsWith("https://ehall.szu.edu.cn/jwapp/sys/wdkb/")
            .endsWith("#/xskcb");
    }
}

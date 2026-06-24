package edu.szu.agent.client.session;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionProbe")
class SessionProbeTest {

    @Mock
    private BrowserLifecycle browser;

    @Test
    @DisplayName("isAlive 返回 Fresh 当 waitForVisible 看到 todo-list-container")
    void isAliveFresh() {
        when(browser.waitForVisible(eq(".todo-list-container"), anyLong())).thenReturn(true);
        SessionProbe probe = new SessionProbe(
            "https://lms.szu.edu.cn/user/index", ".todo-list-container");
        assertThat(probe.isAlive(browser)).isInstanceOf(SessionResult.Fresh.class);
    }

    @Test
    @DisplayName("isAlive 返回 Stale 当 waitForVisible 超时未见")
    void isAliveStale() {
        when(browser.waitForVisible(eq(".todo-list-container"), anyLong())).thenReturn(false);
        SessionProbe probe = new SessionProbe(
            "https://lms.szu.edu.cn/user/index", ".todo-list-container");
        SessionResult r = probe.isAlive(browser);
        assertThat(r).isInstanceOf(SessionResult.Stale.class);
    }

    @Test
    @DisplayName("isAlive 返回 Stale 当 navigate 抛 BookingException")
    void isAliveStaleOnError() {
        org.mockito.Mockito.doThrow(new BookingException(
                ErrorCode.NETWORK_TIMEOUT, "timeout"))
            .when(browser).navigateTo("https://lms.szu.edu.cn/user/index");
        SessionProbe probe = new SessionProbe(
            "https://lms.szu.edu.cn/user/index", ".todo-list-container");
        assertThat(probe.isAlive(browser)).isInstanceOf(SessionResult.Stale.class);
    }
}

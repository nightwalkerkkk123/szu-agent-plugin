package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.Sport;
import edu.szu.agent.domain.YuehaiSport;
import edu.szu.agent.domain.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CasLoginStep")
class CasLoginStepTest {

    @Mock
    private BrowserLifecycle browser;

    private BookingContext ctx;

    @BeforeEach
    void setUp() {
        BookingRequest request = BookingRequest.builder()
            .username("2023150090")
            .campus(Campus.YUEHAI)
            .sport(YuehaiSport.TENNIS)
            .date(LocalDate.now())
            .timeSlot(TimeSlot.T19_20)
            .preferredVenueIndex(1)
            .build();
        Account account = new Account("2023150090", "secret123", "test-user");
        ctx = new BookingContext(request, account);
    }

    @Test
    @DisplayName("execute() navigates to ehall, evaluates DOM-injection script, then probes login indicator")
    void executeNavigatesAndInjectsLoginScript() {
        new CasLoginStep().execute(browser, ctx);

        verify(browser).navigateTo(CasLoginStep.EHALL_VENUE_URL);
        verify(browser).evaluate(CasLoginStep.buildLoginScript("2023150090", "secret123"));
        verify(browser).isVisible(CasLoginStep.SEL_LOGGED_IN_INDICATOR);
    }

    @Test
    @DisplayName("execute() calls browser in order: navigate → evaluate → isVisible")
    void executeCallsInOrder() {
        new CasLoginStep().execute(browser, ctx);

        var inOrder = inOrder(browser);
        inOrder.verify(browser).navigateTo(CasLoginStep.EHALL_VENUE_URL);
        inOrder.verify(browser).evaluate(CasLoginStep.buildLoginScript("2023150090", "secret123"));
        inOrder.verify(browser).isVisible(CasLoginStep.SEL_LOGGED_IN_INDICATOR);
    }

    @Test
    @DisplayName("buildLoginScript() embeds credentials safely (single-quote escaping)")
    void buildLoginScriptEscapesQuotes() {
        String script = CasLoginStep.buildLoginScript("user'X", "pwd\\\"Y");
        assertThat(script).contains("u.value='user\\'X'");
        assertThat(script).contains("p.value='pwd\\\\\"Y'");
    }

    @Test
    @DisplayName("execute() returns Continue on success")
    void executeReturnsContinueOnSuccess() {
        StepOutcome outcome = new CasLoginStep().execute(browser, ctx);
        assertThat(outcome).isInstanceOf(StepOutcome.Continue.class);
    }
}

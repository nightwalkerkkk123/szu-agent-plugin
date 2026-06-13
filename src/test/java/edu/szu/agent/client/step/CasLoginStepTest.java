package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.Sport;
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
            .sport(Sport.TENNIS)
            .date(LocalDate.now())
            .timeSlot(new TimeSlot("19:00", "20:00"))
            .preferredVenueIndex(1)
            .build();
        Account account = new Account("2023150090", "secret123", "test-user");
        ctx = new BookingContext(request, account);
    }

    @Test
    @DisplayName("execute() fills username, password, then clicks submit")
    void executeFillsCredentialsAndSubmits() {
        new CasLoginStep().execute(browser, ctx);

        verify(browser).fill(CasLoginStep.SEL_USERNAME, "2023150090");
        verify(browser).fill(CasLoginStep.SEL_PASSWORD, "secret123");
        verify(browser).click(CasLoginStep.SEL_LOGIN_SUBMIT);
    }

    @Test
    @DisplayName("execute() calls browser in correct order: fill username → fill password → click submit")
    void executeCallsInOrder() {
        new CasLoginStep().execute(browser, ctx);

        var inOrder = inOrder(browser);
        inOrder.verify(browser).fill(CasLoginStep.SEL_USERNAME, "2023150090");
        inOrder.verify(browser).fill(CasLoginStep.SEL_PASSWORD, "secret123");
        inOrder.verify(browser).click(CasLoginStep.SEL_LOGIN_SUBMIT);
    }

    @Test
    @DisplayName("execute() returns null on success")
    void executeReturnsNullOnSuccess() {
        var result = new CasLoginStep().execute(browser, ctx);
        assertThat(result).isNull();
    }
}
package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.Campus;
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
@DisplayName("SelectDateStep")
class SelectDateStepTest {

    @Mock
    private BrowserLifecycle browser;

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account("2023150090", "test-pwd", "test-user");
    }

    private BookingContext contextFor(LocalDate date) {
        BookingRequest request = BookingRequest.builder()
            .campus(Campus.YUEHAI)
            .sport(YuehaiSport.GYM_AEROBIC)
            .date(date)
            .timeSlot(TimeSlot.T20_21)
            .preferredVenueIndex(1)
            .build();
        return new BookingContext(request, account);
    }

    @Test
    @DisplayName("execute() clicks the label for the requested date")
    void executeClicksDateLabel() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        BookingContext ctx = contextFor(tomorrow);
        String expectedSelector = "label[for=\"" + tomorrow + "\"]";

        StepOutcome outcome = new SelectDateStep().execute(browser, ctx);

        verify(browser).click(expectedSelector);
        assertThat(outcome).isInstanceOf(StepOutcome.Continue.class);
    }

    @Test
    @DisplayName("execute() computes selector from request date, not from now()")
    void executeSelectorReflectsRequestDate() {
        // A date 5 days out — proves the selector is derived from ctx.request(),
        // not from LocalDate.now() at execution time. (i.e. no hard-coded date.)
        LocalDate fiveDaysOut = LocalDate.now().plusDays(5);
        BookingContext ctx = contextFor(fiveDaysOut);
        String expectedSelector = "label[for=\"" + fiveDaysOut + "\"]";

        new SelectDateStep().execute(browser, ctx);

        verify(browser).click(expectedSelector);
    }
}

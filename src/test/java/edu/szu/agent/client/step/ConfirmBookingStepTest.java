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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfirmBookingStep")
class ConfirmBookingStepTest {

    @Mock
    private BrowserLifecycle browser;

    private BookingContext ctx;

    @BeforeEach
    void setUp() {
        BookingRequest request = BookingRequest.builder()
            .campus(Campus.YUEHAI)
            .sport(YuehaiSport.TENNIS)
            .date(LocalDate.now())
            .timeSlot(TimeSlot.T19_20)
            .preferredVenueIndex(1)
            .build();
        Account account = new Account("2023150090", "test-pwd", "test-user");
        ctx = new BookingContext(request, account).withSelectedVenue("网球1号场");
    }

    @Test
    @DisplayName("execute() clicks the confirm button and returns Continue")
    void executeClicksConfirmButton() {
        StepOutcome outcome = new ConfirmBookingStep().execute(browser, ctx);

        verify(browser).click(ConfirmBookingStep.SEL_CONFIRM_BUTTON);
        assertThat(outcome).isInstanceOf(StepOutcome.Continue.class);
        assertThat(((StepOutcome.Continue) outcome).nextContext().selectedVenue())
            .isEqualTo("网球1号场");
    }
}

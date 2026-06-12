package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
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
@DisplayName("ConfirmBookingStep")
class ConfirmBookingStepTest {

    @Mock
    private BrowserLifecycle browser;

    private BookingContext ctx;
    private Account account;

    @BeforeEach
    void setUp() {
        BookingRequest request = BookingRequest.builder()
            .campus(Campus.YUEHAI)
            .sport(Sport.TENNIS)
            .date(LocalDate.now())
            .timeSlot(new TimeSlot("19:00", "20:00"))
            .preferredVenueIndex(1)
            .build();
        account = new Account("2023150090", "test-pwd", "test-user");
        ctx = new BookingContext(request, account);
        ctx.selectedVenue("网球1号场");
    }

    @Test
    @DisplayName("execute() clicks the confirm button")
    void executeClicksConfirmButton() {
        new ConfirmBookingStep().execute(browser, ctx);
        verify(browser).click(ConfirmBookingStep.SEL_CONFIRM_BUTTON);
    }

    @Test
    @DisplayName("execute() returns BookingResult.Success with venue name and confirmation")
    void executeReturnsSuccessResult() {
        BookingResult result = new ConfirmBookingStep().execute(browser, ctx);

        assertThat(result).isInstanceOf(BookingResult.Success.class);
        var success = (BookingResult.Success) result;
        assertThat(success.venueName()).isEqualTo("网球1号场");
        assertThat(success.confirmation()).startsWith("CONFIRMED-");
    }
}
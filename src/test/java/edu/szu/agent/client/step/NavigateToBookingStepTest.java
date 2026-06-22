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
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link NavigateToBookingStep}.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NavigateToBookingStep")
class NavigateToBookingStepTest {

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
        ctx = new BookingContext(request, new Account("2023150090", "secret", "test"));
    }

    @Test
    @DisplayName("execute() navigates to ehall booking URL and returns Continue")
    void executeNavigatesToBookingUrl() {
        StepOutcome outcome = new NavigateToBookingStep().execute(browser, ctx);

        assertThat(outcome).isInstanceOf(StepOutcome.Continue.class);
        verify(browser).navigateTo(NavigateToBookingStep.EHALL_BOOKING_URL);
    }
}

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SelectTimeSlotStep")
class SelectTimeSlotStepTest {

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
    }

    @Test
    @DisplayName("execute() finds and clicks the matching time slot li")
    void executeClicksMatchingSlot() {
        when(browser.isVisible(SelectTimeSlotStep.SEL_TIME_SLOT_AREA)).thenReturn(true);
        when(browser.allTextOf(SelectTimeSlotStep.SEL_TIME_SLOT_AREA + " li"))
            .thenReturn(List.of("19:00-20:00", "20:00-21:00"));

        new SelectTimeSlotStep().execute(browser, ctx);

        verify(browser).click(SelectTimeSlotStep.SEL_TIME_SLOT_AREA
            + " li:has-text('19:00-20:00')");
    }

    @Test
    @DisplayName("execute() returns BookingResult.Failure when area is not visible")
    void executeReturnsFailureWhenAreaNotVisible() {
        when(browser.isVisible(SelectTimeSlotStep.SEL_TIME_SLOT_AREA)).thenReturn(false);

        BookingResult result = new SelectTimeSlotStep().execute(browser, ctx);

        assertThat(result).isInstanceOf(BookingResult.Failure.class);
    }

    @Test
    @DisplayName("execute() returns BookingResult.Failure when slot not found in list")
    void executeReturnsFailureWhenSlotNotFound() {
        when(browser.isVisible(SelectTimeSlotStep.SEL_TIME_SLOT_AREA)).thenReturn(true);
        when(browser.allTextOf(SelectTimeSlotStep.SEL_TIME_SLOT_AREA + " li"))
            .thenReturn(List.of("20:00-21:00")); // missing 19:00-20:00

        BookingResult result = new SelectTimeSlotStep().execute(browser, ctx);

        assertThat(result).isInstanceOf(BookingResult.Failure.class);
    }

    @Test
    @DisplayName("execute() returns null on success")
    void executeReturnsNullOnSuccess() {
        when(browser.isVisible(SelectTimeSlotStep.SEL_TIME_SLOT_AREA)).thenReturn(true);
        when(browser.allTextOf(SelectTimeSlotStep.SEL_TIME_SLOT_AREA + " li"))
            .thenReturn(List.of("19:00-20:00"));

        assertThat(new SelectTimeSlotStep().execute(browser, ctx)).isNull();
    }
}
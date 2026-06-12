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
@DisplayName("SelectVenueStep")
class SelectVenueStepTest {

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
    @DisplayName("execute() clicks the 1-based venue index (nth-child)")
    void executeClicksVenueByIndex() {
        when(browser.isVisible(SelectVenueStep.SEL_VENUE_LIST)).thenReturn(true);
        when(browser.allTextOf(SelectVenueStep.SEL_VENUE_LIST + " li"))
            .thenReturn(List.of("网球1号场", "网球2号场"));

        new SelectVenueStep().execute(browser, ctx);

        verify(browser).click(SelectVenueStep.SEL_VENUE_LIST + " li:nth-child(1)");
    }

    @Test
    @DisplayName("execute() stores selected venue name in context")
    void executeStoresVenueNameInContext() {
        when(browser.isVisible(SelectVenueStep.SEL_VENUE_LIST)).thenReturn(true);
        when(browser.allTextOf(SelectVenueStep.SEL_VENUE_LIST + " li"))
            .thenReturn(List.of("网球1号场", "网球2号场"));

        new SelectVenueStep().execute(browser, ctx);

        assertThat(ctx.selectedVenue()).isEqualTo("网球1号场");
    }

    @Test
    @DisplayName("execute() returns BookingResult.Failure when venue list not visible")
    void executeReturnsFailureWhenListNotVisible() {
        when(browser.isVisible(SelectVenueStep.SEL_VENUE_LIST)).thenReturn(false);

        BookingResult result = new SelectVenueStep().execute(browser, ctx);
        assertThat(result).isInstanceOf(BookingResult.Failure.class);
    }

    @Test
    @DisplayName("execute() returns BookingResult.Failure when no venues available")
    void executeReturnsFailureWhenNoVenues() {
        when(browser.isVisible(SelectVenueStep.SEL_VENUE_LIST)).thenReturn(true);
        when(browser.allTextOf(SelectVenueStep.SEL_VENUE_LIST + " li")).thenReturn(List.of());

        BookingResult result = new SelectVenueStep().execute(browser, ctx);
        assertThat(result).isInstanceOf(BookingResult.Failure.class);
    }
}
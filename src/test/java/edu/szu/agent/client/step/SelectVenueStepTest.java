package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
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
        // Cap the venue-render wait so negative-path tests run in ms, not 8s.
        System.setProperty("szu.agent.slot-wait-ms", "0");
        account = new Account("2023150090", "test-pwd", "test-user");
    }

    private BookingContext contextFor(YuehaiSport sport) {
        BookingRequest request = BookingRequest.builder()
            .campus(Campus.YUEHAI)
            .sport(sport)
            .date(LocalDate.now())
            .timeSlot(TimeSlot.T19_20)
            .preferredVenueIndex(1)
            .build();
        return new BookingContext(request, account);
    }

    @Test
    @DisplayName("execute() uses CourtListSelector for tennis and stores venue name")
    void executeTennisPath() {
        ctx = contextFor(YuehaiSport.TENNIS);
        when(browser.isVisible(CourtListSelector.SEL_COURT_LABEL_AVAILABLE)).thenReturn(true);
        when(browser.allTextOf(CourtListSelector.SEL_COURT_LABEL_AVAILABLE + " div.element"))
            .thenReturn(List.of("北区网球1号场(可预约)", "北区网球3号场(可预约)"));

        new SelectVenueStep().execute(browser, ctx);

        verify(browser).click(":nth-match("
            + CourtListSelector.SEL_COURT_LABEL_AVAILABLE + ", 1)");
        assertThat(ctx.selectedVenue()).isEqualTo("北区网球1号场");
    }

    @Test
    @DisplayName("execute() uses CapacityVenueSelector for gym and stores venue name")
    void executeGymPath() {
        ctx = contextFor(YuehaiSport.GYM_AEROBIC);
        when(browser.isVisible(CapacityVenueSelector.SEL_VENUE_LABEL)).thenReturn(true);
        when(browser.allTextOf(CapacityVenueSelector.SEL_VENUE_LABEL + " div.element"))
            .thenReturn(List.of("二楼健身房(42/50)"));

        new SelectVenueStep().execute(browser, ctx);

        assertThat(ctx.selectedVenue()).isEqualTo("二楼健身房");
    }

    @Test
    @DisplayName("execute() returns Failure when no (可预约) courts present")
    void executeReturnsFailureWhenNothingAvailable() {
        ctx = contextFor(YuehaiSport.TENNIS);
        when(browser.isVisible(CourtListSelector.SEL_COURT_LABEL_AVAILABLE)).thenReturn(false);

        BookingResult result = new SelectVenueStep().execute(browser, ctx);
        assertThat(result).isInstanceOf(BookingResult.Failure.class);
    }

    @Test
    @DisplayName("execute() returns Failure when court list reads empty")
    void executeReturnsFailureWhenListEmpty() {
        ctx = contextFor(YuehaiSport.TENNIS);
        when(browser.isVisible(CourtListSelector.SEL_COURT_LABEL_AVAILABLE)).thenReturn(true);
        when(browser.allTextOf(CourtListSelector.SEL_COURT_LABEL_AVAILABLE + " div.element"))
            .thenReturn(List.of());

        BookingResult result = new SelectVenueStep().execute(browser, ctx);
        assertThat(result).isInstanceOf(BookingResult.Failure.class);
    }

    @Test
    @DisplayName("execute() returns Failure when gym venue capacity is zero")
    void executeReturnsFailureWhenGymFull() {
        ctx = contextFor(YuehaiSport.GYM_AEROBIC);
        when(browser.isVisible(CapacityVenueSelector.SEL_VENUE_LABEL)).thenReturn(true);
        when(browser.allTextOf(CapacityVenueSelector.SEL_VENUE_LABEL + " div.element"))
            .thenReturn(List.of("二楼健身房(0/50)"));

        BookingResult result = new SelectVenueStep().execute(browser, ctx);
        assertThat(result).isInstanceOf(BookingResult.Failure.class);
    }
}

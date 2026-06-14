package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.TimeSlot;
import edu.szu.agent.domain.YuehaiSport;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VenueSelector strategies")
class VenueSelectorTest {

    @Mock
    private BrowserLifecycle browser;

    private BookingContext ctx;
    private Account account;

    @BeforeEach
    void setUp() {
        System.setProperty("szu.agent.slot-wait-ms", "0");
        account = new Account("2023150090", "test-pwd", "test-user");
    }

    private BookingContext contextWith(int preferredVenueIndex) {
        BookingRequest request = BookingRequest.builder()
            .campus(Campus.YUEHAI)
            .sport(YuehaiSport.TENNIS)
            .date(LocalDate.now())
            .timeSlot(TimeSlot.T19_20)
            .preferredVenueIndex(preferredVenueIndex)
            .build();
        return new BookingContext(request, account);
    }

    @Nested
    @DisplayName("CourtListSelector")
    class CourtListSelectorTests {

        @Test
        @DisplayName("selects the N-th available court")
        void selectsNthCourt() {
            ctx = contextWith(2);
            when(browser.isVisible(CourtListSelector.SEL_COURT_LABEL_AVAILABLE)).thenReturn(true);
            when(browser.allTextOf(CourtListSelector.SEL_COURT_LABEL_AVAILABLE + " div.element"))
                .thenReturn(List.of("北区网球1号场(可预约)", "北区网球2号场(可预约)", "北区网球3号场(可预约)"));

            String venue = new CourtListSelector().selectAndClick(browser, ctx);

            assertThat(venue).isEqualTo("北区网球2号场");
            verify(browser).click(":nth-match(" + CourtListSelector.SEL_COURT_LABEL_AVAILABLE + ", 2)");
        }

        @Test
        @DisplayName("falls back to the last court when preferred index exceeds availability")
        void fallsBackToLastCourt() {
            ctx = contextWith(10);
            when(browser.isVisible(CourtListSelector.SEL_COURT_LABEL_AVAILABLE)).thenReturn(true);
            when(browser.allTextOf(CourtListSelector.SEL_COURT_LABEL_AVAILABLE + " div.element"))
                .thenReturn(List.of("北区网球1号场(可预约)", "北区网球2号场(可预约)"));

            String venue = new CourtListSelector().selectAndClick(browser, ctx);

            assertThat(venue).isEqualTo("北区网球2号场");
            verify(browser).click(":nth-match(" + CourtListSelector.SEL_COURT_LABEL_AVAILABLE + ", 2)");
        }

        @Test
        @DisplayName("throws NO_AVAILABLE_VENUE when no bookable courts exist")
        void throwsWhenNoCourts() {
            ctx = contextWith(1);
            when(browser.isVisible(CourtListSelector.SEL_COURT_LABEL_AVAILABLE)).thenReturn(false);

            assertThatThrownBy(() -> new CourtListSelector().selectAndClick(browser, ctx))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).code()).isEqualTo(ErrorCode.NO_AVAILABLE_VENUE));
        }
    }

    @Nested
    @DisplayName("CapacityVenueSelector")
    class CapacityVenueSelectorTests {

        private BookingContext gymContextWith(int preferredVenueIndex) {
            BookingRequest request = BookingRequest.builder()
                .campus(Campus.YUEHAI)
                .sport(YuehaiSport.GYM_AEROBIC)
                .date(LocalDate.now())
                .timeSlot(TimeSlot.T19_20)
                .preferredVenueIndex(preferredVenueIndex)
                .build();
            return new BookingContext(request, account);
        }

        @Test
        @DisplayName("selects a single capacity venue and strips the capacity suffix")
        void selectsCapacityVenue() {
            ctx = gymContextWith(1);
            when(browser.isVisible(CapacityVenueSelector.SEL_VENUE_LABEL)).thenReturn(true);
            when(browser.allTextOf(CapacityVenueSelector.SEL_VENUE_LABEL + " div.element"))
                .thenReturn(List.of("二楼健身房(42/50)"));

            String venue = new CapacityVenueSelector().selectAndClick(browser, ctx);

            assertThat(venue).isEqualTo("二楼健身房");
            verify(browser).click(anyString());
        }

        @Test
        @DisplayName("scopes venue search to the 选择场地 section")
        void scopesToVenueSectionOnly() {
            ctx = gymContextWith(1);
            when(browser.isVisible(CapacityVenueSelector.SEL_VENUE_LABEL)).thenReturn(true);
            when(browser.allTextOf(CapacityVenueSelector.SEL_VENUE_LABEL + " div.element"))
                .thenReturn(List.of("二楼健身房(42/50)"));

            String venue = new CapacityVenueSelector().selectAndClick(browser, ctx);

            assertThat(venue).isEqualTo("二楼健身房");
            // The selector targets div.text-wrapper-2:has-text("选择场地") + div,
            // so it cannot accidentally match time-slot labels in the section above.
            assertThat(CapacityVenueSelector.SEL_VENUE_LABEL)
                .contains("选择场地")
                .doesNotContain("time")
                .doesNotContain("slot");
        }

        @Test
        @DisplayName("throws NO_AVAILABLE_VENUE when capacity is zero")
        void throwsWhenFull() {
            ctx = gymContextWith(1);
            when(browser.isVisible(CapacityVenueSelector.SEL_VENUE_LABEL)).thenReturn(true);
            when(browser.allTextOf(CapacityVenueSelector.SEL_VENUE_LABEL + " div.element"))
                .thenReturn(List.of("二楼健身房(0/50)"));

            assertThatThrownBy(() -> new CapacityVenueSelector().selectAndClick(browser, ctx))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).code()).isEqualTo(ErrorCode.NO_AVAILABLE_VENUE));
        }

        @Test
        @DisplayName("throws NO_AVAILABLE_VENUE when no capacity pattern is present")
        void throwsWhenNoCapacityPattern() {
            ctx = gymContextWith(1);
            when(browser.isVisible(CapacityVenueSelector.SEL_VENUE_LABEL)).thenReturn(true);
            when(browser.allTextOf(CapacityVenueSelector.SEL_VENUE_LABEL + " div.element"))
                .thenReturn(List.of("未知场地"));

            assertThatThrownBy(() -> new CapacityVenueSelector().selectAndClick(browser, ctx))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).code()).isEqualTo(ErrorCode.NO_AVAILABLE_VENUE));
        }
    }
}

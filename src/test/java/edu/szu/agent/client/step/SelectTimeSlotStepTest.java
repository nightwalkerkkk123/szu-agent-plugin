package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
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
@DisplayName("SelectTimeSlotStep")
class SelectTimeSlotStepTest {

    @Mock
    private BrowserLifecycle browser;

    private BookingContext ctx;
    private Account account;

    @BeforeEach
    void setUp() {
        // Cap the slot-render wait so the negative-path test runs in ms, not 8s.
        System.setProperty("szu.agent.slot-wait-ms", "0");
        BookingRequest request = BookingRequest.builder()
            .campus(Campus.YUEHAI)
            .sport(YuehaiSport.TENNIS)
            .date(LocalDate.now())
            .timeSlot(TimeSlot.T19_20)
            .preferredVenueIndex(1)
            .build();
        account = new Account("2023150090", "test-pwd", "test-user");
        ctx = new BookingContext(request, account);
    }

    private static String labelSel(String slotId) {
        return String.format(SelectTimeSlotStep.SEL_SLOT_LABEL_TEMPLATE, slotId);
    }

    private static String availableSel(String slotId) {
        return String.format(SelectTimeSlotStep.SEL_SLOT_AVAILABLE_TEMPLATE, slotId);
    }

    @Test
    @DisplayName("execute() clicks the (可预约) label for the requested slot")
    void executeClicksAvailableSlot() {
        when(browser.isVisible(labelSel("19:00-20:00"))).thenReturn(true);
        when(browser.isVisible(availableSel("19:00-20:00"))).thenReturn(true);

        new SelectTimeSlotStep().execute(browser, ctx);

        verify(browser).click(availableSel("19:00-20:00"));
    }

    @Test
    @DisplayName("execute() returns Failure when the slot label is missing entirely")
    void executeReturnsFailureWhenLabelMissing() {
        when(browser.isVisible(labelSel("19:00-20:00"))).thenReturn(false);

        BookingResult result = new SelectTimeSlotStep().execute(browser, ctx);

        assertThat(result).isInstanceOf(BookingResult.Failure.class);
    }

    @Test
    @DisplayName("execute() returns Failure when the slot is 已满员/无开放场地")
    void executeReturnsFailureWhenNotBookable() {
        when(browser.isVisible(labelSel("19:00-20:00"))).thenReturn(true);
        when(browser.isVisible(availableSel("19:00-20:00"))).thenReturn(false);

        BookingResult result = new SelectTimeSlotStep().execute(browser, ctx);

        assertThat(result).isInstanceOf(BookingResult.Failure.class);
    }

    @Test
    @DisplayName("execute() returns null on success")
    void executeReturnsNullOnSuccess() {
        when(browser.isVisible(labelSel("19:00-20:00"))).thenReturn(true);
        when(browser.isVisible(availableSel("19:00-20:00"))).thenReturn(true);

        assertThat(new SelectTimeSlotStep().execute(browser, ctx)).isNull();
    }
}

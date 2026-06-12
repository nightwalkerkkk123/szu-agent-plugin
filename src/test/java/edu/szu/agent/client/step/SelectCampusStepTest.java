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
@DisplayName("SelectCampusStep")
class SelectCampusStepTest {

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
    @DisplayName("execute() fills campus dropdown with ehall code")
    void executeFillsCampusDropdown() {
        new SelectCampusStep().execute(browser, ctx);
        verify(browser).fill(SelectCampusStep.SEL_CAMPUS_DROPDOWN, "yuehai");
    }

    @Test
    @DisplayName("execute() returns null on success")
    void executeReturnsNullOnSuccess() {
        assertThat(new SelectCampusStep().execute(browser, ctx)).isNull();
    }
}
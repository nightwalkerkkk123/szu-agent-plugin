package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.Homework;
import edu.szu.agent.domain.TimeSlot;
import edu.szu.agent.domain.YuehaiSport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersistSessionStep")
class PersistSessionStepTest {

    @Mock private BrowserLifecycle browser;
    @Mock private SessionStore store;

    private Account account;
    private BookingRequest request;

    @BeforeEach
    void setUp() {
        account = new Account("2023150090", "secret", "test");
        request = BookingRequest.builder()
            .username("2023150090")
            .campus(Campus.YUEHAI)
            .sport(YuehaiSport.TENNIS)
            .date(LocalDate.now())
            .timeSlot(TimeSlot.T19_20)
            .preferredVenueIndex(1)
            .build();
    }

    @Test
    @DisplayName("homeworks 非空 -> export")
    void successExports() {
        BookingContext ctx = new BookingContext(request, account);
        ctx.username("2023150090");
        ctx.homeworks(List.of(new Homework("1", "OS", "lab", "2026.06.24", "待提交")));
        Path target = Path.of("fake/path.json");
        when(store.defaultPath()).thenReturn(target);

        new PersistSessionStep(store).execute(browser, ctx);

        verify(browser).exportStorageState(target);
    }

    @Test
    @DisplayName("homeworks 为空 -> 不 export")
    void emptySkips() {
        BookingContext ctx = new BookingContext(request, account);
        ctx.username("2023150090");
        ctx.homeworks(List.of());

        new PersistSessionStep(store).execute(browser, ctx);

        verify(browser, never()).exportStorageState(any());
    }

    @Test
    @DisplayName("homeworks null -> 不 export")
    void nullSkips() {
        BookingContext ctx = new BookingContext(request, account);
        ctx.username("2023150090");

        new PersistSessionStep(store).execute(browser, ctx);

        verify(browser, never()).exportStorageState(any());
    }
}

package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionResult;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.TimeSlot;
import edu.szu.agent.domain.YuehaiSport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestoreSessionStep")
class RestoreSessionStepTest {

    @Mock private BrowserLifecycle browser;
    @Mock private SessionStore store;
    @Mock private SessionProbe probe;

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
    @DisplayName("import 成功 + 探针 Fresh -> sessionOk=true")
    void freshSetsSessionOk() {
        BookingContext ctx = new BookingContext(request, account);
        ctx.username("2023150090");
        when(store.exists()).thenReturn(true);
        when(store.isFresh(Duration.ofDays(30))).thenReturn(true);
        when(browser.importStorageState(any())).thenReturn(true);
        when(probe.isAlive(browser)).thenReturn(new SessionResult.Fresh());

        new RestoreSessionStep(store, probe, Duration.ofDays(30))
            .execute(browser, ctx);

        assertThat(ctx.sessionOk()).isTrue();
    }

    @Test
    @DisplayName("import 失败 -> sessionOk=false")
    void importFailsNoSessionOk() {
        BookingContext ctx = new BookingContext(request, account);
        ctx.username("2023150090");
        when(store.exists()).thenReturn(true);
        when(store.isFresh(Duration.ofDays(30))).thenReturn(true);
        when(browser.importStorageState(any())).thenReturn(false);

        new RestoreSessionStep(store, probe, Duration.ofDays(30))
            .execute(browser, ctx);

        assertThat(ctx.sessionOk()).isFalse();
        verify(probe, never()).isAlive(any());
    }

    @Test
    @DisplayName("TTL 过期 -> 不 import 直接走重登")
    void staleTtlSkipsImport() {
        BookingContext ctx = new BookingContext(request, account);
        ctx.username("2023150090");
        when(store.exists()).thenReturn(true);
        when(store.isFresh(Duration.ofDays(30))).thenReturn(false);

        new RestoreSessionStep(store, probe, Duration.ofDays(30))
            .execute(browser, ctx);

        assertThat(ctx.sessionOk()).isFalse();
        verify(browser, never()).importStorageState(any());
    }

    @Test
    @DisplayName("探针 Stale -> sessionOk=false, 删旧文件")
    void probeStaleDeletes() throws IOException {
        BookingContext ctx = new BookingContext(request, account);
        ctx.username("2023150090");
        when(store.exists()).thenReturn(true);
        when(store.isFresh(Duration.ofDays(30))).thenReturn(true);
        when(browser.importStorageState(any())).thenReturn(true);
        when(probe.isAlive(browser)).thenReturn(
            new SessionResult.Stale("timeout"));

        new RestoreSessionStep(store, probe, Duration.ofDays(30))
            .execute(browser, ctx);

        assertThat(ctx.sessionOk()).isFalse();
        verify(store).deleteIfExists();
    }

    @Test
    @DisplayName("文件不存在 -> sessionOk=false")
    void missingFileNoSessionOk() {
        BookingContext ctx = new BookingContext(request, account);
        ctx.username("2023150090");
        when(store.exists()).thenReturn(false);

        new RestoreSessionStep(store, probe, Duration.ofDays(30))
            .execute(browser, ctx);

        assertThat(ctx.sessionOk()).isFalse();
        verify(browser, never()).importStorageState(any());
    }
}

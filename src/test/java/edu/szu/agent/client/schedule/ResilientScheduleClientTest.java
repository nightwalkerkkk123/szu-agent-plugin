package edu.szu.agent.client.schedule;

import edu.szu.agent.client.EhallScheduleClient;
import edu.szu.agent.domain.ScheduleListResult;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResilientScheduleClientTest {

    @Test
    void real_success_returns_real_result() {
        ScheduleListResult realResult = new ScheduleListResult.Success(
            List.of(), Instant.parse("2026-06-25T00:00:00Z"));
        EhallScheduleClient real = mock(EhallScheduleClient.class);
        when(real.list()).thenReturn(realResult);
        ScheduleListClient fallback = mock(ScheduleListClient.class);

        ScheduleListResult out = new ResilientScheduleClient(real, fallback).list();

        assertThat(out).isSameAs(realResult);
    }

    @Test
    void real_failure_falls_back_to_static() {
        ScheduleListResult failure = new ScheduleListResult.Failure(
            ErrorCode.SCHEDULE_PAGE_LOAD_FAILED, "session expired");
        EhallScheduleClient real = mock(EhallScheduleClient.class);
        when(real.list()).thenReturn(failure);
        ScheduleListResult fallbackResult = new ScheduleListResult.Success(
            List.of(), Instant.parse("2026-06-25T00:00:00Z"));
        ScheduleListClient fallback = mock(ScheduleListClient.class);
        when(fallback.list()).thenReturn(fallbackResult);

        ScheduleListResult out = new ResilientScheduleClient(real, fallback).list();

        assertThat(out).isSameAs(fallbackResult);
    }

    @Test
    void real_throws_falls_back_to_static() {
        EhallScheduleClient real = mock(EhallScheduleClient.class);
        when(real.list()).thenThrow(new RuntimeException("network down"));
        ScheduleListResult fallbackResult = new ScheduleListResult.Success(
            List.of(), Instant.parse("2026-06-25T00:00:00Z"));
        ScheduleListClient fallback = mock(ScheduleListClient.class);
        when(fallback.list()).thenReturn(fallbackResult);

        ScheduleListResult out = new ResilientScheduleClient(real, fallback).list();

        assertThat(out).isSameAs(fallbackResult);
    }

    @Test
    void null_real_uses_static_directly() {
        ScheduleListResult fallbackResult = new ScheduleListResult.Success(
            List.of(), Instant.parse("2026-06-25T00:00:00Z"));
        ScheduleListClient fallback = mock(ScheduleListClient.class);
        when(fallback.list()).thenReturn(fallbackResult);

        ScheduleListResult out = new ResilientScheduleClient(null, fallback).list();

        assertThat(out).isSameAs(fallbackResult);
    }
}

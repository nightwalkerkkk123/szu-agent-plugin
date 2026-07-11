package edu.szu.agent.client.payment;

import edu.szu.agent.client.http.CampusHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OlepayStatusPoller")
class OlepayStatusPollerTest {

    private final CampusHttpClient http = mock(CampusHttpClient.class);
    private final OlepayStatusPoller poller = new OlepayStatusPoller(http);

    @Test
    @DisplayName("state=0 返回 PENDING")
    void stateZeroIsPending() {
        when(http.postForm(any(), any())).thenReturn("{\"state\":0}");
        assertThat(poller.query("P1")).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("state=1 返回 SUCCESS")
    void stateOneIsSuccess() {
        when(http.postForm(any(), any())).thenReturn("{\"state\":1}");
        assertThat(poller.query("P1")).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("空响应返回 UNKNOWN")
    void emptyBodyIsUnknown() {
        when(http.postForm(any(), any())).thenReturn("");
        assertThat(poller.query("P1")).isEqualTo(PaymentStatus.UNKNOWN);
    }
}

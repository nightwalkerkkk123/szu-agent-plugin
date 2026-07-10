package edu.szu.agent.client.http;

import edu.szu.agent.retry.RetryPolicies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EhallSessionManager")
class EhallSessionManagerTest {

    @Test
    @DisplayName("reuses persisted jar when session probe returns JSON array")
    void reusesValidPersistedJar() {
        CampusHttpClient http = mock(CampusHttpClient.class);
        CookieJar jar = new CookieJar();
        CampusHttpClientFactory httpFactory = (j, t) -> http;

        when(http.postForm(any(), any(), any(), any())).thenReturn("[\"2026-07-11\"]");
        when(http.cookieJar()).thenReturn(jar);

        EhallSessionManager manager = new EhallSessionManager(
            "2023150090", "secret", true,
            RetryPolicies.quickFix(), CasLoginClientFactory.DEFAULT, httpFactory);

        CampusHttpClient result = manager.ensureSession(jar);

        assertThat(result).isSameAs(http);
        verify(http, never()).get(any());
    }

    @Test
    @DisplayName("primes session via CAS entry when persisted jar is stale")
    void primesStaleJar() {
        CampusHttpClient http = mock(CampusHttpClient.class);
        CookieJar jar = new CookieJar(List.of(
            new CookieJar.Cookie("CASTGC", "TGT-123", "authserver.szu.edu.cn", "/", null, true, true, -1)
        ));
        CampusHttpClientFactory httpFactory = (j, t) -> http;

        when(http.postForm(any(), any(), any(), any()))
            .thenReturn("<html>login</html>")  // first probe fails
            .thenReturn("[\"2026-07-11\"]");   // second probe succeeds after priming
        when(http.cookieJar()).thenReturn(jar);

        EhallSessionManager manager = new EhallSessionManager(
            "2023150090", "secret", true,
            RetryPolicies.quickFix(), CasLoginClientFactory.DEFAULT, httpFactory);

        CampusHttpClient result = manager.ensureSession(jar);

        assertThat(result).isSameAs(http);
        verify(http).get("https://ehall.szu.edu.cn/login?service=https%3A%2F%2Fehall.szu.edu.cn%2Fqljfwapp%2Fsys%2FlwSzuCgyy%2Findex.do");
    }
}

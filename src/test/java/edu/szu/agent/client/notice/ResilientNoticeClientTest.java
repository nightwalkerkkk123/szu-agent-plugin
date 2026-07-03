package edu.szu.agent.client.notice;

import edu.szu.agent.domain.notice.NoticeListResult;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResilientNoticeClient} — covers the dynamic
 * "real with static fallback" routing defined by
 * PLAN-p1-real-fetch.md §5 阶段 2.
 *
 * <p>// 编程技术: JUnit 5 / AssertJ / sealed type pattern matching
 *
 * @since 0.6.0
 * @author 王子豪
 */
@DisplayName("ResilientNoticeClient")
class ResilientNoticeClientTest {

    private static final String FALLBACK_HTML = """
        <html><body>
        <fieldset><legend><a href="./infolist.asp?infotype=讲座"><strong><font>学术讲座</font></strong></a></legend>
        <table>
        <tr><td><a title="深大讲坛" href="view.asp?id=1">深大讲坛</a></td><td>6/1 8:30</td></tr>
        </table>
        </fieldset>
        </body></html>
        """;

    @Test
    @DisplayName("null real client → uses static fallback directly")
    void nullRealClientUsesStaticFallback() {
        NoticeListClient fallback = new NoticeListClient(FALLBACK_HTML, 2026);
        ResilientNoticeClient client = new ResilientNoticeClient(null, fallback);

        NoticeListResult result = client.list();

        assertThat(result).isInstanceOf(NoticeListResult.Success.class);
        assertThat(((NoticeListResult.Success) result).notices()).hasSize(1);
    }

    @Test
    @DisplayName("real client returns Success → uses real result")
    void realSuccessReturnsRealResult() {
        String realHtml = """
            <html><body>
            <fieldset><legend><a href="./infolist.asp?infotype=讲座"><strong><font>学术讲座</font></strong></a></legend>
            <table>
            <tr><td><a title="真实讲座A" href="view.asp?id=1">真实讲座A</a></td><td>6/1</td></tr>
            <tr><td><a title="真实讲座B" href="view.asp?id=2">真实讲座B</a></td><td>6/2</td></tr>
            </table>
            </fieldset>
            </body></html>
            """;
        NoticeListClient real = new NoticeListClient(new InMemoryProvider(realHtml), FALLBACK_HTML, 2026);
        NoticeListClient fallback = new NoticeListClient(FALLBACK_HTML, 2026);
        ResilientNoticeClient client = new ResilientNoticeClient(real, fallback);

        NoticeListResult result = client.list();

        // Parser sorts by publishedAt descending; 6/2 (B) comes first.
        assertThat(result).isInstanceOf(NoticeListResult.Success.class);
        assertThat(((NoticeListResult.Success) result).notices()).hasSize(2);
        assertThat(((NoticeListResult.Success) result).notices().get(0).title())
            .isEqualTo("真实讲座B");
        assertThat(((NoticeListResult.Success) result).notices().get(1).title())
            .isEqualTo("真实讲座A");
    }

    @Test
    @DisplayName("real provider throws → falls back to static")
    void realProviderThrowsFallsBackToStatic() {
        NoticeFetchProvider throwing = () -> {
            throw new NoticeFetchException(ErrorCode.NOTICE_FETCH_FAILED, "simulated network");
        };
        NoticeListClient real = new NoticeListClient(throwing, FALLBACK_HTML, 2026);
        NoticeListClient fallback = new NoticeListClient(FALLBACK_HTML, 2026);
        ResilientNoticeClient client = new ResilientNoticeClient(real, fallback);

        NoticeListResult result = client.list();

        // The NoticeListClient swallows the provider exception internally
        // and returns the static snapshot as Success. The wrapper then
        // sees Success and returns it. (The wrapper's catch-RuntimeException
        // path is a defensive backstop for unexpected client failures.)
        assertThat(result).isInstanceOf(NoticeListResult.Success.class);
        assertThat(((NoticeListResult.Success) result).notices()).hasSize(1);
    }

    @Test
    @DisplayName("real client throws RuntimeException → wrapper catches and falls back")
    void realClientThrowsFallsBack() {
        NoticeListClient real = new NoticeListClient() {
            @Override
            public NoticeListResult list() {
                throw new IllegalStateException("simulated client boom");
            }
        };
        NoticeListClient fallback = new NoticeListClient(FALLBACK_HTML, 2026);
        ResilientNoticeClient client = new ResilientNoticeClient(real, fallback);

        NoticeListResult result = client.list();

        assertThat(result).isInstanceOf(NoticeListResult.Success.class);
        assertThat(((NoticeListResult.Success) result).notices()).hasSize(1);
    }

    @Test
    @DisplayName("real returns Failure (parse error) → wrapper falls back to static")
    void realFailureFallsBackToStatic() {
        NoticeListClient real = new NoticeListClient() {
            @Override
            public NoticeListResult list() {
                return new NoticeListResult.Failure(
                    ErrorCode.NOTICE_FETCH_FAILED, "simulated parse fail");
            }
        };
        NoticeListClient fallback = new NoticeListClient(FALLBACK_HTML, 2026);
        ResilientNoticeClient client = new ResilientNoticeClient(real, fallback);

        NoticeListResult result = client.list();

        assertThat(result).isInstanceOf(NoticeListResult.Success.class);
        assertThat(((NoticeListResult.Success) result).notices()).hasSize(1);
    }

    /** In-memory provider for tests — no Playwright, no network. */
    private static final class InMemoryProvider implements NoticeFetchProvider {
        private final String html;
        InMemoryProvider(String html) { this.html = html; }
        @Override public String fetchHtml() { return html; }
    }
}

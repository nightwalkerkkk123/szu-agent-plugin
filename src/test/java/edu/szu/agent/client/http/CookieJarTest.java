package edu.szu.agent.client.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CookieJar}.
 *
 * @since 0.6.0
 * @author 王子豪
 */
class CookieJarTest {

    @Test
    void storesAndReturnsSimpleCookie() {
        CookieJar jar = new CookieJar();
        URI source = URI.create("https://auth.szu.edu.cn/cas/login");
        jar.storeFromResponse(source, List.of("CASTGC=TGT-123; Path=/cas; Secure; HttpOnly"));

        String header = jar.cookieHeaderFor(URI.create("https://auth.szu.edu.cn/cas/serviceValidate"));
        assertThat(header).isEqualTo("CASTGC=TGT-123");
    }

    @Test
    void doesNotMatchAcrossDomains() {
        CookieJar jar = new CookieJar();
        jar.storeFromResponse(URI.create("https://auth.szu.edu.cn/cas/login"),
            List.of("session=abc; Path=/"));

        assertThat(jar.cookieHeaderFor(URI.create("https://ehall.szu.edu.cn/"))).isNull();
    }

    @Test
    void respectsPathPrefix() {
        CookieJar jar = new CookieJar();
        jar.storeFromResponse(URI.create("https://auth.szu.edu.cn/cas/login"),
            List.of("ticket=ST-123; Path=/cas"));

        assertThat(jar.cookieHeaderFor(URI.create("https://auth.szu.edu.cn/cas/validate")))
            .isEqualTo("ticket=ST-123");
        assertThat(jar.cookieHeaderFor(URI.create("https://auth.szu.edu.cn/other"))).isNull();
    }

    @Test
    void multipleCookiesJoinedWithSemicolon() {
        CookieJar jar = new CookieJar();
        URI source = URI.create("https://auth.szu.edu.cn/");
        jar.storeFromResponse(source, List.of(
            "a=1; Path=/",
            "b=2; Path=/"
        ));

        String header = jar.cookieHeaderFor(URI.create("https://auth.szu.edu.cn/page"));
        assertThat(header).contains("a=1").contains("b=2").contains("; ");
    }

    @Test
    void clearRemovesAllCookies() {
        CookieJar jar = new CookieJar();
        jar.storeFromResponse(URI.create("https://auth.szu.edu.cn/"), List.of("x=1"));
        jar.clear();
        assertThat(jar.snapshot()).isEmpty();
    }

    @Test
    void loadFromSnapshotReplacesCookies() {
        CookieJar jar = new CookieJar();
        jar.storeFromResponse(URI.create("https://auth.szu.edu.cn/"),
            List.of("old=1; Path=/"));

        CookieJar.Cookie cookie = jar.snapshot().get(0);
        CookieJar restored = new CookieJar(List.of(cookie));

        assertThat(restored.cookieHeaderFor(URI.create("https://auth.szu.edu.cn/page")))
            .isEqualTo("old=1");
        assertThat(restored.snapshot()).hasSize(1);
    }
}

package edu.szu.agent.client.session;

import edu.szu.agent.client.http.CookieJar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HttpSession")
class HttpSessionTest {

    @Test
    @DisplayName("写入后读取保留用户名、保存时间和 cookies")
    void roundTripPreservesState(@TempDir Path tmp) throws Exception {
        CookieJar jar = new CookieJar();
        jar.storeFromResponse(URI.create("https://authserver.szu.edu.cn/"),
            List.of("CASTGC=TGT-123; Path=/; Secure; HttpOnly"));

        SessionStore store = new SessionStore(tmp, "2023150090");
        HttpSession.write(store, jar);

        assertThat(store.exists()).isTrue();

        HttpSession loaded = HttpSession.read(store);
        assertThat(loaded.username()).isEqualTo("2023150090");
        assertThat(loaded.savedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(loaded.cookies()).hasSize(1);

        CookieJar restored = new CookieJar(loaded.cookies());
        assertThat(restored.cookieHeaderFor(URI.create("https://authserver.szu.edu.cn/profile")))
            .isEqualTo("CASTGC=TGT-123");
    }

    @Test
    @DisplayName("JSON 输出不包含密码字段")
    void jsonDoesNotContainPassword(@TempDir Path tmp) throws Exception {
        CookieJar jar = new CookieJar();
        jar.storeFromResponse(URI.create("https://www1.szu.edu.cn/"),
            List.of("ASPSESSIONID=abc123; Path=/"));

        SessionStore store = new SessionStore(tmp, "u");
        HttpSession.write(store, jar);

        String json = Files.readString(store.defaultPath());
        assertThat(json).doesNotContain("password", "pwd", "secret");
    }

    @Test
    @DisplayName("读取缺失文件抛出 IOException")
    void readMissingFileThrows(@TempDir Path tmp) {
        SessionStore store = new SessionStore(tmp, "u");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> HttpSession.read(store))
            .isInstanceOf(java.io.IOException.class);
    }
}

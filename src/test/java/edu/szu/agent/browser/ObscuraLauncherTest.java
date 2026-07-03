package edu.szu.agent.browser;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ObscuraLauncher}.
 *
 * <p>These tests avoid launching the real Rust binary. They exercise the
 * daemon probe and path layout seams that are deterministic inside JUnit.
 *
 * @since 0.6.0
 * @author 王子豪
 */
@DisplayName("ObscuraLauncher")
class ObscuraLauncherTest {

    @Test
    @DisplayName("isRunning(uri) returns true for a healthy /json/version endpoint")
    void isRunningReturnsTrueForHealthyEndpoint() throws Exception {
        try (TestHttpServer server = TestHttpServer.start(200, "{}")) {
            assertThat(ObscuraLauncher.isRunning(server.uri())).isTrue();
        }
    }

    @Test
    @DisplayName("isRunning(uri) returns false when endpoint is unavailable")
    void isRunningReturnsFalseWhenEndpointUnavailable() {
        URI unusedPort = URI.create("http://127.0.0.1:9/json/version");
        assertThat(ObscuraLauncher.isRunning(unusedPort)).isFalse();
    }

    @Test
    @DisplayName("ensureRunning(home, uri) uses existing daemon without extracting binaries")
    void ensureRunningUsesExistingDaemon(@TempDir Path home) throws Exception {
        try (TestHttpServer server = TestHttpServer.start(200, "{}")) {
            ObscuraLauncher.ensureRunning(home, server.uri());

            assertThat(ObscuraLauncher.binaryPath(home)).doesNotExist();
            assertThat(ObscuraLauncher.pidFile(home)).doesNotExist();
        }
    }

    @Test
    @DisplayName("binaryPath(home) resolves under .szu-agent/bin")
    void binaryPathResolvesUnderSzuAgentBin(@TempDir Path home) {
        assertThat(ObscuraLauncher.binaryPath(home))
            .isEqualTo(home.resolve(".szu-agent/bin").resolve(binaryName()));
    }

    @Test
    @DisplayName("pidFile(home) resolves under .szu-agent")
    void pidFileResolvesUnderSzuAgent(@TempDir Path home) {
        assertThat(ObscuraLauncher.pidFile(home))
            .isEqualTo(home.resolve(".szu-agent/obscura.pid"));
    }

    @Test
    @DisplayName("hasExecutableMagic accepts PE / ELF / Mach-O magic and rejects junk")
    void hasExecutableMagicAcceptsRealBinariesAndRejectsJunk(@TempDir Path home) throws IOException {
        // PE / "MZ" — Windows
        Path pe = home.resolve("obscura.exe");
        Files.write(pe, new byte[]{(byte) 0x4D, (byte) 0x5A, (byte) 0x90, 0x00});
        assertThat(ObscuraLauncher.hasExecutableMagic(pe)).isTrue();

        // ELF — Linux
        Path elf = home.resolve("obscura-elf");
        Files.write(elf, new byte[]{(byte) 0x7F, (byte) 'E', (byte) 'L', (byte) 'F'});
        assertThat(ObscuraLauncher.hasExecutableMagic(elf)).isTrue();

        // Mach-O (LE) — macOS
        Path machoLe = home.resolve("obscura-macho");
        Files.write(machoLe, new byte[]{(byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE});
        assertThat(ObscuraLauncher.hasExecutableMagic(machoLe)).isTrue();

        // Same-size junk: a same-size file that is NOT a real binary
        Path junk = home.resolve("obscura-junk");
        int realSize = (int) Files.size(pe);
        byte[] junkBytes = new byte[realSize];
        java.util.Arrays.fill(junkBytes, (byte) 'X');
        Files.write(junk, junkBytes);
        assertThat(junk).hasSize(Files.size(pe));
        assertThat(ObscuraLauncher.hasExecutableMagic(junk)).isFalse();

        // Empty / too-short file
        Path empty = home.resolve("obscura-empty");
        Files.write(empty, new byte[0], StandardOpenOption.CREATE);
        assertThat(ObscuraLauncher.hasExecutableMagic(empty)).isFalse();
    }

    private static String binaryName() {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
            ? "obscura.exe"
            : "obscura";
    }

    private record TestHttpServer(HttpServer server, URI uri) implements AutoCloseable {

        static TestHttpServer start(int status, String body) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            server.createContext("/json/version", exchange -> {
                exchange.sendResponseHeaders(status, bytes.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            server.start();
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/json/version");
            return new TestHttpServer(server, uri);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}

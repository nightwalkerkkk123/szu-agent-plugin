package edu.szu.agent.config;

import edu.szu.agent.browser.BrowserLifecycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ConfigManager}.
 *
 * <p>Per ADR-0005 D1 + ADR-0007 D1: three-layer config (process env > {@code --env-file}
 * > application.yml) with {@link #browserKind()} dispatching the
 * {@link BrowserLifecycle} factory.
 *
 * <p>Singleton tests use {@link ConfigManager#getInstance()} and a
 * {@code @BeforeEach reset()} to isolate state across tests.
 *
 * @since 0.6.0
 * @author 王子豪
 */
class ConfigManagerTest {

    @BeforeEach
    void resetSingleton() {
        ConfigManager.getInstance().reset();
    }

    @AfterEach
    void cleanupSingleton() {
        ConfigManager.getInstance().reset();
    }

    // ---------- singleton ----------

    @Test
    @DisplayName("getInstance() returns the same instance on repeated calls")
    void getInstance_returnsSameInstance() {
        ConfigManager a = ConfigManager.getInstance();
        ConfigManager b = ConfigManager.getInstance();
        assertThat(a).isSameAs(b);
    }

    // ---------- YAML loading ----------

    @Test
    @DisplayName("load() reads application.yml from the classpath")
    void load_readsYmlFromClasspath() {
        ConfigManager.getInstance().load();
        assertThat(ConfigManager.getInstance().get("test.fixture"))
            .isEqualTo("test-fixture-value");
    }

    @Test
    @DisplayName("load() flattens nested YAML keys with dot notation")
    void load_flattensNestedKeys() {
        ConfigManager.getInstance().load();
        assertThat(ConfigManager.getInstance().get("test.nested.deep"))
            .isEqualTo("deep-value");
    }

    @Test
    @DisplayName("browserKind() returns the configured browser.kind value")
    void browserKind_returnsConfigured() {
        ConfigManager.getInstance().load();
        assertThat(ConfigManager.getInstance().browserKind()).isEqualTo("OBSCURA");
    }

    @Test
    @DisplayName("cacheStore() resolves ${user.home} placeholder to user.home")
    void cacheStore_resolvesUserHomePlaceholder() {
        ConfigManager config = ConfigManager.getInstance();
        config.setYamlProperty("cache.home", "${user.home}");
        config.setYamlProperty("cache.ttl.schedule", "PT24H");

        assertThat(config.cacheStore().defaultPath("schedule", "k"))
            .isEqualTo(Path.of(System.getProperty("user.home"))
                .resolve(".szu-agent/cache/schedule/k.json"));
    }

    @Test
    @DisplayName("get() returns null for keys not present in any layer")
    void get_returnsNullForMissingKey() {
        ConfigManager.getInstance().load();
        assertThat(ConfigManager.getInstance().get("no.such.key")).isNull();
    }

    // ---------- env file (--env-file) ----------

    @Test
    @DisplayName("loadEnvFile() loads key=value pairs from a dotenv file")
    void loadEnvFile_loadsFromDotenv(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
            DB_HOST=localhost
            DB_PORT=5432
            """);

        ConfigManager.getInstance().load();
        ConfigManager.getInstance().loadEnvFile(envFile);

        assertThat(ConfigManager.getInstance().get("DB_HOST")).isEqualTo("localhost");
        assertThat(ConfigManager.getInstance().get("DB_PORT")).isEqualTo("5432");
    }

    @Test
    @DisplayName("loadEnvFile() ignores comment lines and blank lines")
    void loadEnvFile_ignoresCommentsAndBlanks(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
            # this is a comment
            KEY1=value1

            KEY2=value2
            """);

        ConfigManager.getInstance().loadEnvFile(envFile);
        assertThat(ConfigManager.getInstance().get("KEY1")).isEqualTo("value1");
        assertThat(ConfigManager.getInstance().get("KEY2")).isEqualTo("value2");
    }

    @Test
    @DisplayName("loadEnvFile() values override YAML values for the same key")
    void loadEnvFile_overridesYaml() throws IOException {
        // application.yml has test.fixture = "test-fixture-value"
        // env file sets the same key differently
        Path envFile = Files.createTempFile("overrides", ".env");
        Files.writeString(envFile, "test.fixture=overridden-by-env-file\n");

        try {
            ConfigManager.getInstance().load();
            ConfigManager.getInstance().loadEnvFile(envFile);
            assertThat(ConfigManager.getInstance().get("test.fixture"))
                .isEqualTo("overridden-by-env-file");
        } finally {
            Files.deleteIfExists(envFile);
        }
    }

    // ---------- three-layer priority ----------

    @Test
    @DisplayName("get() priority: process env > env file > YAML (env file beats YAML)")
    void priority_envFileBeatsYaml() {
        ConfigManager config = ConfigManager.getInstance();
        config.setYamlProperty("k", "yaml-value");
        config.setEnvFileProperty("k", "envfile-value");
        // process env empty
        assertThat(config.get("k", Map.of())).isEqualTo("envfile-value");
    }

    @Test
    @DisplayName("get() priority: process env wins over env file")
    void priority_processEnvBeatsEnvFile() {
        ConfigManager config = ConfigManager.getInstance();
        config.setYamlProperty("k", "yaml-value");
        config.setEnvFileProperty("k", "envfile-value");
        assertThat(config.get("k", Map.of("k", "process-env-value")))
            .isEqualTo("process-env-value");
    }

    @Test
    @DisplayName("get() falls back to YAML when env layers are empty")
    void priority_yamlAsFallback() {
        ConfigManager config = ConfigManager.getInstance();
        config.setYamlProperty("k", "yaml-value");
        assertThat(config.get("k", Map.of())).isEqualTo("yaml-value");
    }

    @Test
    @DisplayName("get() returns null when all three layers are empty")
    void priority_returnsNullWhenAllEmpty() {
        ConfigManager.getInstance();
        assertThat(ConfigManager.getInstance().get("missing", Map.of())).isNull();
    }

    // ---------- browser() factory ----------

    @Test
    @DisplayName("browser() throws when browser.kind is not configured")
    void browser_throwsWhenKindNotConfigured() {
        // No load() — yamlProps empty
        assertThatThrownBy(() -> ConfigManager.getInstance().browser())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("browser.kind");
    }

    @Test
    @DisplayName("browser() throws for unknown browser.kind values")
    void browser_throwsForUnknownKind() {
        ConfigManager config = ConfigManager.getInstance();
        config.setYamlProperty("browser.kind", "WEBDRIVER");
        assertThatThrownBy(() -> config.browser())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("WEBDRIVER");
    }

    @Test
    @DisplayName("browser() throws for FAKE kind (FakeBrowser not yet implemented)")
    void browser_throwsForFake() {
        ConfigManager config = ConfigManager.getInstance();
        config.setYamlProperty("browser.kind", "FAKE");
        assertThatThrownBy(() -> config.browser())
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("FakeBrowser");
    }

    // ---------- reset() ----------

    @Test
    @DisplayName("reset() clears all loaded state")
    void reset_clearsState() {
        ConfigManager config = ConfigManager.getInstance();
        config.load();
        assertThat(config.get("test.fixture")).isEqualTo("test-fixture-value");

        config.reset();

        assertThat(config.get("test.fixture")).isNull();
        assertThat(config.browserKind()).isNull();
    }
}

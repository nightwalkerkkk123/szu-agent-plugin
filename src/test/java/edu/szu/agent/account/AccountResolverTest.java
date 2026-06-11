package edu.szu.agent.account;

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
 * Tests for {@link AccountResolver}.
 *
 * <p>Per ADR-0005 D1: three-layer resolution —
 * process env &gt; {@code --env-file} &gt; Skill injection.
 * The resolver reads {@code SZU_PASSWORD_<studentId>} from env vars
 * or the env file.
 *
 * @since 0.1.0
 * @author 王子豪
 */
class AccountResolverTest {

    @BeforeEach
    void resetConfig() {
        // Ensure clean state
    }

    // ---------- success: process env ----------

    @Test
    @DisplayName("resolve() finds password from process environment variable")
    void resolveFromProcessEnv() {
        String studentId = "2023150090";
        Map<String, String> env = Map.of("SZU_PASSWORD_2023150090", "mypassword");

        Account account = AccountResolver.resolve(studentId, env);

        assertThat(account.studentId()).isEqualTo(studentId);
        assertThat(account.password()).isEqualTo("mypassword");
    }

    @Test
    @DisplayName("resolve() uses env var even when env file has a different value")
    void envVarTakesPrecedenceOverEnvFile(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "SZU_PASSWORD_2023150090=file-password\n");

        Map<String, String> env = Map.of("SZU_PASSWORD_2023150090", "env-password");

        Account account = AccountResolver.resolve("2023150090", env);

        assertThat(account.password()).isEqualTo("env-password");
    }

    // ---------- success: env file ----------

    @Test
    @DisplayName("resolve() finds password from env file when process env has no match")
    void resolveFromEnvFile(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "SZU_PASSWORD_2023150090=file-password\n");

        Account account = AccountResolver.resolve("2023150090", Map.of(), envFile);

        assertThat(account.password()).isEqualTo("file-password");
    }

    // ---------- failure: not found ----------

    @Test
    @DisplayName("resolve() throws when password not found in any layer")
    void throwsWhenNotFound() {
        assertThatThrownBy(() -> AccountResolver.resolve("9999999999", Map.of()))
            .isInstanceOf(AccountResolutionException.class)
            .hasMessageContaining("9999999999");
    }

    // ---------- env var name format ----------

    @Test
    @DisplayName("env var name follows SZU_PASSWORD_<studentId> convention")
    void envVarNameFormat() {
        String name = AccountResolver.envVarName("2023150090");
        assertThat(name).isEqualTo("SZU_PASSWORD_2023150090");
    }
}

package edu.szu.agent.account;

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
 * <p>Covers the three-layer credential lookup per ADR-0005 D1:
 * process env &gt; {@code --env-file} &gt; Skill injection (P1, untested).
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("AccountResolver")
class AccountResolverTest {

    private static final String STUDENT_ID = "2023150090";
    private static final String PASSWORD = "secret-from-env";

    // ---------- Layer 1: process env ----------

    @Test
    @DisplayName("resolve() returns Account when credential is in process env")
    void resolveFromProcessEnv() {
        Map<String, String> env = Map.of(
            AccountResolver.envVarName(STUDENT_ID), PASSWORD);

        Account account = AccountResolver.resolve(STUDENT_ID, env);

        assertThat(account.studentId()).isEqualTo(STUDENT_ID);
        assertThat(account.password()).isEqualTo(PASSWORD);
        assertThat(account.displayName()).isEqualTo(STUDENT_ID);
    }

    // ---------- Layer 2: --env-file fallback ----------

    @Test
    @DisplayName("resolve() falls back to env-file when process env misses")
    void resolveFromEnvFile(@TempDir Path tmp) throws IOException {
        Path envFile = tmp.resolve(".env");
        Files.writeString(envFile,
            AccountResolver.envVarName(STUDENT_ID) + "=" + PASSWORD + "\n");
        Map<String, String> emptyEnv = Map.of();

        Account account = AccountResolver.resolve(STUDENT_ID, emptyEnv, envFile);

        assertThat(account.studentId()).isEqualTo(STUDENT_ID);
        assertThat(account.password()).isEqualTo(PASSWORD);
    }

    @Test
    @DisplayName("process env takes precedence over env-file when both are set")
    void processEnvBeatsEnvFile(@TempDir Path tmp) throws IOException {
        Path envFile = tmp.resolve(".env");
        Files.writeString(envFile,
            AccountResolver.envVarName(STUDENT_ID) + "=from-file\n");
        Map<String, String> env = Map.of(
            AccountResolver.envVarName(STUDENT_ID), "from-process");

        Account account = AccountResolver.resolve(STUDENT_ID, env, envFile);

        assertThat(account.password()).isEqualTo("from-process");
    }

    // ---------- Layer 3: not found ----------

    @Test
    @DisplayName("resolve() throws AccountResolutionException when no source has the credential")
    void resolveMissesThrows(@TempDir Path tmp) throws IOException {
        Path envFile = tmp.resolve(".env");
        Files.writeString(envFile, "SOME_OTHER_KEY=foo\n");
        Map<String, String> emptyEnv = Map.of();

        assertThatThrownBy(() -> AccountResolver.resolve(STUDENT_ID, emptyEnv, envFile))
            .isInstanceOf(AccountResolutionException.class)
            .hasMessageContaining(STUDENT_ID)
            .extracting("studentId").isEqualTo(STUDENT_ID);
    }

    // ---------- Layer 3: Skill injection ----------

    @BeforeEach
    void resetSkillInjection() {
        AccountResolver.resetSkillInjected();
    }

    @Test
    @DisplayName("Layer 3: Skill-injected credential is consumed when env layers miss")
    void resolveFromSkillInjection() {
        char[] pwd = "skill-secret".toCharArray();
        AccountResolver.injectCredential(STUDENT_ID, pwd);

        Account account = AccountResolver.resolve(STUDENT_ID, Map.of());

        assertThat(account.studentId()).isEqualTo(STUDENT_ID);
        assertThat(account.password()).isEqualTo("skill-secret");
        assertThat(account.displayName()).isEqualTo(STUDENT_ID);
    }

    @Test
    @DisplayName("Layer 3: injected char[] buffer is zeroed after consume")
    void injectedCredentialIsZeroedAfterConsume() {
        char[] pwd = "skill-secret".toCharArray();
        AccountResolver.injectCredential(STUDENT_ID, pwd);

        AccountResolver.resolve(STUDENT_ID, Map.of());

        assertThat(pwd).containsOnly('\0');
    }

    @Test
    @DisplayName("Layer 3: process env takes precedence over Skill injection")
    void skillInjectionDoesNotOverrideEnv() {
        char[] pwd = "from-skill".toCharArray();
        AccountResolver.injectCredential(STUDENT_ID, pwd);
        Map<String, String> env = Map.of(
            AccountResolver.envVarName(STUDENT_ID), "from-env");

        Account account = AccountResolver.resolve(STUDENT_ID, env);

        assertThat(account.password()).isEqualTo("from-env");
        // Skill buffer was not consumed (env won); char[] is still intact
        assertThat(pwd).containsExactly('f', 'r', 'o', 'm', '-', 's', 'k', 'i', 'l', 'l');
    }

    @Test
    @DisplayName("Layer 3: clearInjectedCredential is idempotent")
    void clearInjectedCredentialIsIdempotent() {
        char[] pwd = "skill-secret".toCharArray();
        AccountResolver.injectCredential(STUDENT_ID, pwd);

        AccountResolver.clearInjectedCredential(STUDENT_ID);
        AccountResolver.clearInjectedCredential(STUDENT_ID);  // no-op, no throw

        assertThatThrownBy(() -> AccountResolver.resolve(STUDENT_ID, Map.of()))
            .isInstanceOf(AccountResolutionException.class);
    }

    // ---------- envVarName convention ----------

    @Test
    @DisplayName("envVarName() returns SZU_PASSWORD_<id> convention")
    void envVarNameConvention() {
        assertThat(AccountResolver.envVarName("2023150090"))
            .isEqualTo("SZU_PASSWORD_2023150090");
    }
}

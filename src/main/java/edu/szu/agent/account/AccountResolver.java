package edu.szu.agent.account;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Credential resolver — three-layer lookup per ADR-0005 D1.
 *
 * <p>Resolution order (highest priority first):
 * <ol>
 *   <li>Process environment variables ({@code SZU_PASSWORD_<studentId>})</li>
 *   <li>{@code --env-file} contents (dotenv format)</li>
 *   <li>Skill injection (future P1, not yet implemented)</li>
 * </ol>
 *
 * <p>Programming techniques: method overloading (two resolve signatures),
 * immutable return type (record), enum-based env var naming convention.
 *
 * // 编程技术: 重载 / record / 枚举工厂
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class AccountResolver {

    private static final Logger log = LoggerFactory.getLogger(AccountResolver.class);

    public static final String ENV_PREFIX = "SZU_PASSWORD_";

    private AccountResolver() {
    }

    /**
     * Resolves credentials using only process environment variables.
     *
     * @param studentId the student ID to look up
     * @param env       process environment map (usually {@code System.getenv()})
     * @return the resolved Account
     * @throws AccountResolutionException if no credential found
     */
    public static Account resolve(String studentId, Map<String, String> env) {
        return resolve(studentId, env, null);
    }

    /**
     * Resolves credentials using the current process environment.
     *
     * <p>Convenience overload for callers (such as {@link edu.szu.agent.task.BookingTask})
     * that do not already hold an env map. Keeps {@code System.getenv()} inside
     * this class so ArchUnit rules remain satisfied.
     *
     * @param studentId the student ID to look up
     * @return the resolved Account
     * @throws AccountResolutionException if no credential found
     */
    public static Account resolve(String studentId) {
        return resolve(studentId, System.getenv());
    }

    /**
     * Resolves credentials using process env, then env file as fallback.
     *
     * @param studentId the student ID to look up
     * @param env       process environment map
     * @param envFile   optional path to a dotenv file (null = skip)
     * @return the resolved Account
     * @throws AccountResolutionException if no credential found in any layer
     */
    public static Account resolve(String studentId, Map<String, String> env, Path envFile) {
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(env, "env map must not be null");

        String key = envVarName(studentId);

        // Layer 1: process environment
        String fromEnv = env.get(key);
        if (fromEnv != null) {
            log.info("Resolved credential for {} from process env", studentId);
            return new Account(studentId, fromEnv, studentId);
        }

        // Layer 2: --env-file
        if (envFile != null && Files.exists(envFile)) {
            Path absoluteEnv = envFile.toAbsolutePath();
            Dotenv dotenv = Dotenv.configure()
                .filename(absoluteEnv.getFileName().toString())
                .directory(absoluteEnv.getParent().toString())
                .load();
            String fromFile = dotenv.get(key);
            if (fromFile != null) {
                log.info("Resolved credential for {} from env file", studentId);
                return new Account(studentId, fromFile, studentId);
            }
        }

        // Layer 3: Skill injection (P1, not yet implemented)

        throw new AccountResolutionException(studentId);
    }

    /**
     * Returns the environment variable name for a given student ID.
     * Convention: {@code SZU_PASSWORD_<studentId>}.
     *
     * @param studentId the student ID
     * @return the env var name (e.g. "SZU_PASSWORD_2023150090")
     */
    public static String envVarName(String studentId) {
        return ENV_PREFIX + studentId;
    }
}

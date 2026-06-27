package edu.szu.agent.account;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Credential resolver — three-layer lookup per ADR-0005 D1.
 *
 * <p>Resolution order (highest priority first):
 * <ol>
 *   <li>Process environment variables ({@code SZU_PASSWORD_<studentId>})</li>
 *   <li>{@code --env-file} contents (dotenv format)</li>
 *   <li>Skill injection (in-process, consume-once, zero-on-consume)</li>
 * </ol>
 *
 * <p>Programming techniques: method overloading (three resolve signatures),
 * immutable return type (record), enum-based env var naming convention.
 *
 * // 编程技术: 重载 / record / 枚举工厂 / char[]+Arrays.fill(密码可擦除) / ConcurrentHashMap
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class AccountResolver {

    private static final Logger log = LoggerFactory.getLogger(AccountResolver.class);

    public static final String ENV_PREFIX = "SZU_PASSWORD_";

    /**
     * In-process credential injection map for Skill authors. Keyed by
     * student ID; value is the password as a {@code char[]} so it can be
     * zeroed after consume (avoids lingering heap retention).
     *
     * <p>Process-internal only — never written to disk, log, or env.
     *
     * @since 0.5.0
     */
    private static final ConcurrentHashMap<String, char[]> SKILL_INJECTED =
        new ConcurrentHashMap<>();

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

        // Layer 3: Skill injection (consume-once, zero-on-consume)
        char[] injected = SKILL_INJECTED.remove(studentId);
        if (injected != null) {
            try {
                String fromSkill = new String(injected);
                log.info("Resolved credential for {} from Skill injection", studentId);
                return new Account(studentId, fromSkill, studentId);
            } finally {
                Arrays.fill(injected, '\0');
            }
        }

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

    /**
     * Injects a credential into the in-process Skill channel. Ownership of
     * the {@code password} buffer transfers to the resolver — it will be
     * zeroed (via {@code Arrays.fill}) when consumed by the next
     * {@link #resolve(String, Map, Path)} call that reaches Layer 3, or
     * when {@link #clearInjectedCredential(String)} is called.
     *
     * <p>Callers should NOT zero or reuse {@code password} after calling
     * this method. Callers should also wrap their {@code resolve()} call
     * in a {@code try/finally} that invokes
     * {@link #clearInjectedCredential(String)} as a safety net for the
     * case where the credential was already provided via env (so Layer 3
     * was never consulted and the buffer would otherwise linger).
     *
     * <p>Why {@code char[]} and not {@code String}: a {@code char[]} can
     * be zeroed immediately after use, shortening the window during which
     * the password is recoverable from a heap dump. A {@code String} is
     * immutable and cannot be erased.
     *
     * @param studentId the student ID; must not be null
     * @param password  the password buffer; ownership transfers to the
     *                  resolver (will be zeroed on consume or clear)
     * @since 0.5.0
     * @author 王子豪
     */
    public static void injectCredential(String studentId, char[] password) {
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(password, "password must not be null");
        // No defensive copy: ownership transfers. This lets the resolver
        // zero the same buffer the caller passed in, which is the whole
        // point of using char[] instead of String.
        SKILL_INJECTED.put(studentId, password);
    }

    /**
     * Removes a previously-injected Skill credential without resolving.
     * Idempotent — calling on an absent key is a no-op (no exception).
     * The internal buffer, if present, is zeroed before being dropped.
     *
     * @param studentId the student ID
     * @since 0.5.0
     * @author 王子豪
     */
    public static void clearInjectedCredential(String studentId) {
        char[] removed = SKILL_INJECTED.remove(studentId);
        if (removed != null) {
            Arrays.fill(removed, '\0');
        }
    }

    /**
     * Clears all Skill-injected credentials. Intended for test isolation
     * (called from {@code @BeforeEach}); not for production use.
     *
     * @since 0.5.0
     * @author 王子豪
     */
    static void resetSkillInjected() {
        for (char[] buf : SKILL_INJECTED.values()) {
            Arrays.fill(buf, '\0');
        }
        SKILL_INJECTED.clear();
    }
}

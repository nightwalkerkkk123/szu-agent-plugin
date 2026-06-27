package edu.szu.agent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.microsoft.playwright.Playwright;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.browser.PlaywrightBrowserAdapter;
import edu.szu.agent.client.cache.CacheStore;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Centralized configuration — singleton with three-layer lookup.
 *
 * <p>Per ADR-0005 D1: precedence for {@link #get(String)} is
 * <ol>
 *   <li>Process environment variables (highest — set by CI / container)</li>
 *   <li>{@code --env-file} contents (loaded via {@link #loadEnvFile(Path)})</li>
 *   <li>{@code application.yml} on the classpath (lowest — sane defaults)</li>
 * </ol>
 *
 * <p>Per ADR-0007 D1: {@link #browser()} is the only entry point for
 * constructing a {@link BrowserLifecycle} — there is no
 * {@code BrowserFactory} (seam in the wrong place). The dispatch
 * key is {@code browser.kind} in YAML: {@code PLAYWRIGHT} (production)
 * or {@code FAKE} (unit test fixture, not yet implemented).
 *
 * <p>Per design-patterns.md §2: thread-safe singleton with double-checked
 * locking on a {@code volatile} field. {@link #reset()} is package-friendly
 * and used by tests to isolate state.
 *
 * <p>Programming techniques: enum, sealed-switch, Lambda, immutability,
 * package-private test seams ({@code yamlProps}, {@code envFileProps},
 * {@code get(key, env)}).
 *
 * // Design Pattern: Singleton (double-checked locking)
 * // 编程技术: 枚举 / Lambda / 不可变 Map / 重载
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    private static final String CLASSPATH_YML = "application.yml";

    /** Matches {@code ${name}} placeholders in config values. */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /** Dispatch keys (kept here so {@link #browser()} has a single source of truth). */
    private enum BrowserKind {
        PLAYWRIGHT, FAKE;

        static BrowserKind parse(String raw) {
            if (raw == null) {
                throw new IllegalStateException("browser.kind is not configured");
            }
            try {
                return BrowserKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Unknown browser.kind: " + raw, ex);
            }
        }
    }

    private static volatile ConfigManager instance;

    /** YAML values, flattened to dot-notation. */
    private final Map<String, String> yamlProps = new LinkedHashMap<>();

    /** Values loaded from --env-file, override YAML on conflict. */
    private final Map<String, String> envFileProps = new LinkedHashMap<>();

    private ConfigManager() {
    }

    /**
     * @return the process-wide singleton
     */
    public static ConfigManager getInstance() {
        ConfigManager local = instance;
        if (local == null) {
            synchronized (ConfigManager.class) {
                local = instance;
                if (local == null) {
                    local = new ConfigManager();
                    instance = local;
                }
            }
        }
        return local;
    }

    // ---------- loading ----------

    /**
     * Loads {@code application.yml} from the classpath. Replaces any
     * previously loaded YAML values.
     */
    public synchronized void load() {
        URL url = ConfigManager.class.getClassLoader().getResource(CLASSPATH_YML);
        if (url == null) {
            throw new IllegalStateException(
                CLASSPATH_YML + " not found on classpath");
        }
        load(url);
    }

    /**
     * Loads YAML from an arbitrary URL (used by tests to point at fixtures).
     *
     * @param yamlUrl URL to a YAML document; must not be null
     */
    public synchronized void load(URL yamlUrl) {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            JsonNode root = mapper.readTree(yamlUrl);
            yamlProps.clear();
            flatten(root, "", yamlProps);
            log.info("Loaded {} YAML keys from {}", yamlProps.size(), yamlUrl);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load YAML from " + yamlUrl, e);
        }
    }

    /**
     * Loads key=value pairs from a {@code --env-file}. Replaces any
     * previously loaded env-file values.
     *
     * @param envFile path to a dotenv-format file
     */
    public synchronized void loadEnvFile(Path envFile) {
        if (envFile == null || !Files.exists(envFile)) {
            throw new IllegalStateException("env file does not exist: " + envFile);
        }
        Path absoluteEnv = envFile.toAbsolutePath();
        Dotenv dotenv = Dotenv.configure()
            .filename(absoluteEnv.getFileName().toString())
            .directory(absoluteEnv.getParent().toString())
            .load();
        envFileProps.clear();
        dotenv.entries().forEach(e -> envFileProps.put(e.getKey(), e.getValue()));
        log.info("Loaded {} env-file keys from {}", envFileProps.size(), envFile);
    }

    /**
     * Clears all loaded state. Intended for tests.
     */
    public synchronized void reset() {
        yamlProps.clear();
        envFileProps.clear();
    }

    // ---------- lookup ----------

    /**
     * Three-layer lookup. Process env &gt; env file &gt; YAML.
     *
     * @param key dot-notation key (e.g. {@code "browser.kind"})
     * @return the value, or {@code null} if no layer has it
     */
    public String get(String key) {
        return get(key, System.getenv());
    }

    /**
     * Test seam — accepts an explicit env map.
     */
    String get(String key, Map<String, String> env) {
        if (key == null) {
            return null;
        }
        String fromEnv = env.get(key);
        if (fromEnv != null) {
            return fromEnv;
        }
        String fromFile = envFileProps.get(key);
        if (fromFile != null) {
            return fromFile;
        }
        return yamlProps.get(key);
    }

    /**
     * @return the configured browser kind, or {@code null} if unset
     */
    public String browserKind() {
        return get("browser.kind");
    }

    /**
     * Returns a {@link CacheStore} pre-configured with per-scope TTLs
     * from YAML keys {@code cache.home}, {@code cache.ttl.schedule},
     * {@code cache.ttl.calendar}, {@code cache.ttl.exam}.
     *
     * <p>Per the architecture-deepening plan (改动 3): this replaces the former
     * {@code cacheConfig()} method which returned a separate {@code CacheConfig}
     * record that was immediately decomposed. The TTL table now lives inside
     * {@code CacheStore} itself.
     *
     * @return a configured {@link CacheStore} (never {@code null})
     * @since 0.3.0
     * @author 王子豪
     */
    public CacheStore cacheStore() {
        Path home = resolveCacheHome();
        CacheStore.Builder builder = CacheStore.builder(home);
        Duration scheduleTtl = parseDuration(get("cache.ttl.schedule"));
        Duration calendarTtl = parseDuration(get("cache.ttl.calendar"));
        Duration examTtl = parseDuration(get("cache.ttl.exam"));
        if (!scheduleTtl.isZero())  builder.ttl("schedule", scheduleTtl);
        if (!calendarTtl.isZero())   builder.ttl("calendar", calendarTtl);
        if (!examTtl.isZero())      builder.ttl("exam", examTtl);
        return builder.build();
    }

    /**
     * Resolves the cache home directory from {@code cache.home}, interpolating
     * {@code ${property}} placeholders (e.g. {@code ${user.home}}) against
     * system properties then environment variables. Falls back to
     * {@code user.home} when {@code cache.home} is unset.
     *
     * @return the resolved cache home path (never {@code null})
     * @since 0.3.0
     * @author 王子豪
     */
    private Path resolveCacheHome() {
        String raw = get("cache.home", System.getenv());
        if (raw == null || raw.isBlank()) {
            return Path.of(System.getProperty("user.home"));
        }
        return Path.of(interpolate(raw));
    }

    /**
     * Replaces every {@code ${name}} token with the matching system property
     * (or environment variable). Unresolved tokens are left verbatim so the
     * path stays debuggable rather than silently degrading.
     *
     * @param value raw config string, possibly containing placeholders
     * @return the interpolated string
     * @since 0.3.0
     * @author 王子豪
     */
    static String interpolate(String value) {
        Matcher m = PLACEHOLDER_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String ref = m.group(1);
            String resolved = System.getProperty(ref);
            if (resolved == null) {
                resolved = System.getenv(ref);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(
                resolved != null ? resolved : "${" + ref + "}"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Duration parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ZERO; // caller decides default
        }
        try {
            return Duration.parse(value);
        } catch (Exception e) {
            return Duration.ZERO;
        }
    }

    // ---------- package-private test seams ----------

    Map<String, String> yamlProps() {
        return Collections.unmodifiableMap(yamlProps);
    }

    /**
     * Returns env-file properties as an unmodifiable map.
     * Used by {@code VenueCommand} to build the effective env for
     * {@code AccountResolver} (avoids double-loading the env file).
     */
    public Map<String, String> envFileProps() {
        return Collections.unmodifiableMap(envFileProps);
    }

    /** Test seam — directly sets a YAML value (skips load). */
    void setYamlProperty(String key, String value) {
        yamlProps.put(key, value);
    }

    /** Test seam — directly sets an env-file value (skips loadEnvFile). */
    void setEnvFileProperty(String key, String value) {
        envFileProps.put(key, value);
    }

    // ---------- browser factory ----------

    /**
     * Constructs the configured {@link BrowserLifecycle}.
     *
     * <p>Per ADR-0007 D1: this is the only construction seam; callers never
     * pick the kind themselves. The decision lives in {@code browser.kind}.
     *
     * @return a fresh {@link BrowserLifecycle} (caller owns the lifecycle)
     * @throws IllegalStateException        if {@code browser.kind} is unset or unknown
     * @throws UnsupportedOperationException if {@code browser.kind = FAKE}
     *         (FakeBrowser is a Phase 4+ deliverable, not Phase 3)
     */
    public BrowserLifecycle browser() {
        return buildBrowser(false);
    }

    /**
     * Constructs the configured {@link BrowserLifecycle} with an explicit
     * headless override, bypassing the {@code SZU_HEADLESS} env / system
     * property resolution. Used by the headed-fallback path in
     * {@link edu.szu.agent.task.BookingTask}: when no credential is
     * available the user must be shown a real browser to log in manually,
     * regardless of any headless default the user might have configured.
     *
     * @param headless {@code false} to show a browser window; {@code true}
     *                 to keep the default behavior
     * @return a fresh {@link BrowserLifecycle} (caller owns the lifecycle)
     * @throws IllegalStateException        if {@code browser.kind} is unset or unknown
     * @throws UnsupportedOperationException if {@code browser.kind = FAKE}
     * @since 0.5.0
     * @author 王子豪
     */
    public BrowserLifecycle browser(boolean headless) {
        return buildBrowser(headless);
    }

    /**
     * Internal factory: single source of truth for the kind→adapter switch.
     * Future {@link BrowserKind} values (e.g. a real {@code FAKE} adapter)
     * only need to be wired here, in one place.
     *
     * <p>The {@code headlessOverride} is currently always honored — there is
     * no upstream caller that wants the env var to win over an explicit
     * override. If such a caller ever appears, gate it here.
     */
    private BrowserLifecycle buildBrowser(boolean headlessOverride) {
        BrowserKind kind = BrowserKind.parse(browserKind());
        return switch (kind) {
            case PLAYWRIGHT -> {
                Playwright pw = Playwright.create();
                yield new PlaywrightBrowserAdapter(pw, headlessOverride);
            }
            case FAKE -> throw new UnsupportedOperationException(
                "FakeBrowser is not yet implemented (Phase 4+ deliverable, see system-map.md §1)");
        };
    }

    // ---------- helpers ----------

    /**
     * Flattens a JSON/YAML tree to dot-notation keys in {@code out}.
     * Leaves (string / number / boolean) are stringified; null leaves
     * and empty maps are skipped.
     */
    private static void flatten(JsonNode node, String prefix, Map<String, String> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                String next = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
                flatten(e.getValue(), next, out);
            });
        } else if (node.isArray()) {
            // Arrays are uncommon in YAML config; stringify as JSON
            out.put(prefix, node.toString());
        } else {
            String value = node.isTextual() ? node.asText() : node.toString().replaceAll("^\"|\"$", "");
            out.put(prefix, value);
        }
    }

    // ---------- debug ----------

    @Override
    public String toString() {
        return "ConfigManager{yamlKeys=" + yamlProps.keySet().stream()
            .sorted()
            .collect(Collectors.joining(","))
            + ", envFileKeys=" + envFileProps.keySet().stream()
            .sorted()
            .collect(Collectors.joining(","))
            + "}";
    }
}

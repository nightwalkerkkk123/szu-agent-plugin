package edu.szu.agent.client.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * On-disk cache store with mtime-based TTL freshness checking.
 *
 * <p>Layout: {@code <home>/.szu-agent/cache/<scope>/<key>.json}.
 *
 * <p>Use the {@link Builder} to configure per-scope TTLs, then call
 * {@link #isFresh(String, String)} to check freshness.
 *
 * <p>Design Pattern: Adapter (over java.nio.file APIs, mirroring SessionStore).
 * // 编程技术: 不可变 record-like 状态 + NIO.2 + POSIX 文件权限 + Builder
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class CacheStore {

    private static final Logger log = LoggerFactory.getLogger(CacheStore.class);
    private static final Set<PosixFilePermission> FILE_PERMS =
        PosixFilePermissions.fromString("rw-------");
    /** Whitelist for scope used as a path segment. */
    static final Pattern SCOPE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");
    /** Whitelist for key used as a path segment. */
    static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9_-]+$");

    private final Path home;
    /** TTL per scope, registered via {@link Builder#ttl(String, Duration)}. */
    private final Map<String, Duration> ttlByScope = new HashMap<>();

    /**
     * Creates a cache store under the given home directory with no TTL configured
     * (all lookups return not-fresh). Prefer {@link Builder} to configure TTLs.
     *
     * @param home home directory under which {@code .szu-agent/cache/} lives
     * @since 0.6.0
     * @author 王子豪
     */
    public CacheStore(Path home) {
        this.home = Objects.requireNonNull(home, "home");
    }

    /**
     * Resolves the canonical cache file path for the given scope and key.
     *
     * @param scope cache scope (e.g. {@code schedule}, {@code calendar})
     * @param key   cache key within the scope
     * @return path of the form {@code <home>/.szu-agent/cache/<scope>/<key>.json}
     * @throws IllegalArgumentException if scope or key fails validation
     * @since 0.6.0
     * @author 王子豪
     */
    public Path defaultPath(String scope, String key) {
        validateScope(scope);
        validateKey(key);
        return home.resolve(".szu-agent/cache/" + scope + "/" + key + ".json");
    }

    /**
     * Tests whether the cache file currently exists.
     *
     * @param scope cache scope
     * @param key   cache key
     * @return {@code true} if the file exists
     * @since 0.6.0
     * @author 王子豪
     */
    public boolean exists(String scope, String key) {
        return Files.exists(defaultPath(scope, key));
    }

    /**
     * Tests whether the cache file is fresh — exists and its mtime is younger
     * than the TTL registered for the file's scope via {@link Builder#ttl}.
     *
     * @param scope cache scope
     * @param key   cache key
     * @return {@code true} if file exists, age is within scope TTL, and scope has a TTL configured
     * @since 0.6.0
     * @author 王子豪
     */
    public boolean isFresh(String scope, String key) {
        Duration ttl = ttlByScope.get(Objects.requireNonNull(scope, "scope"));
        if (ttl == null) {
            // No TTL configured for this scope → treat as not fresh
            return false;
        }
        return isFresh(scope, key, ttl);
    }

    /**
     * Tests whether the cache file's mtime is younger than the given TTL.
     *
     * @param scope cache scope
     * @param key   cache key
     * @param ttl   maximum allowed age
     * @return {@code true} if file exists and age {@code < ttl}, otherwise {@code false}
     * @since 0.6.0
     * @author 王子豪
     */
    public boolean isFresh(String scope, String key, Duration ttl) {
        Path p = defaultPath(scope, key);
        if (!Files.exists(p)) {
            return false;
        }
        try {
            FileTime mtime = Files.getLastModifiedTime(p);
            Duration age = Duration.between(mtime.toInstant(), Instant.now());
            return age.compareTo(ttl) < 0;
        } catch (IOException e) {
            log.warn("Failed to read mtime for {}/{}, treating as stale: {}",
                scope, key, e.getMessage());
            return false;
        }
    }

    /**
     * Reads raw JSON content from the cache file.
     *
     * @param scope cache scope
     * @param key   cache key
     * @return raw JSON string, or {@code null} if file does not exist
     * @throws IOException if read fails
     * @since 0.6.0
     * @author 王子豪
     */
    public String read(String scope, String key) throws IOException {
        Path target = defaultPath(scope, key);
        if (!Files.exists(target)) {
            return null;
        }
        return Files.readString(target, StandardCharsets.UTF_8);
    }

    /**
     * Writes JSON content atomically: write to a temp file then rename.
     * Applies POSIX 600 permissions where supported.
     *
     * @param scope cache scope
     * @param key   cache key
     * @param json  serialized cache payload
     * @return the written file path
     * @throws IOException if write fails
     * @since 0.6.0
     * @author 王子豪
     */
    public Path write(String scope, String key, String json) throws IOException {
        Objects.requireNonNull(json, "json");
        Path target = defaultPath(scope, key);
        Path parent = target.getParent();
        Files.createDirectories(parent);
        byte[] data = json.getBytes(StandardCharsets.UTF_8);

        // Atomic write: temp file + rename
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.deleteIfExists(tmp);

        try {
            FileAttribute<Set<PosixFilePermission>> attr =
                PosixFilePermissions.asFileAttribute(FILE_PERMS);
            try (SeekableByteChannel ch = Files.newByteChannel(
                    tmp,
                    EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    attr)) {
                ch.write(ByteBuffer.wrap(data));
            }
        } catch (UnsupportedOperationException ignored) {
            // Windows / non-POSIX
            Files.write(tmp, data,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }

        Files.move(tmp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    /**
     * Deletes the cache file if it exists.
     *
     * @param scope cache scope
     * @param key   cache key
     * @throws IOException if deletion fails
     * @since 0.6.0
     * @author 王子豪
     */
    public void deleteIfExists(String scope, String key) throws IOException {
        Files.deleteIfExists(defaultPath(scope, key));
    }

    private static void validateScope(String scope) {
        Objects.requireNonNull(scope, "scope");
        if (!SCOPE_PATTERN.matcher(scope).matches()) {
            throw new IllegalArgumentException(
                "scope must match [a-z][a-z0-9_]+, got: " + scope);
        }
    }

    private static void validateKey(String key) {
        Objects.requireNonNull(key, "key");
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                "key must match [a-z0-9_-]+, got: " + key);
        }
    }

    /**
     * Builder for {@link CacheStore} with per-scope TTL configuration.
     *
     * <p>Example:
     * <pre>{@code
     * CacheStore store = CacheStore.builder(home)
     *     .ttl("schedule", Duration.ofHours(24))
     *     .ttl("calendar", Duration.ofDays(7))
     *     .ttl("exam", Duration.ofHours(1))
     *     .build();
     * }</pre>
     *
     * // 编程技术: Builder 模式
     *
     * @since 0.6.0
     * @author 王子豪
     */
    public static final class Builder {
        private final Path home;
        private final Map<String, Duration> ttls = new HashMap<>();

        /**
         * Creates a builder rooted at the given home directory.
         *
         * @param home cache home directory
         * @since 0.6.0
         * @author 王子豪
         */
        public Builder(Path home) {
            this.home = Objects.requireNonNull(home, "home");
        }

        /**
         * Registers the TTL for a scope. Scopes not registered default to
         * "always stale" (causing a cache miss on next lookup).
         *
         * @param scope cache scope (e.g. {@code schedule})
         * @param ttl  maximum age before treating entries as stale
         * @return this builder
         * @since 0.6.0
         * @author 王子豪
         */
        public Builder ttl(String scope, Duration ttl) {
            validateScope(scope);
            Objects.requireNonNull(ttl, "ttl");
            ttls.put(scope, ttl);
            return this;
        }

        /**
         * Builds an immutable {@link CacheStore} configured with the TTLs
         * registered via {@link #ttl}.
         *
         * @return a configured CacheStore
         * @since 0.6.0
         * @author 王子豪
         */
        public CacheStore build() {
            CacheStore store = new CacheStore(home);
            store.ttlByScope.putAll(ttls);
            return store;
        }
    }

    /** Convenience factory for a builder. */
    public static Builder builder(Path home) {
        return new Builder(home);
    }
}

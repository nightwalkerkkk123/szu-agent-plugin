package edu.szu.agent.client.session;

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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Manages the on-disk storage state for a single SZU account.
 *
 * <p>Layout: {@code <home>/.szu-agent/sessions/<username>.json}.
 *
 * // 编程技术: 不可变 record-like 状态 + NIO.2
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);
    private static final Set<PosixFilePermission> FILE_PERMS =
        PosixFilePermissions.fromString("rw-------");
    /**
     * Whitelist for usernames used as a path segment. Rejects path-traversal
     * input such as {@code ../etc/passwd}, {@code a/b}, or {@code a\b}.
     */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]+$");

    private final Path home;
    private final String username;

    /**
     * Creates a session store for the given user under the given home directory.
     *
     * @param home     home directory under which {@code .szu-agent/sessions/} lives
     * @param username SZU account username (must not be blank)
     * @since 0.6.0
     * @author 王子豪
     */
    public SessionStore(Path home, String username) {
        this.home = Objects.requireNonNull(home, "home");
        Objects.requireNonNull(username, "username");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                "username must match [A-Za-z0-9_.-]+, got: " + username);
        }
        this.username = username;
    }

    /**
     * Returns the username that identifies this session store.
     *
     * @return the SZU account username
     * @since 0.6.0
     * @author 王子豪
     */
    public String username() {
        return username;
    }

    /**
     * Resolves the canonical session file path for this user.
     *
     * @return path of the form {@code <home>/.szu-agent/sessions/<username>.json}
     * @since 0.6.0
     * @author 王子豪
     */
    public Path defaultPath() {
        return home.resolve(".szu-agent/sessions/" + username + ".json");
    }

    /**
     * Reads the session file as a UTF-8 string.
     *
     * @return raw JSON content
     * @throws IOException if the file does not exist or cannot be read
     * @since 0.6.0
     * @author 王子豪
     */
    public String read() throws IOException {
        Path target = defaultPath();
        if (!Files.exists(target)) {
            throw new IOException("Session file does not exist: " + target);
        }
        return Files.readString(target, StandardCharsets.UTF_8);
    }

    /**
     * Tests whether the session file currently exists on disk.
     *
     * @return {@code true} if the file exists
     * @since 0.6.0
     * @author 王子豪
     */
    public boolean exists() {
        return Files.exists(defaultPath());
    }

    /**
     * Ensures the parent directory of the session file exists, creating it if needed.
     *
     * @return the parent directory path
     * @throws IOException if directory creation fails
     * @since 0.6.0
     * @author 王子豪
     */
    public Path ensureParent() throws IOException {
        Path parent = defaultPath().getParent();
        Files.createDirectories(parent);
        return parent;
    }

    /**
     * Tests whether the session file's mtime is younger than the given TTL.
     *
     * @param ttl maximum allowed age
     * @return {@code true} if file exists and age &lt; ttl, otherwise {@code false}
     * @since 0.6.0
     * @author 王子豪
     */
    public boolean isFresh(Duration ttl) {
        Path p = defaultPath();
        if (!Files.exists(p)) {
            return false;
        }
        try {
            FileTime mtime = Files.getLastModifiedTime(p);
            Duration age = Duration.between(mtime.toInstant(), Instant.now());
            return age.compareTo(ttl) < 0;
        } catch (IOException e) {
            log.warn("Failed to read mtime, treating as stale: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Writes raw JSON content to the session file, replacing any existing file
     * and applying POSIX 600 permissions where supported.
     *
     * @param json serialized session payload
     * @return the written file path
     * @throws IOException if the write fails
     * @since 0.6.0
     * @author 王子豪
     */
    public Path write(String json) throws IOException {
        Objects.requireNonNull(json, "json");
        Path target = defaultPath();
        ensureParent();
        Files.deleteIfExists(target);
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        try {
            // POSIX: create + write in one syscall with rw------- — no permission window.
            FileAttribute<Set<PosixFilePermission>> attr =
                PosixFilePermissions.asFileAttribute(FILE_PERMS);
            try (SeekableByteChannel ch = Files.newByteChannel(
                    target,
                    EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    attr)) {
                ch.write(ByteBuffer.wrap(data));
            }
        } catch (UnsupportedOperationException ignored) {
            // Windows / non-POSIX — no per-file POSIX perm syscall available.
            // NTFS ACL inheritance is the protection mechanism on these platforms.
            Files.write(target, data,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        return target;
    }

    /**
     * Deletes the session file if it exists.
     *
     * @throws IOException if deletion fails
     * @since 0.6.0
     * @author 王子豪
     */
    public void deleteIfExists() throws IOException {
        Files.deleteIfExists(defaultPath());
    }
}

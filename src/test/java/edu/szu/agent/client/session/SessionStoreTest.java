package edu.szu.agent.client.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionStore")
class SessionStoreTest {

    @Test
    @DisplayName("defaultPath 解析为 ~/.szu-agent/sessions/<username>.json")
    void defaultPathResolves(@TempDir Path tmp) {
        Path expected = tmp.resolve(".szu-agent/sessions/test-user.json");
        SessionStore store = new SessionStore(tmp, "test-user");
        assertThat(store.defaultPath()).isEqualTo(expected);
    }

    @Test
    @DisplayName("exists 缺失文件返回 false")
    void existsMissingFile(@TempDir Path tmp) {
        SessionStore store = new SessionStore(tmp, "u");
        assertThat(store.exists()).isFalse();
    }

    @Test
    @DisplayName("ensureParent 递归创建 ~/.szu-agent/sessions/")
    void ensureParentCreates(@TempDir Path tmp) throws Exception {
        SessionStore store = new SessionStore(tmp, "u");
        Path created = store.ensureParent();
        assertThat(Files.isDirectory(created)).isTrue();
    }

    @Test
    @DisplayName("isFresh 新建文件返回 true")
    void isFreshNewFile(@TempDir Path tmp) throws Exception {
        SessionStore store = new SessionStore(tmp, "u");
        store.ensureParent();
        Files.writeString(store.defaultPath(), "{\"cookies\":[]}");
        assertThat(store.isFresh(Duration.ofDays(30))).isTrue();
    }

    @Test
    @DisplayName("isFresh 31 天前文件返回 false")
    void isFreshOldFile(@TempDir Path tmp) throws Exception {
        SessionStore store = new SessionStore(tmp, "u");
        store.ensureParent();
        Path file = store.defaultPath();
        Files.writeString(file, "{}");
        Files.setLastModifiedTime(file,
            java.nio.file.attribute.FileTime.from(Instant.now().minus(31, ChronoUnit.DAYS)));
        assertThat(store.isFresh(Duration.ofDays(30))).isFalse();
    }

    @Test
    @DisplayName("isFresh 缺失文件返回 false")
    void isFreshMissingFile(@TempDir Path tmp) {
        SessionStore store = new SessionStore(tmp, "u");
        assertThat(store.isFresh(Duration.ofDays(30))).isFalse();
    }

    @Test
    @DisplayName("POSIX 系统上权限位 600")
    void posixPermissionsApplied(@TempDir Path tmp) throws Exception {
        SessionStore store = new SessionStore(tmp, "u");
        Path file = store.write("{}");
        try {
            Set<java.nio.file.attribute.PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            assertThat(perms).containsExactlyInAnyOrder(OWNER_READ, OWNER_WRITE);
        } catch (UnsupportedOperationException ignored) {
            // Windows / non-POSIX — skip
        }
    }

    @Test
    @DisplayName("构造器拒绝路径穿越 username")
    void rejectsTraversalUsername(@TempDir Path tmp) {
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new SessionStore(tmp, "../etc/passwd"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("username must match");
    }

    @Test
    @DisplayName("构造器拒绝路径分隔符 username")
    void rejectsSlashUsername(@TempDir Path tmp) {
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new SessionStore(tmp, "a/b"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

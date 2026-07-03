package edu.szu.agent.client.cache;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CacheStore}.
 *
 * @since 0.6.0
 * @author 王子豪
 */
@DisplayName("CacheStore")
class CacheStoreTest {

    @Test
    @DisplayName("defaultPath 解析为 ~/.szu-agent/cache/<scope>/<key>.json")
    void defaultPathResolves(@TempDir Path tmp) {
        CacheStore store = new CacheStore(tmp);
        Path expected = tmp.resolve(".szu-agent/cache/schedule/schedule-2023150090.json");
        assertThat(store.defaultPath("schedule", "schedule-2023150090")).isEqualTo(expected);
    }

    @Test
    @DisplayName("exists 缺失文件返回 false")
    void existsMissingFile(@TempDir Path tmp) {
        CacheStore store = new CacheStore(tmp);
        assertThat(store.exists("schedule", "schedule-2023150090")).isFalse();
    }

    @Test
    @DisplayName("exists 存在文件返回 true")
    void existsPresentFile(@TempDir Path tmp) throws Exception {
        CacheStore store = new CacheStore(tmp);
        store.write("schedule", "k1", "{\"fetchedAt\":\"2026-06-23T00:00:00Z\",\"schemaVersion\":1,\"payload\":[]}");
        assertThat(store.exists("schedule", "k1")).isTrue();
    }

    @Test
    @DisplayName("isFresh 新建文件在 TTL 内返回 true")
    void isFreshNewFileWithinTtl(@TempDir Path tmp) throws Exception {
        CacheStore store = new CacheStore(tmp);
        store.write("schedule", "k1", "{\"fetchedAt\":\"2026-06-23T00:00:00Z\",\"schemaVersion\":1,\"payload\":[]}");
        assertThat(store.isFresh("schedule", "k1", Duration.ofDays(1))).isTrue();
    }

    @Test
    @DisplayName("isFresh 31 天前文件在 30 天 TTL 下返回 false")
    void isFreshExpiredFile(@TempDir Path tmp) throws Exception {
        CacheStore store = new CacheStore(tmp);
        Path file = store.write("schedule", "k1",
            "{\"fetchedAt\":\"2026-06-01T00:00:00Z\",\"schemaVersion\":1,\"payload\":[]}");
        Files.setLastModifiedTime(file,
            java.nio.file.attribute.FileTime.from(Instant.now().minus(31, ChronoUnit.DAYS)));
        assertThat(store.isFresh("schedule", "k1", Duration.ofDays(30))).isFalse();
    }

    @Test
    @DisplayName("isFresh 缺失文件返回 false")
    void isFreshMissingFile(@TempDir Path tmp) {
        CacheStore store = new CacheStore(tmp);
        assertThat(store.isFresh("schedule", "nonexistent", Duration.ofDays(30))).isFalse();
    }

    @Test
    @DisplayName("read 缺失文件返回 null")
    void readMissingFile(@TempDir Path tmp) throws Exception {
        CacheStore store = new CacheStore(tmp);
        assertThat(store.read("schedule", "nonexistent")).isNull();
    }

    @Test
    @DisplayName("read 存在文件返回原始 JSON")
    void readExistingFile(@TempDir Path tmp) throws Exception {
        CacheStore store = new CacheStore(tmp);
        String json = "{\"fetchedAt\":\"2026-06-23T00:00:00Z\",\"schemaVersion\":1,\"payload\":[1,2,3]}";
        store.write("schedule", "k1", json);
        assertThat(store.read("schedule", "k1")).isEqualTo(json);
    }

    @Test
    @DisplayName("write + read round-trip 保持 JSON 不变")
    void writeReadRoundTrip(@TempDir Path tmp) throws Exception {
        CacheStore store = new CacheStore(tmp);
        String json = "{\"fetchedAt\":\"2026-06-23T00:00:00Z\",\"schemaVersion\":1,\"payload\":{\"a\":1}}";
        store.write("calendar", "cal-key", json);
        assertThat(store.read("calendar", "cal-key")).isEqualTo(json);
    }

    @Test
    @DisplayName("write 原子写入：临时文件完成后 rename")
    void writeIsAtomic(@TempDir Path tmp) throws Exception {
        CacheStore store = new CacheStore(tmp);
        String json = "{\"fetchedAt\":\"2026-06-23T00:00:00Z\",\"schemaVersion\":1,\"payload\":{}}";
        Path written = store.write("schedule", "k1", json);
        assertThat(Files.exists(tmp.resolve(".szu-agent/cache/schedule/k1.json.tmp"))).isFalse();
        assertThat(Files.exists(written)).isTrue();
    }

    @Test
    @DisplayName("POSIX 系统上权限位 600")
    void posixPermissionsApplied(@TempDir Path tmp) throws Exception {
        CacheStore store = new CacheStore(tmp);
        Path file = store.write("schedule", "k1", "{}");
        try {
            Set<java.nio.file.attribute.PosixFilePermission> perms =
                Files.getPosixFilePermissions(file);
            assertThat(perms).containsExactlyInAnyOrder(OWNER_READ, OWNER_WRITE);
        } catch (UnsupportedOperationException ignored) {
            // Windows / non-POSIX — skip
        }
    }

    @Test
    @DisplayName("构造器拒绝路径穿越 scope")
    void rejectsTraversalScope(@TempDir Path tmp) {
        assertThatThrownBy(() -> new CacheStore(tmp).defaultPath("../etc", "key"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scope must match");
    }

    @Test
    @DisplayName("构造器拒绝数字开头 scope")
    void rejectsNumericScope(@TempDir Path tmp) {
        assertThatThrownBy(() -> new CacheStore(tmp).defaultPath("123scope", "key"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scope must match");
    }

    @Test
    @DisplayName("构造器拒绝非法字符 key")
    void rejectsInvalidKey(@TempDir Path tmp) {
        assertThatThrownBy(() -> new CacheStore(tmp).defaultPath("scope", "key/with/slash"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("key must match");
    }

    @Test
    @DisplayName("deleteIfExists 缺失文件不抛异常")
    void deleteMissingFileNoException(@TempDir Path tmp) throws Exception {
        CacheStore store = new CacheStore(tmp);
        store.deleteIfExists("schedule", "nonexistent");
        // no exception
    }

    @Test
    @DisplayName("deleteIfExists 存在文件删除成功")
    void deleteExistingFile(@TempDir Path tmp) throws Exception {
        CacheStore store = new CacheStore(tmp);
        store.write("schedule", "k1", "{}");
        store.deleteIfExists("schedule", "k1");
        assertThat(store.exists("schedule", "k1")).isFalse();
    }
}

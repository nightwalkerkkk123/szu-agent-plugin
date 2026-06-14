# US-007 登录状态持久化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `homework list` / `booking venue` 二次调用时复用 storage state 跳过 CAS 登录,30 天 TTL + 探针 + 损坏兜底。

**Architecture:** 扩展 `BrowserLifecycle` 接口加 `importStorageState(Path)` / `exportStorageState(Path)`,`PlaywrightBrowserAdapter` 内部用 `BrowserContext` 包装。BookingStep 链路加 `RestoreSessionStep` (前置) + `PersistSessionStep` (后置);失败回退到 `CasLoginStep`。

**Tech Stack:** Java 21, Maven 3.9+, Playwright 1.45.0 (已有), JUnit 5 + AssertJ + Mockito, JUnit `@TempDir`, `java.nio.file.attribute.PosixFilePermission`.

**Spec:** `docs/superpowers/specs/2026-06-14-us-007-session-persistence-design.md`
**Story:** `docs/stories/US-007-session-persistence.md` (Task 16 创建)
**ADR:** `docs/adr/0008-session-persistence.md` (Task 17 创建)

---

## 工作顺序总览

| Task | 内容 | 依赖 |
|---|---|---|
| 1 | `ErrorCode` 加 3 个枚举值 + 改 `ErrorCodeTest` | 无 |
| 2 | `SessionStore` 纯文件 IO(路径 + 权限 + TTL) | Task 1 |
| 3 | `SessionResult` record(sealed) | Task 1 |
| 4 | `SessionProbe`(navigate + isVisible 判定) | Task 3 |
| 5 | `FakeBrowser` 加 2 个 no-op + 标志 | 无 |
| 6 | `BookingContext` 加 `sessionOk` / `username` 字段 | Task 1 |
| 7 | `RestoreSessionStep` + `PersistSessionStep` | Task 2, 3, 4, 5, 6 |
| 8 | `CasLoginStep` 头部加 sessionOk 跳过判断 | Task 6 |
| 9 | `ChaoxingHomeworkClient` 接新 steps | Task 7, 8 |
| 10 | `BrowserLifecycle` 接口加 2 方法 | Task 9 |
| 11 | `PlaywrightBrowserAdapter` 改 `BrowserContext` + 实现 2 方法 | Task 10 |
| 12 | `mvn test` 跑全量,验证 350+ 通过 | Task 11 |
| 13 | `mvn -q -DskipTests package` 验证构建 | Task 12 |
| 14 | `git add` + `git commit` | Task 13 |
| 15 | 更新 `docs/system-map.md` + `docs/design-patterns.md` | Task 14 |
| 16 | 创建 `docs/stories/US-007-session-persistence.md` | Task 15 |
| 17 | 创建 `docs/adr/0008-session-persistence.md` | Task 16 |
| 18 | 创建 `harness-records/traces/YYYYMMDD-HHMMSS-US-007.md` | Task 17 |
| 19 | 最终验证 + `git status --short` 干净 | Task 18 |

---

## Task 1: ErrorCode 加 3 个枚举值

**Files:**
- Modify: `src/main/java/edu/szu/agent/error/ErrorCode.java:32-43`(在现有枚举末尾添加)
- Modify: `src/test/java/edu/szu/agent/error/ErrorCodeTest.java`(加 3 个枚举值的元数据测试)

- [ ] **Step 1: 写失败的测试**

打开 `src/test/java/edu/szu/agent/error/ErrorCodeTest.java`,在 `@DisplayName("所有枚举值至少出现一次")` 那个测试的 `for (ErrorCode value : ErrorCode.values())` 循环中,期望**枚举总数 = 17**(原 14 + 3 新)而不是 14。如果当前是 14,加注释 `// 14 existing + 3 SESSION_* in US-007 = 17`。

加 3 个独立测试:

```java
@Test
@DisplayName("SESSION_NOT_FOUND 元数据正确")
void sessionNotFoundMetadata() {
    ErrorCode c = ErrorCode.SESSION_NOT_FOUND;
    assertThat(c.severity()).isEqualTo(Severity.LOW);
    assertThat(c.isRetryable()).isFalse();
    assertThat(c.shouldSwitchAccount()).isFalse();
    assertThat(c.shouldScreenshot()).isFalse();
    assertThat(c.hint()).isEqualTo("无持久化登录态");
}

@Test
@DisplayName("SESSION_READ_FAILED 元数据正确")
void sessionReadFailedMetadata() {
    ErrorCode c = ErrorCode.SESSION_READ_FAILED;
    assertThat(c.severity()).isEqualTo(Severity.MEDIUM);
    assertThat(c.isRetryable()).isFalse();
    assertThat(c.shouldSwitchAccount()).isFalse();
    assertThat(c.shouldScreenshot()).isFalse();
    assertThat(c.hint()).isEqualTo("持久化登录态损坏");
}

@Test
@DisplayName("SESSION_WRITE_FAILED 元数据正确")
void sessionWriteFailedMetadata() {
    ErrorCode c = ErrorCode.SESSION_WRITE_FAILED;
    assertThat(c.severity()).isEqualTo(Severity.LOW);
    assertThat(c.isRetryable()).isFalse();
    assertThat(c.shouldSwitchAccount()).isFalse();
    assertThat(c.shouldScreenshot()).isFalse();
    assertThat(c.hint()).isEqualTo("持久化登录态写入失败");
}
```

- [ ] **Step 2: 跑测试确认 RED**

```bash
mvn -q test -Dtest=ErrorCodeTest
```

预期:FAIL,3 个测试都报 "Cannot resolve method SESSION_NOT_FOUND"。

- [ ] **Step 3: 在 `ErrorCode.java` 末尾加 3 个枚举值**

在 `public enum ErrorCode {` 的最后一个枚举值(`UNKNOWN`)后面添加(逗号补在 `UNKNOWN` 行末):

```java
    // 登录态持久化(US-007)
    SESSION_NOT_FOUND  (Severity.LOW,    false, false, false, "无持久化登录态"),
    SESSION_READ_FAILED(Severity.MEDIUM, false, false, false, "持久化登录态损坏"),
    SESSION_WRITE_FAILED(Severity.LOW,    false, false, false, "持久化登录态写入失败");
```

注意:前一个枚举值行末的逗号 + 这 3 个的逗号 + 最后一个分号结尾。

- [ ] **Step 4: 跑测试确认 GREEN**

```bash
mvn -q test -Dtest=ErrorCodeTest
```

预期:PASS,3 个新测试 + 原 14 个枚举值都通过(共 17 个枚举值)。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/edu/szu/agent/error/ErrorCode.java src/test/java/edu/szu/agent/error/ErrorCodeTest.java
git commit -m "feat(error): add SESSION_NOT_FOUND/READ_FAILED/WRITE_FAILED codes"
```

---

## Task 2: SessionStore 纯文件 IO

**Files:**
- Create: `src/main/java/edu/szu/agent/client/session/SessionStore.java`
- Create: `src/test/java/edu/szu/agent/client/session/SessionStoreTest.java`

- [ ] **Step 1: 写失败的测试**

`SessionStoreTest.java` 完整内容:

```java
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
        // Use system property override via reflection-friendly ctor (see Step 3).
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
            assertThat(perms).contains(OWNER_READ, OWNER_WRITE);
        } catch (UnsupportedOperationException ignored) {
            // Windows / non-POSIX — skip
        }
    }
}
```

- [ ] **Step 2: 跑测试确认 RED**

```bash
mvn -q test -Dtest=SessionStoreTest
```

预期:FAIL,"Cannot resolve symbol SessionStore"。

- [ ] **Step 3: 实现 `SessionStore`**

```java
package edu.szu.agent.client.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Manages the on-disk storage state for a single SZU account.
 *
 * <p>Layout: {@code <home>/.szu-agent/sessions/<username>.json}.
 *
 * // 编程技术: 不可变 record-like 状态 + NIO.2
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);
    private static final Set<PosixFilePermission> FILE_PERMS =
        PosixFilePermissions.fromString("rw-------");

    private final Path home;
    private final String username;

    public SessionStore(Path home, String username) {
        this.home = Objects.requireNonNull(home, "home");
        this.username = Objects.requireNonNull(username, "username");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
    }

    public Path defaultPath() {
        return home.resolve(".szu-agent/sessions/" + username + ".json");
    }

    public boolean exists() {
        return Files.exists(defaultPath());
    }

    public Path ensureParent() throws IOException {
        Path parent = defaultPath().getParent();
        Files.createDirectories(parent);
        return parent;
    }

    public boolean isFresh(Duration ttl) {
        Path p = defaultPath();
        if (!Files.exists(p)) {
            return false;
        }
        try {
            FileTime mtime = Files.getLastModifiedTime(p);
            Instant age = Duration.between(mtime.toInstant(), Instant.now());
            return age.compareTo(ttl) < 0;
        } catch (IOException e) {
            log.warn("Failed to read mtime of {}, treating as stale", p);
            return false;
        }
    }

    public Path write(String json) throws IOException {
        Objects.requireNonNull(json, "json");
        Path target = defaultPath();
        ensureParent();
        Files.deleteIfExists(target);
        Files.writeString(target, json);
        applyPosixPermissions(target);
        return target;
    }

    public void deleteIfExists() throws IOException {
        Files.deleteIfExists(defaultPath());
    }

    private static void applyPosixPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, FILE_PERMS);
        } catch (UnsupportedOperationException ignored) {
            // Windows / non-POSIX — inherit ACL
        } catch (IOException e) {
            log.warn("Failed to set POSIX permissions on {}", file);
        }
    }
}
```

- [ ] **Step 4: 跑测试确认 GREEN**

```bash
mvn -q test -Dtest=SessionStoreTest
```

预期:PASS,7 个测试全过。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/edu/szu/agent/client/session/SessionStore.java src/test/java/edu/szu/agent/client/session/SessionStoreTest.java
git commit -m "feat(session): add SessionStore with POSIX 600 perms + 30d TTL"
```

---

## Task 3: SessionResult record(sealed)

**Files:**
- Create: `src/main/java/edu/szu/agent/client/session/SessionResult.java`

- [ ] **Step 1: 实现 `SessionResult`(sealed interface,无测试)**

```java
package edu.szu.agent.client.session;

/**
 * Outcome of a {@link SessionProbe} check.
 *
 * <p>// 编程技术: sealed interface + record
 *
 * @since 0.1.0
 * @author 王子豪
 */
public sealed interface SessionResult {

    record Fresh() implements SessionResult {}

    record Stale(String reason) implements SessionResult {}
}
```

- [ ] **Step 2: 跑编译验证**

```bash
mvn -q -DskipTests compile
```

预期:BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/edu/szu/agent/client/session/SessionResult.java
git commit -m "feat(session): add SessionResult sealed type"
```

---

## Task 4: SessionProbe

**Files:**
- Create: `src/main/java/edu/szu/agent/client/session/SessionProbe.java`
- Create: `src/test/java/edu/szu/agent/client/session/SessionProbeTest.java`

- [ ] **Step 1: 写失败的测试**

`SessionProbeTest.java`:

```java
package edu.szu.agent.client.session;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionProbe")
class SessionProbeTest {

    @Mock
    private BrowserLifecycle browser;

    @Test
    @DisplayName("isAlive 返回 Fresh 当 isVisible 看到 todo-list-container")
    void isAliveFresh() {
        when(browser.isVisible(".todo-list-container")).thenReturn(true);
        SessionProbe probe = new SessionProbe(
            "https://lms.szu.edu.cn/user/index", ".todo-list-container");
        assertThat(probe.isAlive(browser)).isInstanceOf(SessionResult.Fresh.class);
    }

    @Test
    @DisplayName("isAlive 返回 Stale 当 isVisible 为 false")
    void isAliveStale() {
        when(browser.isVisible(".todo-list-container")).thenReturn(false);
        SessionProbe probe = new SessionProbe(
            "https://lms.szu.edu.cn/user/index", ".todo-list-container");
        SessionResult r = probe.isAlive(browser);
        assertThat(r).isInstanceOf(SessionResult.Stale.class);
    }

    @Test
    @DisplayName("isAlive 返回 Stale 当 navigate 抛 BookingException")
    void isAliveStaleOnError() {
        org.mockito.Mockito.doThrow(new BookingException(
                ErrorCode.NETWORK_TIMEOUT, "timeout"))
            .when(browser).navigateTo("https://lms.szu.edu.cn/user/index");
        SessionProbe probe = new SessionProbe(
            "https://lms.szu.edu.cn/user/index", ".todo-list-container");
        assertThat(probe.isAlive(browser)).isInstanceOf(SessionResult.Stale.class);
    }
}
```

- [ ] **Step 2: 跑测试确认 RED**

```bash
mvn -q test -Dtest=SessionProbeTest
```

预期:FAIL,"Cannot resolve symbol SessionProbe"。

- [ ] **Step 3: 实现 `SessionProbe`**

```java
package edu.szu.agent.client.session;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Probes whether a freshly-imported session is still valid by navigating
 * to a known-protected URL and checking for a logged-in indicator.
 *
 * <p>// Design Pattern: Strategy (concrete probe)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class SessionProbe {

    private static final Logger log = LoggerFactory.getLogger(SessionProbe.class);

    private final String probeUrl;
    private final String aliveSelector;

    public SessionProbe(String probeUrl, String aliveSelector) {
        this.probeUrl = Objects.requireNonNull(probeUrl, "probeUrl");
        this.aliveSelector = Objects.requireNonNull(aliveSelector, "aliveSelector");
    }

    public SessionResult isAlive(BrowserLifecycle browser) {
        Objects.requireNonNull(browser, "browser");
        try {
            browser.navigateTo(probeUrl);
            if (browser.isVisible(aliveSelector)) {
                return new SessionResult.Fresh();
            }
            return new SessionResult.Stale("indicator not visible after navigate");
        } catch (BookingException e) {
            log.info("probe navigate failed: {}", e.getMessage());
            return new SessionResult.Stale("navigate failed: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 4: 跑测试确认 GREEN**

```bash
mvn -q test -Dtest=SessionProbeTest
```

预期:PASS,3 个测试全过。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/edu/szu/agent/client/session/SessionProbe.java src/test/java/edu/szu/agent/client/session/SessionProbeTest.java
git commit -m "feat(session): add SessionProbe for post-import validation"
```

---

## Task 5: FakeBrowser 加 2 个 no-op + 标志

**Files:**
- Modify: `src/test/java/edu/szu/agent/browser/FakeBrowser.java`

- [ ] **Step 1: 加 2 个方法 + 2 个 boolean 字段**

在 `FakeBrowser.java` 类体加字段:

```java
    private boolean loaded;
    private boolean saved;
    private Path loadedPath;
    private Path savedPath;
```

在 `close()` 后加 2 个方法:

```java
    @Override
    public boolean importStorageState(java.nio.file.Path storageStateFile) {
        this.loadedPath = storageStateFile;
        this.loaded = storageStateFile != null;
        return loaded;
    }

    @Override
    public void exportStorageState(java.nio.file.Path storageStateFile) {
        this.savedPath = storageStateFile;
        this.saved = storageStateFile != null;
    }

    public boolean isLoaded() { return loaded; }
    public boolean isSaved() { return saved; }
    public java.nio.file.Path loadedPath() { return loadedPath; }
    public java.nio.file.Path savedPath() { return savedPath; }
```

- [ ] **Step 2: 跑编译验证(会失败,因 BrowserLifecycle 还没加方法)**

```bash
mvn -q -DskipTests test-compile
```

预期:FAIL,`FakeBrowser is not abstract and does not override abstract method importStorageState(Path) in BrowserLifecycle`。

这是预期的。**先不 commit**。

---

## Task 6: BookingContext 加 sessionOk / username 字段

**Files:**
- Modify: `src/main/java/edu/szu/agent/client/step/BookingContext.java`
- Modify: 现有所有 `BookingContext` 构造器调用方(让它们继续编译)

- [ ] **Step 1: 在 `BookingContext.java` 加字段 + getter/setter**

在 `private List<Homework> homeworks;` 后加:

```java
    private boolean sessionOk;
    private String username;
```

在 `homeworks(List<Homework>)` 后加:

```java
    public boolean sessionOk() {
        return sessionOk;
    }

    public void sessionOk(boolean sessionOk) {
        this.sessionOk = sessionOk;
    }

    public String username() {
        return username;
    }

    public void username(String username) {
        this.username = username;
    }
```

- [ ] **Step 2: 跑编译验证**

```bash
mvn -q -DskipTests test-compile
```

预期:BUILD SUCCESS(只加字段不影响现有调用)。

- [ ] **Step 3: Commit(等 Task 5 一起 commit)**

暂不 commit,与 Task 5 一起。

---

## Task 7: RestoreSessionStep + PersistSessionStep

**Files:**
- Create: `src/main/java/edu/szu/agent/client/step/RestoreSessionStep.java`
- Create: `src/main/java/edu/szu/agent/client/step/PersistSessionStep.java`
- Create: `src/test/java/edu/szu/agent/client/step/RestoreSessionStepTest.java`
- Create: `src/test/java/edu/szu/agent/client/step/PersistSessionStepTest.java`

- [ ] **Step 1: 写 `RestoreSessionStepTest.java`(失败)**

```java
package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionResult;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestoreSessionStep")
class RestoreSessionStepTest {

    @Mock private BrowserLifecycle browser;
    @Mock private SessionStore store;
    @Mock private SessionProbe probe;

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account("2023150090", "secret", "test");
    }

    @Test
    @DisplayName("import 成功 + 探针 Fresh → sessionOk=true,不再 navigate")
    void freshSetsSessionOk() {
        BookingContext ctx = new BookingContext(new BookingRequest.Builder().build(), account);
        when(store.exists()).thenReturn(true);
        when(store.isFresh(Duration.ofDays(30))).thenReturn(true);
        when(browser.importStorageState(any())).thenReturn(true);
        when(probe.isAlive(browser)).thenReturn(new SessionResult.Fresh());

        new RestoreSessionStep(store, probe, Duration.ofDays(30))
            .execute(browser, ctx);

        assertThat(ctx.sessionOk()).isTrue();
        assertThat(ctx.username()).isEqualTo("2023150090");
    }

    @Test
    @DisplayName("import 失败 → sessionOk=false")
    void importFailsNoSessionOk() {
        BookingContext ctx = new BookingContext(new BookingRequest.Builder().build(), account);
        when(store.exists()).thenReturn(true);
        when(browser.importStorageState(any())).thenReturn(false);

        new RestoreSessionStep(store, probe, Duration.ofDays(30))
            .execute(browser, ctx);

        assertThat(ctx.sessionOk()).isFalse();
        verify(probe, never()).isAlive(any());
    }

    @Test
    @DisplayName("TTL 过期 → 不 import 直接走重登")
    void staleTtlSkipsImport() {
        BookingContext ctx = new BookingContext(new BookingRequest.Builder().build(), account);
        when(store.exists()).thenReturn(true);
        when(store.isFresh(Duration.ofDays(30))).thenReturn(false);

        new RestoreSessionStep(store, probe, Duration.ofDays(30))
            .execute(browser, ctx);

        assertThat(ctx.sessionOk()).isFalse();
        verify(browser, never()).importStorageState(any());
    }

    @Test
    @DisplayName("探针 Stale → sessionOk=false,删旧文件")
    void probeStaleDeletes() throws IOException {
        BookingContext ctx = new BookingContext(new BookingRequest.Builder().build(), account);
        when(store.exists()).thenReturn(true);
        when(store.isFresh(Duration.ofDays(30))).thenReturn(true);
        when(browser.importStorageState(any())).thenReturn(true);
        when(probe.isAlive(browser)).thenReturn(
            new SessionResult.Stale("timeout"));

        new RestoreSessionStep(store, probe, Duration.ofDays(30))
            .execute(browser, ctx);

        assertThat(ctx.sessionOk()).isFalse();
        verify(store).deleteIfExists();
    }
}
```

- [ ] **Step 2: 跑测试确认 RED**

```bash
mvn -q test -Dtest=RestoreSessionStepTest
```

预期:FAIL,"Cannot resolve symbol RestoreSessionStep"。

- [ ] **Step 3: 实现 `RestoreSessionStep.java`**

```java
package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionResult;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.BookingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

/**
 * Step that tries to restore a previously persisted login state and
 * validates it with {@link SessionProbe}. On success, sets
 * {@link BookingContext#sessionOk(boolean)} true so {@link CasLoginStep}
 * can skip its work.
 *
 * <p>// Design Pattern: Strategy (concrete step in pipeline)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class RestoreSessionStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(RestoreSessionStep.class);

    private final SessionStore store;
    private final SessionProbe probe;
    private final Duration ttl;

    public RestoreSessionStep(SessionStore store, SessionProbe probe, Duration ttl) {
        this.store = Objects.requireNonNull(store, "store");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
    }

    @Override
    public String name() {
        return "RESTORE_SESSION";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        if (!store.exists() || !store.isFresh(ttl)) {
            log.info("No fresh persisted state for user {}", ctx.username());
            ctx.sessionOk(false);
            return null;
        }

        boolean loaded = browser.importStorageState(store.defaultPath());
        if (!loaded) {
            log.info("import returned false for user {}", ctx.username());
            ctx.sessionOk(false);
            return null;
        }

        SessionResult result = probe.isAlive(browser);
        if (result instanceof SessionResult.Fresh) {
            log.info("Reusing persisted state for user {}", ctx.username());
            ctx.sessionOk(true);
        } else {
            log.info("Persisted state stale, will re-login: {}",
                result instanceof SessionResult.Stale s ? s.reason() : "unknown");
            try {
                store.deleteIfExists();
            } catch (IOException e) {
                log.warn("Failed to delete stale storage state: {}", e.getMessage());
            }
            ctx.sessionOk(false);
        }
        return null;
    }
}
```

- [ ] **Step 4: 写 `PersistSessionStepTest.java`(失败)**

```java
package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.Homework;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersistSessionStep")
class PersistSessionStepTest {

    @Mock private BrowserLifecycle browser;
    @Mock private SessionStore store;

    @Test
    @DisplayName("homeworks 非空 → export")
    void successExports() throws IOException {
        Account account = new Account("2023150090", "secret", "test");
        BookingContext ctx = new BookingContext(new BookingRequest.Builder().build(), account);
        ctx.username("2023150090");
        ctx.homeworks(List.of(new Homework("1", "OS", "lab", "2026.06.24", "待提交")));

        new PersistSessionStep(store).execute(browser, ctx);

        verify(browser).exportStorageState(store.defaultPath());
    }

    @Test
    @DisplayName("homeworks 为空 → 不 export")
    void emptySkips() throws IOException {
        Account account = new Account("2023150090", "secret", "test");
        BookingContext ctx = new BookingContext(new BookingRequest.Builder().build(), account);
        ctx.username("2023150090");
        ctx.homeworks(List.of());

        new PersistSessionStep(store).execute(browser, ctx);

        verify(browser, never()).exportStorageState(any());
    }
}
```

- [ ] **Step 5: 跑 `PersistSessionStepTest` 确认 RED**

```bash
mvn -q test -Dtest=PersistSessionStepTest
```

预期:FAIL,"Cannot resolve symbol PersistSessionStep"。

- [ ] **Step 6: 实现 `PersistSessionStep.java`**

```java
package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.BookingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Step that persists the current browser login state to disk after a
 * successful booking flow. Writes a Playwright storageState JSON.
 *
 * <p>// Design Pattern: Strategy (concrete step in pipeline)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class PersistSessionStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(PersistSessionStep.class);

    private final SessionStore store;

    public PersistSessionStep(SessionStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "PERSIST_SESSION";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        if (ctx.homeworks() == null || ctx.homeworks().isEmpty()) {
            log.info("Skip persist: no homeworks captured for user {}", ctx.username());
            return null;
        }
        try {
            browser.exportStorageState(store.defaultPath());
            log.info("Persisted login state for user {}", ctx.username());
        } catch (RuntimeException e) {
            log.warn("Failed to persist state: {}", e.getMessage());
        }
        return null;
    }
}
```

- [ ] **Step 7: 跑两个新测试确认 GREEN**

```bash
mvn -q test -Dtest=RestoreSessionStepTest,PersistSessionStepTest
```

预期:PASS,7 个测试(4+3)全过。

- [ ] **Step 8: Commit(与 Task 5、6 一起)**

```bash
git add src/main/java/edu/szu/agent/client/step/RestoreSessionStep.java \
        src/main/java/edu/szu/agent/client/step/PersistSessionStep.java \
        src/main/java/edu/szu/agent/client/step/BookingContext.java \
        src/test/java/edu/szu/agent/browser/FakeBrowser.java \
        src/test/java/edu/szu/agent/client/step/RestoreSessionStepTest.java \
        src/test/java/edu/szu/agent/client/step/PersistSessionStepTest.java
git commit -m "feat(session): add RestoreSession/PersistSession steps + context fields"
```

---

## Task 8: CasLoginStep 头部加 sessionOk 跳过判断

**Files:**
- Modify: `src/main/java/edu/szu/agent/client/step/CasLoginStep.java:111-117`

- [ ] **Step 1: 在 `execute` 开头加判断**

在 `execute` 方法 `var account = ctx.account();` 这一行**之前**加:

```java
        if (ctx.sessionOk()) {
            log.info("Skipping CAS login, persisted state valid for {}",
                ctx.account() != null ? ctx.account().studentId() : "unknown");
            return null;
        }
```

- [ ] **Step 2: 跑全量测试确认 GREEN(不应有回归)**

```bash
mvn -q test
```

预期:Tests run: 350+,Failures: 0,Errors: 0,Skipped: 0。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/edu/szu/agent/client/step/CasLoginStep.java
git commit -m "feat(step): CasLoginStep skips when ctx.sessionOk() is true"
```

---

## Task 9: ChaoxingHomeworkClient 接新 steps

**Files:**
- Modify: `src/main/java/edu/szu/agent/client/ChaoxingHomeworkClient.java`

- [ ] **Step 1: 在 `list()` 方法的 steps 列表前置 `RestoreSessionStep`,后置 `PersistSessionStep`**

读 `ChaoxingHomeworkClient.java`,找到构造方法,新增一个接受 `SessionStore` + `SessionProbe` 的构造器(原有 4 参构造器保留,标记 `@Deprecated` 或直接保留兼容):

```java
    private final SessionStore sessionStore;
    private final SessionProbe sessionProbe;
    private final Duration sessionTtl;

    public ChaoxingHomeworkClient(Account account, BrowserLifecycle browser,
                                  RetryPolicy retryPolicy, List<BookingStep> steps,
                                  SessionStore sessionStore, SessionProbe sessionProbe,
                                  Duration sessionTtl) {
        super(account, browser, retryPolicy, steps);
        this.sessionStore = sessionStore;
        this.sessionProbe = sessionProbe;
        this.sessionTtl = sessionTtl;
    }
```

在 `list()` 内部 `executeSteps()` 调用前,先设置 `ctx.username(account.studentId())`。

- [ ] **Step 2: 跑全量测试确认 GREEN(可能需要更新 `ChaoxingHomeworkClientTest`)**

```bash
mvn -q test -Dtest=ChaoxingHomeworkClientTest
```

预期:PASS(原 4 个测试,因构造器是新增,旧构造器仍可用)。

- [ ] **Step 3: 加一个测试验证新 steps 接入**

在 `ChaoxingHomeworkClientTest.java` 加:

```java
    @Test
    @DisplayName("list() 接受 SessionStore/Probe/Ttl 构造器")
    void listWithSessionDependencies() {
        SessionStore store = mock(SessionStore.class);
        SessionProbe probe = mock(SessionProbe.class);
        Homework expected = new Homework("1", "OS", "lab", "2026.06.24", "待提交");
        ChaoxingHomeworkClient client = new ChaoxingHomeworkClient(
            account, browser, RetryPolicies.quickFix(),
            List.of(captureHomeworks("S1", List.of(expected))),
            store, probe, Duration.ofDays(30));
        // 走流程
        HomeworkListResult result = client.list();
        assertThat(result).isInstanceOf(HomeworkListResult.Success.class);
    }
```

需要 import `edu.szu.agent.client.session.SessionStore` / `SessionProbe` / `java.time.Duration` / `static org.mockito.Mockito.mock`。

- [ ] **Step 4: 跑测试确认 GREEN**

```bash
mvn -q test -Dtest=ChaoxingHomeworkClientTest
```

预期:PASS,5 个测试。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/edu/szu/agent/client/ChaoxingHomeworkClient.java \
        src/test/java/edu/szu/agent/client/ChaoxingHomeworkClientTest.java
git commit -m "feat(homework): wire SessionStore/Probe into ChaoxingHomeworkClient"
```

---

## Task 10: BrowserLifecycle 接口加 2 方法

**Files:**
- Modify: `src/main/java/edu/szu/agent/browser/BrowserLifecycle.java`

- [ ] **Step 1: 在接口末尾加 2 个方法**

在 `// Phase 2 complete: all 10 methods of ADR-0002 D1 implemented.` 这一行**之前**加:

```java
    /**
     * Loads cookies + localStorage from a Playwright storageState JSON file.
     * Missing or invalid file silently returns {@code false}; callers should
     * fall back to re-login.
     *
     * @param storageStateFile path to a Playwright storageState JSON; must not be null
     * @return {@code true} if the file existed and was parsed, {@code false} otherwise
     * @since 0.1.0
     */
    boolean importStorageState(java.nio.file.Path storageStateFile);

    /**
     * Saves current cookies + localStorage to a Playwright storageState JSON file.
     * Overwrites any existing file at the same path.
     *
     * @param storageStateFile path to write to; must not be null
     * @throws edu.szu.agent.error.BookingException with SESSION_WRITE_FAILED on disk-write error
     * @since 0.1.0
     */
    void exportStorageState(java.nio.file.Path storageStateFile);
```

把 `// Phase 2 complete: all 10 methods` 改成 `// Phase 5 (US-007): now 12 methods.`

- [ ] **Step 2: 跑全量测试确认 GREEN**

```bash
mvn -q test
```

预期:Tests run: 350+,Failures: 0,Errors: 0,Skipped: 0。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/edu/szu/agent/browser/BrowserLifecycle.java
git commit -m "feat(browser): BrowserLifecycle gains import/exportStorageState (12 methods)"
```

---

## Task 11: PlaywrightBrowserAdapter 改 BrowserContext + 实现 2 方法

**Files:**
- Modify: `src/main/java/edu/szu/agent/browser/PlaywrightBrowserAdapter.java`

- [ ] **Step 1: 改字段 `page` → `page` + `context`**

把:
```java
    private final Playwright playwright;
    private final boolean headless;
    private Browser browser;
    private Page page;
```

改成:
```java
    private final Playwright playwright;
    private final boolean headless;
    private Browser browser;
    private BrowserContext context;
    private Page page;
```

加 import `com.microsoft.playwright.BrowserContext`。

- [ ] **Step 2: 改 `open()` 用 BrowserContext**

```java
    @Override
    public void open() {
        try {
            browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(headless));
            context = browser.newContext();
            page = context.newPage();
            long navTimeout = Long.getLong("szu.agent.nav-timeout-ms", 60_000L);
            page.setDefaultNavigationTimeout(navTimeout);
            page.setDefaultTimeout(navTimeout);
        } catch (Exception e) {
            throw mapException(e);
        }
    }
```

- [ ] **Step 3: 改 `close()` 关闭 page + context + browser**

```java
    @Override
    public void close() {
        try {
            if (page != null) {
                page.close();
                page = null;
            }
            if (context != null) {
                context.close();
                context = null;
            }
            if (browser != null) {
                browser.close();
                browser = null;
            }
        } catch (Exception e) {
            throw mapException(e);
        }
    }
```

- [ ] **Step 4: 实现 `importStorageState` / `exportStorageState`**

```java
    @Override
    public boolean importStorageState(java.nio.file.Path storageStateFile) {
        Objects.requireNonNull(storageStateFile, "storageStateFile");
        if (!java.nio.file.Files.exists(storageStateFile)) {
            return false;
        }
        try {
            String raw = java.nio.file.Files.readString(storageStateFile);
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(raw);
            // Cookies
            if (root.has("cookies") && context != null) {
                context.addCookies(
                    new com.fasterxml.jackson.databind.ObjectMapper()
                        .convertValue(root.get("cookies"),
                            new com.fasterxml.jackson.core.type.TypeReference<
                                java.util.List<com.microsoft.playwright.options.Cookie>>() {}));
            }
            // LocalStorage via addInitScript
            if (root.has("origins") && context != null) {
                StringBuilder script = new StringBuilder();
                root.get("origins").forEach(origin -> {
                    if (origin.has("localStorage")) {
                        origin.get("localStorage").forEach(entry -> {
                            String name = entry.path("name").asText();
                            String value = entry.path("value").asText();
                            script.append("localStorage.setItem(")
                                .append(jsString(name)).append(",")
                                .append(jsString(value)).append(");");
                        });
                    }
                });
                if (script.length() > 0) {
                    context.addInitScript(script.toString());
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("Failed to import storage state: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void exportStorageState(java.nio.file.Path storageStateFile) {
        Objects.requireNonNull(storageStateFile, "storageStateFile");
        if (context == null) {
            throw new BookingException(ErrorCode.SESSION_WRITE_FAILED,
                "no browser context to export");
        }
        try {
            context.storageState(
                new BrowserContext.StorageStateOptions().setPath(storageStateFile));
        } catch (Exception e) {
            throw new BookingException(ErrorCode.SESSION_WRITE_FAILED,
                "export failed: " + e.getMessage(), e);
        }
    }

    private static String jsString(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
```

- [ ] **Step 5: 跑全量测试确认 GREEN**

```bash
mvn -q test
```

预期:Tests run: 350+,Failures: 0,Errors: 0,Skipped: 0。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/edu/szu/agent/browser/PlaywrightBrowserAdapter.java
git commit -m "feat(playwright): BrowserContext wrapping + storageState import/export"
```

---

## Task 12: 跑全量测试

- [ ] **Step 1: 跑全量**

```bash
mvn -q test
```

预期:Tests run: 350+,Failures: 0,Errors: 0,Skipped: 0,BUILD SUCCESS。

- [ ] **Step 2: 跑覆盖率(可选)**

```bash
mvn -q test jacoco:report
```

预期:整体覆盖率 ≥ 80%。

---

## Task 13: 跑构建

- [ ] **Step 1: 构建 jar**

```bash
mvn -q -DskipTests package
```

预期:BUILD SUCCESS,`target/szu-agent-plugin.jar` 存在。

---

## Task 14: 提交 + 推送

- [ ] **Step 1: 检查 git status**

```bash
git status --short
```

预期:无未跟踪文件(untracked docs/adr/0008-...md / docs/stories/US-007-...md / harness-records/traces/... 还没建,正常)。

- [ ] **Step 2: 暂不 commit 文档(等 Task 15-18 一起)**

---

## Task 15: 更新 system-map.md + design-patterns.md

**Files:**
- Modify: `docs/system-map.md`(加 1.小节 + 7. ADR 索引)
- Modify: `docs/design-patterns.md`(如果有,加新 step 落点)

- [ ] **Step 1: 在 system-map.md §1 拓扑加新模块**

在 `└── client/ChaoxingHomeworkClient` 后加:

```text
├── client/
│   ├── session/                       # US-007 登录态持久化
│   │   ├── SessionStore               # 路径解析 + 权限 + TTL
│   │   ├── SessionProbe               # 探针判定
│   │   └── SessionResult              # sealed Fresh/Stale
│   └── step/
│       ├── RestoreSessionStep         # 前置:试 import
│       └── PersistSessionStep         # 后置:成功 export
```

- [ ] **Step 2: 在 system-map.md §7 加 ADR-0008 行**

```markdown
| ADR-0008 | 登录态持久化(storageState + 30d TTL + 探针) | `docs/adr/0008-session-persistence.md` |
```

- [ ] **Step 3: Commit**

```bash
git add docs/system-map.md docs/design-patterns.md
git commit -m "docs(system-map): add US-007 session persistence module + ADR-0008"
```

---

## Task 16: 创建 Story US-007

**Files:**
- Create: `docs/stories/US-007-session-persistence.md`

- [ ] **Step 1: 写 story 文件**

复制 `docs/stories/US-006-chaoxing-homework-list.md` 的结构,内容参考 spec § 1-7,验收标准用 spec § 8 退出条件。

- [ ] **Step 2: Commit**

```bash
git add docs/stories/US-007-session-persistence.md
git commit -m "docs(story): US-007 session persistence story packet"
```

---

## Task 17: 创建 ADR-0008

**Files:**
- Create: `docs/adr/0008-session-persistence.md`

- [ ] **Step 1: 写 ADR**

模板参考 `docs/adr/0005-credential-and-logging-enforcement.md`,包含:
- **Context**: 4 个核心决策(架构落点 / 存储位置 / 会话语义 / 接口签名)与备选方案对比
- **Decisions**: 4 个 D1-D4
- **Consequences**: 好处 / 代价
- **实施细节**: 文件列表

- [ ] **Step 2: Commit**

```bash
git add docs/adr/0008-session-persistence.md
git commit -m "docs(adr): ADR-0008 session persistence decisions"
```

---

## Task 18: 创建 Trace 文件

**Files:**
- Create: `harness-records/traces/YYYYMMDD-HHMMSS-US-007.md`(用真实时间戳)

- [ ] **Step 1: 写 trace**

参考 US-006 trace 格式(如果有),记:
- 时间戳、故事 ID
- 变更的文件列表
- 阅读的文件列表
- 验证结果(`mvn test` 通过数)
- 决策(指向 ADR-0008)
- 摩擦(无)

- [ ] **Step 2: Commit**

```bash
git add harness-records/traces/YYYYMMDD-HHMMSS-US-007.md
git commit -m "docs(trace): record US-007 session persistence implementation"
```

---

## Task 19: 最终验证

- [ ] **Step 1: git status 干净**

```bash
git status --short
```

预期:空输出。

- [ ] **Step 2: 跑最后一次全量测试**

```bash
mvn -q test
```

预期:BUILD SUCCESS,Tests run: 350+。

- [ ] **Step 3: 报告完成**

写一个 `result:` 行给用户(自包含的一句话),说明 US-007 完成 + 关键数字(测试数、commit 数、文件数)。

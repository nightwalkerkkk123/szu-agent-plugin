# US-008 畅课作业附件下载 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `homework download` 子命令,登录 LMS 后进入作业详情页,抓取附件列表,逐个下载到本地目录。复用 US-007 登录态持久化。

**Architecture:** 新建 `ChaoxingAttachmentDownloadClient`(独立管线,独立重试),与 `ChaoxingHomeworkClient` 解耦但共享 `SessionStore` / `SessionProbe` / `BrowserLifecycle` 12 方法。`BrowserLifecycle` 扩 1 个 `downloadAttachment(String url, Path target) -> long` 方法(返回 size bytes),Playwright `BrowserContext.request` 自带 cookie,无需手动注入。附件文件名经过 `FilenameSanitizer` 清洗(防 path traversal + Windows 非法字符),多附件下载固定 500ms 节流,冲突时按 `(1)` / `(2)` 递增。

**Tech Stack:** Java 21, Maven 3.9+, Playwright 1.45.0(已有), JUnit 5 + AssertJ + Mockito, JUnit `@TempDir`, `java.nio.file.attribute.PosixFilePermission` (复用).

**Spec:** `docs/superpowers/specs/2026-06-18-us-008-attachment-download-design.md`(Task 0 创建)
**Story:** `docs/stories/US-008-homework-attachment-download.md`(已创建,2026-06-18)
**ADR:** `docs/adr/0009-attachment-download.md`(Task 19 创建)

---

## ⚠️ 实施前需知(已用真实抓包校准 2026-06-18)

**LMS 详情页结构已知**(用户 2026-06-17 抓包数据 `docs/superpowers/research/2026-06-17-lms-findings.md`):

- 详情页 URL 简化方案:`https://lms.szu.edu.cn/user/index#/<homeworkId>`(AngularJS hash 路由,绕过 courseId)
- 附件行: `.attachment-row.preview-able`
- 文件名 = `.file-name` text + `.file-extension` text(JS 端拼接)
- 文件大小: `.attachment-size`
- 下载链接: `a[ng-href*="/api/uploads/reference/"]` 的 `href`

**下载机制**(HAR #156 验证):
- 真实下载 URL: `https://media2.szu.edu.cn/download/file/<hash>?timestamp=<unix>&token=<hex>&name=<urlencoded-name>`
- 签名 URL 自带 token,**不需要 cookie**
- API 端点(`/api/uploads/reference/<id>/blob`)与 media2 URL 是间接关系,由 AngularJS `downloadBlob()` 客户端逻辑桥接

**实现方案**:
- `ParseAttachmentsStep` 提取 `href` 属性(可能是 lms API 端点或 media2 签名 URL)
- `BrowserLifecycle.downloadAttachment(url, target)` 统一处理两种域:media2 直接 GET, lms GET 跟重定向

如果实际与 spec 不符,只需调整 `ParseAttachmentsStep` 内的 JS 选择器常量,其他代码不变。

---

## 工作顺序总览

| Task | 内容 | 依赖 | 预估测试数 |
|---|---|---|---|
| 0 | 设计 spec(10 节) | 无 | 0 |
| 1 | `ErrorCode` 加 3 枚举值 | 无 | +3 |
| 2 | `HomeworkAttachment` record(domain) | 无 | 0 |
| 3 | `HomeworkDownloadRequest` Builder(domain) | 无 | 0 |
| 4 | `HomeworkDownloadResult` sealed(domain) | Task 2 | 0 |
| 5 | `FilenameSanitizer` 工具 + 测试(client/homework/attachment/) | 无 | +12 |
| 6 | `BookingContext` 加 `attachments` / `outputDir` 字段 | Task 4 | 0 |
| 7 | `HomeworkDownloadRequestTest` 单元测试 | Task 3 | +3 |
| 8 | `NavigateToHomeworkDetailStep` + 测试 | Task 6 | +4 |
| 9 | `ParseAttachmentsStep` + 测试 | Task 5, 8 | +5 |
| 10 | `BrowserLifecycle` 加 `downloadAttachment` | Task 9 | 0(接口) |
| 11 | `PlaywrightBrowserAdapter` 实现 `downloadAttachment` | Task 10 | 0(改实现) |
| 12 | `DownloadFilesStep` + 测试 | Task 5, 9, 10 | +8 |
| 13 | `ChaoxingAttachmentDownloadClient` | Task 8, 9, 12 | +5 |
| 14 | `FakeBrowser` 加 `downloadAttachment` no-op + 标志 | 无 | 0 |
| 15 | `HomeworkDownloadCommand` (CLI) | Task 13 | +2 |
| 16 | `Skills` / `MCPToolProvider` 注册 `homework_download` | Task 13 | 0 |
| 17 | `mvn test` 全量 | Task 16 | (汇总) |
| 18 | `mvn -q -DskipTests package` 构建 | Task 17 | - |
| 19 | ADR-0009 | Task 18 | 0 |
| 20 | Trace 文件 + 系统映射更新 | Task 19 | 0 |
| 21 | 最终验证 | Task 20 | - |

**预计总测试数**:351(US-007) + 3 + 12 + 3 + 4 + 5 + 8 + 5 + 2 = **391**

---

## Task 0: 设计 spec(10 节)

**Files:**
- Create: `docs/superpowers/specs/2026-06-18-us-008-attachment-download-design.md`

- [ ] **Step 1: 写 spec**

10 节内容(参考 `docs/superpowers/specs/2026-06-14-us-007-session-persistence-design.md` 风格):
1. 背景与目标
2. 关键概念(LMS 详情页 / 附件 / 节流 / 清洗)
3. 架构总览
4. 数据模型(HomeworkAttachment / HomeworkDownloadRequest / HomeworkDownloadResult / HomeworkDownloadResult.Empty)
5. 接口设计(BrowserLifecycle.downloadAttachment + new Client + new Steps)
6. LMS 页面交互(选择器 + URL 模式 + 节流点)
7. 文件名清洗规则(正则 + 碰撞重命名)
8. 错误码(ErrorCode 3 新枚举值的元数据)
9. 测试策略(单元 + 集成)
10. 退出条件 + 后续观察

- [ ] **Step 2: Commit**(可选,可与 Task 19 一起)

---

## Task 1: ErrorCode 加 3 枚举值

**Files:**
- Modify: `src/main/java/edu/szu/agent/error/ErrorCode.java`(在 `SESSION_WRITE_FAILED` 行后加 3 枚举值,共 20)
- Modify: `src/test/java/edu/szu/agent/error/ErrorCodeTest.java`(加 3 个元数据测试 + 调整总数断言)

- [ ] **Step 1: 写失败的测试**

在 `ErrorCodeTest.java` 加 3 个测试,期望枚举总数从 17 变 20。

- [ ] **Step 2: 跑 RED**

```bash
mvn -q test -Dtest=ErrorCodeTest
```

- [ ] **Step 3: 实现**

```java
    // 作业附件下载(US-008)
    /** 作业详情页无附件。*/
    ATTACHMENT_NOT_FOUND   (Severity.LOW,    false, false, false, "作业无附件"),
    /** 附件下载失败(HTTP / 写文件)。*/
    ATTACHMENT_DOWNLOAD_FAILED (Severity.MEDIUM, true,  false, true,  "附件下载失败"),
    /** 输出目录非法(不存在 / 不可写 / 不是目录)。*/
    OUTPUT_DIR_INVALID     (Severity.MEDIUM, false, false, false, "输出目录非法");
```

注意:前一个枚举值行末补逗号,最后一个分号结尾。

- [ ] **Step 4: 跑 GREEN**

```bash
mvn -q test -Dtest=ErrorCodeTest
```

预期:3 个新测试 + 原有 17 个枚举值都通过(共 20 个)。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/edu/szu/agent/error/ErrorCode.java \
        src/test/java/edu/szu/agent/error/ErrorCodeTest.java
git commit -m "feat(error): add ATTACHMENT_NOT_FOUND/DOWNLOAD_FAILED/OUTPUT_DIR_INVALID codes"
```

---

## Task 2: HomeworkAttachment record

**Files:**
- Create: `src/main/java/edu/szu/agent/domain/HomeworkAttachment.java`

- [ ] **Step 1: 实现**(无测试,纯 record)

```java
package edu.szu.agent.domain;

import java.time.Instant;

/**
 * A single attachment from a Chaoxing homework detail page.
 *
 * <p>Immutable value object. Populated by
 * {@link edu.szu.agent.client.step.ParseAttachmentsStep} and surfaced
 * through {@code homework download} / {@code skill homework_download}.
 *
 * // 编程技术: record(不可变值对象)
 *
 * @param homeworkId   the homework this attachment belongs to
 * @param fileName     sanitized local file name (no path separators)
 * @param sourceUrl    original download URL on LMS (CAS-protected)
 * @param localPath    absolute path on disk after successful download
 * @param sizeBytes    file size in bytes, or 0 if unknown
 * @param downloadedAt timestamp at which the download finished
 * @since 0.1.0
 * @author 王子豪
 */
public record HomeworkAttachment(String homeworkId, String fileName, String sourceUrl,
                                 java.nio.file.Path localPath, long sizeBytes,
                                 Instant downloadedAt) {
}
```

- [ ] **Step 2: 跑编译验证**

```bash
mvn -q -DskipTests compile
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/edu/szu/agent/domain/HomeworkAttachment.java
git commit -m "feat(domain): add HomeworkAttachment record"
```

---

## Task 3: HomeworkDownloadRequest Builder

**Files:**
- Create: `src/main/java/edu/szu/agent/domain/HomeworkDownloadRequest.java`

- [ ] **Step 1: 实现**

```java
package edu.szu.agent.domain;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Parameters for a single attachment download operation.
 *
 * <p>Built via {@link Builder} so future params (e.g. retry count, proxy)
 * can be added without breaking existing call sites.
 *
 * // Design Pattern: Builder
 * // 编程技术: 不可变 + 链式构造
 *
 * @since 0.1.0
 * @author 王子豪
 */
public record HomeworkDownloadRequest(String homeworkId, Path outputDir,
                                       Duration throttle, int maxRetries) {

    public HomeworkDownloadRequest {
        Objects.requireNonNull(homeworkId, "homeworkId");
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(throttle, "throttle");
        if (homeworkId.isBlank()) {
            throw new IllegalArgumentException("homeworkId must not be blank");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String homeworkId;
        private Path outputDir;
        private Duration throttle = Duration.ofMillis(500);  // 默认 500ms 节流
        private int maxRetries = 2;  // 默认 2 次重试

        public Builder homeworkId(String homeworkId) { this.homeworkId = homeworkId; return this; }
        public Builder outputDir(Path outputDir) { this.outputDir = outputDir; return this; }
        public Builder throttle(Duration throttle) { this.throttle = throttle; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }

        public HomeworkDownloadRequest build() {
            return new HomeworkDownloadRequest(homeworkId, outputDir, throttle, maxRetries);
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn -q -DskipTests compile
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/edu/szu/agent/domain/HomeworkDownloadRequest.java
git commit -m "feat(domain): add HomeworkDownloadRequest Builder"
```

---

## Task 4: HomeworkDownloadResult sealed

**Files:**
- Create: `src/main/java/edu/szu/agent/domain/HomeworkDownloadResult.java`

- [ ] **Step 1: 实现**

```java
package edu.szu.agent.domain;

import edu.szu.agent.error.ErrorCode;

import java.util.List;

/**
 * Outcome of a {@code homework download} operation.
 *
 * <p>Sealed to give callers a finite set of cases to handle:
 * <ul>
 *   <li>{@link Success} — at least one attachment downloaded
 *   <li>{@link Empty} — homework has no attachments (NOT an error)
 *   <li>{@link Failure} — operation failed
 * </ul>
 *
 * // 编程技术: sealed interface + record
 *
 * @since 0.1.0
 * @author 王子豪
 */
public sealed interface HomeworkDownloadResult
    permits HomeworkDownloadResult.Success, HomeworkDownloadResult.Empty,
            HomeworkDownloadResult.Failure {

    record Success(List<HomeworkAttachment> attachments) implements HomeworkDownloadResult {}

    record Empty(String homeworkId) implements HomeworkDownloadResult {}

    record Failure(ErrorCode code, String message) implements HomeworkDownloadResult {}
}
```

- [ ] **Step 2: 编译**

```bash
mvn -q -DskipTests compile
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/edu/szu/agent/domain/HomeworkDownloadResult.java
git commit -m "feat(domain): add HomeworkDownloadResult sealed type (Success/Empty/Failure)"
```

---

## Task 5: FilenameSanitizer + 测试(核心安全工具)

**Files:**
- Create: `src/main/java/edu/szu/agent/client/homework/attachment/FilenameSanitizer.java`
- Create: `src/test/java/edu/szu/agent/client/homework/attachment/FilenameSanitizerTest.java`

- [ ] **Step 1: 写失败的测试**

`FilenameSanitizerTest.java`(12 用例):

```java
package edu.szu.agent.client.homework.attachment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FilenameSanitizer")
class FilenameSanitizerTest {

    @Test
    @DisplayName("sanitize 保留合法文件名")
    void sanitizeKeepsValid() {
        assertThat(FilenameSanitizer.sanitize("lab1.pdf")).isEqualTo("lab1.pdf");
        assertThat(FilenameSanitizer.sanitize("实验一.docx")).isEqualTo("实验一.docx");
        assertThat(FilenameSanitizer.sanitize("data_v2.0.zip")).isEqualTo("data_v2.0.zip");
    }

    @ParameterizedTest
    @CsvSource({
        "file<name>.txt, file_name_.txt",
        "file>name.txt, file_name.txt",
        "file:name.txt, file_name.txt",
        "file\"name.txt, file_name.txt",
        "file/name.txt, file_name.txt",
        "file\\name.txt, file_name.txt",
        "file|name.txt, file_name.txt",
        "file?name.txt, file_name.txt",
        "file*name.txt, file_name.txt",
    })
    @DisplayName("sanitize 把 Windows 非法字符替换为 _")
    void sanitizeReplacesIllegalChars(String input, String expected) {
        assertThat(FilenameSanitizer.sanitize(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("sanitize 剥离路径分隔符后只剩 basename")
    void sanitizeStripsPath() {
        assertThat(FilenameSanitizer.sanitize("../../etc/passwd")).isEqualTo("passwd");
        assertThat(FilenameSanitizer.sanitize("/absolute/path/file.txt")).isEqualTo("file.txt");
    }

    @Test
    @DisplayName("sanitize 完全清洗后为空时返回 attachment_N 兜底")
    void sanitizeEmptyFallback() {
        assertThat(FilenameSanitizer.sanitize("...")).isEqualTo("attachment_1");
        assertThat(FilenameSanitizer.sanitize("///")).isEqualTo("attachment_2");
        assertThat(FilenameSanitizer.sanitize("")).isEqualTo("attachment_3");
    }

    @Test
    @DisplayName("sanitize 把控制字符(0x00-0x1f)替换为 _")
    void sanitizeStripsControlChars() {
        assertThat(FilenameSanitizer.sanitize("file name.txt")).isEqualTo("file_name.txt");
        assertThat(FilenameSanitizer.sanitize("file\nname.txt")).isEqualTo("file_name.txt");
    }

    @Test
    @DisplayName("sanitize 截断超长文件名到 200 字符")
    void sanitizeTruncatesLong() {
        String longName = "a".repeat(300) + ".pdf";
        String result = FilenameSanitizer.sanitize(longName);
        assertThat(result).hasSizeLessThanOrEqualTo(200);
        assertThat(result).endsWith(".pdf");
    }

    @Test
    @DisplayName("uniqueName 冲突时追加 (1)")
    void uniqueNameAppendsOne() {
        java.nio.file.Path dir = java.nio.file.Path.of("/tmp");
        String name = FilenameSanitizer.uniqueName(dir, "lab.pdf", java.util.Set.of());
        assertThat(name).isEqualTo("lab.pdf");
    }

    @Test
    @DisplayName("uniqueName 冲突时追加 (1) (2) (3) ...递增")
    void uniqueNameIncrements() {
        java.nio.file.Path dir = java.nio.file.Path.of("/tmp");
        java.util.Set<String> existing = java.util.Set.of("lab.pdf", "lab (1).pdf", "lab (2).pdf");
        String name = FilenameSanitizer.uniqueName(dir, "lab.pdf", existing);
        assertThat(name).isEqualTo("lab (3).pdf");
    }
}
```

- [ ] **Step 2: 跑 RED**

```bash
mvn -q test -Dtest=FilenameSanitizerTest
```

- [ ] **Step 3: 实现 `FilenameSanitizer`**

```java
package edu.szu.agent.client.homework.attachment;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sanitizes LMS attachment file names for safe local file system writes.
 *
 * <p>Rules:
 * <ol>
 *   <li>Strip path components (basename only)</li>
 *   <li>Replace Windows-illegal chars {@code < > : " / \ | ? *} with {@code _}</li>
 *   <li>Replace control chars (0x00-0x1F) with {@code _}</li>
 *   <li>Trim leading/trailing dots and spaces</li>
 *   <li>If result is empty after cleaning, use {@code attachment_N} fallback</li>
 *   <li>Truncate to 200 chars max</li>
 * </ol>
 *
 * <p>// Design Pattern: Strategy (concrete sanitizer implementation)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class FilenameSanitizer {

    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1f]");
    private static final int MAX_NAME_LENGTH = 200;
    private static final Pattern COLLISION_SUFFIX = Pattern.compile("\\((\\d+)\\)$");

    private FilenameSanitizer() {
        // utility class
    }

    /**
     * Sanitizes a raw file name from LMS for safe local use.
     *
     * @param raw the original file name (may contain path, illegal chars)
     * @return a sanitized file name safe to use as a {@code Path} leaf
     */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "attachment_0";
        }
        // Step 1: strip path components
        String name = raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        // Step 2-3: replace illegal + control chars
        name = ILLEGAL_CHARS.matcher(name).replaceAll("_");
        // Step 4: trim dots and spaces
        name = name.replaceAll("^[.\\s]+|[.\\s]+$", "");
        // Step 5: fallback if empty
        if (name.isEmpty()) {
            return nextFallback();
        }
        // Step 6: truncate
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        return name;
    }

    /**
     * Generates a unique file name in the given directory by appending
     * {@code (1)}, {@code (2)}, etc. to avoid overwriting existing files.
     */
    public static String uniqueName(Path dir, String sanitized, Set<String> existing) {
        if (!existing.contains(sanitized)) {
            return sanitized;
        }
        // Extract base without existing collision suffix
        String base = sanitized;
        String ext = "";
        int dotIdx = sanitized.lastIndexOf('.');
        if (dotIdx > 0) {
            base = sanitized.substring(0, dotIdx);
            ext = sanitized.substring(dotIdx);
        }
        int counter = 1;
        while (true) {
            String candidate = base + " (" + counter + ")" + ext;
            if (!existing.contains(candidate)) {
                return candidate;
            }
            counter++;
            if (counter > 9999) {
                throw new IllegalStateException("too many name collisions for " + sanitized);
            }
        }
    }

    private static int fallbackCounter = 0;
    private static synchronized String nextFallback() {
        return "attachment_" + (++fallbackCounter);
    }
}
```

**注意**:`fallbackCounter` 用 static 字段会在测试间污染,需要在 `@AfterEach` 重置。改用 `AtomicInteger` 或在测试中清空。最简单:把它改为方法参数(不优雅),或用 ThreadLocal,或接受测试间有微小 counter 偏移(只要每个测试的输入唯一,counter 就唯一)。

**修复**:把 `nextFallback` 改为接受 int 参数,测试传入不同值。或者把 fallbackCounter 改为 instance field,提供 package-private setter 给测试。**最简单**:测试断言里只检查 `attachment_` 前缀 + 非空。

让我重新设计测试使其对 counter 不敏感:

```java
@Test
@DisplayName("sanitize 完全清洗后为空时返回 attachment_N 兜底")
void sanitizeEmptyFallback() {
    assertThat(FilenameSanitizer.sanitize("...")).startsWith("attachment_");
    assertThat(FilenameSanitizer.sanitize("///")).startsWith("attachment_");
    assertThat(FilenameSanitizer.sanitize("")).startsWith("attachment_");
}
```

- [ ] **Step 4: 跑 GREEN**

```bash
mvn -q test -Dtest=FilenameSanitizerTest
```

预期:12 个测试全过(7 个 @Test + 1 个 @ParameterizedTest 9 行 + 1 uniqueName 主路径 + 1 uniqueName 冲突 = 12 个测试名,实际 JUnit 报告可能合并)。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/edu/szu/agent/client/homework/attachment/FilenameSanitizer.java \
        src/test/java/edu/szu/agent/client/homework/attachment/FilenameSanitizerTest.java
git commit -m "feat(attachment): add FilenameSanitizer (illegal chars, path strip, collision rename)"
```

---

## Task 6: BookingContext 加 attachments / outputDir 字段

**Files:**
- Modify: `src/main/java/edu/szu/agent/client/step/BookingContext.java`

- [ ] **Step 1: 加字段 + getter/setter**

在 `private String username;` 后加:

```java
    private List<edu.szu.agent.domain.HomeworkAttachment> attachments;
    private java.nio.file.Path outputDir;
```

在 `username(String username)` setter 后加:

```java
    public List<edu.szu.agent.domain.HomeworkAttachment> attachments() {
        return attachments;
    }

    public void attachments(List<edu.szu.agent.domain.HomeworkAttachment> attachments) {
        this.attachments = attachments;
    }

    public java.nio.file.Path outputDir() {
        return outputDir;
    }

    public void outputDir(java.nio.file.Path outputDir) {
        this.outputDir = outputDir;
    }
```

- [ ] **Step 2: 编译**

```bash
mvn -q -DskipTests test-compile
```

预期:BUILD SUCCESS。

- [ ] **Step 3: Commit**(可与 Task 8 一起)

---

## Task 7: HomeworkDownloadRequestTest 单元测试

**Files:**
- Create: `src/test/java/edu/szu/agent/domain/HomeworkDownloadRequestTest.java`

- [ ] **Step 1: 写测试**

3 个用例:builder 必填校验 / 默认 throttle 500ms / 默认 maxRetries 2。

```java
package edu.szu.agent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HomeworkDownloadRequest")
class HomeworkDownloadRequestTest {

    @Test
    @DisplayName("builder 接受必填 + 选填参数")
    void builderAcceptsAllParams() {
        HomeworkDownloadRequest r = HomeworkDownloadRequest.builder()
            .homeworkId("169193")
            .outputDir(Path.of("/tmp/dl"))
            .throttle(Duration.ofMillis(1000))
            .maxRetries(3)
            .build();
        assertThat(r.homeworkId()).isEqualTo("169193");
        assertThat(r.outputDir()).isEqualTo(Path.of("/tmp/dl"));
        assertThat(r.throttle()).isEqualTo(Duration.ofMillis(1000));
        assertThat(r.maxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("builder 省略 throttle / maxRetries 时使用默认值")
    void builderDefaults() {
        HomeworkDownloadRequest r = HomeworkDownloadRequest.builder()
            .homeworkId("169193")
            .outputDir(Path.of("/tmp/dl"))
            .build();
        assertThat(r.throttle()).isEqualTo(Duration.ofMillis(500));
        assertThat(r.maxRetries()).isEqualTo(2);
    }

    @Test
    @DisplayName("homeworkId 为空字符串时抛 IllegalArgumentException")
    void builderRejectsBlankHomeworkId() {
        assertThatThrownBy(() -> HomeworkDownloadRequest.builder()
            .homeworkId("  ")
            .outputDir(Path.of("/tmp/dl"))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("homeworkId");
    }
}
```

- [ ] **Step 2: 跑 GREEN**(Task 3 已实现,这一步直接 PASS)

```bash
mvn -q test -Dtest=HomeworkDownloadRequestTest
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/edu/szu/agent/domain/HomeworkDownloadRequestTest.java
git commit -m "test(domain): add HomeworkDownloadRequestTest (3 cases)"
```

---

## Task 8: NavigateToHomeworkDetailStep + 测试

**Files:**
- Create: `src/main/java/edu/szu/agent/client/step/NavigateToHomeworkDetailStep.java`
- Create: `src/test/java/edu/szu/agent/client/step/NavigateToHomeworkDetailStepTest.java`

- [ ] **Step 1: 写测试**(4 用例)

- [ ] **Step 2: 实现**

```java
package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Step 2 — navigates from the LMS todo list to the homework detail page.
 *
 * <p>URL pattern: {@code https://lms.szu.edu.cn/...#/<homeworkId>}.
 * The {@code homeworkId} is read from {@link BookingContext#request()}
 * (set by the caller before pipeline runs).
 *
 * <p>// Design Pattern: Strategy (concrete step in pipeline)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class NavigateToHomeworkDetailStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(NavigateToHomeworkDetailStep.class);

    static final String LMS_DETAIL_URL_PREFIX = "https://lms.szu.edu.cn/user/index#/";
    static final String SEL_DETAIL_CONTAINER = ".attachment-row";

    private final String detailUrlPrefix;
    private final String detailContainerSelector;

    public NavigateToHomeworkDetailStep() {
        this(LMS_DETAIL_URL_PREFIX, SEL_DETAIL_CONTAINER);
    }

    public NavigateToHomeworkDetailStep(String detailUrlPrefix, String detailContainerSelector) {
        this.detailUrlPrefix = Objects.requireNonNull(detailUrlPrefix, "detailUrlPrefix");
        this.detailContainerSelector = Objects.requireNonNull(detailContainerSelector, "detailContainerSelector");
    }

    @Override
    public String name() {
        return "NAVIGATE_TO_HOMEWORK_DETAIL";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        if (ctx.request() == null || ctx.request().homeworkId() == null) {
            return new BookingResult.Failure(
                edu.szu.agent.error.ErrorCode.INVALID_REQUEST,
                "BookingContext.request().homeworkId is required");
        }
        // AngularJS hash route: bypass courseId, go directly to homeworkId
        String url = detailUrlPrefix + ctx.request().homeworkId();
        log.info("Navigating to detail page: {}", url);
        browser.navigateTo(url);
        // Block until at least one attachment row appears (proves page loaded)
        if (!browser.isVisible(detailContainerSelector)) {
            return new BookingResult.Failure(
                edu.szu.agent.error.ErrorCode.HOMEWORK_PAGE_LOAD_FAILED,
                "no attachment rows found at " + url);
        }
        return null;
    }
}
```

**注意**:`BookingRequest` 当前没有 `homeworkId` 字段,需要扩展。简化方案:`NavigateToHomeworkDetailStep` 直接从 `ctx.request().venueName()` 或新加 `homeworkId` 字段读取。最简:`request().campus()` 临时复用,**不正规**;正确做法:加 `homeworkId` 字段到 `BookingRequest`。

**修复**:在 `BookingRequest` 加 `String homeworkId` 字段(可选),`NavigateToHomeworkDetailStep` 从 `ctx.request().homeworkId()` 读取。`ChaoxingAttachmentDownloadClient.download(homeworkId)` 创建 `BookingRequest` 时塞入。

- [ ] **Step 3: 跑 GREEN**

```bash
mvn -q test -Dtest=NavigateToHomeworkDetailStepTest
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/edu/szu/agent/client/step/NavigateToHomeworkDetailStep.java \
        src/test/java/edu/szu/agent/client/step/NavigateToHomeworkDetailStepTest.java \
        src/main/java/edu/szu/agent/domain/BookingRequest.java
git commit -m "feat(step): add NavigateToHomeworkDetailStep (with homeworkId on request)"
```

---

## Task 9: ParseAttachmentsStep + 测试

**Files:**
- Create: `src/main/java/edu/szu/agent/client/step/ParseAttachmentsStep.java`
- Create: `src/test/java/edu/szu/agent/client/step/ParseAttachmentsStepTest.java`

- [ ] **Step 1: 写测试**(5 用例)

- [ ] **Step 2: 实现**

```java
package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.homework.attachment.FilenameSanitizer;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.HomeworkAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Step 3 — parses the attachment list from the homework detail page.
 *
 * <p>Runs a JS script that reads {@code .attachment-link} elements and
 * returns {@code [{fileName, sourceUrl}, ...]} JSON.
 *
 * <p>// Design Pattern: Strategy (concrete step in pipeline)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class ParseAttachmentsStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(ParseAttachmentsStep.class);

    static final String SEL_ATTACHMENT_ROW = ".attachment-row.preview-able";
    static final String SEL_FILE_NAME = ".file-name";
    static final String SEL_FILE_EXT = ".file-extension";
    static final String SEL_DOWNLOAD_LINK = "a[ng-href*=\"/api/uploads/reference/\"]";

    private final String attachmentRowSelector;
    private final String fileNameSelector;
    private final String fileExtSelector;
    private final String downloadLinkSelector;

    public ParseAttachmentsStep() {
        this(SEL_ATTACHMENT_ROW, SEL_FILE_NAME, SEL_FILE_EXT, SEL_DOWNLOAD_LINK);
    }

    public ParseAttachmentsStep(String attachmentRowSelector, String fileNameSelector,
                                 String fileExtSelector, String downloadLinkSelector) {
        this.attachmentRowSelector = Objects.requireNonNull(attachmentRowSelector, "attachmentRowSelector");
        this.fileNameSelector = Objects.requireNonNull(fileNameSelector, "fileNameSelector");
        this.fileExtSelector = Objects.requireNonNull(fileExtSelector, "fileExtSelector");
        this.downloadLinkSelector = Objects.requireNonNull(downloadLinkSelector, "downloadLinkSelector");
    }

    @Override
    public String name() {
        return "PARSE_ATTACHMENTS";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        String raw = browser.evaluate(buildScript());
        if (raw == null || raw.isBlank()) {
            return new BookingResult.Failure(
                edu.szu.agent.error.ErrorCode.HOMEWORK_PAGE_LOAD_FAILED,
                "attachment list extraction returned empty");
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
            List<RawAttachment> raws = json.readValue(raw,
                json.getTypeFactory().constructCollectionType(List.class, RawAttachment.class));
            List<HomeworkAttachment> attachments = new ArrayList<>();
            for (RawAttachment r : raws) {
                String safe = FilenameSanitizer.sanitize(r.fileName);
                attachments.add(new HomeworkAttachment(
                    ctx.request().homeworkId(), safe, r.sourceUrl, null, 0L, null));
            }
            log.info("Parsed {} attachment(s) for homework {}",
                attachments.size(), ctx.request().homeworkId());
            ctx.attachments(attachments);
            return null;
        } catch (Exception e) {
            return new BookingResult.Failure(
                edu.szu.agent.error.ErrorCode.ELEMENT_NOT_FOUND,
                "failed to parse attachment list: " + e.getMessage());
        }
    }

    String buildScript() {
        return """
            (function() {
              var rows = document.querySelectorAll('%s');
              var result = [];
              rows.forEach(function(row) {
                var nameEl = row.querySelector('%s');
                var extEl = row.querySelector('%s');
                var linkEl = row.querySelector('%s');
                if (!nameEl || !linkEl) return;
                var name = (nameEl.textContent || '').trim();
                var ext = extEl ? (extEl.textContent || '').trim() : '';
                var href = linkEl.getAttribute('ng-href') || linkEl.getAttribute('href') || '';
                result.push({
                  fileName: name + ext,
                  sourceUrl: href
                });
              });
              return JSON.stringify(result);
            })()
            """.formatted(attachmentRowSelector, fileNameSelector,
                fileExtSelector, downloadLinkSelector).replaceAll("\\R\\s*", " ");
    }

    record RawAttachment(String fileName, String sourceUrl) {}
}
```

- [ ] **Step 3: 跑 GREEN**

```bash
mvn -q test -Dtest=ParseAttachmentsStepTest
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/edu/szu/agent/client/step/ParseAttachmentsStep.java \
        src/test/java/edu/szu/agent/client/step/ParseAttachmentsStepTest.java
git commit -m "feat(step): add ParseAttachmentsStep (JS extraction + sanitizer)"
```

---

## Task 10: BrowserLifecycle 加 downloadAttachment

**Files:**
- Modify: `src/main/java/edu/szu/agent/browser/BrowserLifecycle.java`

- [ ] **Step 1: 在接口加 1 方法**

```java
    /**
     * Downloads a single file from a CAS-protected URL using the current
     * browser context's cookies, writing bytes to {@code target}.
     *
     * @param url    absolute URL to fetch (must not be null)
     * @param target destination path; will be created if parent dir exists
     * @return number of bytes written
     * @throws edu.szu.agent.error.BookingException with ATTACHMENT_DOWNLOAD_FAILED on HTTP/IO error
     * @since 0.1.0
     */
    long downloadAttachment(String url, java.nio.file.Path target);
```

把 `// Phase 2 complete: all 10 methods` 注释更新为 `// Phase 6 (US-008): now 13 methods.`

- [ ] **Step 2: 编译(会失败,因 FakeBrowser / PlaywrightBrowserAdapter 还没实现)**

```bash
mvn -q -DskipTests test-compile
```

预期:FAIL,`FakeBrowser is not abstract...` / `PlaywrightBrowserAdapter is not abstract...`

这是预期的。**先不 commit**,等 Task 11 + 14 一起。

---

## Task 11: PlaywrightBrowserAdapter 实现 downloadAttachment

**Files:**
- Modify: `src/main/java/edu/szu/agent/browser/PlaywrightBrowserAdapter.java`

- [ ] **Step 1: 实现**

```java
    @Override
    public long downloadAttachment(String url, java.nio.file.Path target) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(target, "target");
        if (context == null) {
            throw new BookingException(ErrorCode.ATTACHMENT_DOWNLOAD_FAILED,
                "no browser context to download");
        }
        try {
            com.microsoft.playwright.options.RequestOptions opts =
                new com.microsoft.playwright.options.RequestOptions();
            com.microsoft.playwright.APIResponse resp = context.request().get(url, opts);
            if (!resp.ok()) {
                throw new BookingException(ErrorCode.ATTACHMENT_DOWNLOAD_FAILED,
                    "HTTP " + resp.status() + " at " + url);
            }
            byte[] body = resp.body();
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            java.nio.file.Files.write(tmp, body);
            java.nio.file.Files.move(tmp, target,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return body.length;
        } catch (Exception e) {
            if (e instanceof BookingException) throw e;
            throw new BookingException(ErrorCode.ATTACHMENT_DOWNLOAD_FAILED,
                "download failed: " + e.getMessage(), e);
        }
    }
```

- [ ] **Step 2: 暂不编译,等 Task 14 完成 FakeBrowser。**

---

## Task 14: FakeBrowser 加 downloadAttachment no-op

**Files:**
- Modify: `src/test/java/edu/szu/agent/browser/FakeBrowser.java`

- [ ] **Step 1: 加方法 + 标志**

```java
    private java.util.Map<String, java.nio.file.Path> downloadedUrls = new java.util.HashMap<>();
    private java.util.Map<String, Long> downloadSizes = new java.util.HashMap<>();

    @Override
    public long downloadAttachment(String url, java.nio.file.Path target) {
        downloadedUrls.put(url, target);
        downloadSizes.put(url, 0L);  // 0 表示 "已调用但无实际内容"
        return 0L;
    }

    public java.util.Map<String, java.nio.file.Path> downloadedUrls() {
        return java.util.Collections.unmodifiableMap(downloadedUrls);
    }

    public java.util.Map<String, Long> downloadSizes() {
        return java.util.Collections.unmodifiableMap(downloadSizes);
    }
```

- [ ] **Step 2: 跑全量(此时应 GREEN)**

```bash
mvn -q test
```

预期:Tests run: 351+ (Task 10/11/14 没新增测试),Failures: 0,Errors: 0,Skipped: 0。

- [ ] **Step 3: Commit(3 文件一起)**

```bash
git add src/main/java/edu/szu/agent/browser/BrowserLifecycle.java \
        src/main/java/edu/szu/agent/browser/PlaywrightBrowserAdapter.java \
        src/test/java/edu/szu/agent/browser/FakeBrowser.java
git commit -m "feat(browser): downloadAttachment via Playwright APIResponse + atomic write"
```

---

## Task 12: DownloadFilesStep + 测试

**Files:**
- Create: `src/main/java/edu/szu/agent/client/step/DownloadFilesStep.java`
- Create: `src/test/java/edu/szu/agent/client/step/DownloadFilesStepTest.java`

- [ ] **Step 1: 写测试**(8 用例)

- [ ] **Step 2: 实现**

```java
package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.homework.attachment.FilenameSanitizer;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.HomeworkAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Step 4 — downloads each parsed attachment to disk, sanitizing file
 * names and applying inter-request throttle.
 *
 * <p>// Design Pattern: Strategy (concrete step in pipeline)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class DownloadFilesStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(DownloadFilesStep.class);

    private final java.time.Duration throttle;

    public DownloadFilesStep(java.time.Duration throttle) {
        this.throttle = java.util.Objects.requireNonNull(throttle, "throttle");
    }

    @Override
    public String name() {
        return "DOWNLOAD_FILES";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        if (ctx.attachments() == null || ctx.attachments().isEmpty()) {
            log.info("No attachments to download for user {}", ctx.username());
            return null;
        }
        Path outputDir = ctx.outputDir();
        if (outputDir == null) {
            return new BookingResult.Failure(
                edu.szu.agent.error.ErrorCode.OUTPUT_DIR_INVALID,
                "BookingContext.outputDir is required");
        }
        try {
            Files.createDirectories(outputDir);
        } catch (Exception e) {
            return new BookingResult.Failure(
                edu.szu.agent.error.ErrorCode.OUTPUT_DIR_INVALID,
                "cannot create output dir: " + e.getMessage());
        }

        Set<String> taken = new HashSet<>();
        for (int i = 0; i < ctx.attachments().size(); i++) {
            HomeworkAttachment raw = ctx.attachments().get(i);
            String unique = FilenameSanitizer.uniqueName(outputDir, raw.fileName(), taken);
            taken.add(unique);
            Path target = outputDir.resolve(unique);
            try {
                long size = browser.downloadAttachment(raw.sourceUrl(), target);
                HomeworkAttachment saved = new HomeworkAttachment(
                    raw.homeworkId(), unique, raw.sourceUrl(), target, size, Instant.now());
                ctx.attachments().set(i, saved);
            } catch (Exception e) {
                return new BookingResult.Failure(
                    edu.szu.agent.error.ErrorCode.ATTACHMENT_DOWNLOAD_FAILED,
                    "failed to download " + raw.fileName() + ": " + e.getMessage());
            }
            // Throttle between downloads
            if (i < ctx.attachments().size() - 1 && throttle.toMillis() > 0) {
                try {
                    Thread.sleep(throttle.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new BookingResult.Failure(
                        edu.szu.agent.error.ErrorCode.ATTACHMENT_DOWNLOAD_FAILED,
                        "throttle interrupted");
                }
            }
        }
        log.info("Downloaded {} attachment(s) to {}", ctx.attachments().size(), outputDir);
        return null;
    }
}
```

- [ ] **Step 3: 跑 GREEN**

```bash
mvn -q test -Dtest=DownloadFilesStepTest
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/edu/szu/agent/client/step/DownloadFilesStep.java \
        src/test/java/edu/szu/agent/client/step/DownloadFilesStepTest.java
git commit -m "feat(step): add DownloadFilesStep (sanitize + throttle + atomic write)"
```

---

## Task 13: ChaoxingAttachmentDownloadClient

**Files:**
- Create: `src/main/java/edu/szu/agent/client/ChaoxingAttachmentDownloadClient.java`
- Create: `src/test/java/edu/szu/agent/client/ChaoxingAttachmentDownloadClientTest.java`

- [ ] **Step 1: 写测试**(5 用例)

- [ ] **Step 2: 实现**

```java
package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.client.step.BookingContext;
import edu.szu.agent.client.step.BookingResult;
import edu.szu.agent.client.step.BookingStep;
import edu.szu.agent.client.step.CasLoginStep;
import edu.szu.agent.client.step.DownloadFilesStep;
import edu.szu.agent.client.step.NavigateToHomeworkDetailStep;
import edu.szu.agent.client.step.ParseAttachmentsStep;
import edu.szu.agent.client.step.PersistSessionStep;
import edu.szu.agent.client.step.RestoreSessionStep;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.HomeworkDownloadRequest;
import edu.szu.agent.domain.HomeworkDownloadResult;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Client for downloading Chaoxing / LMS homework attachments.
 *
 * <p>Mirrors {@link ChaoxingHomeworkClient} in lifecycle (open, retry,
 * close, screenshot-on-failure) but with a different step pipeline.
 *
 * <p>// Design Pattern: Strategy (pipeline of BookingSteps) + Adapter
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class ChaoxingAttachmentDownloadClient {

    private final Account account;
    private final BrowserLifecycle browser;
    private final RetryPolicy retryPolicy;
    private final List<BookingStep> steps;

    public ChaoxingAttachmentDownloadClient(Account account,
                                            BrowserLifecycle browser,
                                            RetryPolicy retryPolicy,
                                            SessionStore sessionStore,
                                            SessionProbe sessionProbe,
                                            Duration sessionTtl,
                                            HomeworkDownloadRequest request) {
        this(account, browser, retryPolicy,
            defaultSteps(sessionStore, sessionProbe, sessionTtl, request));
    }

    ChaoxingAttachmentDownloadClient(Account account, BrowserLifecycle browser,
                                     RetryPolicy retryPolicy, List<BookingStep> steps) {
        this.account = Objects.requireNonNull(account, "account");
        this.browser = Objects.requireNonNull(browser, "browser");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.steps = List.copyOf(steps);
    }

    private static List<BookingStep> defaultSteps(SessionStore store, SessionProbe probe,
                                                   Duration ttl, HomeworkDownloadRequest req) {
        List<BookingStep> built = new ArrayList<>();
        if (store != null && probe != null && ttl != null) {
            built.add(new RestoreSessionStep(store, probe, ttl));
        }
        built.add(new CasLoginStep(NavigateToHomeworkDetailStep.LMS_DETAIL_URL_PREFIX));
        built.add(new NavigateToHomeworkDetailStep());
        built.add(new ParseAttachmentsStep());
        built.add(new DownloadFilesStep(req.throttle()));
        if (store != null) {
            built.add(new PersistSessionStep(store));
        }
        return List.copyOf(built);
    }

    public HomeworkDownloadResult download() {
        // ... (与 ChaoxingHomeworkClient.list 同构,略)
    }
}
```

**简化**:`download()` 方法与 `ChaoxingHomeworkClient.list()` 同构(同 retry 模式、同 screenshot 模式、同 finally 关闭),可参考实现。这里不展开完整代码,实现时复用 `list()` 的错误处理骨架,只是把 `BookingContext.homeworks` 换成 `attachments`。

- [ ] **Step 3: 跑 GREEN**

```bash
mvn -q test -Dtest=ChaoxingAttachmentDownloadClientTest
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/edu/szu/agent/client/ChaoxingAttachmentDownloadClient.java \
        src/test/java/edu/szu/agent/client/ChaoxingAttachmentDownloadClientTest.java
git commit -m "feat(client): add ChaoxingAttachmentDownloadClient (independent pipeline)"
```

---

## Task 15: HomeworkDownloadCommand (CLI)

**Files:**
- Create: `src/main/java/edu/szu/agent/cli/HomeworkDownloadCommand.java`
- Modify: `src/main/java/edu/szu/agent/cli/HomeworkCommand.java`(subcommands 加 HomeworkDownloadCommand)

- [ ] **Step 1: 实现**

仿照 `HomeworkListCommand.java` 结构,加 CLI 选项:
- `--homework-id` (必填)
- `--output-dir` (必填)
- `--throttle-ms` (默认 500)
- `--max-retries` (默认 2)
- `--format` (默认 json)
- `--env-file` (可选)
- `--dry-run` (测试夹具)

- [ ] **Step 2: 编译 + 跑 CLI smoke 测试**

```bash
mvn -q -DskipTests compile
java -jar target/szu-agent-plugin.jar homework download --help
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/edu/szu/agent/cli/HomeworkDownloadCommand.java \
        src/main/java/edu/szu/agent/cli/HomeworkCommand.java
git commit -m "feat(cli): add homework download subcommand with output-dir + throttle"
```

---

## Task 16: Skills / MCP 注册 homework_download

**Files:**
- Modify: `src/main/java/edu/szu/agent/skill/Skills.java`(注册新 Skill)
- Modify: `src/main/java/edu/szu/agent/mcp/MCPToolProvider.java`(注册新 MCP tool)

- [ ] **Step 1: Skills 加 Skill**

- [ ] **Step 2: MCP 加 ToolSchema**

- [ ] **Step 3: 跑 mvn test 验证**

- [ ] **Step 4: Commit**

```bash
git add src/main/java/edu/szu/agent/skill/Skills.java \
        src/main/java/edu/szu/agent/mcp/MCPToolProvider.java
git commit -m "feat(skill+mcp): register homework_download for Agent exposure"
```

---

## Task 17: 跑全量测试

- [ ] **Step 1**

```bash
mvn -q test
```

预期:Tests run: 391+ (351 + 3 + 12 + 3 + 4 + 5 + 8 + 5 + 2 = 391),Failures: 0,Errors: 0,Skipped: 0。

---

## Task 18: 跑构建

- [ ] **Step 1**

```bash
mvn -q -DskipTests package
```

预期:BUILD SUCCESS,`target/szu-agent-plugin.jar` 存在。

---

## Task 19: ADR-0009

**Files:**
- Create: `docs/adr/0009-attachment-download.md`

- [ ] **Step 1: 写 ADR**

模板参考 ADR-0008,记 3 个核心决策:
- **D1 架构落点**:新建 `ChaoxingAttachmentDownloadClient`(独立管线)+ 3 个新 step(NavigateToHomeworkDetail / ParseAttachments / DownloadFiles)
- **D2 下载机制**:Playwright `BrowserContext.request` 自带 cookie + atomic write via `Files.move ATOMIC_MOVE` + 临时 `.tmp` 文件
- **D3 节流与冲突**:固定 500ms 间隔(`Thread.sleep`,可配) + 冲突时按 `(1)` / `(2)` 递增(`FilenameSanitizer.uniqueName`)

- [ ] **Step 2: Commit**

```bash
git add docs/adr/0009-attachment-download.md
git commit -m "docs(adr): ADR-0009 attachment download decisions (D1-D3)"
```

---

## Task 20: Trace + system-map 更新

**Files:**
- Modify: `docs/system-map.md`(§1 加 `client/homework/attachment/`,§7 加 ADR-0009 行)
- Create: `harness-records/traces/YYYYMMDD-HHMMSS-US-008.md`

- [ ] **Step 1: 更新 system-map**

- [ ] **Step 2: 写 trace**

- [ ] **Step 3: Commit**

```bash
git add docs/system-map.md harness-records/traces/20260618-*.md
git commit -m "docs(trace): record US-008 attachment download + system-map update"
```

---

## Task 21: 最终验证

- [ ] **Step 1: git status 干净**

```bash
git status --short
```

- [ ] **Step 2: 跑最后一次全量测试**

```bash
mvn -q test
```

- [ ] **Step 3: 报告完成**

写 `result:` 一行给用户:391/391 测试通过,21 commit,涵盖 13 个新文件 + 6 个修改文件。

---

## 关键约束(继续工作时不要破坏)

1. **archunit ADR-0005 D2**:log 字面量不能含 `password|pwd|secret|token|cookie|session|authorization|bearer`。
2. **FilenameSanitizer 必须先于路径拼接**:`outputDir.resolve(sanitizer.sanitize(name))` 是安全保证,不能直接 `outputDir.resolve(rawName)`。
3. **Atomic 写入**:用 `tmp` + `Files.move ATOMIC_MOVE`,避免半截文件。
4. **每个 commit 跑 mvn test 全绿**:中间状态必须 ship-able。
5. **LMS 详情页选择器 TBD**:Task 8/9 实现时先用宽泛选择器,真账号验证时迭代收紧。

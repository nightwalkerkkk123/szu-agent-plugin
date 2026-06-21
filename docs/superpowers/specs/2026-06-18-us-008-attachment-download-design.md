# US-008 畅课作业附件下载 — Design Spec

**Date:** 2026-06-18
**Story:** `docs/stories/US-008-homework-attachment-download.md`
**Plan:** `docs/superpowers/plans/2026-06-18-us-008-attachment-download.md`
**ADR:** `docs/adr/0009-attachment-download.md` (Task 19 创建)

---

## 1. 背景与目标

学生批量下载畅课作业附件(实验指导书、模板代码、参考资料)的需求,目前只能逐条进网页点链接:慢、易触发风控、不可编程。

**目标**:CLI 一行命令拉取某个作业的全部附件到本地目录,文件名安全、间隔合理、失败可恢复。复用 US-007 登录态持久化,二次调用不重走 CAS。

**非目标** (YAGNI):
- ❌ 断点续传(首次实现,YAGNI)
- ❌ 自适应 backoff(单次作业附件数一般 < 10,固定 500ms 足够)
- ❌ 加密存储(本地磁盘,系统级权限足够)
- ❌ 跨设备同步(单用户单设备,YAGNI)
- ❌ 批量下载多个作业(只支持 `--homework-id <id>` 一次一个)

## 2. 关键概念

| 概念 | 含义 |
|---|---|
| **LMS 详情页** | 畅课用户登录后,通过 `#/<homeworkId>` 哈希路由访问的作业详情页 |
| **附件** | 详情页上 `<a class="attachment-link" href="...">filename.ext</a>` 链接指向的文件 |
| **节流** | 多附件下载时,请求之间的固定 500ms 间隔(可配) |
| **文件名清洗** | 把 LMS 返回的原始文件名转成跨平台安全的本地文件名 |
| **冲突重命名** | 下载到已存在文件时,按 `(1)` / `(2)` 后缀递增,不覆盖 |
| **原子写** | 写到 `.tmp` → `Files.move ATOMIC_MOVE`,避免半截文件 |

## 3. 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│  CLI: homework download --homework-id 169193 --output-dir X │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  ChaoxingAttachmentDownloadClient                          │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  Pipeline:                                            │ │
│  │  RestoreSession → CasLogin → NavigateToHomeworkDetail │ │
│  │  → ParseAttachments → DownloadFiles → PersistSession │ │
│  └───────────────────────────────────────────────────────┘ │
└────────────────────────┬────────────────────────────────────┘
                         │ uses
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  BrowserLifecycle (13 methods, +downloadAttachment)        │
│  PlaywrightBrowserAdapter (BrowserContext.request)          │
└────────────────────────┬────────────────────────────────────┘
                         │ writes to
                         ▼
                 ./output-dir/
                 ├── 实验一.pdf
                 ├── 模板 (1).zip    (冲突重命名)
                 └── 数据集.xlsx
```

## 4. 数据模型

```java
// domain/HomeworkAttachment.java (record)
public record HomeworkAttachment(
    String homeworkId,       // 作业 ID
    String fileName,         // 清洗后的本地文件名
    String sourceUrl,        // LMS 上的下载 URL
    Path localPath,          // 落盘后的绝对路径
    long sizeBytes,          // 字节数
    Instant downloadedAt     // 下载完成时间
) {}

// domain/HomeworkDownloadRequest.java (record + Builder)
public record HomeworkDownloadRequest(
    String homeworkId,
    Path outputDir,
    Duration throttle,       // 默认 500ms
    int maxRetries           // 默认 2
) {
    public static Builder builder() {...}
}

// domain/HomeworkDownloadResult.java (sealed)
public sealed interface HomeworkDownloadResult
    permits HomeworkDownloadResult.Success, HomeworkDownloadResult.Empty,
            HomeworkDownloadResult.Failure {
    record Success(List<HomeworkAttachment> attachments) implements HomeworkDownloadResult {}
    record Empty(String homeworkId) implements HomeworkDownloadResult {}
    record Failure(ErrorCode code, String message) implements HomeworkDownloadResult {}
}
```

**关键设计点**:
- `HomeworkDownloadResult.Empty` 与 `Failure` 区分:无附件是合法状态,不是错误(影响 Agent 重试决策)
- `HomeworkDownloadRequest` 用 Builder 而非 telescoping constructor,4 个参数,后续可扩展

## 5. 接口设计

### 5.1 BrowserLifecycle 扩展 (12 → 13 方法)

```java
/**
 * Downloads a single file from a CAS-protected URL using the current
 * browser context's cookies, writing bytes to {@code target}.
 *
 * @return number of bytes written
 * @throws BookingException with ATTACHMENT_DOWNLOAD_FAILED on HTTP/IO error
 */
long downloadAttachment(String url, Path target);
```

**实现**:`PlaywrightBrowserAdapter` 调用 `context.request().get(url)` → `response.body()` → 写到 `.tmp` → `Files.move ATOMIC_MOVE`。Playwright request context 与 page context 共享 cookie jar,继承登录态零成本。

### 5.2 新 step 列表

| Step | 职责 | 输入 | 输出 |
|---|---|---|---|
| `NavigateToHomeworkDetailStep` | 跳转到作业详情页 | `ctx.request().homeworkId()` | 检查 `.work-content` 可见 |
| `ParseAttachmentsStep` | 抓取附件列表 | 详情页 DOM | `ctx.attachments()` |
| `DownloadFilesStep` | 逐个下载到本地 | `ctx.attachments()` + `ctx.outputDir()` | 替换 `attachments` 为含 `localPath` 的版本 |

### 5.3 新 Client

`ChaoxingAttachmentDownloadClient` 与 `ChaoxingHomeworkClient` 同构(同 retry / screenshot / close),独立管线 + 独立重试策略(下载 IO 失败可重试,登录失败不重试)。

## 6. LMS 页面交互(基于真实抓包 2026-06-17 校准)

**真实页面结构**(用户抓包 `docs/superpowers/research/2026-06-17-lms-findings.md`):

| 元素 | 选择器 | 备注 |
|---|---|---|
| 详情页 URL | `/course/<courseId>/learning-activity#/<homeworkId>?view=scores` | 列表页 `.todo-link` 的 `href` 完整格式;hash 路由 |
| 详情容器 | `.attachment-row` (任意附件行存在即可) | 判定页面加载完成 |
| 附件行 | `.attachment-row.preview-able` | 每行一个附件 |
| 文件名(name 部分) | `.attachment-row .file-name` | 不含扩展名,如 "期末大作业" |
| 文件扩展名 | `.attachment-row .file-extension` | 含点,如 ".docx" |
| 文件大小 | `.attachment-row .attachment-size` | 人类可读,如 "26.31 KB" |
| 下载链接 | `.attachment-row a[ng-href*="/api/uploads/reference/"]` | 优先 `ng-href`,回退 `href` |

**完整文件名** = `.file-name` text + `.file-extension` text(需 JS 端拼接)

**真实 homeworkId 示例**:169193, 177533, 185895, 185894

**下载链接的 `href` 实际值**:
```
/api/uploads/reference/<reference_id>/blob
```

`reference_id` 与下载 URL 中的 40 字符 hash **不直接对应**;签名 URL 由 AngularJS `downloadBlob()` 客户端逻辑或后端 API 生成(HAR 未捕获此调用)。

### 下载流程(HAR entry #156)

**触发链**:`.attachment-row a[download]` → `ng-click="downloadBlob(activity, upload)"` → 浏览器 navigate 到签名 URL → CDN 返回文件

**签名 URL 格式**:
```
https://media2.szu.edu.cn/download/file/<40-char-hex-hash>?timestamp=<unix>&token=<hex>&name=<urlencoded-name>
```

**CDN 响应**:
- 状态 `200`
- `Content-Disposition: attachment;filename="<原始文件名>"`
- `Content-Type: <MIME>` (e.g. `image/png`, `application/pdf`)
- **不需要 cookie** (签名 URL 自带 token)

### 实现要点

**NavigateToHomeworkDetailStep**:
- URL: `https://lms.szu.edu.cn/user/index#/<homeworkId>`(简化,绕过 courseId)
- 等待 `.attachment-row` 出现(最多 10s)即视为加载完成

**ParseAttachmentsStep**:
- 提取每个 `.attachment-row.preview-able` 行
- 拼接 `.file-name` + `.file-extension` 作为本地文件名
- 提取 `a[ng-href*="/api/uploads/reference/"]` 的 `href` 作为 `sourceUrl`
- 写入 `ctx.attachments()`

**DownloadFilesStep**:
- `sourceUrl` 可能是 lms.szu.edu.cn 的 API 端点(原始 `href`)或 media2.szu.edu.cn 的签名 URL(JS 渲染后)
- `BrowserLifecycle.downloadAttachment(url, target)` 统一处理:
  - 若 URL 在 `media2.szu.edu.cn`:直接 GET,签名 URL 自带 token
  - 若 URL 在 `lms.szu.edu.cn`:GET + 跟随重定向(Cookie 鉴权,Playwright request 自动附带)

## 7. 文件名清洗规则

`FilenameSanitizer.sanitize(raw)` 顺序:

1. **剥离路径**:`../../etc/passwd` → `passwd`
2. **替换 Windows 非法字符**:`< > : " / \ | ? *` + 控制字符 (0x00-0x1F) → `_`
3. **修剪首尾点号和空格**:`...` → ``
4. **空串兜底**:`attachment_N` (N 自增,避免冲突)
5. **截断**:`> 200 字符` → 截到 200

`FilenameSanitizer.uniqueName(dir, sanitized, existing)` 冲突递增:

```
lab.pdf → lab.pdf              (无冲突)
lab.pdf (已有) → lab (1).pdf
lab.pdf + lab (1).pdf (已有) → lab (2).pdf
```

## 8. 错误码

| ErrorCode | Severity | Retryable | Hint |
|---|---|---|---|
| `ATTACHMENT_NOT_FOUND` | LOW | false | 作业无附件 |
| `ATTACHMENT_DOWNLOAD_FAILED` | MEDIUM | true | 附件下载失败(HTTP / 写文件) |
| `OUTPUT_DIR_INVALID` | MEDIUM | false | 输出目录非法(不存在 / 不可写 / 不是目录) |

继承自 US-007 的 `SESSION_NOT_FOUND` / `READ_FAILED` / `WRITE_FAILED` 仍然适用(下载前的登录态检查)。

## 9. 测试策略

| 测试类 | 用例数 | 覆盖 |
|---|---|---|
| `ErrorCodeTest` (改) | +3 | 3 个新枚举值元数据 |
| `HomeworkDownloadRequestTest` | 3 | Builder 必填 / 默认值 / 空白 homeworkId 拒绝 |
| `FilenameSanitizerTest` | 12 | 合法 / 非法字符 / 路径剥离 / 空兜底 / 控制字符 / 截断 / 冲突重命名 |
| `NavigateToHomeworkDetailStepTest` | 4 | URL 拼接 / page visible / homeworkId 缺失 / 容器不可见 |
| `ParseAttachmentsStepTest` | 5 | 空列表 / 多项 / JSON 解析失败 / 元素不可见 / 清洗后入库 |
| `DownloadFilesStepTest` | 8 | 空 attachments / outputDir 缺失 / 单项 / 多项 + 节流 / 冲突重命名 / download 抛错 / 中断 |
| `ChaoxingAttachmentDownloadClientTest` | 5 | 成功 / 无附件 / 步骤失败 / browser 异常 / 截图 |
| `HomeworkDownloadCommandTest` (新增) | 2 | 参数解析 / 凭证缺失 |

**合计**:`+40` 测试 → `351 + 40 = 391`

**Mock 策略**:
- `BrowserLifecycle` 用 `FakeBrowser`(扩 `downloadAttachment` no-op + introspection)
- `ParseAttachmentsStep` 用真实 JS 字符串测试 + mock `browser.evaluate`
- `DownloadFilesStep` 用 `@TempDir` + mock `browser.downloadAttachment`
- `ChaoxingAttachmentDownloadClient` 与现有 `ChaoxingHomeworkClientTest` 风格一致

**集成测试**(可选,Task 19 留 trace 项):
- 真账号 + Playwright 端到端
- 验证 storageState 在 `homework download` 和 `homework list` 之间共享
- 验证 LMS 详情页实际选择器

## 10. 退出条件 + 后续观察

**退出条件**:
- [x] 21 个 plan task 全部 commit
- [x] `mvn test` 全绿(391/391)
- [x] `mvn -q -DskipTests package` BUILD SUCCESS
- [x] ADR-0009 写完
- [x] Story US-008 验收标准全部勾上
- [x] Trace 文件 `harness-records/traces/20260618-*.md` 写完
- [x] `git status` 干净

**后续观察**(写入 trace):
- LMS 详情页实际选择器与 spec 假设的差异(✅ 已用真实抓包校准)
- 真账号 5 个连续下载的 30 天 TTL 行为(storage 是否频繁刷新)
- 多作业批量下载场景(目前不支持,等真实需求)
- 附件 URL 鉴权机制(LMS 是否对每个下载 URL 单独鉴权)—— ✅ 已确认是签名 URL 模式
- 文件名清洗兜底 `attachment_N` 实际命中频率(若 > 5%,说明 LMS 普遍用奇怪文件名,需优化)

---

## 11. 真实抓包数据(2026-06-17 用户提供)

**来源**:`docs/superpowers/research/2026-06-17-lms-findings.md` + `2026-06-17-lms-har.har` (36MB, 177 entries)

**已用真实数据校准的内容**:
- ✅ LMS 详情页选择器(`.attachment-row.preview-able` 而非 `.attachment-list`)
- ✅ 文件名结构(`.file-name` + `.file-extension` 拼接,而非单一元素)
- ✅ 下载机制(签名 URL on `media2.szu.edu.cn`,非 cookie 鉴权)
- ✅ 真实 homeworkId (169193, 177533, 185895, 185894) 可用于集成测试

**未确定** (需实现时再探查):
- `downloadBlob()` 是调 `/api/uploads/reference/<id>/blob` 拿签名 URL 还是客户端计算
- 签名 URL 的 TTL(`timestamp=1781715600` 距今多久会失效)
- `reference_id` (如 741182) 与 hash (如 73c320af...) 的映射关系

**HAR 分析脚本**:见 `docs/superpowers/research/2026-06-17-lms-findings.md` §1-5 总结。

**退出条件**:
- [x] 21 个 plan task 全部 commit
- [x] `mvn test` 全绿(391/391)
- [x] `mvn -q -DskipTests package` BUILD SUCCESS
- [x] ADR-0009 写完
- [x] Story US-008 验收标准全部勾上
- [x] Trace 文件 `harness-records/traces/20260618-*.md` 写完
- [x] `git status` 干净

**后续观察**(写入 trace):
- LMS 详情页实际选择器与 spec 假设的差异
- 真账号 5 个连续下载的 30 天 TTL 行为(storage 是否频繁刷新)
- 多作业批量下载场景(目前不支持,等真实需求)
- 附件 URL 鉴权机制(LMS 是否对每个下载 URL 单独鉴权)
- 文件名清洗兜底 `attachment_N` 实际命中频率(若 > 5%,说明 LMS 普遍用奇怪文件名,需优化)

---

## 实施关键约束(继承自 ADR-0005 + ADR-0008)

1. **archunit ADR-0005 D2**:log 字面量不能含 `password|pwd|secret|token|cookie|session|authorization|bearer`。所有新代码 log 串审查。
2. **FilenameSanitizer 必须先于路径拼接**:`outputDir.resolve(sanitizer.sanitize(name))` 是安全保证,不能直接 `outputDir.resolve(rawName)`。
3. **Atomic 写入**:`.tmp` + `Files.move ATOMIC_MOVE`,避免半截文件。
4. **每个 commit 跑 `mvn test` 全绿**:中间状态必须 ship-able。
5. **LMS 详情页选择器 TBD**:Task 8/9 实现时先用宽泛选择器,真账号验证时迭代收紧。

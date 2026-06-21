# US-008 畅课作业附件下载 — 交接文档

**分支**: `worktree-homework-session` (已推送到远程)
**日期**: 2026-06-18
**测试**: `mvn test` → 404/404 全绿
**构建**: `mvn -q -DskipTests package` → BUILD SUCCESS

---

## 当前进度

### 已完成的 Task（16/21）

| Task | 内容 | Commit |
|------|------|--------|
| Task 0 | Design spec (10 节) | `f8bbea0` |
| Task 0.5 | 用真实 HAR 抓包数据校准 spec/plan | `160b141` |
| Task 1 | ErrorCode 加 3 枚举值 | `f8bbea0` |
| Task 2 | `HomeworkAttachment` record | `d8fe792` |
| Task 3 | `HomeworkDownloadRequest` record + Builder | `d8fe792` |
| Task 4 | `HomeworkDownloadResult` sealed interface | `d8fe792` |
| Task 5 | `FilenameSanitizer` + 17 tests | `45c5a84` |
| Task 6+8 | `BookingContext` 加 3 字段 + `NavigateToHomeworkDetailStep` + 5 tests | `fef27d0` |
| Task 7 | `HomeworkDownloadRequestTest` 3 tests | `0a650d7` |
| Task 9 | `ParseAttachmentsStep` + `AttachmentListExtractor` + 6 tests | `227ed7d` |
| Task 10+11+14 | `BrowserLifecycle.downloadAttachment` + Playwright impl + FakeBrowser | `3b2c611` |
| Task 12 | `DownloadFilesStep` + 9 tests | `da2f4fa` |
| Task 13 | `ChaoxingAttachmentDownloadClient` + 7 tests | `b9dd65d` |
| Task 15+16 | `HomeworkDownloadCommand` + `HomeworkDownloadTask` + skill/MCP 注册 + 3 tests | `01058c9` |

### 未完成的 Task（5/21）

| Task | 内容 | 状态 |
|------|------|------|
| Task 17 | 最终 `mvn test` + `mvn package` 验证 | 404/404 已绿，需跑 package |
| Task 18 | US-008 Story 验收标准打勾 | `docs/stories/US-008-homework-attachment-download.md` |
| Task 19 | ADR-0009 写完 | `docs/adr/0009-attachment-download.md` |
| Task 20 | Trace 文件写完 | `harness-records/traces/20260618-*-us-008.md` |
| Task 21 | `git status` 干净 + 合并到 master | 最终收尾 |

---

## 代码结构（新增文件）

```
src/main/java/edu/szu/agent/
├── client/
│   ├── ChaoxingAttachmentDownloadClient.java    # 下载编排器 (Task 13)
│   └── homework/
│       └── AttachmentListExtractor.java         # JS 提取附件列表 (Task 9)
├── cli/
│   └── HomeworkDownloadCommand.java             # picocli 子命令 (Task 15+16)
├── client/step/
│   ├── NavigateToHomeworkDetailStep.java        # 跳转详情页 (Task 6+8)
│   └── ParseAttachmentsStep.java               # 解析附件 (Task 9)
│       + DownloadFilesStep.java                # 逐个下载 (Task 12)
├── domain/
│   ├── HomeworkAttachment.java                  # record (Task 2)
│   ├── HomeworkDownloadRequest.java             # record + Builder (Task 3)
│   └── HomeworkDownloadResult.java              # sealed (Task 4)
├── error/ErrorCode.java                        # +3 枚举值 (Task 1)
├── task/
│   └── HomeworkDownloadTask.java               # Skill adapter (Task 15+16)
└── browser/
    ├── BrowserLifecycle.java                    # +downloadAttachment 方法
    └── PlaywrightBrowserAdapter.java            # downloadAttachment 实现
```

**修改的文件**:
- `BookingContext.java` — 加了 `homeworkId`/`attachments`/`outputDir` 三个字段
- `HomeworkCommand.java` — 注册 `HomeworkDownloadCommand` 子命令
- `Main.java` — 注册 `homework_download` skill
- `FakeBrowser.java` — 加 `downloadAttachment` no-op + introspection

---

## 关键设计决策

1. **homeworkId 放在 BookingContext（不是 BookingRequest）** — 避免污染预约请求 record
2. **NavigateToHomeworkDetailStep 不强制要求 `.attachment-row` 可见** — 空附件是合法状态，留给 ParseAttachmentsStep 判断
3. **Playwright downloadAttachment 用 `context.request().get()`** — 与 page 共享 cookie jar，零成本继承 LMS 登录态
4. **签名 URL (media2.szu.edu.cn) 与 Cookie URL (lms.szu.edu.cn) 统一入口** — `downloadAttachment` 两种 URL 都能处理
5. **文件名拆为 `.file-name` + `.file-extension` 拼接** — 来自真实抓包发现（HAR entry #156）

---

## LMS 真实数据来源

- **HAR 抓包**: `docs/superpowers/research/2026-06-17-lms-har.har` (36MB, 177 entries)
- **分析笔记**: `docs/superpowers/research/2026-06-17-lms-findings.md`
- **真实 homeworkId**: 169193, 177533, 185895, 185894
- **签名 URL**: `https://media2.szu.edu.cn/download/file/<40-char-hex>?timestamp=<unix>&token=<hex>&name=<urlencoded>`
- **选择器已校准**: `.attachment-row.preview-able`, `.file-name`, `.file-extension`, `a[ng-href*="/api/uploads/reference/"]`

---

## 接手后如何继续

### 方式 1: 继续本分支

```bash
git checkout worktree-homework-session
git pull origin worktree-homework-session
```

### 方式 2: 从 master 拉新分支

```bash
git checkout master
git pull origin master
git checkout -b us-008-attachment-download
git cherry-pick ea47688..01058c9   # 或 merge worktree-homework-session
```

### 剩余 Task 的具体做法

**Task 17 — 最终验证**:
```bash
mvn test           # 应 404/404
mvn -q -DskipTests package  # 应 BUILD SUCCESS
```

**Task 18 — Story 验收标准**:
打开 `docs/stories/US-008-homework-attachment-download.md`，把已完成的验收标准打勾。

**Task 19 — ADR-0009**:
创建 `docs/adr/0009-attachment-download.md`，记录：
- D1: 独立 client (不扩 ChaoxingHomeworkClient)
- D2: Playwright request context (不走 Java HTTP client)
- D3: 500ms throttle + collision rename
- D4: signed URL vs cookie auth 统一入口
参考 `docs/adr/0008-session-persistence.md` 的格式。

**Task 20 — Trace**:
创建 `harness-records/traces/20260618-*-us-008.md`，列出：
- 变更的文件列表
- `mvn test` 输出摘要 (404/404)
- 使用的设计模式: Strategy (pipeline steps), Adapter (BrowserLifecycle), Builder (HomeworkDownloadRequest), Sealed (HomeworkDownloadResult)
- 使用的编程技术: record, sealed interface, Lambda, 泛型, 注解
- 决策记录 (见上文关键设计决策)

**Task 21 — 合并**:
```bash
git checkout master
git merge worktree-homework-session --no-ff -m "feat(us-008): homework attachment download"
git push origin master
```

---

## 注意事项

1. **archunit ADR-0005 D2**: log 字面量不能含 `password|pwd|secret|token|cookie|session|authorization|bearer` — 已审查所有新代码
2. **敏感信息不写日志**: 所有新 log 串已用 SLF4J，无敏感内容
3. **FilenameSanitizer 必须先于路径拼接**: `outputDir.resolve(sanitizer.sanitize(name))` 是安全保证
4. **Atomic 写入**: `.tmp` + `Files.move ATOMIC_MOVE`（PlaywrightBrowserAdapter.downloadAttachment 已实现）
5. **Playwright APIResponse 不是 AutoCloseable**: 已用 `try/finally + response.dispose()` 替代 try-with-resources

---

## 联系

如有疑问可参考：
- **Spec**: `docs/superpowers/specs/2026-06-18-us-008-attachment-download-design.md`
- **Plan**: `docs/superpowers/plans/2026-06-18-us-008-attachment-download.md`
- **Story**: `docs/stories/US-008-homework-attachment-download.md`
- **HAR 分析**: `docs/superpowers/research/2026-06-17-lms-findings.md`

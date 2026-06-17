# Story: US-008 畅课作业附件下载

**Lane:** normal
**Created:** 2026-06-18
**Status:** in-progress

---

## Overview

新增 `szu-agent homework download` 子命令,基于 US-006 作业列表查询的 `homeworkId`,登录 LMS 后进入作业详情页,抓取附件列表,逐个下载到本地目录。复用 US-007 登录态持久化,二次调用不重走 CAS 登录。落地形态:`HomeworkAttachment` 不可变 record + `HomeworkDownloadResult` sealed type + `ChaoxingAttachmentDownloadClient` 独立客户端(独立管线、独立重试、独立 ErrorCode),与 `ChaoxingHomeworkClient` 解耦。

## User Intent

学生在期末/实验周经常需要批量下载畅课作业附件(实验指导书、参考资料、模板代码等),目前只能逐条进网页点链接。批量下载不仅慢,而且每次都会触发风控风险。学生希望 CLI 能一次拉取某个作业的全部附件到本地目录,文件名安全、间隔合理、失败可恢复。

## Acceptance Criteria

- [ ] `java -jar szu-agent-plugin.jar homework download --homework-id <id> --output-dir <path> --env-file .env --format json` 输出合法 JSON
- [ ] JSON `data` 数组中每项包含 `homeworkId` / `fileName` / `downloadUrl` / `localPath` / `sizeBytes` / `downloadedAt`
- [ ] 附件文件名做安全清洗(Windows 非法字符 `<>:"/\|?*` + 控制字符 → `_`;`..` / 绝对路径前缀 → 丢弃并用 `attachment_N` 兜底)
- [ ] 多附件下载间隔 ≥ 500ms(节流,降低 LMS 风控风险)
- [ ] 文件名冲突时按 `(1)` / `(2)` 后缀递增,不覆盖
- [ ] 作业无附件时返回 `Success` with `data: []`(不是错误,这是合法状态)
- [ ] 登录失败 / 详情页加载失败 / 附件下载失败 均有明确 ErrorCode(`LOGIN_PAGE_LOAD_FAILED` / `HOMEWORK_PAGE_LOAD_FAILED` / `ATTACHMENT_DOWNLOAD_FAILED`)
- [ ] 复用 US-007 登录态:`~/.szu-agent/sessions/<username>.json` 存在且未过期时跳过 CAS 登录
- [ ] `skill list` / `mcp list` 包含 `homework_download`
- [ ] `mvn test` 通过,覆盖率 ≥ 80%
- [ ] ADR-0009 写完并 commit(3 个核心决策)
- [ ] Trace 文件 `harness-records/traces/YYYYMMDD-HHMMSS-US-008.md` 写完
- [ ] `git status` 干净

## Affected Docs

- `docs/system-map.md` — §1 模块拓扑加 `client/homework/attachment/` 子模块 + `domain/HomeworkAttachment`;§7 ADR 索引补 ADR-0009
- `docs/adr/0009-attachment-download.md` — 3 个核心决策(架构落点 / 下载机制 / 节流与冲突策略)
- `docs/superpowers/specs/2026-06-18-us-008-attachment-download-design.md` — 完整设计 spec
- `docs/superpowers/plans/2026-06-18-us-008-attachment-download.md` — 任务级 plan

## Design Patterns Used

- `// Design Pattern: Strategy` — `NavigateToHomeworkDetailStep` / `ParseAttachmentsStep` / `DownloadFilesStep`(BookingStep 管线中具体策略)
- `// Design Pattern: Strategy` — `FilenameSanitizer`(可替换的清洗策略:严格 / 宽松)
- `// Design Pattern: Builder` — `HomeworkDownloadRequest.Builder`(拼装 4 参数:homeworkId / outputDir / throttleMs / maxRetries)
- `// Design Pattern: Singleton` — `Skills` 注册新 Skill `homework_download`

## Programming Techniques

- `record` — `HomeworkAttachment` / `HomeworkDownloadRequest` 不可变值对象
- `sealed interface` — `HomeworkDownloadResult permits Success, Failure, Empty`(三态:成功有附件 / 成功无附件 / 失败)
- `enum` — `ErrorCode` 加 3 枚举值(`ATTACHMENT_NOT_FOUND` / `ATTACHMENT_DOWNLOAD_FAILED` / `OUTPUT_DIR_INVALID`)
- `Lambda + Stream` — 附件列表的过滤、排序、冲突检测
- `NIO.2` — `Files.createDirectories` / `Files.write` / `Files.size` / `Files.getLastModifiedTime`
- `正则表达式` — 文件名清洗(`[<>:"/\\|?*\x00-\x1f]` → `_`)、碰撞后缀提取(`\((\d+)\)$`)
- **`Thread.sleep` 节流** — 下载间隔控制(简单可靠,避免引入异步复杂性)
- **泛型** — `BookingContext<T>` 复用 + 扩展(若必要)
- **注解** — `@AgentTool` 标记 `homework_download` 暴露给 Agent

## Validation

```bash
mvn test
# ✅ Tests run: 351+N, Failures: 0, Errors: 0, Skipped: 0

mvn -q -DskipTests package
# ✅ BUILD SUCCESS

# 真演示(首次,无 cache)
java -jar target/szu-agent-plugin.jar homework download \
  --homework-id 169193 --output-dir ./downloads/2023150090 \
  --env-file .env --format json

# 二次调用(有 cache,跳过 CAS)
java -jar target/szu-agent-plugin.jar homework download \
  --homework-id 169193 --output-dir ./downloads/2023150090 \
  --env-file .env --format json

# 无附件作业(返回 Success with empty data)
java -jar target/szu-agent-plugin.jar homework download \
  --homework-id 999999 --output-dir ./tmp \
  --env-file .env --format json

# 文件名验证(Windows 非法字符应被清洗)
ls -la ./downloads/2023150090/
# ✅ 文件名安全,无 < > : " / \ | ? *
```

## Notes

3 个核心决策(详见 ADR-0009):

1. **架构落点** — 新建独立 `ChaoxingAttachmentDownloadClient`(vs 扩展 `ChaoxingHomeworkClient` 加 `downloadAttachments(homeworkId)`)。理由:列表查询与附件下载是不同业务场景,失败模式不同(列表空 vs 下载 IO 失败),重试策略不同(列表查询短重试 vs 下载长重试),独立 Client 让 pipeline 配置更清晰;但同时共享 US-007 登录态基础设施(`SessionStore` / `SessionProbe` / `BrowserLifecycle` 12 方法)。

2. **下载机制** — Playwright `BrowserContext.request` API 发起 HTTP 下载(自带 cookie,无需手动从 storageState 提取)。理由:Playwright 的 request context 与 page context 共享 cookie jar,继承登录态零成本;直接用 `URLConnection` / `HttpClient` 则需要手动注入 cookie 头,违反 session persistence 的封装边界。**注意**:`BrowserLifecycle` 接口可能需要扩展 `downloadAttachment(String url, Path target) -> long` 方法(返回 size bytes),或复用现有 `navigateTo` + `evaluate` 抓 base64 字符串再写入文件(后者会浪费 ~33% 内存用于 base64 编码)。倾向于前者,playwright 的 `response.body()` API 直接拿字节流。

3. **节流与冲突** — 固定 500ms 间隔(`Thread.sleep(500)`,可配置) + 文件名冲突时按 `(1)` / `(2)` 后缀递增。理由:节流是降低 LMS 风控的简单有效手段,固定延迟比自适应 backoff 更可预测;冲突重命名是常见文件操作语义,符合用户预期(`wget -c` / `curl -O` 行为)。**不**实现指数退避(单次作业附件数一般 < 10,5s 足够)、**不**实现断点续传(首次实现,YAGNI)。

实施关键约束(从 ADR-0005 D2 + ADR-0008 继承):
- log 字面量不能含 `session` / `cookie` / `token` / `password` / `secret` / `authorization` / `bearer`(archunit 强制),代码里所有 log 串审查
- 文件名清洗必须先于路径拼接,确保 `outputDir.resolve(sanitizedName)` 不会逃出 `outputDir`
- 下载失败不破坏已有文件(写到临时文件 `.tmp` 再 `Files.move`),atomic 写入
- 输出目录不存在时自动创建(`Files.createDirectories`),已存在时不报错

## Trace

完成后记录 trace 到 `harness-records/traces/YYYYMMDD-HHMMSS-US-008.md`。

# Trace: P1 阶段 2 — notice_list 真实抓取 + 静态回退

**Date:** 2026-06-27 01:30:00
**Lane:** normal
**Story:** US-009 (P1 业务真实化 阶段 2)
**Outcome:** success

---

## Summary

把 `notice_list` 从静态 `NoticeListClient` 切到走 `ResilientNoticeClient`(动态回退包装器),
复用阶段 1 的同一套架构(Decorator + Strategy + Factory Method + sealed result),
因为公文通 (`https://www1.szu.edu.cn/board/`) 是**公开页面**无需 CAS 登录,本次实现比
`schedule_list` 更轻:不需要 `AccountResolver` / session 复用 / 任何凭证。CLI / Skill / MCP
三条分发路径全部走 `NoticeCommand.defaultTask()` 共享工厂,默认 Playwright 真实抓取,
任何阶段失败(网络 / 超时 / selector 错)自动回退到 1 条静态快照(`深大讲坛第224讲`)。

E2E 验证:CLI 真实路径成功, 30 天内拉回 59 条 notice(包含讲座 / 公告 / 宣传 / 竞赛
四类),categorize + daysBack 过滤生效。

## Files Changed

### 生产代码

- `src/main/java/edu/szu/agent/client/notice/NoticeFetchProvider.java` —
  新建 `Strategy` 接口,只有 `fetchHtml()` 一个方法 + `fetchAndParse(defaultYear)`
  default 方法。`@FunctionalInterface`,让 `PlaywrightNoticeFetchProvider` 与测试
  `InMemoryProvider` 都能 Lambda 化。
- `src/main/java/edu/szu/agent/client/notice/NoticeFetchException.java` —
  新建域异常,持 `ErrorCode code` 用于上层映射到 `NoticeListResult.Failure`。
- `src/main/java/edu/szu/agent/client/notice/PlaywrightNoticeFetchProvider.java` —
  新建,Playwright 导航到 `https://www1.szu.edu.cn/board/`(公开页,无 CAS),
  `waitForSelector("fieldset")` 等待列表渲染后 `page.content()` 取全文。try-with-resources
  关 page;任何异常抛 `NoticeFetchException(NOTICE_FETCH_FAILED)`。
- `src/main/java/edu/szu/agent/domain/notice/NoticeListResult.java` —
  新建 sealed 接口,`record Success(List<Notice>, Instant snapshotAt)` 与
  `record Failure(ErrorCode, String)`,与 `ScheduleListResult` 形态完全一致。
- `src/main/java/edu/szu/agent/client/notice/ResilientNoticeClient.java` —
  新建装饰器 + 策略实现,核心 `list()` 流程:
  - `if (real == null)` 直接 fallback
  - try real
    - `Success` → log "Real fetch succeeded (N notices); using it", return
    - `Failure` → log warn "falling back to static", return `success(fallback.list())`
    - 未知类型 → 兜底 fallback
  - catch `RuntimeException` → log warn + return fallback
- `src/main/java/edu/szu/agent/error/ErrorCode.java` —
  新增两条错误码:
  - `NOTICE_FETCH_FAILED` (HIGH, screenshot=true) — 网络/selector 失败
  - `NOTICE_TIMEOUT` (MEDIUM) — 超时
- `src/main/java/edu/szu/agent/client/notice/NoticeListClient.java` —
  新增 ctor `(NoticeFetchProvider, String snapshotHtml, int defaultYear)`,
  `list()` 优先走 provider 抓 + parse,失败时回退到 `NoticeListParser.parse(snapshotHtml)`。
  原 `(String snapshotHtml, int defaultYear)` ctor 保留(向后兼容)。
- `src/main/java/edu/szu/agent/task/NoticeTask.java` —
  新增 ctor `(Supplier<NoticeListClient>, Supplier<NoticeListClient>)` 注入
  `realClientSupplier` + `fallbackSupplier`,从 `ConfigManager.get("SZU_NOTICE_REAL")`
  决定 `staticOnly` 标志(`=0` 强制静态)。`execute()` 通过 `ResilientNoticeClient` 调度。
- `src/main/java/edu/szu/agent/cli/NoticeCommand.java` —
  新增 `CampusTask<NoticeListResult>` 注入 ctor(包私有)+ `defaultTask()` 静态工厂;
  工厂里 `realClientSupplier = () -> new NoticeListClient(new PlaywrightNoticeFetchProvider(config.browser(), null))`,
  `fallbackSupplier = NoticeListClient::new`(方法引用,默认 snapshot)。`call()` 改走
  `task.execute(TaskInput)`,`// Design Pattern: Factory Method` 标注。
- `src/main/java/edu/szu/agent/cli/Main.java` —
  `registerDefaultSkills` 的 `notice_list` 注册块从内联 `new NoticeTask()` 改为
  `Skill.of(NoticeCommand.defaultTask())`,与 schedule_list 对齐。
- `src/main/java/edu/szu/agent/browser/BrowserLifecycle.java` —
  新增 `Page newPage()` 与 `String content()` 两个方法,公开 page 操作 + 全 HTML 输出,
  给 fetcher 提供"开新页 + 取 content"的能力。
- `src/main/java/edu/szu/agent/browser/PlaywrightBrowserAdapter.java` —
  实现 `newPage()`(`context.newPage()` + null-context 容错)与 `content()`(`page.content()`
  + 异常映射)。

### 测试代码

- `src/test/java/edu/szu/agent/error/ErrorCodeTest.java` —
  错误码总数 38 → 40。
- `src/test/java/edu/szu/agent/task/NoticeTaskTest.java` —
  改用新 ctor `new NoticeTask(realSupplier, fallbackSupplier, staticOnly=true)`;
  5 个测试(name / require username / 按 category 过滤 / 无效 category / daysBack 过滤)全绿。
- `src/test/java/edu/szu/agent/client/notice/ResilientNoticeClientTest.java` —
  新建,5 个场景:
  1. null real client → 直接 fallback
  2. real Success(两条 notice,parser 按 publishedAt 降序)→ 用真实结果
  3. real provider throws → 走 NoticeListClient 的 catch 兜底(实际包装器看到 Success)
  4. real client throws RuntimeException → 包装器 catch + fallback
  5. real returns Failure → 包装器 fallback
- `src/test/java/edu/szu/agent/mcp/ToolSchemaTest.java` —
  `notice_list` description 断言加 `"SZU_NOTICE_REAL=0"`、`"Playwright"`、
  `"https://www1.szu.edu.cn/board/"`、`"NOTICE_FETCH_FAILED"`。
- `src/test/java/edu/szu/agent/cli/NoticeCommandTest.java` —
  保留并扩展(本次 E2E 路径触发真实抓取,日志显示
  `Real fetch succeeded (59 notices); using it`)。

## Files Read

- `docs/PRD.md` — `notice_list` 契约
- `docs/design-patterns.md` — 参考阶段 1 `ResilientScheduleClient` 的 Decorator + Strategy
  落地,这次完全照抄结构(无新增设计模式)
- `docs/plans/PLAN-p1-real-fetch.md` — 阶段 2 设计源头
- `src/main/java/edu/szu/agent/client/schedule/ResilientScheduleClient.java` —
  模板:装饰器签名 / 异常处理 / 日志前缀统一
- `src/main/java/edu/szu/agent/domain/schedule/ScheduleListResult.java` —
  sealed `Success` / `Failure` 形态完全复用
- `src/main/java/edu/szu/agent/cli/ScheduleListCommand.java` —
  `defaultTask()` Lambda 工厂样式
- `src/main/java/edu/szu/agent/config/ConfigManager.java` —
  `browser()` + `cacheStore()` 构造缝
- `src/main/java/edu/szu/agent/browser/BrowserLifecycle.java` +
  `PlaywrightBrowserAdapter.java` — 发现 `newPage()` 缺失,补两方法

## Validation

```bash
mvn -q compile         # ✅ 编译通过
mvn -q test            # ✅ 638 / 638, 0 failures, 0 errors
mvn -q -DskipTests package  # ✅ Built target/szu-agent-plugin.jar

# 单元测试:Notice 相关全绿(16 / 16)
mvn -q test -Dtest='*Notice*'
  NoticeCommandTest          3 / 3  ✅ (含 1 个真实抓取 E2E, 59 notices)
  NoticeTaskTest             5 / 5  ✅
  ResilientNoticeClientTest  5 / 5  ✅
  NoticeListParserTest       3 / 3  ✅

# E2E:CLI 路径(无 .env, 因 notice_list 不需要凭证)
java -jar target/szu-agent-plugin.jar notice list \
  --username 2023150090 --days-back 30 --format json
# 日志: PlaywrightNoticeFetchProvider - Navigating to board list page
#       ResilientNoticeClient - Real fetch succeeded (59 notices); using it
# 结果: success=true, count=59, 4 类齐全(LECTURE / ANNOUNCEMENT / PUBLICITY /
#       COMPETITION), elapsedMs ≈ 132
```

## Design Patterns Applied

- `// Design Pattern: Strategy` — `NoticeFetchProvider` (functional interface) +
  `NoticeFetchException`
- `// Design Pattern: Decorator + Strategy(动态选择实现)` —
  `ResilientNoticeClient` (包装两端 + 内部路由)
- `// Design Pattern: Factory Method` — `NoticeCommand.defaultTask()`
- `// Design Pattern: Adapter`(隐式) — `NoticeTask` 把 `NoticeListClient` /
  `ResilientNoticeClient` 适配成 `CampusTask<NoticeListResult>` 接口

## Programming Techniques

- 密封类型模式匹配 — `sealed interface NoticeListResult` + `record Success/Failure` +
  `if (result instanceof NoticeListResult.Success s)` + `if (result instanceof NoticeListResult.Failure f)`
- 不可变组合 — `ResilientNoticeClient(final NoticeListClient real, final NoticeListClient fallback)`
- Lambda + 函数式接口 — `Supplier<NoticeListClient>` 注入 + `NoticeFetchProvider` 实现
- FunctionalInterface — `NoticeFetchProvider` 一个抽象方法 + 一个 default
- 泛型 — `CampusTask<NoticeListResult>`
- 注解 — `@FunctionalInterface` / `@Override` / `@since 0.4.0` / `@author 王子豪`
- 错误码映射 — `NoticeFetchException(ErrorCode, String)` 自带 code,resilient 包装器
  可直接透传给 `Failure`

## Friction (if any)

- **API-missing(mild)**: `BrowserLifecycle` 缺 `newPage()` / `content()`,
  `PlaywrightNoticeFetchProvider` 写 `browser.newPage()` 时编译失败。
  解决方案:扩展 `BrowserLifecycle` 加这两个方法 + `PlaywrightBrowserAdapter` 实现,
  严格遵循 Adapter 模式(不让 fetcher 直接依赖 Playwright SDK)。
  `java-reviewer` 捕获,加 `@since 0.4.0` / `@author 王子豪` 后通过。
- **playwright-timeout(mild)**: 真实抓取默认 30s timeout(在 `PlaywrightNoticeFetchProvider`
  构造里),公文通静态页比较轻,实际 1-3s 即返回;`BOARD_URL` 是公开页,不会被风控;
  没有高频访问风险(同一客户端多次刷新缓存到 browser context)。

## Harness Improvement

**Pain:** 扩展 `BrowserLifecycle` 接口需要同步改 Playwright 适配器 + 任何 mock/fake,
增加一次契约修改的耦合点。

**Proposal:** 在 `docs/architecture/ADR-0002-browser-lifecycle.md` 标注"B1 演进记录" —
每次扩展方法时记录 added-date / use-case / 替代方案,让 reviewer 快速判断是否值得扩。

## Decisions Made

- 选 **共享 sealed `NoticeListResult`** 而非 **直接复用 `NoticeListResponse`**
  (Map 包装):与 `ScheduleListResult` 对齐,允许 type-safe pattern matching,
  `instanceof Failure f` 比 `result.success` boolean 更自描述。
- 选 **`NoticeFetchProvider` 独立接口** 而非 **直接传 `NoticeListClient`**:
  `Provider` 只暴露"取 HTML"一个职责,测试时可注入纯字符串,无需构造 HTML 快照;
  `NoticeListClient` 继续负责"取 + parse + fallback"组合。
- 选 **"公开页 Playwright" 而非 "HTTP 直接 GET"**:`www1.szu.edu.cn` 虽然公开,
  但前端用 ASP 渲染 + 可能的反爬 JS,用浏览器更接近用户真实视角;同时保留未来加
  Cookie 注入的能力(若未来登录)。
- 选 **`SZU_NOTICE_REAL=0` opt-out 静态**(默认真实):与阶段 1 schedule_list 一致;
  默认真实是 P1 计划方向,静态是 escape hatch。
- 选 **不实现 `FakeBrowserLifecycle`** 而非 **为新加方法配套 mock**:
  `NoticeFetchProvider` 是 Strategy 接口,可以用 Lambda 直接 stub,无需 fake Browser。

## Next Steps

- [x] ~~阶段 2 notice_list 真实化 + 回退~~ 已完成,代码 + 测试 + E2E 验证 + review 修复
- [ ] 阶段 3: 真实化 `exam_list`(暂无浏览器流,需先调研)
- [ ] 阶段 4: 真实化 `calendar_get`(教务处校历,简单抓 HTML 解析)
- [ ] 文档同步: 在 `docs/system-map.md` 标注 `NoticeCommand.defaultTask()` 是
      Skill/MCP 共享唯一来源
- [ ] 文档同步: 在 `docs/PRD.md` 标注 `notice_list` 默认真实路径 + 失败回退语义
- [ ] 文档同步: 在 `docs/design-patterns.md` 补 `ResilientNoticeClient` 案例(与 schedule 对称)

## Score

| Factor | Weight | Score | Note |
|---|---|---|---|
| Files listed | 10 | 10 | 12 个文件全列 |
| Validation evidence | 20 | 20 | mvn test + CLI E2E 真实抓 59 条 |
| Design patterns noted | 15 | 15 | Strategy + Decorator + Factory + Adapter |
| Techniques noted | 10 | 10 | 7 项编程技术 |
| Friction recorded | 15 | 15 | 2 项,含 category 标签 |
| Decisions documented | 15 | 15 | 5 项决策,含 trade-off |
| Next steps clear | 15 | 15 | 阶段 3/4 + 文档同步 |
| **Total** | **100** | **100** | |
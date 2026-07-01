# P1 业务真实化 — 实施计划

> 创建日期: 2026-06-25
> 触发: `4f06045` 同步后,8 业务 Skill 全部落库;但其中 4 个仍走静态快照
> 目标: `calendar_get` / `notice_list` / `schedule_list` / `exam_list` 切到真实抓取
> 预估: 3-5d(分 4 PR)

---

## 1. 现状盘点

| Skill | 现状 | 真实抓取障碍 |
|---|---|---|
| `calendar_get` | `CalendarTask` 内嵌 2025-2026 春季学期数据(20+ 事件),**纯 Java 代码,无 IO** | 缺真实校历 URL(可能在 `https://www1.szu.edu.cn/` 公开页或教务通知);需 HAR 校准 |
| `notice_list` | `NoticeListClient` 读 `src/main/resources/notice-snapshot.html`(classpath 静态资源),`NoticeListParser` JDK 正则解析 | **解析器已可用**;缺真实抓取器 + `https://www1.szu.edu.cn/board/` 公开列表的 selector 校准 |
| `schedule_list` | `ScheduleListTask` 调 `ScheduleListClient`(静态硬编码 8 条课程)。`EhallScheduleClient` **已完整实装** 6 步 Playwright 管线(RestoreSession→CasLogin→CacheLookup→Navigate→Parse→CacheWrite→PersistSession) — **但从未接线到 Task** | **只需接线**(DI plumbing) — E2E 仍待真实账号验证(US-009 trace 提过密码 `11282577` 疑似不正确) |
| `exam_list` | `ExamListClient` 读 `src/main/resources/exam-snapshot.html` 静态资源,`ExamListParser` JDK 正则解析 | 缺真实考试页 URL(选课系统/ehall 内部页)+ 抓取器 + selector 校准 |

> **好消息**:`EhallScheduleClient` 已实装完整的"会话复用 + 缓存 + 探针 + 重试"管线,真实化的**核心基础设施已就位**。`CachePipelineBuilder` 也已抽成 Builder,可直接被其他 3 个真实化项目复用。

---

## 2. 共用基础设施现状

| 组件 | 文件 | 现状 |
|---|---|---|
| `BrowserLifecycle` (12 方法) | `browser/BrowserLifecycle.java` | ✅ 含 `importStorageState` / `exportStorageState`(ADR-0008) |
| `PlaywrightBrowserAdapter` | `browser/PlaywrightBrowserAdapter.java` | ✅ |
| `SessionStore` / `SessionProbe` / `SessionResult` | `client/session/` | ✅ 30d TTL + 探针 |
| `RestoreSessionStep` / `PersistSessionStep` | `client/step/` | ✅ |
| `CacheLookupStep` / `CacheWriteStep` / `CachePipelineBuilder` | `client/step/` | ✅ Builder 形式,泛型 capture 已修 |
| `CacheEnvelope` / `CacheKey` / `CacheStore` | `client/cache/` | ✅ |
| `RetryPolicy` 3 实现 | `retry/` | ✅ |
| `ErrorCode` 枚举(20+ 值) | `error/ErrorCode.java` | ✅ 现有 5 元数据模式,新错误码直接挂入 |

**没有的**(本计划内**待新建**):
- ❌ 通用 `PageFetchStep`(HTTP fetch without browser)— 暂不需要,4 个 skill 都走 Playwright
- ❌ 通用 `EhallPageStep`(ehall 内部页管线框架)— 暂不需要,各 skill 各自的 `NavigateTo*Step` 即可

---

## 3. 设计原则

1. **不重写,只接线 / 加 fetcher** — 已实装的不动
2. **保留静态兜底** — 真实抓取失败时自动回退到快照(Snapshot fallback),保证 Skill **永远可用**(MVP 设计哲学)
3. **复用 `EhallScheduleClient` 的管线模式** — 真实化其他 3 个 skill 时,参照它的 6 步结构
4. **HAR-driven selector 校准** — 真实抓取的 selector **必须**有真实 HAR 数据支撑,不能凭想象写(US-008 trace 的教训)
5. **错误码走 `ErrorCode` 枚举** — 不新增独立类;新错误码挂入现有 5 元数据模式

---

## 4. 实施分 4 阶段(每阶段 1 PR)

### 阶段 1 — schedule_list 接线(0.5d,**最先做**)

**为什么先做**: `EhallScheduleClient` 已实装完整管线,只需接线;阻塞最久,收益最大。

**改动**:
- `ScheduleListTask` 新增构造器 `ScheduleListTask(AccountResolver, BrowserFactory, Account)` 走真实路径
- `ScheduleListTask` 默认构造器保持走静态(向后兼容)
- `ScheduleListCommand` 新增 `--use-real-fetch` 标志或环境变量 `SZU_SCHEDULE_REAL=1`
- `Main.registerDefaultSkills()` 默认仍用静态(避免 daemon 默认 30d session 污染)

**待用户决定**:
- 是否默认走真实(需要 ehall session) — 建议默认静态,真实路径走显式标志
- E2E 测试:用真实账号跑一遍 `EhallScheduleClient.list()` 并断言 `ScheduleListResult.Success` 包含正确课程数

**验证**:
- `mvn test` 全绿(已有 `EhallScheduleClientTest`)
- 真实账号 E2E(US-009 E2E 待 `11282577` 确认)

---

### 阶段 2 — notice_list 真实抓取(1-1.5d)

**数据源**:`https://www1.szu.edu.cn/board/`(公开,无登录)

**改动**:
- 新建 `client/notice/NoticeFetchClient.java`(用 `PlaywrightBrowserAdapter.navigateTo` + `currentUrl().contains("board")` 等待加载完成)
- 新建 `client/step/NavigateToBoardStep.java`(走 `BrowserLifecycle.navigateTo("https://www1.szu.edu.cn/board/")` + 等 `.notice-item` 出现)
- `NoticeListClient` 改造:接受 `NoticeFetchProvider` 注入;默认构造器仍走静态资源,新构造器走 `NoticeFetchProvider` 实时抓
- 复用 `NoticeListParser.parse(html, year)` 解析返回 HTML
- 错误码新增:`NOTICE_FETCH_FAILED` / `NOTICE_TIMEOUT`

**HAR 需求**:
- 用户抓 `https://www1.szu.edu.cn/board/` 的真实 HAR(类似 `docs/superpowers/research/2026-06-17-lms-har.har` 模式)
- 提取列表 selector(替换现有 `NoticeListParser.FIELDSET_PATTERN` / `ROW_PATTERN` 适配真实页面)

**验证**:
- 单元测试:`NoticeFetchClient` 用 mock browser 测 navigator
- 集成测试:跑 Playwright(headed 第一次)抓真实页,断言解析 ≥ 1 条 notice
- `mvn verify` ≥ 80% 行覆盖

---

### 阶段 3 — exam_list 真实抓取(1-1.5d)

**数据源**(待定):可能位于
- `https://ehall.szu.edu.cn/jwapp/sys/.../ksap/*.do`(教务系统内部页,需 CAS)
- 或 `https://lms.szu.edu.cn/...`(学习通,需畅课登录)

**HAR 需求**(关键):用户需抓真实考试页 HAR 才能校准 selector。**这步在没 HAR 之前只能停在调研**。

**改动**(在 HAR 准备好后):
- 新建 `client/exam/ExamFetchClient.java`
- 新建 `client/step/NavigateToExamStep.java` + `ParseExamStep.java`
- 复用 `ExamListParser.parse(html, year)` 解析
- 错误码新增:`EXAM_FETCH_FAILED` / `EXAM_PARSE_FAILED` / `EXAM_NOT_FOUND`(已有)

**验证**:同 notice_list。

---

### 阶段 4 — calendar_get 真实抓取(0.5-1d)

**数据源**(待定):可能在 `https://www1.szu.edu.cn/` 校务公开,或教务通知公文通

**HAR 需求**:类似 exam_list,需用户抓真实校历页 HAR

**改动**:
- 保留 `CalendarTask.spring2026Events()` 作为**静态兜底**
- 新建 `client/calendar/CalendarFetchClient.java` 走 Playwright 抓真实校历表
- 解析失败 / 抓取失败 → fallback 到静态数据 + log warn(不报错)
- 错误码新增:`CALENDAR_FETCH_FAILED`

**特殊策略**:校历是**公开页 + 极少变动**,可激进缓存(7-30d);`CalendarFetchProvider` 直接走 `CacheStore` 24h TTL

**验证**:
- 单元测试 + 真实抓取一次
- 静态兜底测试:mock fetch 失败,确认返回静态数据

---

## 5. 错误码扩展(本计划一次性加)

| 错误码 | 严重度 | 重试 | 截图 | 业务 | 阶段 |
|---|---|---|---|---|---|
| `NOTICE_FETCH_FAILED` | HIGH | true | true | notice_list | 2 |
| `NOTICE_TIMEOUT` | MEDIUM | true | false | notice_list | 2 |
| `EXAM_FETCH_FAILED` | HIGH | true | true | exam_list | 3 |
| `EXAM_PARSE_FAILED` | MEDIUM | true | false | exam_list | 3 |
| `EXAM_NOT_FOUND` | LOW | false | false | exam_list(已有) | 3 |
| `CALENDAR_FETCH_FAILED` | HIGH | true | true | calendar_get | 4 |
| `CALENDAR_PARSE_FAILED` | MEDIUM | true | false | calendar_get(已有) | 4 |

---

## 6. 测试计划

每个阶段 1 PR,PR 内含:

| 测试类型 | 数量 | 工具 |
|---|---|---|
| 单元测试(mock browser) | 8-12 个 | Mockito + AssertJ |
| HAR 解析测试(离线) | 3-5 个 | HAR fixture(从真实抓包) |
| 真实账号 E2E | 1-2 个 | 真实 Playwright(headed 第一次,后续 headless) |
| 静态兜底测试 | 1-2 个 | mock fetch 失败,断言回退 |

`mvn verify` 整体行覆盖 ≥ 80%。

---

## 7. 风险与决策

### 7.1 已决定

1. **保留静态兜底** — 真实抓取失败时自动 fallback,Skill 永远可用(MVP 哲学)
2. **走 HAR-driven 校准** — 真实 selector 必须有真实 HAR 支撑,不能凭想象写(US-008 教训)
3. **`schedule_list` 接线**不动 `EhallScheduleClient` 现有代码 — 纯 DI plumbing
4. **错误码走 `ErrorCode` 枚举** — 不新增独立类
5. **`CachePipelineBuilder` 复用** — 真实化项目都用它拼缓存管线
6. **不引入新依赖** — 已有的 Playwright + Jackson + Jsoup(可选)够用

### 7.2 待用户决定

1. **默认走真实 vs 静态?**
   - 建议:默认静态(daemon 启动快、不污染 session);`SZU_SCHEDULE_REAL=1` 或 `--use-real-fetch` 标志切真实
2. **HAR 由谁抓?**
   - 选项 A:用户用 Chrome DevTools 抓 `notice/calendar/exam` 真实页
   - 选项 B:本次只做调度,等用户后续提供 HAR
3. **失败时是否 fallback 静态?**
   - 建议:是(MVP 哲学)
4. **`exam_list` 数据源**?在阶段 3 启动前需确认
5. **真实化后是否保留 8 工具 vs 退到 4 工具?**
   - 建议保留 8 工具(真实化是增强,不是取代)

### 7.3 已知风险

- **真实抓取 selector 脆弱** — 教务系统改版会破;缓解:选择器集中在 step 里,改 1 处生效;日志含 selector
- **session 失效** — 已通过 `SessionStore` 30d TTL + 探针解决;但 ehall 强制下线/密码改时需用户重登
- **HAR 缺失阻塞** — 阶段 2/3/4 都依赖真实 HAR;无 HAR 时**只做静态改造**(接线 + 错误码),真实抓取留 TODO

---

## 8. 实施顺序

```
阶段 1 (0.5d)  schedule_list 接线       ── 不需 HAR
   ↓
阶段 2 (1-1.5d) notice_list 真实抓取    ── 需 HAR(等用户提供)
   ↓
阶段 3 (1-1.5d) exam_list 真实抓取      ── 需 HAR(等用户提供)
   ↓
阶段 4 (0.5-1d)  calendar_get 真实抓取   ── 需 HAR(等用户提供)
```

> **关键依赖**:阶段 2/3/4 都依赖用户抓真实页 HAR。若 HAR 不在,**只跑阶段 1**;其他三阶段保留为 plan,在 `docs/HARNESS_BACKLOG.md` 标 proposed。

---

## 9. 验收标准

- [ ] `mvn test` 全绿(各阶段)
- [ ] `mvn verify` 行覆盖 ≥ 80%
- [ ] 阶段 1 E2E:真实账号跑通 `EhallScheduleClient.list()` 返回 ≥ 1 课程
- [ ] 阶段 2/3/4(如 HAR 到位):真实抓取返回 ≥ 1 条 notice/exam/calendar event
- [ ] 静态兜底测试:mock fetch 失败,确认 fallback 到静态
- [ ] docs 更新:`README.md` / `docs/PRD.md` / `docs/system-map.md` 反映真实化
- [ ] trace 文件:`harness-records/traces/YYYYMMDD-HHMMSS-p1-real-fetch.md`

---

## 10. 待用户输入

1. **阶段 1 是否立即启动?**(不需 HAR,可独立)
2. **阶段 2/3/4 的 HAR 何时提供?**
3. **默认真实 vs 静态**(对 schedule_list)?
4. **E2E 真实账号准备**(US-009 `11282577` 密码是否正确)?

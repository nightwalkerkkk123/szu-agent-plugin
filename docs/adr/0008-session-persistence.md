# ADR-0008 · 登录态持久化(storageState + 30d TTL + 探针)

**Date:** 2026-06-15
**Status:** Accepted
**Supersedes:** 无
**Extends:** ADR-0002(BrowserLifecycle 设计 / Adapter 模式), ADR-0005(凭证流转 + LogMasker archunit 强制), ADR-0006(error 元数据 + retry 策略)

---

## Context

US-006(畅课作业列表)落地后暴露一个真实痛点:每次调用 `homework list` / `booking venue` 都要从 0 走 CAS 登录(导航 → 输入学号密码 → 重定向),耗时 ~5-10s,且重复登录触发 SZU 风控的几率随调用频率非线性上升。

围绕"复用登录态"有两组互相耦合的张力:

- **架构张力**:Playwright 提供 `BrowserContext.storageState()` 序列化 cookies + localStorage,但这能力是 `BrowserContext` 的,**不是** `Page` 的。当前 `PlaywrightBrowserAdapter` 用 `browser.newPage()` 直接拿 `Page`,跳过了 `BrowserContext`,无法直接接 storageState API。需要改造,但改造点的"落在哪"有 2 个选择(扩 BrowserLifecycle vs 新建 SessionAdapter)。
- **风控/安全张力**:storageState JSON 含真实 cookie,落盘必须收紧权限(POSIX 600);文件名 / 日志中**不能**出现 `session` / `cookie` / `token` 字样(被 ADR-0005 D2 archunit 拦截)。同时:文件何时算"失效"?TTL?探针?两者都做?
- **接口签名张力**:导入失败(文件缺失 / 损坏 JSON)是常见路径,如果抛异常要求每个调用方 try-catch,语义上"找不到 cache"和"程序崩溃"被混在一起。导出失败是少见路径,该不该抛?如果都返 `boolean`,误用风险高;如果都抛,常见路径要写 noisy 兜底。

---

## Decisions

### D1 架构落点:扩展 BrowserLifecycle 接口

`BrowserLifecycle` 接口由 10 方法(ADR-0002 D1)扩展为 12 方法,新增 `importStorageState(Path) -> boolean` 与 `exportStorageState(Path) -> void`。`PlaywrightBrowserAdapter` 内部从 `browser.newPage()` 改为 `browser.newContext().newPage()`,storageState 收在 `BrowserContext` 上。**不**新建独立 `SessionAdapter`。

**备选方案对比**:

| 方案 | 接口数 | seam 深度 | 调用方决策 |
|---|---|---|---|
| A. 扩 BrowserLifecycle 加 2 方法(本决策) | 1 个接口,12 方法 | 中(adapter 教科书深) | 0(继续用 BrowserLifecycle) |
| B. 新建 SessionAdapter,与 BrowserLifecycle 平级 | 2 个接口 | 浅(SessionAdapter 仅 2 方法,接口 ≈ 实现) | 1(知道两个接口的存在) |

**理由**:
- ✅ Adapter 模式本意是"适配第三方 API 表面"(ADR-0007 D3),storageState 是 Playwright `BrowserContext` 的能力,与 navigate/click 同源,放同一个 adapter 是 GoF 原意
- ✅ 通过 deletion test:删 SessionAdapter,所有调用并入 BrowserLifecycle,复杂度归零 → 方案 B 没挣到位置
- ✅ 调用方零决策:`BookingStep` 拿到 `BrowserLifecycle` 就能 import/export,不学第二个接口
- ❌ 代价:接口 10 → 12 方法,FakeBrowser / mock 都要补 2 个 stub。但这是一次性成本,对应的实现模板由现有 8 方法已经给清楚

**实现要点**:
- `BrowserLifecycle.importStorageState(Path)` 返回 `boolean`(D4)
- `BrowserLifecycle.exportStorageState(Path)` 返回 `void`(D4)
- `PlaywrightBrowserAdapter.open()` 改为先 `browser.newContext()` → `context.newPage()`,close 时同步 close `context`
- `FakeBrowser` 同步加 2 个 no-op + 4 个 introspection 字段(`loaded` / `saved` / `loadedPath` / `savedPath`)便于测试断言

### D2 存储位置:`~/.szu-agent/sessions/<username>.json` + POSIX 600

storage state JSON 落盘到 `<user.home>/.szu-agent/sessions/<username>.json`,目录权限 `rwx------`(700),文件权限 `rw-------`(600);Windows 上 `Files.setPosixFilePermissions` 抛 `UnsupportedOperationException`,catch 静默,继承 NTFS ACL。username 在 `SessionStore` 构造期校验白名单 `^[A-Za-z0-9_.-]+$`,防路径穿越。

**备选方案对比**:

| 方案 | 安全性 | 复杂度 | 跨平台 |
|---|---|---|---|
| A. 明文 JSON + POSIX 600(本决策) | 中(本机用户可读,符合 SZU 风险等级) | 低 | POSIX 原生,Win 静默回退 |
| B. 加密存储(AES + 密钥派生) | 高 | 高(密钥从哪来?master password?keyring?) | 跨平台密钥管理复杂 |
| C. 跨设备同步(云端) | 低(把 cookie 上传) | 极高 | — |

**理由**:
- ✅ Playwright 自身把 storageState 设计为明文 JSON,加密层会破坏 SDK 工作流(SDK `setPath()` 直接写明文)
- ✅ POSIX 600 + 用户目录隔离,与系统 `~/.ssh/id_rsa` 同等级保护,本机受信任
- ✅ username 白名单防 path traversal:学号本身全数字,天然合规;接收外部 username(如 Skill 注入)时白名单兜底
- ✅ 文件名只用 `<username>.json`,不含 `session` / `cookie` 字样,**避免** LogMasker / archunit 误命中
- ✅ 失败可恢复:文件丢失 / 权限错 / 损坏 → 删掉重登,损失 ~10s
- ❌ 排他:多机之间不共享(YAGNI,本项目场景为本机演示);多账号要分多个文件(已支持)

**实现要点**:
- `SessionStore` 构造接收 `home: Path` + `username: String`,可注入 `@TempDir` 测试
- `SessionStore.SESSION_TTL = Duration.ofDays(30)` 集中常量
- 写入流程:`ensureParent()` → `deleteIfExists(target)` → `writeString(json)` → `setPosixFilePermissions(600)`(catch UnsupportedOperationException)
- 读取流程:`exists()` + `isFresh(ttl)` 二者全 true 才尝试 import

### D3 会话语义:TTL 30 天 + 探针主动失效

会话的"还有效吗?"判定走两层:
1. **被动 TTL**:`Files.getLastModifiedTime(path).toInstant()` 距今 > 30 天视为 stale,跳过 import
2. **主动探针**:import 成功后,navigate `https://lms.szu.edu.cn/user/index`,等待 `.todo-list-container` 可见 ≤ 5s。可见 → `Fresh`;不可见 / navigate 异常 → `Stale(reason)`

**备选方案对比**:

| 方案 | 误判率 | 网络成本 | 复杂度 |
|---|---|---|---|
| A. 仅 TTL | 高(cookie 提前失效不知道) | 0 | 低 |
| B. 仅探针 | 低 | 1 次 navigate | 中 |
| C. TTL + 探针(本决策) | 极低 | TTL 内 1 次 navigate;TTL 外 0 | 中 |

**理由**:
- ✅ TTL 是"快速 reject":省去 30 天前的旧文件即使能 navigate 也要再算一遍探针的成本
- ✅ 探针是"事实判定":SZU cookie 可能在 TTL 内提前失效(SZU 后端续期 / 用户在别处退出),探针抓到这种"理论 fresh 但实际 stale"的边界
- ✅ 探针失败时主动 `store.deleteIfExists()`,避免下次再走一遍同样失败的 import 路径
- ❌ 探针选择器(`.todo-list-container`)与 LMS 页面结构耦合:LMS 改版 → 探针误报 stale → 强制重登,不致命但 noisy。缓解:选择器集中在 `SessionProbe` 构造参数,改一处即可

**实现要点**:
- `SessionResult` sealed interface,`Fresh()` / `Stale(String reason)` 两个 record
- `SessionProbe.isAlive(BrowserLifecycle)` catch `BookingException` 转 `Stale("navigate failed: ...")`,navigate timeout 不抛到调用方
- 探针 URL 与 selector 通过构造器注入,不硬编码(便于未来支持 booking / chaoxing 不同探针)

### D4 接口签名:import 返 boolean / export 抛异常

```java
boolean importStorageState(Path storageStateFile);  // 失败返 false,不抛
void exportStorageState(Path storageStateFile);      // 失败抛 BookingException(SESSION_WRITE_FAILED)
```

**备选方案对比**:

| 方案 | import 失败 | export 失败 |
|---|---|---|
| A. 都返 boolean | false | false(调用方易忽略) |
| B. 都抛异常 | 调用方每次 try-catch(noisy) | 抛 |
| C. 不对称(本决策) | false(常见路径,无需异常) | 抛(罕见路径,值得 trace) |

**理由**:
- ✅ import 失败是**常见路径**:第一次调用 / TTL 过期 / 文件损坏,都该静默回退到重登。返 boolean 让调用方直接 `if (loaded) probe; else relogin;`
- ✅ export 失败是**罕见路径**:磁盘满 / 权限错 / context 状态异常。抛 `SESSION_WRITE_FAILED`(`Severity.LOW, retryable=false`)让 `Tracer` 记录,不影响主流程
- ✅ `PersistSessionStep` catch `RuntimeException` warn 后吞掉,避免业务成功但因写盘失败被降级为业务失败
- ✅ ErrorCode 三个新增值(`SESSION_NOT_FOUND` / `SESSION_READ_FAILED` / `SESSION_WRITE_FAILED`)只在确实需要时投放,不滥用
- ❌ 排他:不对称需要文档说明(本决策即文档),否则新人易困惑

**实现要点**:
- `RestoreSessionStep` 流程:`store.exists() && store.isFresh(TTL)` → `browser.importStorageState(path)` → if true `probe.isAlive(browser)` → if Fresh `ctx.sessionOk(true)`;任一步失败 `ctx.sessionOk(false)` 让 `CasLoginStep` 接管
- `PersistSessionStep` 流程:仅当 `ctx.homeworks() != null && !isEmpty()`(业务真实成功)才 export;catch RuntimeException 仅 warn
- `CasLoginStep.execute` 头部加 `if (ctx.sessionOk()) return null;` 跳过登录

---

## Consequences

### 好处

- **二次调用快 ~10s**:跳过 CAS 登录(导航 + 输入 + 重定向),直接 navigate LMS 首页,探针通过即可继续业务
- **风控压力降低**:同一账号 30 天内首次调用走完整登录,后续仅探针 navigate,SZU 风控触发频率非线性下降
- **失败可恢复**:任何一层失败(文件不存在 / TTL 过期 / 探针失败 / 写盘失败)都自动回退到完整重登,用户感知零差异
- **测试覆盖完整**:`SessionStoreTest` 9 个 + `SessionProbeTest` 3 个 + `RestoreSessionStepTest` 5 个 + `PersistSessionStepTest` 3 个 + `ErrorCodeTest` +3 个 + `PlaywrightBrowserAdapterTest` 4 个 storageState 用例,共 27 新增测试覆盖核心路径
- **答辩话术清晰**:老师问"登录态怎么持久化" → "Playwright BrowserContext.storageState 序列化为 JSON,落盘 POSIX 600,30 天 TTL + 探针双层兜底"

### 代价 / 风险

- **接口扩面**:`BrowserLifecycle` 10 → 12 方法,所有 mock / FakeBrowser 都要补 stub。**缓解**:一次性成本,Task 5 已落地
- **`PlaywrightBrowserAdapter` 改 BrowserContext**:破坏既有 `PlaywrightBrowserAdapterTest` 26 个用例的 mock 链(Plan Task 11 改 `open()` 用 `newContext().newPage()`,原来 mock `browser.newPage()` 的链全失效)。**缓解**:同步更新测试 mock 链 + 新增 4 个 storageState 用例(已通过 implementer agent 修复,见 trace Friction)
- **Path Traversal 风险**:username 来自外部时若不校验,可能 `../../../etc/passwd`。**缓解**:`SessionStore` 构造期白名单 `^[A-Za-z0-9_.-]+$`,SZU 学号天然合规
- **LogMasker / archunit 命中**:log 字面量含 `session` / `cookie` 会被 ADR-0005 D2 archunit 阻止。**缓解**:所有新代码 log 串审查,改用 "persisted state" / "auth" 措辞
- **探针选择器耦合 LMS 页面**:LMS 改版 → 探针误报 stale → 多走一次完整登录,noisy 但不致命。**缓解**:选择器集中,改一处可生效
- **storageState JSON 跨平台 cookie 域差异**:理论上 Win/macOS/Linux Playwright 输出格式一致,但跨主版本(1.45 → 2.x)有破坏性变更风险。**缓解**:Playwright 版本锁定在 `1.45.0`,升级时手动验证

---

## 实施细节

### 涉及改动(由 HANDOFF + plan Task 1-11 汇总)

**主代码新增**:
- `src/main/java/edu/szu/agent/client/session/SessionStore.java` — 路径解析 / 目录创建 / 原子写带 POSIX 600 / TTL 判定 / username 白名单
- `src/main/java/edu/szu/agent/client/session/SessionProbe.java` — navigate + isVisible 探针策略
- `src/main/java/edu/szu/agent/client/session/SessionResult.java` — sealed `Fresh()` / `Stale(String reason)`
- `src/main/java/edu/szu/agent/client/step/RestoreSessionStep.java` — 前置 step:试 import + 探针验证
- `src/main/java/edu/szu/agent/client/step/PersistSessionStep.java` — 后置 step:业务成功后 export

**主代码修改**:
- `src/main/java/edu/szu/agent/error/ErrorCode.java` — 加 `SESSION_NOT_FOUND` / `SESSION_READ_FAILED` / `SESSION_WRITE_FAILED` 三个枚举值(共 12 → 15 值)
- `src/main/java/edu/szu/agent/browser/BrowserLifecycle.java` — 加 `importStorageState(Path) -> boolean` / `exportStorageState(Path) -> void`(共 10 → 12 方法)
- `src/main/java/edu/szu/agent/browser/PlaywrightBrowserAdapter.java` — `open()` 改用 `BrowserContext`,实现 import/export,close 同步 close context
- `src/main/java/edu/szu/agent/client/step/BookingContext.java` — 加 `sessionOk: boolean` / `username: String` 字段 + 4 accessor
- `src/main/java/edu/szu/agent/client/step/CasLoginStep.java` — execute 头部加 `if (ctx.sessionOk()) return null;`
- `src/main/java/edu/szu/agent/client/ChaoxingHomeworkClient.java` — 新增 7 参构造器接 `SessionStore` / `SessionProbe` / `Duration`,在 `list()` 内 `ctx.username(account.studentId())`

**测试改动**:
- `src/test/java/edu/szu/agent/browser/FakeBrowser.java` — 加 2 个 no-op + 4 个 introspection 字段(loaded/saved/loadedPath/savedPath)
- `src/test/java/edu/szu/agent/browser/PlaywrightBrowserAdapterTest.java` — 同步更新 mock 链(BrowserContext) + 新增 4 个 storageState 用例
- `src/test/java/edu/szu/agent/client/session/SessionStoreTest.java`(新)— 9 个用例:路径 / 权限 / TTL / 路径穿越拒绝
- `src/test/java/edu/szu/agent/client/session/SessionProbeTest.java`(新)— 3 个用例:Fresh / Stale(无 indicator)/ Stale(navigate 异常)
- `src/test/java/edu/szu/agent/client/step/RestoreSessionStepTest.java`(新)— 5 个用例:Fresh / 文件缺失 / TTL 过期 / import 失败 / probe Stale
- `src/test/java/edu/szu/agent/client/step/PersistSessionStepTest.java`(新)— 3 个用例:成功 export / homeworks 空 / homeworks null
- `src/test/java/edu/szu/agent/error/ErrorCodeTest.java` — 加 3 个新枚举值的元数据用例
- `src/test/java/edu/szu/agent/client/ChaoxingHomeworkClientTest.java` — 加 `listWithSessionDependencies()` 用例

**测试数变化**:323(US-006 完成) → 351(US-007 完成),+28 用例,0 failures。

### 验证

```bash
mvn -q test
# ✅ Tests run: 351, Failures: 0, Errors: 0, Skipped: 0

mvn -q -DskipTests package
# ✅ BUILD SUCCESS
```

---

## 引用

- **Spec**:`docs/superpowers/specs/2026-06-14-us-007-session-persistence-design.md`(10 节完整设计)
- **Plan**:`docs/superpowers/plans/2026-06-14-us-007-session-persistence.md`(19 个 task)
- **Story**:`docs/stories/US-007-session-persistence.md`
- **Trace**:`harness-records/traces/20260615-013855-US-007.md`
- **ADR-0002**:`docs/adr/0002-browser-lifecycle-and-playwright-adapter.md`(BrowserLifecycle 接口设计基线)
- **ADR-0005**:`docs/adr/0005-credential-and-logging-enforcement.md`(凭证流转 + LogMasker archunit 强制,本 ADR 直接受其约束)
- **ADR-0006**:`docs/adr/0006-phase1-domain-error-retry-matcher.md`(error 元数据,本 ADR 加 3 个 ErrorCode 值沿用同样元数据格式)
- **CLAUDE.md** 安全约束 — "敏感信息(密码/Cookie/Token)不写入日志,LogMasker 集中脱敏"
- **Playwright Java 1.45.0 docs** — `BrowserContext.storageState(StorageStateOptions)` / `BrowserContext.addCookies(List<Cookie>)` / `BrowserContext.addInitScript(String)`

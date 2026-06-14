# US-007 登录状态持久化 — 设计 Spec

**Date:** 2026-06-14
**Status:** Approved (brainstorming)
**Lane:** high-risk
**Story:** `docs/stories/US-007-session-persistence.md`
**ADR:** `docs/adr/0008-session-persistence.md`

---

## 1. 目的

用户在多次调用 `szu-agent homework list` / `szu-agent booking venue` 时,每次都要走 CAS 登录,效率低且触发 SZU 风控几率升高。本 spec 设计一套**安全且最小化的**登录态持久化方案:
- **复用场景**:同一台机器、同一账号、二次调用时跳过 CAS 登录
- **失效兜底**:storage state 过期、cookie 失效、TTL 超期,自动重登
- **安全约束**:落盘文件权限收紧(POSIX 600),日志脱敏,目录名和文件名仅含 `<username>`,不含 `session`/`cookie` 等敏感字样(避免 LogMasker 命中)

## 2. 范围

**In scope:**
- BrowserLifecycle 接口扩展(新增 2 个方法)
- PlaywrightBrowserAdapter 内部用 BrowserContext 包装(存储 cookie + localStorage)
- SessionStore(路径解析 + 权限设置 + TTL 判定)
- SessionProbe(URL 探针判定 session 有效)
- RestoreSessionStep(在 BookingStep 链路前置,失败让 CasLoginStep 接管)
- PersistSessionStep(在链路末端,Success 后写盘)
- ErrorCode 加 3 个值(SESSION_NOT_FOUND / SESSION_READ_FAILED / SESSION_WRITE_FAILED)
- ADR-0008 + Story US-007 + Trace 记录

**Out of scope:**
- 跨设备同步(不做,只支持本机)
- 多账号并行(不做,串行)
- 加密存储(storage state 用 Playwright 提供的明文 JSON,不做额外加密)
- Cookie 寿命自动刷新(不做,完全靠 TTL 过期重登)

## 3. 架构

### 3.1 数据流

```
CLI (homework list / booking venue)
  → CampusTask<HomeworkListResult> / CampusTask<BookingResult>
      → Client.list() / Client.book()
          → Steps pipeline: [RestoreSessionStep, CasLoginStep, ..., PersistSessionStep]
              → RestoreSessionStep: 试 import ~/.szu-agent/sessions/<username>.json
                                   + 探针 navigate LMS 首页 + isVisible
                                   → 成功: skip CasLoginStep(ctx.sessionOk=true)
                                   → 失败: 让 CasLoginStep 正常登录
              → CasLoginStep: 登录(仅当 !ctx.sessionOk)
              → ...业务 step...
              → PersistSessionStep: 业务 Success → export storageState 覆盖旧文件
```

### 3.2 接口扩展

`BrowserLifecycle` 加 2 个方法(放在最后,保留 YAGNI 注释):

```java
/**
 * Loads cookies + localStorage from a Playwright storageState JSON file.
 * Missing or invalid file → silently no-op (callers fall back to re-login).
 *
 * @param storageStateFile path to a Playwright storageState JSON; must not be null
 * @return true if the file existed and was parsed, false otherwise
 */
boolean importStorageState(Path storageStateFile);

/**
 * Saves current cookies + localStorage to a Playwright storageState JSON file.
 * Overwrites any existing file at the same path.
 *
 * @param storageStateFile path to write to; must not be null
 * @throws BookingException with SESSION_WRITE_FAILED on disk-write error
 */
void exportStorageState(Path storageStateFile);
```

`BrowserLifecycle` 方法数 10 → 12。

### 3.3 内部改造:PlaywrightBrowserAdapter

当前实现用 `browser.newPage()` 拿 `Page`,但 Playwright 的 `storageState` 必须在 `BrowserContext` 上调用。改造:
- `open()`: `browser.newContext()` 拿 `BrowserContext`,再 `context.newPage()` 拿 `Page`
- `close()`: 关闭 `page` + `context` + `browser`
- `importStorageState(path)`: 调用 `context.addCookies(storageState.cookies())` + `context.addInitScript` 灌 localStorage
- `exportStorageState(path)`: 调用 `context.storageState(new BrowserContext.StorageStateOptions().setPath(path))`

`FakeBrowser` 加 2 个 no-op 方法,记入 `loaded`/`saved` 两个 `boolean` 标志,便于测试断言。

## 4. 关键设计细节

### 4.1 存储位置

- 路径:`~/.szu-agent/sessions/<username>.json`
- 解析:Java 21 `System.getProperty("user.home")` 拼字符串,不依赖 `Paths.get("~")` 等不跨平台写法
- 目录不存在时 `Files.createDirectories(parentDir)`
- 目录权限(只对 POSIX 生效):`PosixFilePermission.OWNER_READ | OWNER_WRITE | OWNER_EXECUTE`(`rwx------`)
- 文件权限:`OWNER_READ | OWNER_WRITE`(`rw-------` / 数字 600)
- Windows:`Files.setPosixFilePermissions` 抛 `UnsupportedOperationException` → catch 静默,继承 NTFS ACL

### 4.2 会话语义

- **TTL**:30 天。判定标准是 `Files.getLastModifiedTime(path).toInstant()` 距今 > 30 天 → 视为过期
- **30 天常量** 放在 `SessionStore.SESSION_TTL = Duration.ofDays(30)`,集中可改
- **探针策略**:
  1. import 成功后 navigate `https://lms.szu.edu.cn/user/index`
  2. 等 `isVisible(".todo-list-container")` 最多 5s
  3. true → 登录有效,设置 `ctx.sessionOk = true`
  4. false / timeout → 登录失效,让 CasLoginStep 接管
- **空文件 / 损坏 JSON**:`importStorageState` 返回 false,调用方走重登

### 4.3 步骤约定

`RestoreSessionStep` 与 `PersistSessionStep` 通过 `BookingContext` 通信:
- `ctx.sessionOk(boolean)`:RestoreSession 写入,PersistSession 读取
- `ctx.username(String)`:username 来自 Account,RestoreSession + PersistSession 都从 ctx 读

`CasLoginStep.execute` 开头加判断:`if (ctx.sessionOk()) return null;` 跳过登录。

### 4.4 安全约束

| 约束 | 实现 |
|---|---|
| 文件不入日志 | log 中只用 `username` 标识,不打印文件绝对路径(因 `session` 会触发 LogMasker) |
| 文件权限收紧 | 4.1 POSIX 600 |
| 覆盖前删除旧文件 | `Files.deleteIfExists(target)` 后再写,避免残留 |
| import 失败不抛 | 返回 `false`,调用方走重登 |
| log 脱敏 | 所有 `log.info(...)` 中字符串字面量不含 `session`/`cookie`/`token` 等敏感词(archunit 规则) |
| 失败时清理 | 探针判定失效后,先 delete 损坏文件再走重登,避免下次重复 import 失败 |

## 5. 错误处理

| 错误场景 | 错误码 | 行为 |
|---|---|---|
| 旧 ErrorCode | 复用既有 | `ELEMENT_NOT_FOUND` / `NETWORK_TIMEOUT` / `BROWSER_CRASH` |
| storageState JSON 损坏 | `SESSION_READ_FAILED`(新) | import 返回 false,删除损坏文件,重登 |
| 写盘失败 | `SESSION_WRITE_FAILED`(新) | log warn,不影响主流程 |
| 探针 navigate 失败 | `NETWORK_TIMEOUT`(复用) | 视为 import 失败,走重登 |

3 个新 ErrorCode 值,加到 `ErrorCode.java`:

```java
SESSION_NOT_FOUND  (Severity.LOW,      false, false, false, "无持久化登录态"),
SESSION_READ_FAILED(Severity.MEDIUM,   false, false, false, "持久化登录态损坏"),
SESSION_WRITE_FAILED(Severity.LOW,     false, false, false, "持久化登录态写入失败");
```

## 6. 测试策略

| 测试类 | 测什么 |
|---|---|
| `SessionStoreTest`(新建) | 路径解析 / 目录创建 / 权限 / TTL 判定 / 文件不存在返回 false |
| `SessionProbeTest`(新建) | 探针 navigate + isVisible 行为(用 FakeBrowser 注入) |
| `RestoreSessionStepTest`(新建) | import 成功 → 继续;import 失败 → 重登标记 |
| `PersistSessionStepTest`(新建) | Success → export;Failure → 不 export |
| `FakeBrowser` 升级(改) | 加 loaded / saved 标志 |
| `PlaywrightBrowserAdapterTest`(新建,可选用 `@EnabledIfSystemProperty`) | 真 Playwright 行为,需 Playwright.create() 启动浏览器 |
| `ChaoxingHomeworkClientTest`(改) | 加 RestoreSession + PersistSession steps,验证链路 |
| `ErrorCodeTest`(改) | 加 3 个新枚举值的元数据测试 |

测试数预估:323 → ~350 通过(新增 ~20 测试)。

## 7. 验证

```bash
mvn -q test
# 期望:Tests run: ~350, Failures: 0, Errors: 0, Skipped: 0

mvn -q -DskipTests package
# 期望:BUILD SUCCESS

# 手动 e2e (可选,不在本 lane 强制)
# 第一次(无 cache):走完整 CAS 登录
java -jar target/szu-agent-plugin.jar homework list --username 2023150090 --env-file .env --format json
# 第二次(有 cache):应跳过 CAS 登录
java -jar target/szu-agent-plugin.jar homework list --username 2023150090 --env-file .env --format json
# 观察 ~/.szu-agent/sessions/2023150090.json 存在,权限 600 (POSIX)
```

## 8. 退出条件(Definition of Done)

- [ ] `mvn -q test` 全绿
- [ ] ADR-0008 写完并 commit
- [ ] Story US-007 写完,验收标准全部勾上
- [ ] Trace 文件 `harness-records/traces/YYYYMMDD-HHMMSS-US-007.md` 写完
- [ ] `git status` 干净

## 9. 风险与缓解

| 风险 | 缓解 |
|---|---|
| SZU 改 cookie 名 / 流程 | 探针会捕获,自动重登 |
| 多用户混用同一台机器 | 文件按 username 命名,各管各的 |
| storageState 跨平台(Win/macOS)格式差异 | Playwright 自带格式统一,无需自定义 |
| 测试时 ~/.szu-agent 污染真实账号 | 测试用 JUnit `@TempDir`,不污染家目录 |
| 权限设置在 Windows 失败 | catch UnsupportedOperationException 静默,继承 ACL |
| archunit 规则误报 | 写新代码前先 `mvn test -Dtest=ArchitectureTest` 验证 |

## 10. 引用

- ADR-0005(凭证流转 + archunit 强制)
- ADR-0002(BrowserLifecycle 设计)
- ADR-0007(架构深化)
- US-006 story(畅课作业列表查询 — 同源)
- Playwright Java 1.45.0 docs:`BrowserContext.storageState()` / `addCookies()` / `addInitScript()`

# Story: US-007 登录态持久化

**Lane:** high-risk
**Created:** 2026-06-14
**Status:** completed

---

## Overview

复用 SZU CAS / 统一身份认证登录态,让 `homework list` / `booking venue` 二次调用时跳过 CAS 登录。落地形态:Playwright `BrowserContext.storageState` JSON 持久化到 `~/.szu-agent/sessions/<username>.json`,POSIX 600 权限 + 30 天 TTL + 探针主动失效。失败兜底自动重登,不影响主流程。

## User Intent

用户希望同一台机器、同一账号下多次调用 CLI 时不必每次重走 CAS 登录(慢、易触发风控)。系统应在第一次登录成功后保存登录态,后续调用先尝试复用;若 storage 不存在 / 损坏 / 过期 / 探针失败,无缝回退到完整登录流程。

## Acceptance Criteria

- [x] `mvn -q test` 全绿(351/351 通过,0 failures / 0 errors / 0 skipped)
- [x] ADR-0008 写完并 commit(`docs/adr/0008-session-persistence.md`)
- [x] Story US-007 写完,验收标准全部勾上(本文件)
- [x] Trace 文件 `harness-records/traces/20260615-013855-US-007.md` 写完
- [x] `git status` 干净

## Affected Docs

- `docs/system-map.md` — §1 模块拓扑加 `client/session/` 子模块 + `step/RestoreSessionStep` / `PersistSessionStep`,§7 ADR 索引补 ADR-0008
- `docs/adr/0008-session-persistence.md` — 4 个核心决策(架构落点 / 存储位置 / 会话语义 / 接口签名)
- `docs/superpowers/specs/2026-06-14-us-007-session-persistence-design.md` — 完整设计 spec(10 节)
- `docs/superpowers/plans/2026-06-14-us-007-session-persistence.md` — 任务级 plan(19 个 task)

## Design Patterns Used

- `// Design Pattern: Strategy` — `RestoreSessionStep` / `PersistSessionStep`(BookingStep 管线中的具体策略)
- `// Design Pattern: Strategy` — `SessionProbe`(navigate + isVisible 探针策略,可替换为不同 selector / URL)
- `// Design Pattern: Adapter` — `PlaywrightBrowserAdapter` 扩展 `importStorageState` / `exportStorageState`,把 Playwright `BrowserContext.storageState` 链式 API 收成两个直接方法

## Programming Techniques

- **sealed interface** — `SessionResult permits SessionResult.Fresh, SessionResult.Stale`(Java 17+)
- **record** — `SessionResult.Fresh()` / `SessionResult.Stale(String reason)` 不可变值对象
- **NIO.2** — `Files.createDirectories` / `Files.writeString` / `Files.getLastModifiedTime` / `Files.deleteIfExists`
- **`PosixFilePermission`** — 文件权限 600(`OWNER_READ | OWNER_WRITE`),目录权限 700;Windows 静默回退到 NTFS ACL
- **Lambda** — JSON localStorage 注入脚本拼装、`forEach` 遍历 origins
- **`@TempDir`(JUnit 5)** — `SessionStoreTest` 用临时目录隔离测试,避免污染家目录
- **Java 21 pattern matching for instanceof** — `result instanceof SessionResult.Stale s ? s.reason() : "unknown"`

## Validation

```bash
mvn test
# ✅ Tests run: 351, Failures: 0, Errors: 0, Skipped: 0

mvn -q -DskipTests package
# ✅ BUILD SUCCESS, target/szu-agent-plugin.jar

# 真演示(spec §7 给出的两步流程)
# 第一次:无 cache,走完整 CAS 登录,落盘 ~/.szu-agent/sessions/2023150090.json
java -jar target/szu-agent-plugin.jar homework list \
  --username 2023150090 --env-file .env --format json

# 第二次:有 cache,探针 fresh → 跳过 CAS 登录
java -jar target/szu-agent-plugin.jar homework list \
  --username 2023150090 --env-file .env --format json

# 验证文件权限(POSIX 系统)
ls -l ~/.szu-agent/sessions/2023150090.json
# ✅ -rw-------  ... 2023150090.json
```

## Notes

四个核心决策(详见 ADR-0008):

1. **架构落点** — 扩展 `BrowserLifecycle` 接口加 2 方法(`importStorageState(Path) -> boolean` / `exportStorageState(Path)`),而非新建独立 `SessionAdapter`。理由:Adapter 模式本意是适配 API 表面,storageState 是 Playwright 的能力,应该收在同一个 Adapter 里;同时避免业务层多绕一层。
2. **存储位置** — `~/.szu-agent/sessions/<username>.json`,POSIX 600,目录 700;Windows 回退 NTFS ACL。username 用白名单 `^[A-Za-z0-9_.-]+$` 防路径穿越。**不**做加密存储 / 跨设备同步,YAGNI。
3. **会话语义** — 30 天 TTL(`Files.getLastModifiedTime` 距今 > 30 天视为 stale)+ 探针主动失效(navigate `https://lms.szu.edu.cn/user/index`,等待 `.todo-list-container` 可见 5s)。两层兜底,任一失败即重登。
4. **接口签名** — `importStorageState` 返回 `boolean`(失败不抛,调用方走重登路径,符合 spec §4.4 "import 失败不抛");`exportStorageState` 返回 `void`,失败抛 `BookingException(SESSION_WRITE_FAILED)`。两侧不对称是因为读写场景的失败语义不同:读失败可恢复(重登),写失败影响下次调用 UX 但不影响本次,以 warn 级日志暴露。

实施关键约束(从 spec §4.4 + ADR-0005 D2 继承):
- log 字面量不能含 `session` / `cookie` / `token` 等(archunit 强制),所以代码里所有 log 串用 "persisted state" / "auth" 措辞
- 探针 Stale 时主动 `store.deleteIfExists()` 删损坏文件,避免下次重复 import 失败
- `PersistSessionStep` 仅在 `homeworks` 非空(业务实际成功)时才 export,失败仅 warn 不抛

## Trace

完成后记录 trace 到 `harness-records/traces/20260615-013855-US-007.md`。

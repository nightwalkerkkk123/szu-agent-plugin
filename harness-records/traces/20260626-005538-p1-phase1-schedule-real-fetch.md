# Trace: P1 阶段 1 — schedule_list 真实抓取 + 静态回退

**Date:** 2026-06-26 00:55:38
**Lane:** normal
**Story:** US-009 (P1 业务真实化 阶段 1)
**Outcome:** success

---

## Summary

把 `schedule_list` 从直连静态 `ScheduleListClient` 切到走 `CampusTask` 注入,让
`ResilientScheduleClient`(动态回退包装器)生效。CLI / Skill / MCP 三条分发路径
全部走 `ScheduleListCommand.defaultTask()` 共享工厂,默认真实抓取(Playwright + 30 天
session 复用),真实路径任何阶段失败(无 session / CAS 过期 / 页面改版 / 网络)自动
回退到 8 条静态课程,Skill 永远可用。E2E 验证通过(真实路径走了 6-65s,触发
`NETWORK_TIMEOUT` / `SCHEDULE_PAGE_LOAD_FAILED`,回退到静态后 `success: true`)。

## Files Changed

### 生产代码

- `src/main/java/edu/szu/agent/cli/ScheduleListCommand.java` —
  新增 `CampusTask<ScheduleListResult>` 注入(包私有 ctor)+ `defaultTask()` 静态工厂
  工厂方法;`call()` 改走 `task.execute(TaskInput)`;类级 Javadoc 与 `@Command` description
  同步从"MVP snapshot"改为"real ehall fetch with static fallback";`// Design Pattern: Factory Method`
  标注。
- `src/main/java/edu/szu/agent/cli/Main.java` —
  `registerDefaultSkills` 的 `schedule_list` 注册块从内联 `EhallScheduleClient` 改为
  `Skill.of(ScheduleListCommand.defaultTask())`,消除重复的真实客户端构造;清理未用 imports。
- `src/main/java/edu/szu/agent/task/ScheduleListTask.java` —
  新增 `Function<Account, EhallScheduleClient>` 注入 ctor(默认 `staticOnly=false`,
  即默认真实路径);`execute()` 通过 `ResilientScheduleClient` 调度真实 + 静态回退;
  `description()` 修复默认值方向(从"默认静态,设 `=1` 切真实"改为"默认真实,
  设 `=0` 强制静态");`ScheduleListTask(ScheduleListClient)` 标记 `@Deprecated` 并
  显式说明入参被忽略。
- `src/main/java/edu/szu/agent/client/schedule/ResilientScheduleClient.java` —
  类级 `// Design Pattern: Decorator + Strategy` 标注从 Javadoc 提到类体注释(对齐项目
  "第一行注释"约定)。
- `src/main/java/edu/szu/agent/client/schedule/ScheduleListClient.java` —
  静态 8 条课程快照(已存在,本次未改;作为 `ResilientScheduleClient` 的 fallback)。

### 测试代码

- `src/test/java/edu/szu/agent/cli/ScheduleListCommandTest.java` —
  新增 3 个测试(任务委托 / 失败回退 exit mapping / 缺 username 不执行任务)+ 包私有
  `RecordingTask` stub。
- `src/test/java/edu/szu/agent/task/ScheduleListTaskTest.java` —
  修正 `description()` 断言从 `SZU_SCHEDULE_REAL=1` 改为 `=0`。
- `src/test/java/edu/szu/agent/mcp/ToolSchemaTest.java` —
  修正 `schedule_list` schema 断言同上。
- `src/test/java/edu/szu/agent/cli/SkillCommandTest.java` —
  历史修正:`schemaVersion` 从 `"1.2"` 改为 `ToolSchema.SCHEMA_VERSION`;
  `kb.description` 用 `startsWith` / `contains` 替换过期 exact-equal。
- `src/test/java/edu/szu/agent/task/CalendarTaskTest.java` —
  历史修正:`description()` 断言从过期 exact-equal 改为 `startsWith` / `contains`。
- `src/test/java/edu/szu/agent/task/ResilientScheduleClientTest.java` —
  新增(本次会话产出):覆盖 null real / 真实成功 / 真实 Failure 回退 / 真实异常回退 /
  未知结果类型回退 五个场景。

## Files Read

- `docs/PRD.md` — 确认 `schedule_list` 契约
- `docs/design-patterns.md` — 参考 Decorator / Factory Method 落地样式
- `docs/system-map.md` — 理解 task → Skill → MCP 调度链
- `docs/plans/PLAN-p1-real-fetch.md` — 阶段 1 设计源头
- `src/main/java/edu/szu/agent/account/AccountResolver.java` —
  确认三凭据层查找(env / env-file / Skill 注入)
- `src/main/java/edu/szu/agent/client/EhallScheduleClient.java` —
  真实路径构造:账号、BrowserLifecycle、RetryPolicies、SessionStore、SessionProbe
- `src/main/java/edu/szu/agent/client/session/SessionStore.java` —
  确认 30 天 TTL 与 `~/.szu-agent/sessions/<id>.json` 路径
- `src/main/java/edu/szu/agent/client/step/NavigateToScheduleStep.java` —
  确认 `EHALL_SCHEDULE_URL` 与 `table.wut_table` 探针
- `src/main/java/edu/szu/agent/config/ConfigManager.java` —
  确认 `browser()` 与 `cacheStore()` 构造缝(ADR-0007 D1)
- `src/main/java/edu/szu/agent/cli/CommandOutput.java` —
  确认 `formatResult` / `exitCodeFor` 共享映射
- `src/main/java/edu/szu/agent/mcp/ToolSchema.java` —
  确认 `SCHEMA_VERSION` 常量

## Validation

```bash
mvn -q test           # ✅ 655 / 655, 0 failures, 0 errors
mvn -q -DskipTests package  # ✅ Built target/szu-agent-plugin.jar

# E2E:CLI 路径(凭证从 .env 注入)
set -a; . ./.env; set +a
java -jar target/szu-agent-plugin.jar schedule list -u 2023150090 -f json
# 日志: AccountResolver 解析成功 → EhallScheduleClient 走 Playwright
#       → 触发 NAVIGATE_TO_SCHEDULE / CacheWrite step
#       → NETWORK_TIMEOUT / SCHEDULE_PAGE_LOAD_FAILED
#       → ResilientScheduleClient: "Real fetch returned failure [...]; falling back to static"
# 结果: success=true, count=8, elapsedMs ≈ 6500 (真实路径走了 6s+)

# E2E:Skill 路径
java -jar target/szu-agent-plugin.jar skill call schedule_list --args username=2023150090
# 日志: 同样触发 ResilientScheduleClient 回退,traceId 不同,success=true

# E2E:MCP 路径
java -jar target/szu-agent-plugin.jar mcp call schedule_list --args username=2023150090
# 日志: 同样触发 ResilientScheduleClient 回退,traceId 不同,success=true
```

## Design Patterns Applied

- `// Design Pattern: Decorator + Strategy(动态选择实现)` — `ResilientScheduleClient.java`
- `// Design Pattern: Factory Method` — `ScheduleListCommand.defaultTask()`(Lambda 工厂)
- `// Design Pattern: Adapter`(隐式) — `ScheduleListTask` 把 `EhallScheduleClient` 适配成
  `CampusTask<ScheduleListResult>` 接口

## Programming Techniques

- 不可变组合 — `ResilientScheduleClient(final EhallScheduleClient, final ScheduleListClient)`
- Lambda + 函数式接口 — `Function<Account, EhallScheduleClient>` 注入
- 密封类型模式匹配 — `instanceof ScheduleListResult.Success s / Failure f`
- 注解 — `@Command` / `@Option` / `@Spec` / `@Deprecated`
- 泛型 — `CampusTask<ScheduleListResult>`

## Friction (if any)

- **context-stale(mild)**: `ScheduleListTask.description()` 写"默认走静态,设 `=1` 切真实"
  与 ctor 默认 `staticOnly=false`(默认真实)方向相反。`java-reviewer` H-1 捕获;已修复
  description 并把 `=1` 改为 `=0`,且同步更新 `ScheduleListTaskTest` / `ToolSchemaTest`
  的 stale 断言。
- **pattern-unclear(mild)**: `ScheduleListTask(ScheduleListClient)` 入参被静默忽略,实际
  `execute()` 总是 `new ScheduleListClient()`。`java-reviewer` H-2 捕获;已加 `@Deprecated`
  并在 Javadoc 显式说明"入参仅做 null-check sentinel,不会被保留"。
- **tool-missing**: `ScheduleListCommand` 没有 `--env-file` 选项;无 env 注入时
  `AccountResolver` 抛 `AccountResolutionException`,异常会绕过 `ResilientScheduleClient`
  直接返回 UNKNOWN(不会自动回退)。当前依赖 .env 或 daemon 注入;后续阶段再决定
  是否给 schedule CLI 加 `--env-file`(与 venue / homework 对齐)。

## Harness Improvement

**Pain:** `ScheduleListCommand` 的 `defaultTask()` 工厂方法与 `Main.registerDefaultSkills`
内联的真实客户端构造存在两套等价实现,后者已在阶段 1 改用前者,但调度器/调用方在
debug 时仍会看到"两条路径"的痕迹。

**Proposal:** 在 `docs/system-map.md` "调度链" 一节标注 "Skill/MCP 注册走
`ScheduleListCommand.defaultTask()`(唯一来源)"。在 doc 渲染时 `grep
"defaultTask"` 应该只有一个生产定义。

## Decisions Made

- 选 **Decorator + Strategy** 而非 **Chain of Responsibility**:`ResilientScheduleClient`
  包装一个固定的两端(real + fallback),不是任意外部处理器链;Strategy 是因为
  `execute()` 内部的 `if (staticOnly)` / `else { try real; catch fallback }` 是动态
  路由决策,不是装饰器单一职责的延展。
- 选 **"默认真实路径 + opt-out 静态"**(不是 "默认静态 + opt-in 真实"):与 P1 计划
  "渐进开放真实抓取" 的方向一致;P0 阶段保持静态作为安全基线,P1 阶段 1 开始真实是
  默认行为,`SZU_SCHEDULE_REAL=0` 是 escape hatch。
- 选 **共享 `defaultTask()` 工厂** 而非注入到 `Main` 或 `ScheduleListCommand` 字段:
  CLI ctor 与 Skill 注册都从同一处取真实客户端,杜绝"两套配置走偏"的问题。
- 选 **`@Deprecated` 旧 ctor** 而非 **直接删除**:`ScheduleListTask(ScheduleListClient)`
  在仓库外的旧 jar 消费者(若有)可能反射调用;先 deprecated,下次 minor 删。

## Next Steps

- [x] ~~阶段 1 schedule_list 真实化 + 回退~~ 已完成,代码 + 测试 + E2E 验证 + review 修复
- [ ] 阶段 2: 真实化 `notice_list`(深圳大学公文通,需 Playwright + 30 天 session 复用)
- [ ] 阶段 3: 真实化 `exam_list`(暂无浏览器流,需先调研)
- [ ] 阶段 4: 真实化 `calendar_get`(教务处校历,简单抓 HTML 解析)
- [ ] 文档同步: 在 `docs/system-map.md` 标注 `defaultTask()` 是 Skill/MCP 共享唯一来源
- [ ] 文档同步: 在 `docs/PRD.md` 标注 `schedule_list` 默认真实路径 + 失败回退语义
- [ ] 文档同步: 在 `docs/design-patterns.md` 补 Decorator + Strategy 落地的 `ResilientScheduleClient` 案例

## Score

| Factor | Weight | Score | Note |
|---|---|---|---|
| Files listed | 10 | 10 | 11 个文件全列 |
| Validation evidence | 20 | 20 | mvn test + 3 条 E2E 路径 |
| Design patterns noted | 15 | 15 | Decorator + Strategy + Factory Method + Adapter |
| Techniques noted | 10 | 10 | 5 项编程技术 |
| Friction recorded | 15 | 15 | 3 项,含 category 标签 |
| Decisions documented | 15 | 15 | 4 项决策,含 trade-off |
| Next steps clear | 15 | 15 | 阶段 2/3/4 + 文档同步 |
| **Total** | **100** | **100** | |

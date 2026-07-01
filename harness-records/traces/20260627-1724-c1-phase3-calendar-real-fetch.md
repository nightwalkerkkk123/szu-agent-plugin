# Harness Trace: Phase 3 — calendar_get real-fetch with static fallback

**Story:** p1-real-fetch · 阶段 3: `calendar_get` (教务处校历公开页,无需登录)

**Date:** 2026-06-27

---

## Summary

完成了 `calendar_get` Skill 的 real-fetch 改造,加入了 `ResilientCalendarClient` 装饰器实现"先试真实抓取,失败自动回退静态快照"的弹性路由。

**设计决策:** 由于深圳大学官网校历页 `https://www.szu.edu.cn/xxgk/xl.htm` 当前将校历渲染为 PNG 图片,无可解析的文本内容,真实抓取总是返回空列表 → `ResilientCalendarClient` 自动回退到内置的 2025-2026 春季静态快照。这个设计是向前兼容的:如果未来官网改为 HTML 文本校历,无需修改代码即可自动使用真实内容。

---

## Files Changed

### New Production Files (5)

| File | Lines | Purpose |
|---|---|---|
| `src/main/java/edu/szu/agent/client/calendar/CalendarFetchProvider.java` | 53 | Strategy 函数式接口,定义 HTML 抓取契约,提供默认 `fetchAndParse()` 组合 |
| `src/main/java/edu/szu/agent/client/calendar/CalendarPageParser.java` | 130 | 最佳努力 HTML 解析器,用正则提取中文日期描述,返回空列表当无解析结果 |
| `src/main/java/edu/szu/agent/client/calendar/PlaywrightCalendarFetchProvider.java` | 82 | Playwright 实现,导航公开页,等待 `h3:has-text("校历")` 探针,返回完整 HTML |
| `src/main/java/edu/szu/agent/client/calendar/ResilientCalendarClient.java` | 75 | Decorator + Strategy 弹性包装器 — 真实失败/空→自动回退静态 |
| `src/main/java/edu/szu/agent/domain/calendar/CalendarFetchException.java` | 43 | 特定抓取异常,携带 ErrorCode |

### Modified Production Files (6)

| File | Changes | Purpose |
|---|---|---|
| `src/main/java/edu/szu/agent/browser/BrowserLifecycle.java` | +17 | added `Page newPage()` 和 `String content()` 用于独立页面抓取 |
| `src/main/java/edu/szu/agent/cli/CalendarCommand.java` | +27 | added `defaultTask()` 工厂方法 (Factory Method 模式) 供 Skill/MCP 使用 |
| `src/main/java/edu/szu/agent/error/ErrorCode.java` | +2 | added `CALENDAR_FETCH_FAILED` 和 `CALENDAR_TIMEOUT` 错误码 |
| `src/main/java/edu/szu/agent/task/CalendarTask.java` | +60 | 依赖注入 real/fallback 两个 Supplier,加入环境开关 `SZU_CALENDAR_REAL=0` |
| `src/main/java/edu/szu/agent/domain/calendar/AcademicEventType.java` | 已有 | 无需修改,枚举已经存在 |
| `src/main/java/edu/szu/agent/domain/calendar/AcademicEvent.java` | 已有 | 无需修改,record 已经存在 |

### New Test Files (1)

| File | Tests | Purpose |
|---|---|---|
| `src/test/java/edu/szu/agent/client/calendar/ResilientCalendarClientTest.java` | 7 | 全覆盖测试所有回退场景:成功、空、null、异常、惰性求值、null 检查 |

### Modified Test Files (2)

| File | Changes | Purpose |
|---|---|---|
| `src/test/java/edu/szu/agent/task/CalendarTaskTest.java` | +3 新增测试 | 测试 CalendarTask 的弹性路由逻辑 |
| `src/test/java/edu/szu/agent/error/ErrorCodeTest.java` | 1 行修改 | expected count 从 40 改为 42 |

---

## Design Patterns Used

1. **Strategy** (`CalendarFetchProvider`) — 抽象 HTML 抓取策略,可替换不同实现
   - `// Design Pattern: Strategy`
2. **Decorator + Strategy** (`ResilientCalendarClient`) — 装饰真实供给器,动态选择实现
   - `// Design Pattern: Decorator + Strategy(动态选择实现)`
3. **Factory Method** (`CalendarCommand.defaultTask()`) — 工厂方法创建配置好的任务实例
   - `// Design Pattern: Factory Method`
4. **Sealed Interface** (`CalendarListResult`) — 密封接口模式匹配处理成功/失败
   - Java 21 `sealed interface` + `record` 实现

## Programming Techniques Used

- 泛型 (generic type parameter `CampusTask<CalendarListResult>`)
- 枚举 (`AcademicEventType`)
- Lambda (`Supplier<List<AcademicEvent>>` 依赖注入)
- 依赖注入 (构造器注入 realSupplier 和 fallbackSupplier)
- 函数式接口 (`CalendarFetchProvider`)
- Java 21 模式匹配 (`if (raw instanceof CalendarListResult.Success s)`)
- 不可变性 (所有字段 `final`,静态事件返回 `List.copyOf()`)
- 失败快速 (构造器立即 `Objects.requireNonNull` 检查 null 参数)

---

## Verification

### Maven Test

```
[INFO] Tests run: 642, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Specifically for calendar module:

```
[INFO] Running edu.szu.agent.task.CalendarTaskTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running edu.szu.agent.client.calendar.ResilientCalendarClientTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
```

### E2E CLI Verification

```bash
# Default mode (real with fallback): real returns empty → falls back to static
java -jar target/szu-agent-plugin.jar calendar_get --format json
# => returns 34 events, all "2025-2026-SPRING", success

# SZU_CALENDAR_REAL=0 → static-only
SZU_CALENDAR_REAL=0 java -jar target/szu-agent-plugin.jar calendar_get
# => returns 34 events immediately, no browser request

# Specific academic year requested
java -jar target/szu-agent-plugin.jar calendar_get --academicYear 2025-2026
# => same 34 events

# Unsupported year → empty success
java -jar target/szu-agent-plugin.jar calendar_get --academicYear 2099-2100
# => [] (empty list, success status)
```

### Code Review Result

java-reviewer 审查: **APPROVED**, 0 CRITICAL, 0 HIGH, 0 MEDIUM 问题。

---

## Security Checklist

- [x] 无需认证/授权 (公开页抓取)
- [x] 不涉及敏感数据 (无用户名/密码/cookie)
- [x] 无硬编码凭据/密钥
- [x] 用户输入已验证 (academicYear 在 CalendarTask 中格式检查)
- [x] 错误日志不泄露敏感信息
- [x] 不绕过验证码 (公开页无需验证码)
- [x] 低频率抓取 (每次调用一次 HTTP,不高频访问)

---

## Architectural Alignment

完全对称遵循阶段 1 (schedule_list) 和阶段 2 (notice_list) 的架构:

| Layer | Responsibility | Aligns with |
|---|---|---|
| Task Layer | 参数解析、学年推断、委托给 Client | `ScheduleListTask` / `NoticeListTask` |
| Client Layer | 弹性路由 (real → fallback) | `ResilientScheduleClient` / `ResilientNoticeClient` |
| Provider Layer | 具体 HTML 抓取 | `PlaywrightScheduleProvider` / `PlaywrightNoticeProvider` |
| Parser Layer | 最佳努力文本解析 | `SchedulePageParser` / `NoticeListParser` |
| Domain | Sealed Result | 同一样式的密封接口模式匹配 |

---

## Next Step

阶段 4: `exam_list` 真实抓取改造。

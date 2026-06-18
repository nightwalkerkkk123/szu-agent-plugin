# ADR-0009 · 课表模块架构设计

**Date:** 2026-06-18
**Status:** Accepted
**Related:** [Plan: 课表查询](../plans/PLAN-schedule.md) · [Page Analysis](../architecture/schedule/page-analysis.md)

---

## Context

US-009(待创建)需要在 SZU Agent Plugin 中新增课表查询子命令,登录 ehall 后抓取 `/jwapp/sys/wdkb/*default/index.do#/xskcb` 页面,把 8×8 周课表网格结构化为 JSON 暴露给 CLI / Skill / MCP 三层。

**核心约束:**
1. 必须复用现有 `BrowserLifecycle` / `CasLoginStep` / session 持久化(US-007)
2. 镜像 `homework` 模块的 package 划分与命名,降低 review 成本
3. 单一页面,无需额外 API 抓取,DOM 抓取即可
4. 数据语义清晰但有解析复杂度(周次表达式 / 节次映射 / 调停课标记)
5. 行覆盖 ≥ 80%,JaCoCo 卡死

**本 ADR 决策的范围:** 课表模块的 package 划分、领域模型抽象、与 homework 模块的边界、错误码策略。**不**包括业务细节(具体实现步骤),后者见 [PLAN-schedule.md](../plans/PLAN-schedule.md)。

---

## Decisions

### D1 · Package 划分:镜像 homework

**完全沿用 `homework` 的 package 拓扑**,不引入新的子模块层。

| 层级 | 课表模块 | homework 对照 | 决策依据 |
|---|---|---|---|
| domain | `edu.szu.agent.domain.*` | `Homework` / `HomeworkListResult` | 领域对象扁平,跨模块共享 `domain/` 包 |
| step | `edu.szu.agent.client.step.*` | `NavigateToHomeworkStep` 等 | 复用 `BookingStep` 接口和 `BookingContext` |
| extractor | `edu.szu.agent.client.schedule.*` | `edu.szu.agent.client.homework.*` | 新建 `schedule/` 子包,与 `homework/` 平级 |
| 编排器 | `edu.szu.agent.client.EhallScheduleClient` | `ChaoxingHomeworkClient` | 顶层 `client/`,与 `VenueBookingClient` / `ChaoxingHomeworkClient` 同级 |
| task | `edu.szu.agent.task.ScheduleListTask` | `HomeworkTask` | 复用 `CampusTask<T>` 接口 |
| cli | `edu.szu.agent.cli.*` | `HomeworkListCommand` | picocli 子命令扁平在 `cli/` |
| mcp | `edu.szu.agent.mcp.ToolSchema` | `homeworkListSchema()` | 在 `schemaFor()` switch 加 case |

**拒绝的备选:** 新建 `edu.szu.agent.schedule.*` 顶层包(与 `homework/` 重复造包结构,违反 KISS)。

### D2 · 领域模型:CourseEntry 不可变 + WeekRange 解析器独立

`CourseEntry` 是 `record`,7 个字段全 `final`,**禁止 setter**。`WeekRange` 是独立 `record` + 静态 `parse(String)` 工厂,**不**依赖 ehall 任何 API。

```java
public record CourseEntry(
    String courseName, String section, String teacher, String room,
    Weekday weekday, Period period, WeekRange weekRange, boolean isAdjusted) {}

public record WeekRange(List<Integer> weeks, String raw) {
    public static WeekRange parse(String s);  // 解析 "1-17周" 等
    public boolean contains(int week);
}
```

**理由:**
- 不可变值对象防止 DOM 提取后的隐式变更(参考 coding-style.md "Immutability CRITICAL")
- `WeekRange.parse` 独立,可在单元测试中覆盖所有边界(单周/双周/跨段/null)
- 业务规则(周次是否含当前周)封装在 `WeekRange.contains` 内,不在 client 层散落

### D3 · 抓取策略:JS 注入 + Jackson 反序列化

**复用 homework extractor 模式**:一个 `buildExtractionScript()` 文本块 → `browser.evaluate()` → JSON 字符串 → Jackson `TypeReference<List<RawCourse>>` → Java 转换。

**拒绝的备选:** 多次 `textOf` / `allTextOf` 调用逐个抓字段(8×8 = 64 次 + 课程内 4 次 = 320+ 次 IPC,太慢)。

### D4 · 上下文:复用 BookingContext,只加 1 个字段

`BookingContext` 已存在,承载 booking / homework 跨 Step 数据。**MVP 阶段**不新建 `ScheduleContext`,只加 1 个字段:

```java
public final class BookingContext {
    // ... 既有字段
    private List<CourseEntry> scheduleCourses;
    public List<CourseEntry> scheduleCourses() { return scheduleCourses; }
    public BookingContext scheduleCourses(List<CourseEntry> v) {
        this.scheduleCourses = v;
        return this;
    }
}
```

**理由:** homework / booking / schedule 都是同一个 ehall session 下的"页内任务",共享上下文比拆 3 个 Context 简单。

**拒绝的备选:** 新建 `ScheduleContext` 与 `BookingContext` 平级(类膨胀,3 个模块只有 ~5 个字段,合并更清晰)。

**长期:** 如果未来模块数 > 3,应重构为 `Context` sealed 化或 `Context<T>` 泛型,见 ADR-0007 的 D 类提示。

### D5 · 错误码:3 个新值,沿用 5 字段元数据

在 `ErrorCode` 加 3 个常量:

```java
SCHEDULE_PAGE_LOAD_FAILED (Severity.HIGH,   true,  false, true,  "课表页加载失败"),
SCHEDULE_PARSE_FAILED     (Severity.MEDIUM, true,  false, true,  "课表解析失败"),
SCHEDULE_EMPTY            (Severity.LOW,    false, false, false, "课表为空(可能学期未开始)");
```

**不**在 `BookingException` 上重载 reason 文本(避免污染 `error/ErrorCode` 的元数据驱动模型)。

**截图策略:** `SCHEDULE_PAGE_LOAD_FAILED` / `SCHEDULE_PARSE_FAILED` 都 `shouldScreenshot=true`,便于 ehall 页面变更时 debug。

### D6 · 编排器:镜像 ChaoxingHomeworkClient 的 6 段结构

`EhallScheduleClient` 构造器签名、retry 策略、session 持久化接入、try-finally 关闭 browser、截图等 **完全镜像** `ChaoxingHomeworkClient`。代码大量复用,**不允许**抽 `AbstractClient` 父类(只有 2 个实现,提前抽象违反 YAGNI)。

**Pipeline 顺序:**
```
RestoreSessionStep (optional)
  → CasLoginStep(constructor URL = EHALL_SCHEDULE_URL)
  → NavigateToScheduleStep
  → ParseScheduleStep
  → PersistSessionStep (optional)
```

### D7 · CLI:抽 CommandOutput 工具类

`HomeworkListCommand.formatResult` 和 `exitCodeFor` 是通用 JSON envelope + exit code 映射,与 homework 业务无关。**在 PR-3 抽出**为 `edu.szu.agent.cli.CommandOutput`,两边复用。

**附带重构:** `HomeworkListCommand` 改用 `CommandOutput.formatResult(...)` 和 `CommandOutput.exitCodeFor(code)`,行为不变,通过 `HomeworkListCommandTest` 回归断言保证。

**理由:** DRY 原则 + 后续 P1 业务(调停课通知、公文通)会继续需要同一套 JSON envelope。

**拒绝的备选:**
- 不抽工具类,直接复制粘贴到 `ScheduleListCommand`(违反 DRY,3 个命令时必崩)
- 抽到 `domain/` 或 `mcp/`(职责错位,这是 CLI 层的输出格式)

### D8 · 时刻表常量:占位 + P1 校准

`PeriodMapping` 是一张 8 项静态 `Map<String, Period>`,起始时间 **先用经验值**(08:00 / 10:10 / 14:00 / 15:00 / 16:10 / 19:00 / 21:00 / 22:00),文档明确"P1 校准"。

**理由:** 课表页本身**不显示具体时间**,只显示节次(`1-2节` / `3-4节`)。时间值需要从学校教务处公开课表或学生手册获取。MVP 不阻塞 `schedule list` 核心功能(JSON 中可以暂时不输出 `startTime` / `endTime`,只输出 `beginUnit` / `endUnit`)。

**MVP 决策:** JSON 输出**包含** `startTime` / `endTime`(占位),并在 PR-4 trace 中记录"时间值未经校准,可能与实际 ±10 分钟"。

### D9 · 设计模式:复用既有 4 模式,不新增

`homework` 模块的设计模式标注已是项目内最全(Strategy / Adapter / Singleton / Builder 全覆盖)。课表模块 **不引入新模式**:

| 模式 | 课表模块落点 |
|---|---|
| Strategy | `ScheduleListExtractor` / `NavigateToScheduleStep` / `ParseScheduleStep` |
| Adapter | 复用 `BrowserLifecycle` / `PlaywrightBrowserAdapter` |
| Singleton | 复用 `ConfigManager` / `Tracer` / `Skills` |
| Builder | `WeekRangeParser` 内有链式转换(轻度),无独立 Builder 类 |

**理由:** 报告要求 4 模式每个有 2+ 处落地;课表模块不需要第 5 模式,只复用既有。

### D10 · 范围控制:不做的事情清单

明确**不在 MVP 范围**:
1. 学期切换 UI(MVP 默认本周)
2. 课程详情页抓取(只抓主页网格)
3. ICS 日历导出
4. 与 homework 模块的交叉联动(比如"作业 deadline 临近时建议预约时间")
5. 自定义时间表(沿用 SZU 标准节次)
6. 单双周过滤(用户传 `--week` flag,留 P1)
7. 课表变更通知(轮询 + 推送,不属于 CLI 范畴)

每个"不做"都对应一个 P1 扩展点,在 `Plan` 文档中标注。

---

## Consequences

### 好处

- **代码低风险**:完全镜像 homework,review 成本低(新文件 < 12 个)
- **复用率高**:`BrowserLifecycle` / `CasLoginStep` / `BookingContext` / `Account` / `ConfigManager` 全部复用,新增抽象 = 0
- **测试模板现成**:`HomeworkListExtractorTest` / `ChaoxingHomeworkClientTest` / `HomeworkListCommandTest` 直接参照
- **可独立 PR**:分 4 个 PR,每个 ~300-500 行,review 友好
- **演示重复性**:schedule 不与 booking 抢场地,演示日不冲突

### 代价 / 风险

- **时刻表数据未校准**:文档明确"占位常量",MVP JSON 中 `startTime` / `endTime` 可能与实际偏差 10 分钟
- **BookingContext 字段膨胀**:再加 1 个字段后,该类承载 4 个模块的数据,未来需重构
- **课表页 UI 变更风险**:ehall 升级改版(如改用 React 重写)后,JS 选择器失效,需重新分析页面
- **周次表达式变体**:实际可能遇到 "1-17(单)周" 等变体,`WeekRangeParser` 需用宽松正则
- **P1 业务延后**:学期切换 / 调停课标记过滤 / ICS 导出需后续 story

### 缓解措施

- 时刻表:文档明确标注 + PR-4 trace 记录
- BookingContext:超过 3 个模块后强制重构(`domain/Context<T>` 泛型化)
- UI 变更:在 `ErrorCode.SCHEDULE_PAGE_LOAD_FAILED` / `SCHEDULE_PARSE_FAILED` 触发 `screenshot=true`,自动留 debug 证据
- 周次变体:`WeekRangeParserTest` 用 `@ParameterizedTest` + `@CsvSource` 覆盖 5+ 变体
- P1 业务:在 `docs/HARNESS_BACKLOG.md` 登记扩展点

---

## 实施路径(4 个 PR,2-3 天)

```
PR-1 [0.5d]  Domain + Parser      Weekday/Period/WeekRange/CourseEntry/ScheduleListResult + WeekRangeParser + PeriodMapping + ErrorCode 3 个
PR-2 [0.5d]  Extractor + Steps    ScheduleListExtractor + NavigateToScheduleStep + ParseScheduleStep + BookingContext 加字段
PR-3 [1.0d]  Client + CLI         EhallScheduleClient + ScheduleListTask + ScheduleCommand + ScheduleListCommand + CommandOutput 抽取 + Main/ToolSchema 改动
PR-4 [0.5d]  联调 + Trace         真实账号跑通,写 harness-records/traces/20260618-XXXXXX-US-009.md
```

每个 PR 完成后:
- `mvn test` 必须绿
- `mvn verify` JaCoCo 整体 ≥ 80%
- 写一段 dev note(可选)
- 不能跨 PR 提 commit(避免一个 PR 失败阻塞其他)

---

## 后续 ADR 索引(预留)

- ADR-0010: 学期起始日推算 + 当前周过滤(P1)
- ADR-0011: 课表变更监听 + 推送(P1)
- ADR-0012: ICS 日历导出格式(P1)

---

## 引用

- **Plan:** `docs/plans/PLAN-schedule.md`
- **Page Analysis:** `docs/architecture/schedule/page-analysis.md`
- **历史参考 ADR:** ADR-0007 · 架构深度审视(BookingContext 膨胀相关讨论)
- **homework 模板:**
  - `docs/plans/PLAN-homework-and-session.md`
  - `docs/stories/US-006-chaoxing-homework-list.md`
- **Coding Style:** `.claude/rules/ecc/java/coding-style.md`

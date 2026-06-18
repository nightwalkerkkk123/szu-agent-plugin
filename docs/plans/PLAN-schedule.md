# Plan: 课表查询 (Schedule List)

**Created:** 2026-06-18
**Story:** US-009 (待创建)
**ADR:** [ADR-0009 · 课表模块架构设计](../adr/0009-schedule-module-design.md)
**Analysis:** [Page Analysis](../architecture/schedule/page-analysis.md)

---

## 背景

深大 ehall 课表页 (`/jwapp/sys/wdkb/*default/index.do#/xskcb`) 提供 8×8 周课表网格。需要把课表数据纳入现有 SZU Agent Plugin,新增 `schedule` 子命令,让 CLI / Skill / MCP 都能查询并结构化输出学生课表。

**业务复用:**
- `BrowserLifecycle` 浏览器抽象(已有)
- `CasLoginStep` CAS 登录(只需改起始 URL)
- `RestoreSessionStep` / `PersistSessionStep` US-007 持久化(已有)
- `Account` / `AccountResolver` / `ConfigManager` 凭证与配置(已有)

**不在范围(MVP):**
- 学期切换 UI、课程详情页抓取、ICS 日历导出
- 与 homework 模块的交叉联动
- 自定义时间表(沿用 SZU 标准节次时刻)

---

## 实施步骤(分 4 个 PR)

### PR-1 · Domain + Parser + Mapping

| 步骤 | 文件 | 说明 |
|---|---|---|
| 1 | `src/main/java/edu/szu/agent/domain/Weekday.java` | enum (SUN..SAT,值=DOM 编号 1-7) |
| 2 | `src/main/java/edu/szu/agent/domain/Period.java` | record(beginUnit, endUnit, startTime, endTime) |
| 3 | `src/main/java/edu/szu/agent/domain/WeekRange.java` | record(weeks, raw) + 静态 parse |
| 4 | `src/main/java/edu/szu/agent/domain/CourseEntry.java` | record(不可变值对象) |
| 5 | `src/main/java/edu/szu/agent/domain/ScheduleListResult.java` | sealed interface {Success, Failure} |
| 6 | `src/main/java/edu/szu/agent/client/schedule/WeekRangeParser.java` | "1-17周" / "1-8,10-17周" / 单双周 解析 |
| 7 | `src/main/java/edu/szu/agent/client/schedule/PeriodMapping.java` | 1-2节 → 08:00-09:50 静态常量表 |
| 8 | `src/main/java/edu/szu/agent/error/ErrorCode.java` | 新增 `SCHEDULE_PAGE_LOAD_FAILED` / `SCHEDULE_PARSE_FAILED` / `SCHEDULE_EMPTY` |
| 9 | 测试 | `WeekRangeParserTest` / `PeriodMappingTest` / `CourseEntryTest` / `WeekdayTest` |

**完成标志:** `mvn test` 绿,该子模块行覆盖 ≥ 80%。

### PR-2 · Extractor + Steps

| 步骤 | 文件 | 说明 |
|---|---|---|
| 1 | `src/main/java/edu/szu/agent/client/schedule/ScheduleListExtractor.java` | JS DOM → JSON → List<CourseEntry> |
| 2 | `src/main/java/edu/szu/agent/client/step/NavigateToScheduleStep.java` | 导航到 ehall 课表页,等 `table.wut_table` 可见 |
| 3 | `src/main/java/edu/szu/agent/client/step/ParseScheduleStep.java` | 调 extractor 写 `ctx.scheduleCourses` |
| 4 | `src/main/java/edu/szu/agent/client/step/BookingContext.java` | 新增 `scheduleCourses(List<CourseEntry>)` 字段 |
| 5 | 测试 | `ScheduleListExtractorTest` / `NavigateToScheduleStepTest` / `ParseScheduleStepTest` |

**完成标志:** 单元测试 mock `BrowserLifecycle.evaluate()` 返回预制 JSON,验证提取逻辑。

### PR-3 · Client + Task + CLI

| 步骤 | 文件 | 说明 |
|---|---|---|
| 1 | `src/main/java/edu/szu/agent/client/EhallScheduleClient.java` | 编排器(镜像 ChaoxingHomeworkClient) |
| 2 | `src/main/java/edu/szu/agent/task/ScheduleListTask.java` | `CampusTask<ScheduleListResult>` 实现 |
| 3 | `src/main/java/edu/szu/agent/cli/ScheduleCommand.java` | `schedule` 父命令 |
| 4 | `src/main/java/edu/szu/agent/cli/ScheduleListCommand.java` | `schedule list` 子命令 |
| 5 | `src/main/java/edu/szu/agent/cli/CommandOutput.java` | **抽取** `formatResult` / `exitCodeFor` 静态方法(从 `HomeworkListCommand` 提) |
| 6 | `src/main/java/edu/szu/agent/cli/HomeworkListCommand.java` | 改用 `CommandOutput` 工具类(行为不变) |
| 7 | `src/main/java/edu/szu/agent/mcp/ToolSchema.java` | `schemaFor()` switch 加 `case "schedule_list" -> scheduleListSchema()` |
| 8 | `src/main/java/edu/szu/agent/cli/Main.java` | 注册 `ScheduleCommand` + `schedule_list` Skill |
| 9 | 测试 | `EhallScheduleClientTest` / `ScheduleListTaskTest` / `ScheduleListCommandTest` / `CommandOutputTest` + `HomeworkListCommandTest` 回归 |

**完成标志:** `mvn test` 绿 + `mvn verify` JaCoCo 整体 ≥ 80%。

### PR-4 · 端到端联调 + Trace

| 步骤 | 文件 | 说明 |
|---|---|---|
| 1 | 真实账号跑通 `schedule list` | 验证页面提取、JSON envelope、Skill 列表 |
| 2 | `harness-records/traces/YYYYMMDD-HHMMSS-US-009.md` | 写 trace 记录真实抓取数据 |

**完成标志:** 真实数据进入 trace 文档,异常路径有截图。

---

## 依赖与风险

### 依赖

- ✅ `BrowserLifecycle` 抽象已稳定
- ✅ `CasLoginStep` 可复用(传不同 URL)
- ✅ `RestoreSessionStep` / `PersistSessionStep` US-007 已落地
- ✅ `ErrorCode` 元数据 5 字段模式已成熟
- ⚠️ `HomeworkListCommand` 的 `formatResult` / `exitCodeFor` 需要抽取为公共工具,涉及 1 个文件的非破坏性重构

### 风险

| 风险 | 缓解 |
|---|---|
| ehall URL 参数 (`t_s`, `_sec_version_`, `gid_`) 是否必要 | 先用基础 URL + `#/xskcb` hash 跑,失败再加 query |
| `PeriodMapping` 时刻表未校准 | 文档说明"占位常量,P1 校准";不影响 MVP |
| Extractor JS 脚本字符数 | 单脚本预计 < 2KB,远低于 evaluate 上限 |
| `BookingContext` 字段膨胀 | 只加 1 个 `scheduleCourses` 字段 |
| 现有 `homework_list` Skill 描述语言不一致 | 统一中文短描述,homework 不强求改 |

---

## 端到端验证

```bash
# 1. 全量测试 + 覆盖率
mvn -q test
mvn verify

# 2. CLI dry-run (不依赖真实账号)
java -jar target/szu-agent-plugin.jar schedule list --dry-run --format json

# 3. Skill 列表 (确认注册)
java -jar target/szu-agent-plugin.jar skill list --format json | jq '.skills[].name'

# 4. MCP 工具列表 (确认 schema 暴露)
java -jar target/szu-agent-plugin.jar mcp list --format json | jq '.tools[].name'

# 5. 真实账号
java -jar target/szu-agent-plugin.jar schedule list -u 2023150090 -e .env --format json

# 6. human format
java -jar target/szu-agent-plugin.jar schedule list -u 2023150090 -e .env --format human
```

---

## 引用

- **ADR-0009 · 课表模块架构设计:** `docs/adr/0009-schedule-module-design.md`
- **页面分析报告:** `docs/architecture/schedule/page-analysis.md`
- **Story (待创建):** `docs/stories/US-009-schedule-list.md`
- **参考模板:**
  - `docs/plans/PLAN-homework-and-session.md`
  - `docs/stories/US-006-chaoxing-homework-list.md`
  - `docs/adr/0001-project-direction-recalibration.md`

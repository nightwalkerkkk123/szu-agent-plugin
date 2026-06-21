# Story: US-009 课表查询

**Lane:** normal
**Created:** 2026-06-19
**Status:** in-progress (E2E pending correct credentials)
**Trace:** [20260619-013627-US-009](../harness-records/traces/20260619-013627-US-009.md)

---

## Overview

新增 `szu-agent schedule list` 子命令,登录深圳大学 ehall (`https://ehall.szu.edu.cn/jwapp/sys/wdkb/*default/index.do#/xskcb`) 后抓取周课表网格,以 JSON 形式返回全部课程条目。

## User Intent

用户需要一个"课表查询"功能,与"作业查询" / "场馆预约" 一起构成 SZU Agent 工具矩阵,让外部 AI Agent 能够查询"今天/这周有什么课",避免与场馆预约时间冲突。

## Acceptance Criteria

- [x] `java -jar szu-agent-plugin.jar schedule list --dry-run --format json` 输出合法 JSON envelope
- [x] JSON `data` 包含 `snapshotAt` / `count` / `courses` 字段
- [x] 每个 course entry 包含 `courseName` / `section` / `teacher` / `room` / `weekday` / `beginUnit` / `endUnit` / `weekRange` / `weeks` / `isAdjusted`
- [x] `skill list` / `mcp list` 包含 `schedule_list`
- [ ] `java -jar szu-agent-plugin.jar schedule list -u <id> -e .env --format json` 返回真实课表数据(E2E ⏸️)
- [x] `mvn test` 通过,JaCoCo 覆盖率 ≥ 80%
- [x] 登录失败/页面加载失败/列表为空均有明确错误码

## Design Patterns Used

- `// Design Pattern: Strategy` in `ScheduleListExtractor` / `NavigateToScheduleStep` / `ParseScheduleStep`
- `// Design Pattern: Adapter` in `PlaywrightBrowserAdapter` (reused)
- `// Design Pattern: Singleton` in `Skills` / `ConfigManager` / `Tracer` (reused)

## Programming Techniques

- `record` — `Weekday` / `Period` / `WeekRange` / `CourseEntry` / `ScheduleListResult`
- `sealed interface` — `ScheduleListResult` permits Success / Failure
- `enum` — `Weekday` with code mapping
- `正则表达式` — `WeekRangeParser` / `CourseEntry` section extraction
- `Lambda` — `BookingStep.of()` compatible

## Notes

- 周次时刻表 `PeriodMapping` 是占位常量(未校准),ADR-0009 D8 明确"P1 校准"
- 课程数据来自单一 HTML 页面抓取,无需额外 API
- `weekday` 编码:1=周一,7=周日(与 ehall `data-week` 属性一致)
- E2E 联调时发现 CAS 登录 `landed on ehall: false`,密码 `11282577` 疑似不正确,待用户确认

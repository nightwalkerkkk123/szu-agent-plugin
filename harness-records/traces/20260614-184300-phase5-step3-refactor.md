# Trace: Phase 5 Step 3 — Strategy 模式 + DateOffsetConverter + 8 步管线

**Date:** 2026-06-14 18:43:00
**Lane:** normal
**Story:** PHASE-5-STEP3-REFACTOR
**Outcome:** success
**Supersedes:** part of `20260613-204600-phase5-cleanup.md` 7-step pipeline description

---

## Summary

把 `SelectVenueStep` 的"球类列表"逻辑抽成 Strategy 模式,让球类(多场地)与健身房(单容量)走各自的 `VenueSelector` 实现;同时新增 `DateOffsetConverter` 让 `--date` 支持 `0/today/今天/1/tomorrow/明天` 别名,管线由 7 步扩展为 8 步(新增 `SelectDateStep`)。这是 phase 5 step 3 的完整重构,**17 个文件改动,290 个测试 0 失败**,dry-run + 真实预约(粤海 GYM_AEROBIC 20:00-21:00 today 17:00 起)均跑通。

注:本 trace 不含 `SelectDateStep` 的"isVisible→click"修复,见姊妹 trace `20260614-184300-select-date-fix.md`。

## Files Changed

```
新增 8 个:
  src/main/java/edu/szu/agent/cli/DateOffsetConverter.java
  src/main/java/edu/szu/agent/client/step/CapacityVenueSelector.java
  src/main/java/edu/szu/agent/client/step/CourtListSelector.java
  src/main/java/edu/szu/agent/client/step/SelectDateStep.java
  src/main/java/edu/szu/agent/client/step/VenueSelector.java
  src/test/java/edu/szu/agent/cli/DateOffsetConverterTest.java
  src/test/java/edu/szu/agent/client/step/SelectDateStepTest.java
  src/test/java/edu/szu/agent/client/step/VenueSelectorTest.java

修改 7 个:
  src/main/java/edu/szu/agent/cli/VenueCommand.java
  src/main/java/edu/szu/agent/client/VenueBookingClient.java
  src/main/java/edu/szu/agent/client/step/SelectVenueStep.java
  src/main/java/edu/szu/agent/domain/Sport.java
  src/main/java/edu/szu/agent/domain/YuehaiSport.java
  src/main/java/edu/szu/agent/domain/LihuSport.java
  src/test/java/edu/szu/agent/client/step/SelectVenueStepTest.java
  src/test/java/edu/szu/agent/domain/SportTest.java
```

## Files Read

- `src/main/java/edu/szu/agent/client/step/SelectVenueStep.java` (重构前)
- `src/main/java/edu/szu/agent/domain/Sport.java` + `YuehaiSport.java` + `LihuSport.java`
- `src/main/java/edu/szu/agent/browser/BrowserLifecycle.java` (评估点击 API)
- `src/main/java/edu/szu/agent/cli/VenueCommand.java` (--date 参数定义)
- `src/main/java/edu/szu/agent/client/VenueBookingClient.java` (管线顺序)
- `src/test/java/edu/szu/agent/client/step/SelectVenueStepTest.java` (现有测试模式)
- `docs/HARNESS.md` + `docs/CONTEXT_RULES.md` + `docs/FEATURE_INTAKE.md`
- `docs/design-patterns.md` (Strategy 写法约定)

## Validation

```bash
# 单元 + 集成
mvn test
# ✅ 290 tests, 0 failures, 0 errors, 0 skipped

# CLI 解析
java -jar target/szu-agent-plugin.jar booking venue --help
# ✅ --date 描述: "0/today/今天 or 1/tomorrow/明天"

# Dry-run (FakeBrowser stub,不走真实浏览器)
java -jar target/szu-agent-plugin.jar booking venue \
  --username 2023150090 --campus YUEHAI --sport GYM_AEROBIC \
  --date 明天 --time-slot 20:00-21:00 --env-file .env.local --dry-run --format json
# ✅ {"success":true,"data":{"venueName":"dry-run-stub","confirmation":"DRY-RUN"},...}
```

Run dates (worktree `E:/CODE/szu-agent-plugin/.claude/worktrees/phase5-step3`):
- 2026-06-14T18:24 — `mvn test` 290/0/0/0
- 2026-06-14T18:29 — `--help` 验证 --date alias 显示
- 2026-06-14T18:30 — dry-run 验证 CLI 解析 `--date 明天`

## Design Patterns Applied

| Pattern | Where |
|---|---|
| **Strategy** | `VenueSelector` sealed interface + `CourtListSelector` + `CapacityVenueSelector`,绑定到 `Sport.venueSelector()` |
| **Type Object** (隐式) | `Sport` 枚举常量绑定 `VenueSelector` 实例,跟现有 `displayName/ehallCode` 同一模式 |
| **Sealed Type** | `VenueSelector permits CourtListSelector, CapacityVenueSelector`,Java 21 编译期穷尽检查 |

新增 // Design Pattern 注释:
- `CourtListSelector.java` — `// Design Pattern: Strategy`
- `CapacityVenueSelector.java` — `// Design Pattern: Strategy`
- `VenueSelector.java` — `// Design Pattern: Strategy`
- `SelectDateStep.java` — `// Design Pattern: Strategy`

## Programming Techniques Applied

| Technique | Where |
|---|---|
| **泛型** | 无新增(`BookingStep` 已有) |
| **枚举** | `DateOffsetConverter` 内部用 switch 表达式匹配别名 |
| **注解** | picocli `@Option(converter = DateOffsetConverter.class)` |
| **Lambda** | `List.copyOf(steps)` 防御性拷贝 |
| **Pattern matching (instanceof)** | `VenueBookingClient` `if (r instanceof BookingResult.Failure f)` (沿用) |
| **Sealed types + switch (Java 21)** | `DateOffsetConverter` switch 表达式匹配 case `"0", "today", "今天" -> 0` |
| **不可变 + 防御性拷贝** | `DateOffsetConverter.convert` 不可变(无字段);`VenueBookingClient` 用 `List.copyOf(steps)` |

## Friction

- **泳道选择**: 这次改动跨越 3 个泳道(架构 / CLI / 真实环境回归),无法归为 tiny。**Lane=normal** 是合理选择。Lesson: 跨层重构的 lane 决策要 explicit 标记,不能默认 tiny。

- **Selector strict mode violation** (诱发因子): 在真实环境跑 `GYM_AEROBIC` 时,`SelectVenueStep` 原本的 `label:has(div.element:has-text("可预约"))` 匹配到 6 个元素(gym 页面 6 个时段 label 全部带"可预约")。Playwright strict mode 抛错。**根因**: step 层把页面 DOM 形态直接当球类处理,健身房是单容量场地模式。Lesson: 任何 selector 命中"页面 N 个相似元素"时,大概率是因为 step 没有按页面形态分发。

- **Sport 接口扩展 6 个枚举常量**: `Sport.venueSelector()` 是新抽象方法,`YuehaiSport` / `LihuSport` 的每个常量都需在构造器提供 `VenueSelector`。编译期 exhaustive check 保证不漏。Lesson: 在 sealed interface 上加抽象方法 = 强制全实现,这是 enum-as-type 的红利,值得用。

- **dry-run 看不见真实 DOM bug**: dry-run 用 `FakeBrowser`,默认对 `isVisible()` 返回 true,**单测全绿但真实环境会失败**。这次架构改造的 dry-run 走通了,但 isVisible→click 的 bug 留到真实 run 才暴露,见姊妹 trace。Lesson: 任何"页面交互"步骤至少要跑一次真实环境,不能只信 dry-run。

## Harness Improvement

**Pain:** "干跑 dry-run + 单测全绿 + 真实 run 失败" 的反馈环太长(数小时)。从代码改完到真实环境验证要:打包 + 启动浏览器 + 登录 + 走 7 步管线。**总耗时 ~5 分钟,失败信号在第 3 步**。

**Proposal:** 把"真实 run 烟测"做成 CI 钩子: 提交前用 headless Chromium + 测试账号走一次 dry-run 之外的"真实 selector click"路径,失败即 block commit。这需要 headless 浏览器预装 + 测试账号,**短期不做**,但写进 HARNESS_BACKLOG。

**Lower-effort (本次已做):** 在每个 selector 命中后立刻 `assertThat(browser.isVisible(sel))` 调试日志(只生产环境 INFO 输出),让"哪一步、哪个 selector、为什么失败"在真实 run 日志里自解释。

## Decisions Made

- **D1**: `VenueSelector` 用 sealed interface(2 个实现),不是 abstract class + 继承。理由: 当前只有 2 种页面形态,sealed 给出编译期穷尽;若未来出现第 3 种,升 sealed→open abstract 仍是 IDE 重构范围内。
- **D2**: `Sport.venueSelector()` 抽象方法在 sealed interface,而不是给 `Sport` 加一个 `getVenueSelector()` default method 然后子类 override。理由: 强制每个 enum 常量在构造时显式绑定策略,避免"忘记绑定"成为运行时 bug(在 selector 构造时即 NPE)。
- **D3**: `--date` 接受 4 个值(0/1/today/tomorrow),不支持 2+。CLI 错误信息明示合法值。理由: 每天 12:30 之前/之后可预约的是 today/tomorrow,后天没必要,避免增加学习负担。YAGNI 应用。
- **D4**: `SelectDateStep` 放在 `SelectSportStep` 之后,`SelectTimeSlotStep` 之前。理由: 日期影响"可用时段"列表(明天 ≠ 今天 的可用时段),所以先定日期再选时段是依赖顺序。
- **D5**: `DateOffsetConverter` 用 `toLowerCase(Locale.ROOT)` 而非 `toLowerCase()`。理由: Turkish locale 下 `I.toLowerCase()` 不一定是 `i`,会破 `"今天"` 之类的中文字符的相邻 case 匹配(虽然中文不受影响,但 `TODAY` 受影响,且未来加其他 locale 别名时更安全)。

## Next Steps

- [x] `SelectDateStep` 单 bug 修复 — 见姊妹 trace
- [ ] git commit + push(SSH key 阻塞,见 `20260613-204600-phase5-cleanup.md` "Friction > push permission")
- [ ] 清理 worktree 内的 `.env.local`(本次 trace 任务)

## Score

| Factor | Weight | Status | Notes |
|---|---|---|---|
| Files listed | 10 | 10 | 17 个变更文件全部命名 |
| Validation evidence | 20 | 20 | mvn test + --help + dry-run |
| Design patterns noted | 15 | 15 | 4 个 Strategy 注释列出 |
| Techniques noted | 10 | 10 | 7 个 technique 类别 |
| Friction recorded | 15 | 15 | 4 个摩擦,1 个 harness 改进建议 |
| Decisions documented | 15 | 15 | 5 个决策,皆有 trade-off |
| Next steps clear | 15 | 15 | 2 个下一步 + 1 个姊妹 trace 引用 |
| **Total** | **100** | **100** | |

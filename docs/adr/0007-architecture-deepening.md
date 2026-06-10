# ADR-0007 · 架构深度审视(improve-codebase-architecture)

**Date:** 2026-06-11
**Status:** Accepted
**Supersedes:** ADR-0001 D9 表格 5 模式;ADR-0006 retry 子决定中 4 个 RetryPolicy 实现;Q1 13 包中 observability/Tracer 接口描述
**Method:** 通过 `improve-codebase-architecture` skill 走读设计文档(无代码),用 [LANGUAGE.md] 词汇挑战深度

---

## Context

设计阶段末期(Phase 0 实施前),用 `improve-codebase-architecture` skill 走读已固化文档。
skill 关注"模块深度(depth)":接口小、背后行为多 = **深**;接口 ≈ 实现 = **浅**,pass-through。

`LANGUAGE.md` 关键铁律:
- **One adapter = hypothetical seam. Two adapters = real one.**
- **Deletion test**:删模块,复杂度归零 → 没挣到位置;reappear in N callers → 挣到了

走读发现 4 处"装饰/浅"风险,通过 grilling 全部确认要改。

---

## Decisions

### D1 删 BrowserFactory,改 ConfigManager 配置注入

**问题**:`BrowserFactory.create(Kind)` 3 行 switch,实现 ≈ 接口复杂度;seam 在错位置(调用方被迫"选择")

**解决**:
- `BrowserFactory` 类删除
- `application.yml` 增字段 `browser.kind: PLAYWRIGHT` (或 `FAKE`)
- `ConfigManager.getInstance().browser()` 返回 `BrowserLifecycle`,**调用方零决策**
- 测试改 yml 切换 `FAKE`,**调用代码不变**

**影响**:
- 5 模式表格从 5 个改 **4 个**:`Builder / Singleton / Strategy / Adapter`
- "Static Factory"作为独立模式**消失**,BrowserFactory 章节整段重写
- 报告里**主动交代**:原 5 模式 → 4 模式 + 配置注入,理由是 seam 深度

### D2 删 JitteredBackoff,RetryPolicy 3 实现

**问题**:`JitteredBackoff` 是 NoOp 占位,0 行为;按 deletion test 删它复杂度归零 → **没挣到位置**

**解决**:
- 删 `JitteredBackoff` 类
- `RetryPolicy` 剩 3 个实现:`FixedDelay` / `ExponentialBackoff` / `NoRetry`
- `RetryPolicies` 工厂剩 3 个静态方法:`defaultBooking()` / `login()` / `quickFix()` + 删 `jittered()`(若有)
- P1 真需要时(多 Agent 并发)加回,**15 行代码**

**影响**:
- ADR-0006 retry 子决定 4 实现 → 3 实现
- design-patterns.md §3 策略模式表改 3 行
- 报告交代:真业务驱动 3 个,装饰的 1 个删掉

### D3 BrowserLifecycle 6 方法保留,接受 Adapter 教科书浅

**问题**:6 方法(launch / navigate / click / type / screenshot / close)对 Playwright API 1:1 转发,接口 ≈ 实现;看似浅

**解决**:
- **不收缩**接口
- 接受 Adapter 模式的"教科书深度":**把 Playwright 链式 API 收成直接方法**
- caller 写 6 个调用就够,不学 Playwright 的 `page.locator(sel).click()` 链式

**影响**:
- 报告里**明确写**:Adapter 模式本意是"适配 API 表面",不是"藏业务逻辑";浅是 GoF 定义
- **答辩话术**:"Adapter 把 Playwright 的 `locator().click()` 收成 `browser.click(sel)`,业务层不接触 Playwright 链式 API,这就是 Adapter 模式的杠杆点"

### D4 Tracer 不接 Throwable,接收 ErrorCode + message + screenshotPath

**问题**:`Tracer.recordFailure(BookingException e)` 隐式 import error 包;observability ↔ error 之间 seam 没画清;Tracer 看到 stack trace 也不打印

**解决**:
- `Tracer.recordFailure(ErrorCode code, String message, Optional<Path> screenshotPath)`
- **Tracer 不 import `BookingException` / `Throwable`**
- 截图决策在 `BookingTask`(离现场最近):`if (e.code().shouldScreenshot()) ... else ...`
- Retry 层捕获 BookingException 决定是否重试,**不传给 Tracer**

**影响**:
- observability 包的依赖收缩(只 import domain + error/ErrorCode)
- 隐含接受:Tracer 知道 `BookingResult.Success` (domain 被动被记录),不知道 `BookingException`
- 这是 observability ↔ domain seam(domain 被动)vs observability ↔ error seam(避免)的关键区别

---

## Consequences

### 好处
- **5 模式表更诚实**:每个模式都"必须"有 2+ 处真业务落地;**装饰模式被清掉**
- **seam 深度提升**:ConfigManager 注入 vs BrowserFactory.create() — 前者调用方零学习,后者调用方必须学工厂
- **observability 包纯净**:Tracer 不依赖 error 包的复杂类,只依赖 ErrorCode enum(12 个值,稳定)
- **代码量减少**:
  - `BrowserFactory` 类 -1(-15 行)
  - `JitteredBackoff` 类 -1(-20 行)
  - `MetricsCollector` 类 -1(-30 行,如果之前存在;system-map.md 老描述里出现过,**实际从未在 design 里规划**)
- **报告话术更锋利**:"5 模式 → 4 模式 + 配置注入,Adapter 浅是 GoF 本意,Tracer 不接 Throwable 是关注点分离" — 每个决定都有答辩话术

### 代价 / 风险
- **老师 grep 不到 "Static Factory"**:可能在评分里"模式数量"维度扣分
  - **缓解**:报告里**主动交代**,把"5 → 4 + 理由"作为设计智慧展示
- **老师 grep 不到 `JitteredBackoff`**:可能问"为什么 retry 只有 3 个实现"
  - **缓解**:报告里写"NoOp 占位违反 YAGNI,P1 真需要时 15 行添加"
- **Tracer 接口方法签名改**:`recordFailure(BookingException)` → `recordFailure(ErrorCode, String, Optional<Path>)` 是 breaking change
  - **缓解**:Phase 0 还没编码,改的是**设计**,无业务影响
- **ConfigManager 引入新字段 `browser.kind`**:跟现有 config 加载链路耦合
  - **缓解**:ConfigManager 在 Phase 3 实施,Q3 早就设计过加载逻辑,新增字段是 1 行 yml + 1 个 getter

---

## 实施回改清单(Phase 0 启动前必做)

| 文件 | 改动 |
|---|---|
| `docs/adr/0001-project-direction-recalibration.md` | D9 表格 5 模式 → 4 模式;加段"BrowserFactory 删除理由" |
| `docs/adr/0006-phase1-domain-error-retry-matcher.md`(待建) | retry 子决定:4 实现 → 3 实现,删 JitteredBackoff |
| `docs/design-patterns.md` | §5 静态工厂模式章节整段重写;§3 retry 表格改 3 行;§5 末尾"5 模式落地"表改 4 行 |
| `docs/system-map.md` | retry 章节 4 实现 → 3 实现;observability 章节 Tracer 接口描述 |
| `README.md` | 5 模式表改 4 行;包结构删 BrowserFactory 提及 |
| `WORKING-CONTEXT.md` | 5 模式表同步;新增 ADR-0007 引用 |
| `docs/plans/README.md` | "待实现"表 retry 章节 4 → 3 |

---

## 引用

- [LANGUAGE.md] `/Users/wangzihao/.claude/skills/improve-codebase-architecture/LANGUAGE.md`
- [DEEPENING.md] `/Users/wangzihao/.claude/skills/improve-codebase-architecture/DEEPENING.md`
- ADR-0001 §D9(原 5 模式表格)
- ADR-0006 retry 子决定(原 4 个 RetryPolicy 实现)
- ADR-0005 D1(凭证流转,不与本 ADR 冲突)

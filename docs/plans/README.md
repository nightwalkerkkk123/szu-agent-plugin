# 功能规划

每个要实现的功能先在此写 plan，答辩时可作"先规划再实现"的证据。

> ⚠️ **ADR 校准声明**(2026-06-11):已按 **ADR-0001 / 0005 / 0006 / 0007** 同步。
> - `ClientFactory` → `BrowserFactory` → **删除**(ADR-0007 D1),改 `ConfigManager` 配置 `browser.kind` 注入
> - 5 模式 → **4 模式**(ADR-0007 D1):删 Static Factory
> - `CloakBrowserAdapter` → `PlaywrightBrowserAdapter`
> - `NoticeQueryClient` / `ChaoxingCourseClient` / `GrowthPlanClient` / `MCPToolProvider` 全部降为 P1(roadmap)
> - `ErrorClassifier` 已删除
> - 凭证流转:`--env-file` 参数(ADR-0005 D1),不走 cwd
> - `FixedDelayRetry` → `FixedDelay`,`TextMatcher` → `ExactMatcher`,新增 `VenueIndexMatcher`(业务专用)
> - `JitteredBackoff` NoOp 占位删除(ADR-0007 D2),`RetryPolicy` 4 实现 → **3 实现**(FixedDelay/ExponentialBackoff/NoRetry)
> - `Tracer` 不接 `Throwable`/`BookingException`(ADR-0007 D4),接 `ErrorCode + String + Optional<Path>`
> 详细理由见 `docs/adr/0001-project-direction-recalibration.md` / `0005-credential-and-logging-enforcement.md` / `0006-phase1-domain-error-retry-matcher.md` / `0007-architecture-deepening.md`。

## 已完成设计文档

| 功能 | 文档位置 |
|---|---|
| 域模型设计 | `docs/PRD.md` §6 数据模型 + `Q4.1` 设计对话(本会话) |
| 错误码设计 | `docs/system-map.md` §4 错误码枚举 + `Q4.2` |
| 重试策略设计 | `docs/design-patterns.md` §3 策略模式 + `Q4.3` |
| 匹配器策略设计 | `docs/design-patterns.md` §3 策略模式 + `Q4.4` |
| 浏览器适配器设计 | `docs/system-map.md` §1 模块拓扑 |
| 静态工厂设计 | `docs/design-patterns.md` §5 静态工厂模式(`BrowserFactory`) — **已删除**(ADR-0007 D1),改 `ConfigManager` 配置注入 |
| 凭证流转 / 日志强制 | `docs/adr/0005-credential-and-logging-enforcement.md` |

## 待实现功能（P0,按 5 天路径）

| 功能 | 详细设计 | 状态 |
|---|---|---|
| **Phase 0 骨架** | pom.xml + 13 个空包 + Logback JSON + dotenv-java 3.0 | 待开始 |
| **Phase 1 域模型** | `Campus` / `Sport` / `TimeSlot` / `BookingRequest.Builder` / `BookingResult` sealed(详见 `Q4.1`) | 待开始 |
| **Phase 1 错误层** | `ErrorCode`(12 值 5 元数据) + `Severity` + `BookingException` + `LogMasker`(详见 `Q4.2`) | 待开始 |
| **Phase 1 重试** | `RetryPolicy` + 3 实现(FixedDelay/ExponentialBackoff/NoRetry,详见 ADR-0007 D2) + `RetryPolicies` 工厂 | 待开始 |
| **Phase 1 匹配器** | `Matcher<T>` + `AbstractMatcher` + 4 实现 + `Matchers` 工厂(详见 `Q4.4`) | 待开始 |
| **Phase 2 浏览器** | `BrowserLifecycle` sealed(6 方法,见 ADR-0007 D3) + `PlaywrightBrowserAdapter` + `FakeBrowser`(**无 BrowserFactory**,改 ConfigManager 注入,ADR-0007 D1) | 待开始 |
| **Phase 3 业务编排** | `BookingTask` / `BookingClient` / `ConfigManager`(Singleton) / `Tracer`(Singleton) / `AccountResolver` | 待开始 |
| **Phase 4 CLI + Wrapper** | `BookingCommand` / `SkillCommand` / `McpCommand` + `CampusTask<T>` 扩展(ADR-0003/0004 待写) | 待开始 |
| **Phase 5 收尾** | 覆盖率补 80% + 课程报告(局限性分析) + 演示脚本 | 待开始 |

## P1 扩展（roadmap,代码层不实现）

| 功能 | 说明 | 状态 |
|---|---|---|
| NoticeQueryClient | 公文通查询 | roadmap |
| ChaoxingCourseClient | 畅课任务 | roadmap |
| GrowthPlanClient | 成长方案 | roadmap |
| MCPToolProvider | tools/list + tools/call (薄壳 wrapper) | roadmap |
| SkillManager / @AgentTool | 反射生成 Skill schema (薄壳 wrapper) | roadmap |
| CampusTask<T> / TaskExecutor | 通用任务框架(扩展点) | roadmap |

## Plan 格式

文件名: `YYYYMMDD-feature-name.md`

每个 plan 包含:概述 / 类变更表 / 设计模式 / 实现步骤 / 验收标准

---

## 实现原则

1. **先写设计文档，再写代码** — 每个功能先在 `docs/plans/` 下创建 plan
2. **显式标注设计模式** — `// Design Pattern: XXX` 在每个模式类第一行
3. **显式标注编程技术** — `// 编程技术: 泛型/枚举/注解/重载/抽象类/Lambda`
4. **测试驱动** — 核心逻辑先写测试，测试通过后再实现
5. **5 秒可验证** — 每个规则都能在 5 秒内判断代码是否符合
# Working Context

> Last updated: 2026-06-10
> 当前阶段: 文档设计阶段 (Phase 1) — 尚未开始编码实现

---

## Project Identity

- **Type**: 面向对象高级编程 — 个人大作业
- **Author**: 学号 2023150090 / 姓名 王子豪
- **Source**: `E:\CODE\szu-sports-booking\` (Python 参考)
- **ECC Rules**: `E:\CODE\ECC\rules\` (已装载)

---

## 当前状态

### 阶段：文档先行（Phase 1）

已完成：
- [x] `docs/PRD.md` — 产品需求文档
- [x] `docs/design-patterns.md` — 设计模式应用清单（5 种）
- [x] `docs/system-map.md` — 系统地图 + 局限性分析
- [x] `.claude/` — 规则 + agents + skills 基建
- [x] `SECURITY.md` — 项目安全策略（已更新）
- [x] `FILETREE.md` — 反映实际文件存在状态
- [x] `MCP.md` — 更新为待实现状态
- [x] `CONTRIBUTING.md` — 明确为评分指南
- [x] `CLAUDE.md` — 更新实现顺序和阶段说明

待创建：
- [ ] `pom.xml` — Maven 构建配置
- [ ] `design/2023150090_王子豪_大作业自拟题目.md` — 提案文档
- [ ] `docs/class-diagram.puml` — PlantUML 类图

待实现：
- [ ] Maven 骨架 + pom.xml
- [ ] 核心代码实现（按包顺序）
- [ ] 单元测试

---

## 技术栈

- Java 21 / Maven / picocli / Jackson / Logback / JUnit 5 + AssertJ
- CloakBrowser / Playwright Java（通过 `BrowserLifecycle` 适配器）

---

## 设计模式（5 种，报告必含）

| 模式 | 类 |
|---|---|
| 静态工厂 | `ClientFactory` |
| Builder | `BookingRequest.Builder` |
| 单例 | `ConfigManager`, `Tracer` |
| 策略 | `RetryPolicy`, `Matcher`, `ErrorClassifier` |
| 适配器 | `CloakBrowserAdapter` |

---

## 编程技术（≥5 种）

泛型 / 枚举 / 注解 / 重载 / 抽象类 / Lambda+Stream

---

## 实现顺序（待执行）

1. `domain/` — 值对象 records（Campus, Sport, TimeSlot, Venue, BookingRequest）
2. `error/` — ErrorCode 枚举 + BookingException
3. `retry/` — RetryPolicy 接口 + FixedDelayRetry / ExponentialBackoff
4. `account/` — AccountState 枚举 + Account + AccountManager
5. `browser/` — BrowserLifecycle 接口 + FakeBrowser + CloakBrowserAdapter
6. `matcher/` — Matcher 接口 + TextMatcher / RegexMatcher / ContainsMatcher / CompositeMatcher
7. `client/` — VenueBookingClient + ClientFactory（静态工厂）
8. `task/` — CampusTask<T> 接口 + TaskResult<T> + TaskExecutor
9. `config/` — ConfigManager（单例）
10. `observability/` — Tracer（单例）+ MetricsCollector
11. `platform/` — AgentToolPlatform（Facade）
12. `skill/` — Skill 接口 + @AgentTool 注解 + SkillManager
13. `mcp/` — MCPToolProvider
14. `cli/` — picocli 入口 + BookingCommand + NoticeCommand + JsonOutput

---

## 当前约束

- 不引入 Gradle（只用 Maven）
- 不访问真实系统（默认 `--dry-run` 模式）
- 敏感信息不写日志（LogMasker 集中脱敏）
- `mvn test` 必须通过才能结束实现

---

## 接口

- Claude Code session ← 交互主入口
- `mvn test` ← 测试验证
- `mvn package` ← 最终交付

---

## 关键文档说明

| 文档 | 内容 | 状态 |
|---|---|---|
| `CLAUDE.md` | 项目入口、编码规则、实现顺序 | ✅ 已更新 |
| `README.md` | 项目定位、架构概览、快速开始 | ✅ 已存在 |
| `FILETREE.md` | 实际文件地图 | ✅ 已更新 |
| `MCP.md` | MCP 工具导出契约 | ✅ 已更新为待实现 |
| `SECURITY.md` | 项目安全策略 | ✅ 已更新 |
| `SOUL.md` | 项目灵魂、核心原则 | ✅ 已存在 |
| `RULES.md` | 项目规则汇总 | ✅ 已存在 |
| `WORKING-CONTEXT.md` | 工作上下文 | ✅ 已更新 |
| `CONTRIBUTING.md` | 教师评分指南 | ✅ 已更新 |

---

## 下一步

1. 创建 `pom.xml` Maven 构建配置
2. 创建 `design/2023150090_王子豪_大作业自拟题目.md` 提案文档
3. 按实现顺序开始编码（从 `domain/` 开始）
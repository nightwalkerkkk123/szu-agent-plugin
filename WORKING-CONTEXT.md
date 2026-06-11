# Working Context

> Last updated: 2026-06-12
> 当前阶段: **Phase 2 完成,Phase 3 进行中**(ADR-0001/0002/0005/0006/0007 均 Accepted)

> ⚠️ **ADR 校准声明**(2026-06-11):本工作上下文已按 **ADR-0001 / 0005 / 0006** 重写。
> - 5 模式落点重选:`BrowserFactory` 替代 `ClientFactory`,`PlaywrightBrowserAdapter` 替代 `CloakBrowserAdapter`,`ErrorClassifier` 删除
> - 业务聚焦:`book` 唯一 P0 业务;`CampusTask<T>` 保留为 P1 扩展点
> - 演示模式:真跑 Playwright(ADR-0001 D2),`--dry-run` 仅作测试夹具
> - 凭证流转:Skill wrapper 传 `--env-file` 给 CLI(ADR-0005 D1),不走 cwd
> - 日志强制:`LogMasker` 走 archunit 静态规则(ADR-0005 D2)
> 详细理由见 `docs/adr/0001-project-direction-recalibration.md` / `0002-browser-lifecycle-and-playwright-adapter.md` / `0005-credential-and-logging-enforcement.md` / `0006-phase1-domain-error-retry-matcher.md` / `0007-architecture-deepening.md`。

---

## Project Identity

- **Type**: 面向对象高级编程 — 个人大作业
- **Author**: 学号 2023150090 / 姓名 王子豪
- **Source**: `E:\CODE\szu-sports-booking\` (Python 参考)
- **ECC Rules**: `E:\CODE\ECC\rules\` (已装载)

---

## 当前状态

### 阶段：Phase 2 完成，Phase 3 进行中（2026-06-12）

已完成：
- [x] `pom.xml` — Maven 构建配置（Phase 0）
- [x] Phase 0 骨架 + Phase 1 域模型/错误/重试/匹配器（38源码 + 20 测试）
- [x] Phase 2 浏览器抽象（BrowserLifecycle 10 方法 + PlaywrightBrowserAdapter）
- [x] `docs/adr/0001-project-direction-recalibration.md` — **方向校准 ADR (Accepted)**
- [x] `docs/adr/0002-browser-lifecycle-and-playwright-adapter.md` — BrowserLifecycle(Accepted)
- [x] `docs/adr/0005-credential-and-logging-enforcement.md` — 凭证流转 + archunit 强制 (Accepted)
- [x] `docs/adr/0006-phase1-domain-error-retry-matcher.md` — Phase 1 子决定(Accepted)
- [x] `docs/adr/0007-architecture-deepening.md` —架构深化(Accepted)
- [x] `docs/PRD.md` — 产品需求文档(已加 ADR 校准声明)
- [x] `docs/design-patterns.md` — 设计模式清单(4 种已重选)
- [x] `docs/system-map.md` — 系统地图 + 局限性分析(已同步)
- [x] `SECURITY.md` — LogMasker + ErrorCode + archunit 段(已同步)
- [x] `docs/HARNESS_BACKLOG.md` — OQ1-OQ4 已登记
- [x] `README.md` — 项目定位、架构、快速开始(已同步)
- [x] `FILETREE.md` — 文件地图(已加 docs/adr/)
- [x] `.claude/` — 规则 + agents + skills 基建

待创建：
- [ ] `design/2023150090_王子豪_大作业自拟题目.md` — 提案文档
- [ ] `docs/class-diagram.puml` — PlantUML 类图

进行中/待实现：
- [ ] Phase 3 业务编排（client/ + config/ + observability/ + account/）
- [ ] Phase 4 CLI + Wrapper（cli/ + skill/ + mcp/）
- [ ] Phase 5 测试80% + 报告 + 演示脚本

---

## 技术栈

- Java 21 / Maven / picocli / Jackson / Logback / JUnit 5 + AssertJ
- Playwright Java（通过 `PlaywrightBrowserAdapter` 适配）
- dotenv-java（凭证注入，ADR-0001 D6）

---

## 设计模式（4 种，按 ADR-0001 D9 + ADR-0007 D1 落点）

| 模式 | 类 |
|---|---|
| Builder | `BookingRequest.Builder` |
| 单例 | `ConfigManager`, `Tracer` |
| 策略 | `Matcher<T>` (4 实现), `RetryPolicy` (3 实现,见 ADR-0007 D2) |
| 适配器 | `PlaywrightBrowserAdapter`, `FakeBrowser` |

> ⚠️ `ErrorClassifier` 已删除(枚举自带元数据);`ClientFactory` 已删除(只 1 个 Skill 无业务价值);
> `BrowserFactory` 已删除(ADR-0007 D1),改 `ConfigManager` 配 `browser.kind` 注入;
> `JitteredBackoff` 已删除(ADR-0007 D2),`RetryPolicy` 4 实现 → 3 实现;
> `CloakBrowserAdapter` 已重命名;模板方法已删。

---

## 编程技术（≥5 种）

泛型 / 枚举 / 注解 / 重载 / 抽象类 / Lambda+Stream

---

## 实现顺序（按 ADR-0001 5 天路径）

| Phase | 时长 | 内容 | 状态 |
|---|---|---|---|
| 0 | 0.5d | 骨架：pom.xml + 包结构 + Logback + dotenv-java | ✅ 完成 |
| 1 | 1.0d | 无依赖基础：domain/ + error/ + retry/ + matcher/ | ✅ 完成 |
| 2 | 1.0d | 浏览器抽象：browser/ + BrowserLifecycle 10 方法 | ✅ 完成 |
| 3 | 1.0d | 业务编排：client/ + config/ + observability/ + account/ | 🔄 进行中 |
| 4 | 1.0d | CLI + Wrapper：cli/ + skill/ + mcp/ | 待开始 |
| 5 | 0.5d | 收尾：测试 80% + 报告 + 演示脚本 | 待开始 |

> ⚠️ 旧的 14 步顺序（含 `ClientFactory` / `AgentToolPlatform` / `ErrorClassifier` / `Notice*` / `Chaoxing*` / `Growth*`）已废弃。
> `task/` 整个包移到 P1。

---

## 当前约束

- 不引入 Gradle（只用 Maven）
- **真演示**：课堂演示走 Playwright 真跑，`--dry-run` 仅作单元测试夹具
- 凭证流转:Skill wrapper 传 `--env-file` 给 CLI(ADR-0005 D1),优先级:进程 env > `--env-file` 指向的 .env > Skill 注入
- 敏感信息不写日志(LogMasker 集中脱敏,12 个 Pattern,archunit 强制,ADR-0005 D2)
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
| `CLAUDE.md` | 项目入口、编码规则、5 天实施路径 | ✅ 已更新 |
| `docs/adr/0001-*.md` | 方向校准 ADR | ✅ Accepted |
| `docs/adr/0005-*.md` | 凭证流转 + archunit 强制 | ✅ Accepted |
| `docs/adr/0006-*.md` | Phase 1 子决定 | ⏳ Phase 1 收尾建 |
| `README.md` | 项目定位、架构概览、快速开始 | ✅ 已同步 ADR-0005/0006 |
| `FILETREE.md` | 实际文件地图 | ✅ 已加 docs/adr/ |
| `docs/PRD.md` | 产品需求(含 ADR 校准声明) | ✅ 已同步 |
| `docs/design-patterns.md` | 设计模式清单 | ✅ 5 模式 + 子决定已重选 |
| `docs/system-map.md` | 系统架构 + 局限性分析 | ✅ retry/matcher/account 已同步 |
| `docs/HARNESS_BACKLOG.md` | OQ1-OQ4 已登记 | ✅ |
| `SECURITY.md` | LogMasker + ErrorCode + archunit 段 | ✅ 已同步 |
| `MCP.md` | MCP 工具导出契约 | ✅ 已同步(dryRun 已移除) |
| `SOUL.md` | 项目灵魂、核心原则 | ✅ 已同步(4 模式) |
| `RULES.md` | 项目规则汇总 | ✅ 已同步(sealed → interface) |
| `CONTRIBUTING.md` | 教师评分指南 | ✅ 已同步(4 模式) |

---

## 下一步

1. Phase 3 业务编排（client/ + observability/ + account/）
2. Phase 4 CLI + Skill/MCP wrapper
3. 创建 `design/2023150090_王子豪_大作业自拟题目.md` 提案文档
4. 创建 `docs/class-diagram.puml` PlantUML 类图
5. Phase 5 测试 80% + 报告 + 演示脚本
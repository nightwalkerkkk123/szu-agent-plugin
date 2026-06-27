# Working Context

> Last updated: 2026-06-25
> 当前状态: **全部 Phase + US-006/007/008/009/010 完成,稳定迭代中**
> 当前 commit: `4f06045`(feat: external Skill loader + standalone MCP server;feat: architecture deepening + exam-list feature + review fixes)
> ADR Accepted: 7 份(0001/0002/0005/0006/0007/0008/0009)

> ⚠️ **ADR 校准声明**(2026-06-11 起多次校准):本工作上下文已按 **ADR-0001 / 0005 / 0006 / 0007 / 0008** 重写。
> - 5 模式落点重选:`BrowserFactory` 替代 `ClientFactory`,`PlaywrightBrowserAdapter` 替代 `CloakBrowserAdapter`,`ErrorClassifier` 删除
> - 业务聚焦:8 个 Skill 全部实装(`CampusTask<T>` 不再是 P1 扩展点)
> - 演示模式:真跑 Playwright(ADR-0001 D2),`--dry-run` 仅作测试夹具
> - 凭证流转:Skill wrapper 传 `--env-file` 给 CLI(ADR-0005 D1),不走 cwd
> - 日志强制:`LogMasker` 走 archunit 静态规则(ADR-0005 D2)
> - 会话复用:30 天 TTL + 探针(ADR-0008),`~/.szu-agent/sessions/<username>.json`
> - 2026-06 增量:`McpHttpServer` 常驻 daemon(4 端点);`ExternalSkillLoader`(扫描 `SZU_SKILL_PATH`);`JsonMappers` 集中 `ObjectMapper` 工厂
> 详细理由见 `docs/adr/0001-project-direction-recalibration.md` / `0002-browser-lifecycle-and-playwright-adapter.md` / `0005-credential-and-logging-enforcement.md` / `0006-phase1-domain-error-retry-matcher.md` / `0007-architecture-deepening.md` / `0008-session-persistence.md`。

> ✅ **Phase 5 收敛声明**(2026-06-13):经 ADR-0007 D1 审视 — 业务自然生长出的模式只有 4 种(Builder/Singleton/Strategy/Adapter),Pipeline 是 Strategy 的架构应用、Command 是 picocli 框架机制,**不计入主清单**。代码内已删除 `// Design Pattern: Command` 注释,proposal/proposal.md 数字与 `docs/grep-evidence.md` 完全一致(23 + 38 处)。

---

## Project Identity

- **Type**: 面向对象高级编程 — 个人大作业
- **Author**: 学号 2023150090 / 姓名 王子豪
- **Source**: `E:\CODE\szu-sports-booking\` (Python 参考)
- **ECC Rules**: `E:\CODE\ECC\rules\` (已装载)

---

## 当前状态

### 阶段:Phase 0-5 全部完成 + 8 业务 Skill 落库(2026-06-25)

**已固化交付**(本地 master `4f06045`,与 origin/master 拓扑 fast-forward 兼容):

| 项 | 状态 | 证据 |
|---|---|---|
| 编译 | ✅ `mvn compile` 零警告 | 多次 commit CI 通过 |
| 测试 | ✅ 503+ 通过 0 失败 | `mvn test` 在 4f06045 跑过 |
| 覆盖率 | ✅ ≥ 80% 行 | `target/site/jacoco/jacoco.csv` |
| 打包 | ✅ `target/szu-agent-plugin.jar` | `mvn -q -DskipTests package` |
| 业务 Skill | ✅ 8 个全部实装 | `skill list` / `mcp list` / `/tools` |
| 4 模式 grep 命中 | ✅ 24+ 文件 | `docs/grep-evidence.md` 静态守卫 |
| 6 技术 grep 命中 | ✅ 46+ 文件 | `docs/grep-evidence.md` 静态守卫 |
| 常驻 HTTP daemon | ✅ `McpHttpServer` | `scripts/serve.sh --background` |
| 外部 Skill loader | ✅ `ExternalSkillLoader` | `SZU_SKILL_PATH` 扫描 |
| 会话复用 | ✅ 30d TTL + 探针 | `~/.szu-agent/sessions/` |
| ADR 7 个 | ✅ 0001/0002/0005/0006/0007/0008/0009 Accepted | `docs/adr/` |
| Story 5 个 | ✅ US-006/007/008/009/010 | `docs/stories/` |
| 设计文档 | ✅ PRD + design-patterns + system-map + grep-evidence | `docs/` |

**最近重要 commit**(从 US-008 ~ 4f06045):

```
4f06045  feat: architecture deepening + exam-list feature + review fixes
705cf21  feat: external Skill loader + standalone MCP server
7db92be  feat: add calendar_get + notice_list MVP skills
bd334b9  fix: migrate merged step + client pipeline to StepOutcome sealed interface
...  (US-006/007/008/009 系列)
```

---

## 技术栈

- Java 21 / Maven / picocli / Jackson(`jackson-databind` + `jackson-datatype-jsr310` + `jackson-dataformat-yaml`) / Logback / JUnit 5 + AssertJ
- Playwright Java 1.45.0(通过 `PlaywrightBrowserAdapter` 适配)
- dotenv-java(凭证注入,ADR-0005 D1)
- JaCoCo 0.8.13(覆盖率 ≥ 80% 红线)
- ArchUnit(架构静态守卫,含 ADR-0005 D2 的 LogMasker 规则)
- Node.js 18+(可选,仅 `external/mcp-server/` 独立 MCP server 需要)

---

## 设计模式(4 种,ADR-0007 D1 收敛)

| 模式 | grep 命中 | 关键类 |
|---|---|---|
| Builder | 3 文件 | `domain.BookingRequest.Builder` / `domain.HomeworkDownloadRequest.Builder` / `knowledge.KnowledgeDocBuilder` |
| 单例 | 3 文件 | `config.ConfigManager` / `observability.Tracer` / `skill.Skills`(均双检锁 + volatile) |
| 策略 | 25+ 文件 | `BookingStep`(15+)+ `VenueSelector`(2)+ `RetryPolicy`(3)+ `MatchingStrategy`(3)+ `Sport` |
| 适配器 | 4 文件 | `browser.BrowserLifecycle`(目标)+ `PlaywrightBrowserAdapter`(适配)+ `client.BookingFlowLauncher`(seam)+ `mcp.McpHttpServer`(HTTP 适配 stdio dispatch) |

> ⚠️ `ErrorClassifier` 已删除(枚举自带元数据);`ClientFactory` 已删除(只 1 个 Skill 无业务价值);
> `BrowserFactory` 已删除(ADR-0007 D1),改 `ConfigManager` 配 `browser.kind` 注入;
> `JitteredBackoff` 已删除(ADR-0007 D2),`RetryPolicy` 4 实现 → 3 实现;
> `CloakBrowserAdapter` 已重命名;模板方法已删。
>
> **架构模式**:`VenueBookingClient.book()` 串联 15+ 个 `BookingStep` 是 Strategy 模式的架构应用,**不计入主清单**。
>
> **2026-06 增量**:
> - `Skill.of(CampusTask)` 静态工厂(避免 description 漂移)— Factory Method 在产品语义层落地,不计入主清单
> - `JsonMappers.standard()` 集中 ObjectMapper 工厂 — Factory Method 模式

---

## 编程技术(6 种)

| 技术 | grep 命中 | 关键示例 |
|---|---|---|
| 泛型 | 10+ 文件 | `RetryPolicy` / `BookingStep` / `Skill<T>` / `CampusTask<T>` / `Task<T>` / `MCPToolCallHandler` / `Skills` / `JsonMappers` / `HomeworkDownloadRequest.Builder` / `KnowledgeDocBuilder` |
| 枚举 | 22+ 文件 | CLI / client.step / domain / error / account / config / mcp / skill / task / observability / knowledge |
| 注解 | 4+ 文件 | picocli `@Command/@Option/@Spec/@Parameters` 在 CLI 类;`@JsonIgnoreProperties` 在 ExternalSkillManifest;`@Since` `@Author` |
| 重载 | 5+ 文件 | `AccountResolver.resolve` / `ConfigManager.load` / `ExponentialBackoff` / `FixedDelay` / `ExternalSkill(manifest, mapper)` |
| 抽象类 | 0 文件 | 已删除 `AbstractMatcher`,P0 使用接口 + default 方法;`BrowserLifecycle` 是接口 |
| Lambda+Stream | 20+ 文件 | CLI / client.step / config / error / mcp / retry / skill / task / knowledge |
| **合计** | **61+ 文件** | `docs/grep-evidence.md` 静态守卫(待重跑固化) |

> 注: `@FunctionalInterface` 出现在 step/retry/task/skill 接口,散落在 Strategy/CampusTask 接口命中文件中,未单独计入"注解"类。JUnit 注解在测试代码,未计入。
>
> **Phase 5 清理**: `matcher/` 包(7 main + 5 test 文件)已删除;`// Design Pattern: Type Object` 从枚举注释中移除。当前为 4 种设计模式 + 6 种编程技术。

---

## 实现顺序(按 ADR-0001 5 天路径 + 后续 P1)

| Phase | 时长 | 内容 | 状态 |
|---|---|---|---|
| 0 | 0.5d | 骨架:pom.xml + 包结构 + Logback + dotenv-java | ✅ 完成 |
| 1 | 1.0d | 无依赖基础:domain/ + error/ + retry/ + step/ | ✅ 完成 |
| 2 | 1.0d | 浏览器抽象:browser/ + BrowserLifecycle 10 方法 | ✅ 完成 |
| 3 | 1.0d | 业务编排:client/ + config/ + observability/ + account/ | ✅ 完成 |
| 4 | 1.0d | CLI + Wrapper:cli/ + skill/ + mcp/ | ✅ CLI 完成;skill/mcp 是 P1 薄壳 |
| 5 | 0.5d | 收尾:测试 80% + 报告 + 演示脚本 | ✅ 完成 |
| US-006 | 1.0d | homework_list(畅课作业查询) | ✅ 完成 |
| US-007 | 1.0d | 会话持久化(ADR-0008,30d TTL + 探针) | ✅ 完成 |
| US-008 | 1.5d | homework_download(附件下载) | ✅ 完成 |
| US-009 | 1.0d | schedule_list(课表,E2E pending) | ✅ 完成 |
| US-010 | 1.0d | knowledge base(kb_query) | ✅ 完成 |
| 增量 | 1.0d | calendar_get / notice_list / exam_list + 外部 Skill loader + HTTP daemon | ✅ 完成 |

---

## 当前约束

- 不引入 Gradle(只用 Maven)
- **真演示**:课堂演示走 Playwright 真跑,`--dry-run` 仅作单元测试夹具
- 凭证流转:Skill wrapper 传 `--env-file` 给 CLI(ADR-0005 D1),优先级:进程 env > `--env-file` 指向的 .env > Skill 注入
- 敏感信息不写日志(LogMasker 集中脱敏,archunit 强制,ADR-0005 D2)
- `mvn test` 必须通过才能结束实现
- 4 模式 + 6 技术 grep 命中数字由 `scripts/grep-runs.sh` 静态守卫,任何漂移 exit 1
- 常驻 HTTP daemon:用 `scripts/serve.sh --background`,凭证自动从 `.env` 加载
- 外部 Skill:把 `skill.yaml + run` 放到 `SZU_SKILL_PATH` 下的子目录,jar 启动时自动注册

---

## 接口

- Claude Code session ← 交互主入口
- `mvn test` ← 测试验证
- `mvn package` ← 最终交付
- `scripts/serve.sh --background` ← 启动常驻 HTTP daemon(默认 8765)
- `curl localhost:8765/health` / `/tools` / `/call` ← daemon 三个调试端点
- `external/skills/szu-campus/run` ← 仓库内置的 skill 包装(转发到 daemon)
- `bash scripts/grep-runs.sh` ← 设计模式/编程技术 grep 守卫(历史)

---

## 关键文档说明

| 文档 | 内容 | 状态 |
|---|---|---|
| `CLAUDE.md` | 项目入口、编码规则、可用 skill | ✅ 已更新 |
| `README.md` | 项目自述 + 8 工具 + 部署 | ✅ 已更新 |
| `SERVICE.md` | 常驻 HTTP daemon 部署 + 4 端点契约 | ✅ |
| `MCP.md` | MCP 协议(8 工具 + stdio/HTTP 双 transport) | ✅ 已更新 |
| `FILETREE.md` | 文件地图(93 main + 53 test) | ✅ 已更新 |
| `docs/adr/0001-*.md` | 方向校准 ADR | ✅ Accepted |
| `docs/adr/0002-*.md` | BrowserLifecycle (Accepted) | ✅ |
| `docs/adr/0005-*.md` | 凭证流转 + archunit 强制 | ✅ Accepted |
| `docs/adr/0006-*.md` | Phase 1 子决定 | ✅ Accepted |
| `docs/adr/0007-*.md` | 架构深化(4 模式收敛) | ✅ Accepted |
| `docs/adr/0008-*.md` | 会话持久化 | ✅ Accepted |
| `docs/adr/0009-*.md` | 课表模块 | ✅ Accepted |
| `docs/stories/US-006..010-*.md` | 5 个 story packet | ✅ |
| `docs/tools/booking-venue.md` | `booking_venue` 完整操作文档 | ✅ |
| `docs/grep-evidence.md` | 4 模式 + 6 技术 grep 证据表(待重新跑固化) | ⚠️ |
| `scripts/serve.sh` / `serve.bat` | 常驻 daemon 启动 | ✅ |
| `scripts/grep-runs.sh` | 数字漂移守卫(退出 1,历史) | ✅ |
| `scripts/demo.sh` | 课堂演示 4 步流程(历史) | ✅ |
| `design/2023150090_王子豪_大作业自拟题目.md` | 提案文档(231 行) | ✅ |
| `docs/PRD.md` | 产品需求(含 ADR 校准声明) | ✅ 已同步 |
| `docs/design-patterns.md` | 设计模式清单(4 种已重选) | ✅ 已更新 |
| `docs/system-map.md` | 系统地图 + 局限性分析 | ✅ 已更新 |
| `docs/HARNESS_BACKLOG.md` | OQ1-OQ4 + ID-002 已登记 | ✅ |
| `.claude/skills/szu-agent/SKILL.md` | Claude Code 内置 skill(自动探活 daemon) | ✅ |
| `external/mcp-server/README.md` | 独立 Node MCP server | ✅ |
| `external/skills/szu-campus/` | 仓库内置 szu_campus skill 包装 | ✅ |

---

## 下一步(候选)

1. **E2E 真实账号跑通** — US-009 trace 显示 CAS 登录 `landed on ehall: false`(密码 `11282577` 疑似不正确),需用户确认
2. **课堂报告 HTML** — `/article-to-html` skill 把 `design/.../大作业自拟题目.md` + `docs/grep-evidence.md` 转精美 HTML
3. **课堂问答准备** — 类图、设计模式、ADR 决策、复现命令
4. **重跑 grep-runs.sh** — 4f06045 增量后,数字可能漂移;`docs/grep-evidence.md` 需更新

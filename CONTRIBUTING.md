# 教师评分指南

> 本文件面向助教和教师，说明如何运行、测试和评估本项目。
> 文件名 CONTRIBUTING.md 为 GitHub 惯例，实际内容为评分指南。

> ⚠️ **ADR 校准声明**(2026-06-11):评分清单已按 **ADR-0001 / 0007** 同步。
> 5 模式 → 4 模式(`BrowserFactory` / Static Factory 删除改 `ConfigManager` 注入),
> 课堂演示命令改为**真跑** Playwright(不推荐 dry-run 演示)。
> 详细理由见 `docs/adr/0001-project-direction-recalibration.md`。

---

## 项目信息

| 字段 | 值 |
|---|---|
| **学号** | 2023150090 |
| **姓名** | 王子豪 |
| **课程** | 面向对象高级编程 |
| **类型** | 个人大作业 |
| **语言** | Java 21 |
| **构建工具** | Maven |

---

## 如何运行

### 构建

```bash
mvn package
```

### 单元测试

```bash
mvn test
```

### 核心演示（**真跑** Playwright,ADR-0001 D2）

```bash
java -jar target/szu-agent-plugin.jar booking venue \
  --username 2023150090 --campus 粤海 --sport 网球 \
  --date 0 --time-slot 19:00-20:00 --format json
```

预期输出：合法 JSON，`success: true`，退出码 0，**真实占位场地**。

> **重要**: `--dry-run` 仅作单元测试夹具,**不作为课堂演示模式**(ADR-0001 D4)。
> 真演示依赖凭证就绪 — `SZU_PASSWORD_XXXX` 环境变量 / cwd `.env` / skill 目录 `.env`
> (优先级见 ADR-0001 D6)。演示前手工 `cat .env` 验证。

### 列出所有 Skill/MCP 工具

```bash
java -jar target/szu-agent-plugin.jar skill list --format json
```

---

## 评分清单

### 编程技术（≥5 种，每种必须有实际代码体现）

| 技术 | 检查方式 |
|---|---|
| **泛型** | `grep -rn "<T>" src/` 应找到 `TaskResult<T>`、`CampusTask<T>` |
| **枚举** | `grep -rn "enum" src/` 应找到 `ErrorCode`、`TaskStatus`、`AccountState`，每个枚举值携带方法 |
| **注解** | `grep -rn "@interface" src/` 应找到 `@AgentTool` 自定义注解 |
| **重载** | `grep -rn "public.*(" src/` 应找到同名方法不同参数形式 |
| **抽象类** | `grep -rn "abstract class" src/` 当前为 0;项目使用接口 + default 方法替代 |
| **Lambda + Stream** | `grep -rn "\.stream\(\)" src/` 应找到列表过滤/映射/聚合 |

### 设计模式（4 种，每种必须在代码中显式可见）

在 `src/main/java/` 下搜索：

```bash
grep -rn "Design Pattern:" src/
```

预期找到至少 4 个标注（**按 ADR-0001 D9 + ADR-0007 D1 落点**）：

| 模式 | 类 |
|---|---|
| Builder | `BookingRequest.Builder` |
| 单例 | `ConfigManager`、`Tracer`、`Skills` |
| 策略 | `BookingStep`、`VenueSelector`、`RetryPolicy` |
| 适配器 | `BrowserLifecycle`、`PlaywrightBrowserAdapter`、`BookingFlowLauncher` |

> ADR-0007 D1:删 Static Factory / `BrowserFactory`,改 `ConfigManager` 配置注入,5 模式 → 4 模式

> **历史变更**(2026-06-11 ADR-0001 D9):原 `ClientFactory` / `ErrorClassifier` / `CloakBrowserAdapter` 已删除/重命名。

### 代码质量

- [ ] `mvn test` 全部通过
- [ ] `mvn package` 生成可执行 jar
- [ ] 所有公开方法有 Javadoc（含 `@author` 和 `@since`）
- [ ] 无 `System.out.println` 在生产代码中
- [ ] 敏感信息（密码）在日志中脱敏

### 报告质量

- [ ] `docs/design-patterns.md` 包含全部 4 种设计模式说明（**按 ADR-0001 D9 + ADR-0007 D1 落点**）
- [ ] `docs/system-map.md` 包含"局限性分析与改进建议"章节（≥6 种技术/模式分析）
- [ ] 报告分析有技术发展动态引用（注明来源）
- [ ] `docs/class-diagram.puml` PlantUML 类图完整（待创建）
- [ ] 报告必含"局限性分析"章节（含 CAS 验证码场景,见 ADR-0001 D7）

### 文档完整性

- [ ] `CLAUDE.md` 存在且包含项目说明
- [ ] `README.md` "快速开始"章节可实际执行
- [ ] `docs/PRD.md` 需求文档完整
- [ ] `docs/adr/0001-*.md` 方向校准 ADR 存在
- [ ] `design/2023150090_王子豪_大作业自拟题目.md` 自拟题目文档存在（待创建）

---

## 设计模式在代码中的位置

| 模式 | 文件 | 关键字 |
|---|---|---|
| Builder | `domain/BookingRequest.java` | `// Design Pattern: Builder` |
| 单例 | `config/ConfigManager.java` | `// Design Pattern: Singleton` |
| 策略 | `client/step/BookingStep.java` | `// Design Pattern: Strategy` |
| 适配器 | `browser/PlaywrightBrowserAdapter.java` | `// Design Pattern: Adapter` |

---

## 如何评分

| 分项 | 分值 |
|---|---|
| 编程技术（≥5 种） | 20 分 |
| 设计模式（≥5 种） | 20 分 |
| 代码可运行 | 20 分 |
| 设计模式在代码中显式可见 | 10 分 |
| 局限性分析与改进建议 | 20 分 |
| 报告质量与文档完整性 | 10 分 |
| **总分** | **100 分** |

---

## 当前实现状态

本项目 Phase 0 骨架和 Phase 1 核心域已完成（2026-06-12），Phase 2 浏览器抽象进行中。
38 个源码文件，20 个测试文件，`mvn test`全部通过。

设计模式已按 ADR 重选（4 种）：
- `docs/adr/0001-project-direction-recalibration.md` — 方向校准（Accepted）
- `docs/adr/0002-browser-lifecycle-and-playwright-adapter.md` — BrowserLifecycle 10 方法（Accepted）
- `docs/adr/0005-credential-and-logging-enforcement.md` — 凭证流转（Accepted）
- `docs/adr/0006-phase1-domain-error-retry-matcher.md` — Phase 1 子决定（Accepted）
- `docs/adr/0007-architecture-deepening.md` —架构深化（Accepted）
- `docs/PRD.md` — 产品需求（含 ADR 校准声明）
- `docs/design-patterns.md` — 4 模式落点（已同步）
- `docs/system-map.md` — 系统架构 + 局限性分析（已同步）

> ⚠️ `AgentToolPlatform` Facade / `ClientFactory` / `ErrorClassifier` / `NoticeQueryClient` / `ChaoxingCourseClient` / `GrowthPlanClient` / `CloakBrowserAdapter` 已删除/重命名，**评分 grep 不应再找到这些类名**。
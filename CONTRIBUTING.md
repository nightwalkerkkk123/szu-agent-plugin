# 教师评分指南

> 本文件面向助教和教师，说明如何运行、测试和评估本项目。
> 文件名 CONTRIBUTING.md 为 GitHub 惯例，实际内容为评分指南。

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

### 核心演示（dry-run 模式，不访问真实系统）

```bash
java -jar target/szu-agent-plugin.jar booking venue \
  --username 2023150090 --campus 粤海 --sport 网球 \
  --date 0 --time-slot 19:00-20:00 --dry-run --format json
```

预期输出：合法 JSON，`success: true`，退出码 0。

### 列出所有 Skill/MCP 工具

```bash
java -jar target/szu-agent-plugin.jar skill list --format json
```

---

## 评分清单

### 编程技术（≥5 种，每种必须有实际代码体现）

| 技术 | 检查方式 |
|---|---|
| **泛型** | `grep -rn "<T>" src/` 应找到 `TaskResult<T>`、`CampusTask<T>`、`Repository<T>` |
| **枚举** | `grep -rn "enum" src/` 应找到 `ErrorCode`、`TaskStatus`、`AccountState`，每个枚举值携带方法 |
| **注解** | `grep -rn "@interface" src/` 应找到 `@AgentTool` 自定义注解 |
| **重载** | `grep -rn "public.*(" src/` 应找到同名方法不同参数形式 |
| **抽象类** | `grep -rn "abstract class" src/` 应找到 `AbstractBrowser` |
| **Lambda + Stream** | `grep -rn "\.stream\(\)" src/` 应找到列表过滤/映射/聚合 |

### 设计模式（≥5 种，每种必须在代码中显式可见）

在 `src/main/java/` 下搜索：

```bash
grep -rn "Design Pattern:" src/
```

预期找到至少 5 个标注：

| 模式 | 类 |
|---|---|
| 静态工厂 | `ClientFactory` |
| Builder | `BookingRequest.Builder` |
| 单例 | `ConfigManager`、`Tracer` |
| 策略 | `RetryPolicy`、`Matcher`、`ErrorClassifier` |
| 适配器 | `CloakBrowserAdapter`、`FakeBrowser` |

### 代码质量

- [ ] `mvn test` 全部通过
- [ ] `mvn package` 生成可执行 jar
- [ ] 所有公开方法有 Javadoc（含 `@author` 和 `@since`）
- [ ] 无 `System.out.println` 在生产代码中
- [ ] 敏感信息（密码）在日志中脱敏

### 报告质量

- [ ] `docs/design-patterns.md` 包含全部使用的设计模式说明
- [ ] `docs/system-map.md` 包含"局限性分析与改进建议"章节（≥6 种技术/模式分析）
- [ ] 报告分析有技术发展动态引用（注明来源）
- [ ] `docs/class-diagram.puml` PlantUML 类图完整（待创建）

### 文档完整性

- [ ] `CLAUDE.md` 存在且包含项目说明
- [ ] `README.md` "快速开始"章节可实际执行
- [ ] `docs/PRD.md` 需求文档完整
- [ ] `design/2023150090_王子豪_大作业自拟题目.md` 自拟题目文档存在（待创建）

---

## 设计模式在代码中的位置

| 模式 | 文件 | 行号（grep 关键字） |
|---|---|---|
| 静态工厂 | `client/ClientFactory.java` | `// Design Pattern: Static Factory` |
| Builder | `domain/BookingRequest.java` | `// Design Pattern: Builder` |
| 单例 | `config/ConfigManager.java` | `// Design Pattern: Singleton` |
| 策略 | `retry/RetryPolicy.java` | `// Design Pattern: Strategy` |
| 适配器 | `browser/CloakBrowserAdapter.java` | `// Design Pattern: Adapter` |

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

本项目**尚处于文档设计阶段**，核心代码（`src/`）尚未实现。
所有设计模式、编程技术的实现计划已记录在文档中：
- `docs/PRD.md` — 产品需求
- `docs/design-patterns.md` — 设计模式清单
- `docs/system-map.md` — 系统架构 + 局限性分析
- `CLAUDE.md` — 实现顺序

实现完成后的目录结构：

```
src/main/java/edu/szu/agent/
├── platform/AgentToolPlatform.java
├── task/CampusTask.java, TaskResult.java, TaskStatus.java, TaskExecutor.java
├── domain/Campus.java, Sport.java, TimeSlot.java, Venue.java, BookingRequest.java
├── client/ClientFactory.java, VenueBookingClient.java, NoticeQueryClient.java
├── browser/BrowserLifecycle.java, CloakBrowserAdapter.java, FakeBrowser.java
├── config/ConfigManager.java
├── account/Account.java, AccountManager.java, AccountState.java
├── retry/RetryPolicy.java, FixedDelayRetry.java, ExponentialBackoff.java
├── error/ErrorCode.java, BookingException.java, ErrorClassifier.java
├── matcher/Matcher.java, TextMatcher.java, RegexMatcher.java, ...
├── observability/Tracer.java, MetricsCollector.java
├── skill/Skill.java, SkillManager.java, @AgentTool.java
├── mcp/MCPToolProvider.java
└── cli/Main.java, BookingCommand.java, NoticeCommand.java
```
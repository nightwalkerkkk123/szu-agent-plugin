# SZU Agent Plugin

> 面向深圳大学内部网的 AI Agent 工具与 CLI 插件
>
> 大作业自拟题目 — 学号 2023150090 · 姓名 王子豪

## 项目定位

本项目**不是**一个 AI Agent，而是一套**供 AI Agent 调用的工具集与 CLI 插件**。

深圳大学内部网存在大量重复、固定、流程化的操作（体育场馆预约、公文通查询、畅课任务、成长方案、邮件草稿等）。本项目将这些操作封装成**标准化的 CLI 工具**与**Skill / MCP 插件**，使 OpenClaw、ChatGPT Agent、其他 Agent 系统可以通过命令调用，由本地浏览器（Playwright）在用户授权下完成实际网页操作。

```
┌────────────────────┐    理解自然语言    ┌──────────────────┐
│   外部 Agent        │  ──────────────►  │  本项目 (CLI)     │
│ (OpenClaw / ChatGPT │                   │  - 子命令          │
│  Agent / 其他)      │  ◄──────────────  │  - JSON I/O       │
└────────────────────┘   返回结构化结果    │  - Skill / MCP    │
                                          └─────────┬────────┘
                                                    │ 内部调用
                                                    ▼
                                          ┌──────────────────┐
                                          │ BrowserLifecycle │  ← 接口
                                          │     (接口)        │
                                          └─────────┬────────┘
                                                    │ 实现
                                      ┌─────────────┴─────────────┐
                                      ▼                           ▼
                          ┌──────────────────┐        ┌──────────────────┐
                          │ PlaywrightBrowser│        │   FakeBrowser    │
                          │     Adapter      │        │   (dry-run)      │
                          └──────────────────┘        └──────────────────┘
```

## 项目状态

**当前阶段：文档设计阶段（Phase 1）**
- 核心代码（`src/`）尚未实现
- 设计文档已完成：`docs/PRD.md`、`docs/design-patterns.md`、`docs/system-map.md`
- 计划按实现顺序逐步完成编码

## 范围

| 阶段 | 范围 | 状态 |
|---|---|---|
| **P0 核心** | 体育场馆定时预约、通用任务框架、CloakBrowser 适配器、CLI 入口、配置/日志/Trace/错误码、有限重试、Skill/MCP 最小原型 | 设计完成，实现待开始 |
| **P1 扩展接口** | 公文通查询、畅课任务、成长方案、邮件草稿、企业微信摘要 | 设计接口 + 部分示例 |
| **P2 未来** | 校园小巴、电费、报修、Dashboard、任务队列、Docker | 不做 |

## 快速开始（待实现后可用）

```bash
# 编译
mvn package

# 体育场馆预约（核心 demo）
java -jar target/szu-agent-plugin.jar booking venue \
  --username 2023150090 --campus 粤海 --sport 网球 \
  --date 0 --time-slot 19:00-20:00

# 查询公文通（扩展 demo）
java -jar target/szu-agent-plugin.jar notice list --keyword 讲座 --limit 10

# 输出为 JSON，供 Agent 解析
java -jar target/szu-agent-plugin.jar booking venue ... --format json
```

Agent 端只需 `exec` 本 CLI 并解析 stdout JSON / 退出码。详细契约见 [`docs/PRD.md`](docs/PRD.md) §5。

## 架构概览

```
edu.szu.agent
├── platform/      AgentToolPlatform 平台门面（Facade）
├── task/          CampusTask<T> 通用任务接口 + TaskResult<T> + TaskStatus
├── client/        VenueBookingClient / NoticeQueryClient / ChaoxingCourseClient / ...
├── browser/       BrowserLifecycle 接口 + CloakBrowserAdapter + FakeBrowser
├── config/        ConfigManager（单例）
├── account/       Account + AccountManager + AccountState
├── retry/         RetryPolicy（策略）+ FixedDelay / ExponentialBackoff
├── error/         ErrorCode 枚举 + ErrorClassifier（策略）+ BookingException
├── observability/ Tracer / MetricsCollector（单例）
├── skill/         Skill 接口 + SkillManager（@AgentTool 反射）
├── mcp/           MCPToolProvider（暴露工具 schema）
├── cli/           CLI 入口（picocli）+ JSON 序列化
└── domain/        Campus / Sport / TimeSlot / Venue / BookingRequest
```

## 设计模式（5 种）

| 模式 | 落点 | 作用 |
|---|---|---|
| **静态工厂** | `ClientFactory.create(CliName)` | 按名称创建不同的校园服务客户端 |
| **Builder** | `BookingRequest.Builder` | 拼装多参数预约请求（账号/校区/项目/日期/时间段/重试） |
| **单例** | `ConfigManager` / `Tracer` | 全局唯一，保证配置/追踪上下文一致 |
| **策略** | `RetryPolicy` / `ErrorClassifier` / `Matcher` | 行为族可替换，新增策略不改调用方 |
| **适配器** | `CloakBrowserAdapter` 适配 `BrowserLifecycle` | 把第三方浏览器库封装为统一接口，业务不感知 Playwright |

完整说明: [`docs/design-patterns.md`](docs/design-patterns.md)。

## 编程技术（6 种）

| 技术 | 体现 |
|---|---|
| **泛型** | `TaskResult<T>` / `CampusTask<T>` |
| **枚举** | `TaskStatus` / `AccountState` / `ErrorCode`（每个枚举值携带元数据） |
| **注解** | `@AgentTool` 标记可暴露给 Agent 的方法，运行期反射生成 Skill / MCP schema |
| **重载** | `BookingRequest.Builder.addAccount(String)` / `.addAccount(Account)` 等多形式构造 |
| **抽象类** | `AbstractBrowser` 模板方法，子类实现 `doLaunch` / `doClose` |
| **Lambda + Stream** | 账号过滤、结果聚合、观察者通知 |

## 局限性分析与改进

完整章节: [`docs/system-map.md` §6 局限性分析与改进建议](docs/system-map.md#6-局限性分析与改进建议)。

要点：
- 浏览器自动化对页面结构变化敏感 → 后续可引入基于视觉的页面元素识别
- 依赖本地浏览器环境 → Docker 化 + 健康检查
- 无任务确认机制 → 高风险操作前要求用户二次确认
- 缺乏细粒度权限模型 → Skill 级别 ACL + 审计日志
- 错误处理策略硬编码 → 改为可插拔的错误处理流水线

## Harness 工作流

> 本项目使用 Harness 工作流（参考 [repository-harness](https://github.com/hoangnb24/repository-harness) 范式）。
> 每次任务经过 intake → implementation → trace 的闭环。

### Feature Intake（每个任务前必做）

1. **Classify** — 使用 [`docs/FEATURE_INTAKE.md`](docs/FEATURE_INTAKE.md) 判断输入类型和车道
   - **Tiny**: 低风险文档/命名/窄编辑
   - **Normal**: 故事级行为，有界爆炸半径
   - **High-Risk**: 影响安全/数据/多域
2. **Find affected docs** — 识别需要阅读的产品文档
3. **Run risk checklist** — 检查 Auth/Authorization/Data model/Audit 硬门

### Context Rules（按阶段读文档）

| Phase | Must read |
|---|---|
| Intake | `CLAUDE.md`, `docs/FEATURE_INTAKE.md` |
| Planning | `docs/system-map.md`, `docs/design-patterns.md` |
| Implementation | 变更的文件 + 相邻同模式文件 |
| Validation | `docs/PRD.md` 验收标准, `mvn test` |
| Trace | `docs/TRACE_SPEC.md`, `git status --short` |

详见 [`docs/CONTEXT_RULES.md`](docs/CONTEXT_RULES.md)。

### Record Trace（任务后必做）

Normal/High-risk 任务完成后记录 trace 到 `harness-records/traces/`：
- 列出变更的文件和阅读的文件
- 记录验证结果（`mvn test` 输出）
- 记录设计模式和编程技术
- 记录摩擦（如有）

详见 [`docs/TRACE_SPEC.md`](docs/TRACE_SPEC.md)。

### Harness Growth（遇到摩擦时）

当遇到摩擦、混淆或需要新验证命令时：
- 直接修复（如果简单）
- 或记录到 [`docs/HARNESS_BACKLOG.md`](docs/HARNESS_BACKLOG.md)

## 目录结构

```
szu-agent-plugin/
├── CLAUDE.md                项目入口配置（含 Harness 上下文）
├── README.md                本文件
├── FILETREE.md              文件地图
├── MCP.md                   MCP 协议接口文档
├── SECURITY.md              安全策略
├── SOUL.md                  项目灵魂
├── RULES.md                 项目规则汇总
├── WORKING-CONTEXT.md       工作上下文
├── CONTRIBUTING.md          教师评分指南
├── docs/
│   ├── PRD.md               产品需求文档
│   ├── design-patterns.md   设计模式应用清单
│   ├── system-map.md        系统地图 + 局限性分析
│   ├── FEATURE_INTAKE.md    Feature intake 三车道分类
│   ├── CONTEXT_RULES.md     上下文阶段规则
│   ├── TRACE_SPEC.md        Trace 记录规范
│   ├── HARNESS_BACKLOG.md   Harness 改进 backlog
│   ├── plans/               功能规划说明
│   ├── templates/           Story packet 模板
│   ├── stories/             Story packets（待填充）
│   └── decisions/           Decision records（待填充）
├── harness-records/         持久化记录（类比 repository-harness SQLite）
│   ├── traces/              每次任务的 trace 记录
│   └── friction/            摩擦记录
├── design/                  提案文档（待创建）
├── configs/                 业务配置模板（待创建）
├── pom.xml                  Maven 构建配置（待创建）
├── src/                     Java 源码（待实现）
└── .claude/                 Claude Code subagent 基建

## 依赖（待配置）

- Java 21
- Maven 3.9+
- picocli（CLI 框架）
- Jackson（YAML / JSON）
- Logback
- JUnit 5 + AssertJ（测试）
- Playwright Java（在 `CloakBrowserAdapter` 适配）

## 致谢

本项目受现有 `szu-sports-booking`（Python）启发，但**全部代码独立设计、Java 重写**。原项目提供业务模型与流程参考，不直接复用。
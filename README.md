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

**当前阶段：Phase 2 完成，Phase 3 进行中（ADR-0001/0002/0005/0006/0007 Accepted）**

项目已完成 Phase 0骨架（pom.xml + 13 packages + Logback）和 Phase 1 核心域（domain/ + error/ + retry/ + step/），Phase 2 浏览器抽象（BrowserLifecycle 10 方法 + PlaywrightBrowserAdapter）已完成。

**实施路径**（5 天）：

| Phase | 时长 | 内容 | 状态 |
|---|---|---|---|
| P0 | 0.5d | 骨架（pom.xml + 包结构 + Logback + dotenv-java） | ✅ 完成 |
| P1 | 1.0d | 无依赖基础（domain/ + error/ + retry/ + step/） | ✅ 完成 |
| P2 | 1.0d | 浏览器抽象（browser/ + BrowserLifecycle 10 方法） | ✅ 完成 |
| P3 | 1.0d | 业务编排（client/ + config/ + observability/ + account/） | 🔄 进行中 |
| P4 | 1.0d | CLI + Wrapper（cli/ + skill/ + mcp/） | 待开始 |
| P5 | 0.5d | 收尾（测试 80% + 报告 + 演示脚本） | 待开始 |

每阶段过 `mvn test` 才进下一阶段。

## 范围

| 阶段 | 范围 | 状态 |
|---|---|---|
| **P0 核心** | 体育场馆定时预约（`BookingClient` 唯一业务）、`PlaywrightBrowserAdapter` 适配器、CLI 入口、配置/日志/Trace/错误码、有界重试（≤3，见 ADR-0006 retry 子决定） | 设计完成，方向已校准 |
| **P1 扩展接口** | `CampusTask<T>` 扩展点、公文通查询、畅课任务、成长方案、邮件草稿、企业微信摘要 | roadmap，不实现 |
| **P2 未来** | 校园小巴、电费、报修、Dashboard、任务队列、Docker | 不做 |

## 快速开始

```bash
# 编译
mvn package

# 体育场馆预约（核心 demo，**真跑** Playwright,ADR-0001 D2）
java -jar target/szu-agent-plugin.jar booking venue \
  --username 2023150090 --campus YUEHAI --sport TENNIS \
  --date 2026-06-12 --time-slot 19:00-20:00

# 输出为 JSON,供 Agent 解析
java -jar target/szu-agent-plugin.jar booking venue ... --format json

# 演示从 Skill 目录调(Skill wrapper 传 --env-file,见 ADR-0005 D1)
java -jar /opt/szu-agent-plugin.jar booking venue \
  --env-file /opt/skills/szu-sports/.env \
  --username 2023150090 --campus YUEHAI --sport TENNIS \
  --date 2026-06-12 --time-slot 19:00-20:00
```

Agent 端只需 `exec` 本 CLI 并解析 stdout JSON / 退出码。
**注意**:`--dry-run` 标志仅作单元测试夹具用(ADR-0001 D4),课堂演示必须真跑。
凭证通过 `SZU_PASSWORD_XXXX` 进程环境变量 / `--env-file` 指向的 .env / Skill wrapper 注入(优先级见 ADR-0005 D1)。
详细契约见 [`docs/PRD.md`](docs/PRD.md) §5。

## 作为常驻服务运行 — 从零到第一次调用(跨机器完整流程)

除按需 fork CLI 外,本项目可跑成一个**常驻 HTTP 服务**:一个热 JVM 同时给
Skill(`curl`)、MCP 宿主(Claude Code / Desktop)、自带技能三个调用面提供能力,
调用毫秒级、无重复冷启动。下面是在一台**全新机器**上从零跑通的完整顺序,
照抄即可,**无需修改任何路径**。

### 阶段 0 — 前置条件

| 用途 | 要求 |
|---|---|
| 运行服务 | Java **21+** runtime |
| 构建 / 测试 | Java **21**(务必;见阶段 2 说明)+ Maven 3.9+ |
| Skill 调用 | `curl`(macOS / Linux / Win10+ 自带) |

```bash
git clone <repo-url> szu-agent-plugin && cd szu-agent-plugin
```

### 阶段 1 — 定位 Java 21

构建必须用 Java 21,更高版本(如 26)会让 Mockito 插桩失败、`mvn test` 报错。
先把 `JAVA_HOME` 钉到 21,**按安装来源选对应一行**:

macOS / Linux(bash):

```bash
# macOS · Homebrew 装的 openjdk@21(java_home 找不到它,用 opt 路径):
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
# macOS · 系统安装器装的 JDK:export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
# Linux:export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
"$JAVA_HOME/bin/java" -version    # 必须显示 21.x
export PATH="$JAVA_HOME/bin:$PATH"
```

Windows(cmd;把路径换成你本机 JDK 21 的实际位置,如 `E:\tools\jdk-21`):

```bat
set "JAVA_HOME=E:\tools\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
java -version    REM 必须显示 21.x
```

### 阶段 2 — 构建 fat jar

```bash
mvn -q -DskipTests package         # 首次会下载依赖;产出 target/szu-agent-plugin.jar
# 可选:跑测试自证健康(604 测试全绿,同样需 Java 21)
mvn test
```

### 阶段 3 — 启动常驻服务(初始化)

服务进程在启动时一次性注册全部 8 个工具到内存,之后常驻、热处理每次调用:

```bash
# macOS / Linux:
scripts/serve.sh --background      # 后台启动,默认端口 8765,PID 写入 logs/serve.pid
#   前台运行(Ctrl-C 停):scripts/serve.sh
#   换端口:scripts/serve.sh --background --port 9000(同时改下方 .mcp.json 的 URL)
```

```bat
REM Windows(cmd):
scripts\serve.bat --new-window     REM 独立窗口启动(相当于后台),关窗即停
REM   前台运行(Ctrl-C 停):scripts\serve.bat
REM   换端口:scripts\serve.bat --new-window --port 9000(同时改下方 .mcp.json 的 URL)
```

### 阶段 4 — 健康检查 + 确认工具就绪

```bash
curl localhost:8765/health         # {"status":"ok"} —— 服务活着
curl -s localhost:8765/tools | grep -o '"name":"[^"]*"'   # 列出 8 个工具名
```

看到 `calendar_get` / `kb_query` / `schedule_list` / `notice_list` / `exam_list` /
`homework_list` / `homework_download` / `booking_venue` 即初始化完成。

### 阶段 5 — 三个调用面接入

**A. curl 直调(最底层,验证后端)** — 请求体即 `{name, arguments}`:

```bash
curl -s localhost:8765/call -H 'Content-Type: application/json' \
  -d '{"name":"calendar_get","arguments":{}}'        # 返回 {success,data,...,traceId}
```

**B. 自带 Skill 包装** — `external/skills/szu-campus/run` 从 stdin 读 `{name,arguments}`
转发给服务,daemon 地址由 `SZU_AGENT_URL` 配置(默认 8765):

```bash
echo '{"name":"kb_query","arguments":{"query":"图书馆","limit":3}}' \
  | external/skills/szu-campus/run
```

**C. MCP 接入 Claude Code** — 项目根 `.mcp.json` 已注册(纯 URL,无绝对路径):

```jsonc
{ "mcpServers": { "szu-agent": { "type": "http", "url": "http://localhost:8765/mcp" } } }
```

1. 在本项目目录启动 `claude`;
2. 首次会提示**批准**项目级 MCP server,批准 `szu-agent`(安全机制);
3. `/mcp` 应显示 `szu-agent` 已连接、含 8 个工具;
4. 之后直接说人话即可,无需记命令——见下方"自带技能"。

**最省心 — 自带 Claude Code 技能**:仓库内置 `.claude/skills/szu-agent/` 随仓库分发。
在本项目目录用 Claude Code 时,说"查深大校历"、"看我的课表"、"szu-agent 起了没"
之类的话,Claude 会自动触发技能:先探活(必要时启动 daemon)、再按 schema 调用。

### 阶段 6 — 停止服务

```bash
scripts/serve.sh --stop            # macOS / Linux:停后台服务(读 logs/serve.pid)
```

Windows:前台模式按 `Ctrl-C`;`--new-window` 模式关闭那个标题为 `SZU Agent :端口` 的窗口即可。

### 故障速查

| 现象 | 处理 |
|---|---|
| `/health` 不通 | daemon 没起 → `scripts/serve.sh --background` |
| `mvn` 报 `Mockito cannot mock` | 用了高版本 JDK → 固定 `JAVA_HOME` 指向 Java 21(阶段 1) |
| `INVALID_REQUEST: Missing required parameter` | 缺必填参数(多为 `username`),见 [`SERVICE.md`](SERVICE.md) 工具表 |
| MCP 工具调用返回空 | 已修复(`tools/call` 现返回 MCP `content`);若仍空,重启 daemon 用最新 jar |
| `/mcp` 连不上但 `/call` 能通 | daemon 正常,问题在 MCP 注册:核对 `.mcp.json` 端口 = daemon 端口,并重启 `claude` 重新批准 |

> 端点契约、工具凭证需求详见 [`SERVICE.md`](SERVICE.md);
> 技能定义见 [`.claude/skills/szu-agent/SKILL.md`](.claude/skills/szu-agent/SKILL.md)。

## 架构概览

```
edu.szu.agent
├── Main.java                          # picocli 入口
├── cli/           Main / BookingCommand(第一性工作单元,ADR-0001 D1)
├── domain/        Campus / Sport / TimeSlot / BookingRequest(Builder) / BookingResult
├── browser/       BrowserLifecycle(10 方法,见 ADR-0002 D1) + PlaywrightBrowserAdapter
│   # BrowserFactory 已删除(ADR-0007 D1),改 ConfigManager 配 browser.kind 注入
├── client/        VenueBookingClient(P0 唯一业务) + BookingFlowLauncher(seam) + step/
│   └── step/      BookingStep(Strategy,7 实现) + VenueSelector(Strategy,2 实现)
├── retry/         RetryPolicy(Strategy,3 实现:FixedDelay/ExponentialBackoff/NoRetry,见 ADR-0007 D2) + RetryPolicies
├── error/         ErrorCode(枚举,12 值 5 元数据) + Severity + BookingException + LogMasker
├── config/        ConfigManager(单例,Singleton)
├── observability/ Tracer(单例,Singleton) + RunRecord(JSON 落盘)
├── account/       Account + AccountResolver(3 层凭证,ADR-0005 D1) + EnvVarName
├── task/          CampusTask<T>(P1 扩展点,代码层保留)
├── skill/         Skill + SkillManager(P1 薄壳 wrapper)
└── mcp/           MCPToolProvider(P1 薄壳 wrapper,ADR-0001 D5)
```

> **历史变更**(ADR-0001 D9, 2026-06-11):删除 `platform/AgentToolPlatform` Facade、
> `client/ClientFactory`、`error/ErrorClassifier`、`client/NoticeQueryClient` /
> `ChaoxingCourseClient` / `GrowthPlanClient`。
> `CloakBrowserAdapter` 改名为 `PlaywrightBrowserAdapter`。
> `CampusTask<T>` 保留为 P1 扩展点(不算第 6 个模式)。

## 设计模式（4 种，ADR-0001 D9 + ADR-0007 D1）

| 模式 | 落点 | 作用 |
|---|---|---|
| **Builder** | `BookingRequest.Builder` | 拼装 6 参数预约请求（校区/项目/日期/时段/场地号/备注） |
| **单例** | `ConfigManager` / `Tracer` | 全局唯一，保证配置/追踪上下文一致 |
| **策略** | `BookingStep` (7 实现) / `VenueSelector` (2 实现) / `RetryPolicy` (3 实现) | 步骤/选择器/重试策略可替换，新增策略不改调用方 |
| **适配器** | `PlaywrightBrowserAdapter` 适配 `BrowserLifecycle` | 把 Playwright 封装为统一接口，业务不感知具体浏览器 |

> **5 → 4 模式变更**(ADR-0007 D1):`BrowserFactory` / Static Factory 删除,改 `ConfigManager` 配置 `browser.kind` 注入;seam 深度提升,调用方零决策。详见 [docs/design-patterns.md](docs/design-patterns.md) §5。

完整说明: [`docs/design-patterns.md`](docs/design-patterns.md)。

## 编程技术（6 种）

| 技术 | 体现 |
|---|---|
| **泛型** | `CampusTask<T>` / `Skill<T>` / `RetryPolicy.execute(Supplier<T>)` |
| **枚举** | `ErrorCode` / `Campus` / `Sport` / `TimeSlot`（每个枚举值携带元数据） |
| **注解** | picocli `@Command` / `@Option` / `@Spec` / `@Parameters` |
| **重载** | `AccountResolver.resolve(String)` / `resolve(String, Map)` 等多形式构造 |
| **抽象类** | P0 已删除 `AbstractMatcher`;使用接口 + default 方法替代 |
| **Lambda + Stream** | 默认方法组合、Stream 过滤、函数式接口实现 |

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
├── pom.xml                  Maven 构建配置
├── dependency-reduced-pom.xml
├── .env.example             环境变量模板
├── logs/                    运行时日志
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
│   └── adr/                 Architecture Decision Records
│       ├── 0001-project-direction-recalibration.md
│       ├── 0002-browser-lifecycle-and-playwright-adapter.md
│       ├── 0005-credential-and-logging-enforcement.md
│       ├── 0006-phase1-domain-error-retry-matcher.md
│       └── 0007-architecture-deepening.md
├── harness-records/         持久化记录（类比 repository-harness SQLite）
│   ├── traces/              每次任务的 trace 记录
│   └── friction/            摩擦记录
├── src/
│   ├── main/java/edu/szu/agent/   (38 source files, 13 packages)
│   ├── main/resources/
│   └── test/java/edu/szu/agent/   (20 test files)
└── .claude/                 Claude Code subagent 基建

## 依赖

- Java 21
- Maven 3.9+
- picocli（CLI 框架）
- Jackson（YAML / JSON）
- Logback
- JUnit 5 + AssertJ（测试）
- Playwright Java（在 `PlaywrightBrowserAdapter` 适配）
- dotenv-java（凭证 .env 注入，ADR-0001 D6）

## 致谢

本项目受现有 `szu-sports-booking`（Python）启发，但**全部代码独立设计、Java 重写**。原项目提供业务模型与流程参考，不直接复用。
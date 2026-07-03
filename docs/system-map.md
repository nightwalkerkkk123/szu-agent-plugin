# 系统地图

> 本文档描述系统整体架构、模块拓扑、核心时序、状态机与关键设计决策。
> 集成"局限性分析与改进建议"章节(课程报告必含)。

> ⚠️ **ADR 校准声明**:本文档的模块拓扑与设计模式归属已按 **ADR-0001** (2026-06-11) D9/D10 重选。
> - 删除 `platform/AgentToolPlatform` Facade、`client/ClientFactory`、`error/ErrorClassifier`
> - 删除 `client/NoticeQueryClient` / `ChaoxingCourseClient` / `GrowthPlanClient`(P1 扩展点保留,代码层只 `book`)
> - `browser/CloakBrowserAdapter` → **`PlaywrightBrowserAdapter`**
> - 5 模式重选后,本文件 §6 局限性章节对应同步
>
> 详细理由见 `docs/adr/0001-project-direction-recalibration.md`。

---

## 1. 模块拓扑

```
edu.szu.agent
│
├── task/                          # 8 个业务实现,经 CampusTask<T> 注册
│   ├── CampusTask<T>              # 任务抽象接口
│   ├── TaskInput                  # 字符串契约 + require()/getInt() 辅助
│   ├── TaskInputSchema            # 工厂:requiredSingle / schemaWithOptional / optionalOnly
│   ├── BookingTask                # booking_venue(真跑 Playwright)
│   ├── HomeworkTask               # homework_list(畅课 + 会话复用)
│   ├── HomeworkDownloadTask       # homework_download(批量下载 + 30d TTL)
│   ├── ScheduleListTask           # schedule_list(默认真实抓取 + 静态回退,见 ResilientScheduleClient)
│   ├── CalendarTask               # calendar_get(2025-2026 校历)
│   ├── NoticeTask                 # notice_list(分类 + daysBack)
│   ├── ExamListTask               # exam_list(status 过滤)
│   └── KnowledgeTask              # kb_query(本地 KB + 3 匹配策略)
│
├── domain/                         # 不可变 record
│   ├── Campus / Sport / TimeSlot / BookingRequest(Builder) / BookingResult
│   ├── Homework / HomeworkAttachment / HomeworkDownloadRequest(Builder) / HomeworkListResult
│   ├── CourseEntry / Period / Weekday / WeekRange / LihuSport / YuehaiSport
│   ├── calendar/ AcademicEvent / AcademicEventType
│   ├── exam/     ExamSchedule
│   └── notice/   Notice / NoticeCategory
│
├── client/                         # 校园服务客户端
│   ├── VenueBookingClient          # 体育场馆预约(P0 核心实现)
│   ├── BookingFlowLauncher         # // Design Pattern: Adapter(seam,per-user session)
│   ├── ChaoxingHomeworkClient      # 畅课作业列表查询(US-006)
│   ├── ChaoxingAttachmentDownloadClient # 畅课附件下载(US-008)
│   ├── EhallScheduleClient         # 课表抓取(US-009,带 session)
│   ├── session/                    # US-007 登录态持久化(ADR-0008)
│   │   ├── SessionStore            # 路径解析 + POSIX 600 + TTL 30d + username 白名单
│   │   ├── SessionProbe            # // Design Pattern: Strategy(navigate + isVisible 探针)
│   │   └── SessionResult           # sealed Fresh() / Stale(String reason)
│   ├── step/                       # BookingStep 管线(// Design Pattern: Strategy,15+ 实现)
│   │   ├── BookingStep / BookingContext / StepOutcome(sealed) / VenueSelector(Strategy,2 实现)
│   │   ├── CacheLookupStep / CacheWriteStep / CachePipelineBuilder
│   │   ├── CasLoginStep / NavigateToBookingStep
│   │   ├── SelectCampusStep / SelectSportStep / SelectDateStep / SelectTimeSlotStep / SelectVenueStep
│   │   ├── CapacityVenueSelector / CourtListSelector / ConfirmBookingStep
│   │   ├── RestoreSessionStep / PersistSessionStep(US-007)
│   │   ├── NavigateToHomeworkStep / NavigateToHomeworkDetailStep
│   │   ├── NavigateToScheduleStep / ParseHomeworkListStep / ParseAttachmentsStep
│   │   ├── ParseScheduleStep / DownloadFilesStep
│   ├── cache/                      # CacheEnvelope / CacheKey / CacheStore(分层缓存)
│   ├── exam/      ExamListClient / ExamListParser
│   │         ExamFetchProvider (Strategy) / PlaywrightExamFetchProvider / ResilientExamListClient (Decorator+Strategy)
│   ├── notice/    NoticeListClient / NoticeListParser
│   │         NoticeFetchProvider (Strategy) / PlaywrightNoticeFetchProvider / ResilientNoticeListClient (Decorator+Strategy)
│   ├── schedule/  ScheduleListClient / Extractor / PeriodMapping / WeekRangeParser
│   │         ScheduleFetchProvider (Strategy) / PlaywrightScheduleFetchProvider / ResilientScheduleListClient (Decorator+Strategy)
│   ├── calendar/  CalendarFetchProvider (Strategy) / ResilientCalendarClient (Decorator+Strategy) / CalendarPageParser / PlaywrightCalendarFetchProvider
│   └── homework/  HomeworkListExtractor / AttachmentListExtractor + attachment/FilenameSanitizer
│
├── browser/                        # 浏览器抽象层(Adapter + ConfigManager 注入)
│   ├── BrowserLifecycle            # Design Pattern: Adapter 目标接口(12 方法,见 ADR-0002 D1 + ADR-0008)
│   │ # open / close / navigateTo / click / fill /
│   │ # isVisible / textOf / allTextOf / currentUrl / screenshot /
│   │ # importStorageState / exportStorageState(US-007)
│   ├── PlaywrightBrowserAdapter    # Design Pattern: Adapter,真演示唯一入口
│   │   # open() 用 chromium().connectOverCDP(wsUrl) 连 Obscura daemon
│   │   # (Obscura 二进制由 ObscuraLauncher 拉起,见 ADR-0010)
│   ├── ObscuraLauncher             # Design Pattern: Process Supervisor(GoF Singleton + Lifecycle seam)
│   │   # ensureRunning() 探测 → 抽取 ~/.szu-agent/bin/obscura{,.exe} → 启进程 → 等待 ready
│   │   # shutdown hook 在 JVM exit 时 destroy() daemon
│   └── FakeBrowser                 # 单元测试夹具,不出现在课堂演示
│   # BrowserFactory 已删除(ADR-0007 D1),改 ConfigManager.browser() 注入
│
├── config/
│   └── ConfigManager               # Design Pattern: Singleton
│
├── account/                        # 凭证层(ADR-0005 D1)
│   ├── Account                     # record(学号 + 密码 + 显示名)
│   ├── AccountResolver             # 三层凭证查找(进程 env → --env-file → Skill 注入)
│   └── AccountResolutionException  # 三层查找失败时抛
│
├── retry/                          # Design Pattern: Strategy(FunctionalInterface + orElse default)
│   ├── RetryPolicy                 # 策略接口(@FunctionalInterface)
│   ├── FixedDelay / ExponentialBackoff / NoRetry(Singleton INSTANCE)
│   └── RetryPolicies               # 工厂(defaultBooking/login/quickFix)
│
├── error/
│   ├── ErrorCode                   # 错误码枚举(20+ 值,自带元数据:severity/retryable/switchAccount/screenshot/hint)
│   ├── Severity                    # LOW/MEDIUM/HIGH/CRITICAL
│   ├── BookingException            # 统一异常(extends RuntimeException)
│   └── LogMasker                   # 静态脱敏工具(见 ADR-0005 D2)
│
├── observability/
│   ├── Tracer                      # Design Pattern: Singleton(trace_id 管理 + 步骤记录)
│   │   # recordFailure(ErrorCode, String, Optional<Path>),不接 Throwable(ADR-0007 D4)
│   └── RunRecord                   # run 结束落盘 JSON
│
├── knowledge/                      # 本地 KB(2026-06 新增)
│   ├── KnowledgeRepository         # Deep Module:加载 + 校验 + 索引 + 检索
│   ├── KnowledgeCategory / KnowledgeResult / KnowledgeDoc(Builder)
│   └── MatchingStrategy            # // Design Pattern: Strategy(3 实现)
│       ├── ContainsMatchingStrategy
│       ├── ExactMatchingStrategy
│       └── RegexMatchingStrategy
│
├── json/                           # 集中 ObjectMapper 工厂(2026-06 新增)
│   └── JsonMappers                 # // Design Pattern: Factory Method
│                                   # 统一 JavaTimeModule + 关 WRITE_DATES_AS_TIMESTAMPS
│
├── skill/                          # Skill 注册中心
│   ├── Skill<T>                    # record(name, description, CampusTask<T>)
│   ├── Skills                      # Design Pattern: Singleton 注册中心
│   │   + Skill.of(CampusTask) 静态工厂(2026-06 加,description 与 task 同源)
│   └── external/                   # 外部 Skill 加载器(2026-06 新增)
│       ├── ExternalSkillLoader     # 扫描 SZU_SKILL_PATH 加载独立 Skill
│       ├── ExternalSkill           # 实现 CampusTask<Map<String,Object>>,调用 entry 脚本
│       └── ExternalSkillManifest   # record(skill.yaml 解析结果)
│
├── mcp/                            # MCP 协议层(2026-06 升级:stdio + HTTP 双 transport)
│   ├── McpStdioServer              # JSON-RPC 2.0 over stdio,handle() 单方法可复用
│   ├── McpHttpServer               # 常驻 HTTP daemon,4 端点(/health /tools /call /mcp)
│   │                               # // Design Pattern: Adapter(HTTP 适配 stdio dispatch)
│   ├── MCPToolCallHandler          # tools/call 核心
│   └── ToolSchema                  # tools/list(SCHEMA_VERSION="1.2",委托 task.inputSchema())
│
└── cli/                            # 第一性工作单元(ADR-0001 D1)
    ├── Main                        # picocli 入口 + registerDefaultSkills()(8 内部 + N 外部)
    ├── BookingCommand / VenueCommand      # booking 子命令
    ├── HomeworkCommand / HomeworkListCommand / HomeworkDownloadCommand
    ├── ScheduleCommand / ScheduleListCommand
    ├── CalendarCommand / NoticeCommand / ExamCommand / KnowledgeCommand
    ├── SkillCommand                       # skill list / call
    ├── MCPCommand                         # mcp list / call / serve(--http 可选)
    └── CommandOutput / DateOffsetConverter
```

### 包依赖关系

```
(无 Facade 入口;CLI 直接路由,ADR-0001 D9 删 AgentToolPlatform;ADR-0007 D1 删 BrowserFactory)
cli.Main
  ├──► cli.BookingCommand / HomeworkCommand / ScheduleCommand / CalendarCommand
  │     / NoticeCommand / ExamCommand / KnowledgeCommand
  ├──► cli.SkillCommand     → mcp.MCPToolCallHandler → skill.Skills
  ├──► cli.MCPCommand       → mcp.McpStdioServer / McpHttpServer
  │                            + mcp.MCPToolCallHandler
  │                            + mcp.ToolSchema
  └──► cli 各 Command → task.*Task (CampusTask<T>)
                          ├──► domain.*(值对象,record)
                          ├──► account.AccountResolver (3 层凭证,ADR-0005 D1)
                          ├──► config.ConfigManager.browser() (Singleton,按 browser.kind 配置注入)
                          │     └──► browser.PlaywrightBrowserAdapter
                          │           └──► browser.BrowserLifecycle
                          ├──► client.*Client (业务编排)
                          │     ├──► client.step.BookingStep (Strategy,15+ 实现)
                          │     ├──► client.session.SessionStore + SessionProbe (US-007)
                          │     └──► retry.RetryPolicy (Strategy,3 实现)
                          ├──► config.ConfigManager (Singleton)
                          ├──► observability.Tracer (Singleton)
                          ├──► knowledge.KnowledgeRepository (kb_query 走这里)
                          ├──► json.JsonMappers (统一 ObjectMapper)
                          └──► domain / error.ErrorCode

mcp.McpHttpServer (HTTP daemon,2026-06 新增)
  ├──► 复用 mcp.McpStdioServer.handle(String) 做 JSON-RPC 分发
  │     // Design Pattern: Adapter
  ├──► mcp.MCPToolCallHandler.call(name, arguments) 做 /call 端点
  └──► mcp.ToolSchema.toolsList(skills) 做 /tools 端点

skill.Skills (单例注册中心)
  ├── 8 内部 Skill: registerDefaultSkills() 注册
  └── N 外部 Skill: skill.external.ExternalSkillLoader 扫描 SZU_SKILL_PATH
```

---

## 2. 预约流程时序图

```
Agent 调用 CLI                              系统内部
    │                                          │
    ▼                                          │
java -jar ... booking venue                   │
    │                                          │
    ▼                                          │
ConfigManager.getInstance().load()            │ Singleton
    │                                          │
ConfigManager.getInstance().browser()       │ Singleton + 配置文件
    │     ▼                                    │
    │   PlaywrightBrowserAdapter               │ Adapter
    │     ▼                                    │
    │   BrowserLifecycle.launch()             │
    │                                          │
login(username, pwd)                          │
    │     ▼                                    │
    │   BrowserLifecycle.navigate(url)         │
    │   BrowserLifecycle.click(sel)            │
    │   BrowserLifecycle.type(sel,text)        │
    │                                          │
selectCampus(campus)                          │
selectSport(sport)                            │
selectTimeSlot(slot)                          │
selectVenue()                                 │
    │     ▼                                    │
    │   VenueSelector.selectAndClick(...)      │ Strategy
    │                                          │
confirm()                                     │
    │     ▼                                    │
    │   RetryPolicy.execute()                  │ Strategy
    │   (有界重试 ≤3 次,详见 ADR-0006 retry 子决定) │
    │                                          │
BrowserLifecycle.close()                      │
    │                                          │
Tracer.generateTraceId()                       │ Singleton
    │                                          │
return TaskResult (JSON)                       │
    ▼                                          │
Agent 解析结果                                 │
```

---

## 3. 账号状态机

```
                    失败重试超限
                    ┌──────────────┐
                    ▼              │
         AVAILABLE ──► COOLDOWN ────► LOCKED
              ▲                        │
              │    冷却时间结束         │
              └────────────────────────┘

状态转移规则:
- AVAILABLE → COOLDOWN: 连续失败次数 ≥ maxFailures
- COOLDOWN → AVAILABLE: 冷却时间到期后自动恢复
- COOLDOWN → LOCKED: 冷却期内再次失败
- LOCKED → AVAILABLE: 手动重置(用户干预)

AccountState enum (每个值携带行为):
  AVAILABLE: get() 返回账号; markFailure() 计数
  COOLDOWN:  get() 返回 null; isInCooldown() true; countdown()
  LOCKED:   get() 返回 null; isLocked() true; requiresManualReset()
```

---

## 4. 错误码枚举

```java
// 编程技术: 枚举(12 值 5 元数据;元数据即分类依据,无需外部分类器,见 ADR-0001 D9 + ADR-0006 §2.1+2.2)
public enum ErrorCode {
    // 登录阶段
    LOGIN_PAGE_LOAD_FAILED (Severity.HIGH,     true,  false, true,  "登录页加载失败"),
    CAS_REDIRECT_TIMEOUT   (Severity.HIGH,     true,  false, true,  "CAS 重定向超时"),
    PASSWORD_INCORRECT     (Severity.CRITICAL, false, true,  true,  "密码错误"),
    ACCOUNT_LOCKED         (Severity.CRITICAL, false, true,  true,  "账号被锁"),
    CAPTCHA_REQUIRED       (Severity.HIGH,     true,  false, true,  "触发图形验证码"),
    // 选场地阶段
    VENUE_OCCUPIED         (Severity.MEDIUM,   true,  false, false, "目标场地已被预约"),
    NO_AVAILABLE_VENUE     (Severity.MEDIUM,   true,  false, false, "该时段无任何可用场地"),
    ELEMENT_NOT_FOUND      (Severity.MEDIUM,   true,  false, true,  "未找到目标元素"),
    // 网络 / 浏览器
    NETWORK_TIMEOUT        (Severity.MEDIUM,   true,  false, false, "网络超时"),
    BROWSER_CRASH          (Severity.HIGH,     true,  false, true,  "浏览器进程崩溃"),
    // 业务编排
    INVALID_REQUEST        (Severity.LOW,      false, false, false, "请求参数不合法"),
    UNKNOWN                (Severity.HIGH,     true,  false, true,  "未知异常");

    private final Severity severity;
    private final boolean retryable;
    private final boolean switchAccount;
    private final boolean screenshot;
    private final String  hint;

    ErrorCode(Severity severity, boolean retryable, boolean switchAccount,
              boolean screenshot, String hint) { /* 字段赋值 */ }

    public Severity severity()            { return severity; }
    public boolean  isRetryable()         { return retryable; }
    public boolean  shouldSwitchAccount() { return switchAccount; }
    public boolean  shouldScreenshot()    { return screenshot; }
    public String   hint()                { return hint; }
}
```

---

## 5. CLI 契约

### 子命令路由

```
szu-agent <command> [subcommand] [OPTIONS]

顶层 commands:
  booking   venue 体育场馆预约(P0 核心,真跑 Playwright)
  homework  list   畅课作业列表
            download 畅课作业附件下载
  schedule  list   课表查询
  calendar  get    校历查询
  notice    list   公文通通知
  exam      list   考试安排
  knowledge query  深大知识库
  skill     list/call  Skill 注册中心
  mcp       list/call/serve   MCP 协议(serve 可选 --http 常驻)

通用 options:
  --username   学号
  --format     输出格式(json/human,默认 human)
  --help       显示帮助
  --version    显示版本(0.1.0+)

业务特定 options(随 command 变化):
  --campus / --sport / --date / --time-slot / --preferred-venue  (booking venue)
  --query / --limit / --category                                  (knowledge query / notice list)
  --daysBack                                                       (notice list)
  --status                                                         (exam list)
  --homework-id / --output-dir / --throttle-ms / --max-retries    (homework download)
  --args k=v ... (skill call / mcp call)                          (扁平化参数)
  --http / --port 8765                                            (mcp serve)

凭证加载(3 层查找,ADR-0005 D1):
  1. 进程环境变量 SZU_PASSWORD_<学号>
  2. --env-file 指向的 .env
  3. Skill wrapper 注入
```

### JSON 输出 Schema

```json
{
  "success":       true,
  "data":          { "traceId": "...", "bookingId": "..." },
  "errorCode":      null,
  "errorMessage":   null,
  "traceId":        "20240610-abc123",
  "elapsedMs":      4321
}
```

### 退出码

| 退出码 | 含义 |
|---|---|
| 0 | 成功 |
| 1 | 业务失败(如无可用时段) |
| 2 | 参数错误 |
| 3 | 环境错误(配置缺失) |
| 4 | 浏览器错误(启动失败) |

---

## 6. 局限性分析与改进建议

> 课程报告必含章节。对每种使用的编程技术和设计模式,分析局限性并提出改进方向。

### 6.1 泛型 (`<T>`, `TaskResult<T>`, `CampusTask<T>`)

**局限性:**

- 运行时类型擦除导致 `instanceof` 检查和泛型强制转换不直观
- 上界通配符(`? extends T`)与下界(`? super T`)容易混淆,增加学习成本
- 泛型不能用于基本类型(`List<int>` 非法),只能用包装类型

**改进方向:**

- Java 21 的 **Type Prediction** 改进擦除问题(仍在演进中)
- 使用 `sealed interface` + `record` 组合,可让编译器更严格地约束类型范围
- 引入 **泛型工厂方法**(`GenericFactory<T>`)减少强制类型转换

**参考:**
- JEP 445: Unnamed Patterns and Variables (Java 22)
- "Effective Java" 第 3 版,Item 31-33: 泛型最佳实践

---

### 6.2 枚举 (`ErrorCode`, `TaskStatus`, `AccountState`)

**局限性:**

- 枚举是编译期常量,新增枚举值需要重新编译所有引用类
- 枚举方法(如 `isRetryable()`)与业务逻辑耦合,违反单一职责
- 在分布式场景下,枚举值难以跨服务同步(需要额外的序列化层)

**改进方向:**

- Java 17+ 的 **sealed enum** 可限制枚举子类,增强类型安全
- 将枚举行为抽取到策略类(`ErrorHandler`),枚举只承载数据
- 考虑用 **String-based enum + 配置文件** 替代硬编码枚举,支持热更新

**参考:**
- "Effective Java" 第 3 版,Item 34-36: 枚举最佳实践
- JEP 409: Sealed Classes (Java 17)

---

### 6.3 注解 (`@AgentTool`)

**局限性:**

- 注解仅在编译期或运行时被读取,不提供编译时类型安全
- 反射扫描注解有运行时开销,且增加代码复杂度
- 注解的参数校验(如 `@NotNull`)需要额外的验证框架(如 Hibernate Validator)

**改进方向:**

- Java 17+ 的 **注解作用域增强** 可在类型上标注更精确的元数据
- 引入 **编译时注解处理器**(APT)生成代码,减少反射开销
- 用 `jakarta.annotation.*` 替代自定义注解,复用成熟的验证生态

**参考:**
- JEP 404: Generative + Selective Class-file (Java 22 preview)
- Jakarta Annotations 3.0 Specification

---

### 6.4 重载 (多构造器/工厂方法)

**局限性:**

- 参数类型相近时(如 `String` 和 `int` 都能作为 campus),重载决议容易选错方法
- 方法签名相同时编译器选择"更具体"的版本,可能导致意外行为
- 重载不能跨泛型边界(如 `List<String>` 和 `List<Integer>` 在重载时视为同签名)

**改进方向:**

- 避免重载,改用 ** Builder 模式** 或 **命名参数模式**(Java 21 record 支持命名构造)
- 用方法名区分而非参数类型(如 `fromCampusName(String)` vs `fromCampusEnum(Campus)`)
- 检查性重载:增加编译期参数校验或单元测试覆盖所有重载分支

**参考:**
- "Effective Java" 第 3 版,Item 52: 慎用重载
- JEP 447: Statements Before super(...) (Java 22)

---

### 6.5 抽象类 (`AbstractBrowser`, `BrowserLifecycle`)

**局限性:**

- Java 不允许多重继承,抽象类一旦继承就无法再继承其他类
- 抽象类的演化成本高:在抽象类中添加新方法,所有子类必须实现
- 抽象类比接口更难测试(mock 需要处理抽象方法)

**改进方向:**

- Java 17+ 优先使用 **sealed interface** + **default 方法**替代抽象类
- 将 `AbstractBrowser` 改为 `BrowserLifecycle` 接口 + `default` 方法实现通用行为
- 使用 **组合优于继承**:将共享行为抽取为独立组件,通过委托而非继承实现复用

**参考:**
- "Effective Java" 第 3 版,Item 20: 优先使用接口而非抽象类
- JEP 420: Pattern Matching for switch (Java 21)

---

### 6.6 Lambda 表达式与 Stream API

**局限性:**

- 链式 Stream 操作调试困难(stack trace 不直观)
- 复杂 Stream 操作(如 `flatMap` + `groupingBy`)性能开销高于显式循环
- Stream 隐式化控制流,多人协作时代码可读性下降

**改进方向:**

- 关键路径(重试循环、错误处理)用显式循环,非关键路径用 Stream
- 用 `@FunctionalInterface` 约束 Lambda 参数类型,减少类型推断歧义
- Java 21 的 **Virtual Threads** 可与 Stream 并行组合,提升 I/O 密集场景吞吐量

**参考:**
- "Modern Java in Action" (Manning): Stream 性能分析
- JEP 444: Virtual Threads (Java 21)

---

### 6.7 ~~静态工厂模式 (`BrowserFactory`)~~ 已删除(ADR-0007 D1)

**为什么删**:`BrowserFactory.create(Kind)` 3 行 switch,实现 ≈ 接口复杂度;**seam 在错位置**(调用方被迫"选择" Kind)。
按 `LANGUAGE.md` 词汇,One adapter means a hypothetical seam — 调用方从不"挑",只是被告知。

**改用 `ConfigManager` 配置注入**:

```yaml
# src/main/resources/application.yml
browser:
  kind: PLAYWRIGHT    # 测试改 FAKE,生产不动
```

```java
// 业务方调用代码(无变化)
BrowserLifecycle browser = ConfigManager.getInstance().browser();
```

**深度对比**:

| 方案 | 接口复杂度 | 调用方决策 | 配置变更影响范围 |
|---|---|---|---|
| `BrowserFactory.create(Kind)` | 1 静态方法 + 1 enum | 必须知道 2 个 Kind | 改 Kind = 改代码 |
| `ConfigManager.browser()` | 1 个 getter | 零决策 | 改 yml = 不改代码 |

后者 seam 深度更高:调用方不学工厂,只学配置。

> **5 模式 → 4 模式**(ADR-0007 D1):删 Static Factory 模式;其他 4 模式每个都有 2+ 处真业务落地。
> 详细报告话术见 `docs/design-patterns.md` §5。

---

### 6.8 单例模式 (`ConfigManager` / `Tracer`)

**局限性:**

- 难以测试:单例持有状态,测试间相互影响(需手动 reset 或使用反射清空)
- 单一实例无法应对多租户场景(如同时管理多个校园账号体系)
- 反射可破坏单例保护(`AccessibleObject.setAccessible`)

**改进方向:**

- 用 **枚举单例**(`enum ConfigManager { INSTANCE }`)防止反射攻击
- 引入 **单例注册表**(类似 Spring `BeanFactory`)管理单例生命周期
- 对于需要多实例的场景,改为 **原型模式**(`ConfigManager` 实例工厂)按需创建

**参考:**
- "Effective Java" 第 3 版,Item 3: 私有构造器强化单例
- JEP 447: Statements Before super(...)

---

### 6.9 策略模式 (`BookingStep` / `VenueSelector` / `RetryPolicy`)

**局限性:**

- 策略数量增加后,客户端需要知道所有策略实现才能选择,增加耦合
- 策略之间难以共享状态(如 `RetryPolicy` 需要知道当前错误码才能决定延迟)
- 策略的测试与业务逻辑耦合,单元测试覆盖成本高

**改进方向:**

- 用 **sealed interface** 限制策略实现集合,编译器防止未知策略注入
- 引入 **策略注册表 + 优先级机制**,由框架而非客户端选择策略
- 用 **Java 17+ 的 pattern matching** 简化策略选择逻辑(`switch` 表达式模式匹配)
- `RetryPolicy` 与 ADR-0006 retry 子决定协同:`execute(Supplier<T>)` 在 `e.code().isRetryable() == false` / 重试耗尽 时直接抛 `BookingException`,**不**调用 action 第三次以上

**参考:**
- "Head First Design Patterns": Strategy Pattern
- JEP 441: Pattern Matching for switch (Java 21)

> **历史变更**(ADR-0001 D9, 2026-06-11):原 `ErrorClassifier` 策略已删除 —
> `ErrorCode` 枚举每个值自带 `isRetryable` / `shouldSwitchAccount` / `shouldScreenshot` 元数据,
> 无需外部分类器。

---

### 6.10 适配器模式 (`PlaywrightBrowserAdapter`)

**局限性:**

- 适配器封装了第三方库的进阶能力(如网络拦截、请求重写),使用户无法访问
- 适配层出错时,需要同时理解目标接口和被适配者 API,调试困难
- Playwright SDK + Obs CDP 端的 API 变化会直接冲击适配器层,维护成本高(ADR-0010 D1 决定保留 SDK 当 CDP 客户端,但底层浏览器从 Chromium 切到 Obscura 后,Obscura 未实现的 CDP 方法如 `Page.captureScreenshot` 会通过 `mapException` 暴露为 `BROWSER_CRASH`)
- 真演示唯一路径,任何 Adapter bug 都会让演示翻车(ADR-0001 D2)

**改进方向:**

- 引入 **Decorator** 分层(如 `LoggingBrowser`, `RateLimitBrowser`)分离横切关注点
- 在适配器层暴露 **配置对象**(`BrowserConfig`),减少硬编码参数
- 引入 **版本协商机制**:适配器声明支持的 API 版本,被适配者返回兼容实现
- 适配失败时快速失败 + 清晰 trace(已点提交后不再重试,见 ADR-0001 OQ4)

**参考:**
- "Design Patterns: Elements of Reusable Object-Oriented Software": Adapter + Decorator
- Playwright Java Official Documentation + [Obscura README](https://github.com/h4ckf0r0day/obscura)(声明为 headless Chrome 的 Playwright/Puppeteer drop-in replacement)
- ADR-0010(Obscura 后端替换的完整决策链)

> **历史变更**(ADR-0001 D9, 2026-06-11):原 `CloakBrowserAdapter` 已重命名为
> `PlaywrightBrowserAdapter` — 直接封装 Playwright Java 绑定,无中间层。

---

### 6.11 系统级局限性

**浏览器自动化对页面结构变化的脆弱性:**

- 选择器(如 CSS selector, XPath)依赖页面 DOM 结构,页面改版后选择器失效
- 改进:引入基于语义的元素识别(ARIA role、data-testid)、视觉元素识别(Playwright 的 locator API 改进)

**CloakBrowser 依赖本地浏览器环境:**

- 不同用户电脑环境(puppeteer / chromium 版本、路径)差异导致"在我机器上能跑"问题
- 改进:提供 Docker 镜像或环境健康检查脚本,在启动前验证依赖

**Agent 意图理解的误判风险:**

- 如果上层 Agent 错误理解用户意图,调用本系统的 CLI 参数错误,可能导致不期望的行为
- 改进:在 CLI 层增加参数校验和**操作确认机制**(如 `--confirm` 标志),高风险操作二次确认

**MCP 工具的权限控制缺失:**

- MCP tools/list 暴露所有工具,无细粒度权限控制,错误调用可能产生副作用
- 改进:引入工具级别 ACL,`MCPToolProvider` 支持 `toolPermissions.json` 配置

**改进建议总结表:**

| 问题 | 改进方向 | 优先级 |
|---|---|---|
| 页面结构脆弱性 | 视觉定位 + ARIA 语义 | 高 |
| 浏览器环境差异 | Docker 镜像 + 健康检查 | 高 |
| Agent 意图误判 | 参数校验 + 二次确认 | 中 |
| MCP 权限缺失 | 工具级别 ACL + 审计日志 | 中 |
| 枚举演化成本 | sealed enum + 配置外置 | 低 |
| 单例测试困难 | 枚举单例 + 注册表 | 低 |

---

## 7. 关键设计决策 (ADR 索引)

| ADR | 决策 | 文档 | 状态 |
|---|---|---|---|
| ADR-0001 | 项目方向校准(Grill 共识) | `docs/adr/0001-project-direction-recalibration.md` | Accepted |
| ADR-0002 | `BrowserLifecycle` 接口设计与 Playwright 适配细节 | `docs/adr/0002-browser-lifecycle-and-playwright-adapter.md` | Accepted |
| ADR-0005 | 凭证流转 + archunit 强制 | `docs/adr/0005-credential-and-logging-enforcement.md` | Accepted |
| ADR-0006 | Phase 1 子决定(domain + error + retry + matcher) | `docs/adr/0006-phase1-domain-error-retry-matcher.md` | Accepted |
| ADR-0007 | 架构深度审视(improve-codebase-architecture) | `docs/adr/0007-architecture-deepening.md` | Accepted |
| ADR-0008 | 登录态持久化(storageState + 30d TTL + 探针) | `docs/adr/0008-session-persistence.md` | Accepted |
| ADR-0009 | 课表模块设计 | `docs/adr/0009-schedule-module-design.md` | Accepted |
| ADR-0010 | Obscura 替换 Playwright-Chromium 后端(SDK 复用) | `docs/adr/0010-obscura-backend-replacement.md` | Accepted |

> 实际 ADR 文档在 `docs/adr/` 目录下,采用 `{NNNN}-{kebab-case-slug}.md` 命名。
> ADR-0003 / 0004 槽位未占(扩展点放在 P1 后续 ADR 描述,见 `CampusTask<T>` 与 `ExternalSkill` 设计)。
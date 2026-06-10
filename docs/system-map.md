# 系统地图

> 本文档描述系统整体架构、模块拓扑、核心时序、状态机与关键设计决策。
> 集成"局限性分析与改进建议"章节(课程报告必含)。

---

## 1. 模块拓扑

```
edu.szu.agent
│
├── platform/
│   └── AgentToolPlatform          # Facade:统一入口,协调各子系统
│
├── task/
│   ├── CampusTask<T>              # 任务抽象接口
│   ├── TaskResult<T>              # 任务返回结果(record)
│   ├── TaskStatus                 # 任务状态枚举
│   └── TaskExecutor               # 任务执行器(重试/超时/状态记录)
│
├── domain/                         # 值对象(不可变 record)
│   ├── Campus                      # 校区枚举(粤海/丽湖)
│   ├── Sport                       # 项目枚举(网球/羽毛球/...)
│   ├── TimeSlot                    # 时间段
│   ├── Venue                       # 场地
│   └── BookingRequest              # 预约请求(Builder 构造)
│
├── client/                         # 校园服务客户端(静态工厂创建)
│   ├── ClientFactory               # Design Pattern: Static Factory
│   ├── VenueBookingClient          # 体育场馆预约(核心实现)
│   ├── NoticeQueryClient           # 公文通查询
│   ├── ChaoxingCourseClient        # 畅课任务
│   └── GrowthPlanClient            # 成长方案
│
├── browser/                        # 浏览器抽象层(适配器模式)
│   ├── BrowserLifecycle            # 目标接口: launch/navigate/click/type/screenshot/close
│   ├── PlaywrightBrowserAdapter    # Playwright 实现 BrowserLifecycle
│   ├── FakeBrowser                 # 测试/干跑实现 BrowserLifecycle
│   └── AbstractBrowser             # 模板方法骨架(可选)
│
├── config/
│   └── ConfigManager               # Design Pattern: Singleton
│
├── account/
│   ├── Account                     # 账号实体
│   ├── AccountManager               # 多账号管理
│   └── AccountState                 # 账号状态枚举(AVAILABLE/COOLDOWN/LOCKED)
│
├── retry/
│   ├── RetryPolicy                 # Design Pattern: Strategy(策略接口)
│   ├── FixedDelayRetry             # 固定延迟策略
│   └── ExponentialBackoff          # 指数退避策略
│
├── error/
│   ├── ErrorCode                   # 错误码枚举(每个值带元数据)
│   ├── BookingException            # 统一异常
│   └── ErrorClassifier             # Design Pattern: Strategy(错误分类策略)
│
├── matcher/                        # Design Pattern: Strategy
│   ├── Matcher<T>                  # 匹配器接口
│   ├── TextMatcher                 # 精确文本匹配
│   ├── RegexMatcher                # 正则匹配
│   ├── ContainsMatcher             # 包含匹配
│   └── CompositeMatcher            # 组合匹配
│
├── observability/
│   ├── Tracer                      # Design Pattern: Singleton(trace_id 管理)
│   └── MetricsCollector            # 指标收集(成功/失败/耗时)
│
├── skill/
│   ├── Skill                       # Skill 接口
│   ├── SkillManager                # Skill 注册/加载/执行
│   └── @AgentTool                  # 注解:标记可暴露给 Agent 的方法
│
├── mcp/
│   └── MCPToolProvider             # MCP tools/list 导出
│
└── cli/
    ├── Main                        # picocli 入口
    ├── BookingCommand              # booking 子命令
    ├── NoticeCommand               # notice 子命令
    └── JsonOutput                  # JSON 序列化
```

### 包依赖关系

```
platform.AgentToolPlatform
  ├──► task.TaskExecutor
  │     ├──► domain.BookingRequest
  │     └──► error.ErrorCode / BookingException
  │
  ├──► client.ClientFactory
  │     └──► client.VenueBookingClient
  │           ├──► browser.BrowserLifecycle
  │           ├──► matcher.Matcher (Strategy)
  │           ├──► retry.RetryPolicy (Strategy)
  │           ├──► account.AccountManager
  │           └──► domain.BookingRequest
  │
  ├──► config.ConfigManager (Singleton)
  ├──► observability.Tracer (Singleton)
  └──► skill.SkillManager
        └──► mcp.MCPToolProvider
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
AgentToolPlatform.run("booking.venue", args)  │
    │                                          │
    ├──► ConfigManager.getInstance().load()    │ Singleton
    │                                          │
    ├──► ClientFactory.create("booking.venue") │ Static Factory
    │        ▼                                 │
    │   VenueBookingClient                    │
    │                                          │
    ├──► TaskExecutor.execute(client)          │
    │        │                                │
    │        ├──► BrowserLifecycle.launch()    │
    │        │        ▼                       │
    │        │   CloakBrowserAdapter.doLaunch()│ Adapter
    │        │   (或 FakeBrowser.dryRun())    │
    │        │                                  │
    │        ├──► client.login(username, pwd)  │
    │        │        ▼                       │
    │        │   BrowserLifecycle.navigate(url)│
    │        │   BrowserLifecycle.click(sel)   │
    │        │   BrowserLifecycle.type(sel,text)│
    │        │                                  │
    │        ├──► client.selectCampus(campus)   │
    │        ├──► client.selectSport(sport)     │
    │        ├──► client.selectTimeSlot(slot)   │
    │        │        ▼                       │
    │        │   Matcher.match(elements)        │ Strategy
    │        │                                  │
    │        ├──► client.selectVenue()          │
    │        │        ▼                       │
    │        │   Matcher.filter(venues, regex)  │ Strategy
    │        │                                  │
    │        ├──► client.confirm()              │
    │        │        ▼                       │
    │        │   RetryPolicy.shouldRetry()      │ Strategy
    │        │   (失败时重试,最多 maxRetry 次)   │
    │        │                                  │
    │        └──► BrowserLifecycle.close()      │
    │                                          │
    ├──► Tracer.generateTraceId()               │ Singleton
    │                                          │
    └──► return TaskResult<T> (JSON)           │
         ▼                                     │
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
// 编程技术: 枚举(每个枚举值携带元数据方法)
// Design Pattern: Strategy (ErrorClassifier 根据 ErrorCode 选择处理策略)
public enum ErrorCode {
    LOGIN_FAILED       (true,  false, true),
    PASSWORD_INCORRECT (false, true,  true),
    ACCOUNT_LOCKED    (false, true,  true),
    CAPTCHA_REQUIRED   (false, true,  true),
    PAGE_LOAD_TIMEOUT (true,  false, false),
    ELEMENT_NOT_FOUND  (true,  false, true),
    NO_AVAILABLE_SLOT  (true,  false, false),
    SUBMIT_FAILED      (true,  false, true),
    NETWORK_ERROR      (true,  false, false),
    BROWSER_CRASHED    (true,  false, true),
    UNKNOWN_ERROR      (false, false, true);

    private final boolean retryable;
    private final boolean switchAccount;
    private final boolean screenshot;

    ErrorCode(boolean retryable, boolean switchAccount, boolean screenshot) {
        this.retryable = retryable;
        this.switchAccount = switchAccount;
        this.screenshot = screenshot;
    }

    public boolean isRetryable()       { return retryable; }
    public boolean shouldSwitchAccount(){ return switchAccount; }
    public boolean shouldScreenshot() { return screenshot; }
}
```

---

## 5. CLI 契约

### 子命令路由

```
szu-agent <skill> <action> [OPTIONS]

skills:
  booking venue   体育场馆预约(P0 核心)
  notice list     公文通查询(P1 示例)
  chaoxing tasks  畅课任务(P1 设计)
  skill list      列出所有 Skill(P0)

options:
  --username  学号
  --campus    校区(粤海/丽湖)
  --sport     项目(网球/羽毛球/...)
  --date      日期索引(0=今天)
  --time-slot 时间段(如 19:00-20:00)
  --format    输出格式(json/human,默认 human)
  --dry-run   干跑模式(FakeBrowser)
  --help      显示帮助
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

### 6.7 静态工厂模式 (`ClientFactory`)

**局限性:**

- 工厂类需要维护注册表,新增产品时必须修改工厂类(违反开闭原则的严格解释)
- 字符串键(如 `"booking.venue"`)无编译时检查,拼写错误到运行时才暴露
- 工厂方法无法表达构造参数(如需要传入 `ConfigManager` 的工厂)

**改进方向:**

- 用 **Java `ServiceLoader`** 替代手写注册表,自动发现 `CampusTask` 实现
- 用 `Enum` 作为工厂键,编译期检查拼写
- 引入 **依赖注入**(Spring / Jakarta CDI),工厂由容器管理,消除硬编码注册

**参考:**
- "Effective Java" 第 3 版,Item 1-2: 静态工厂
- Java ServiceLoader Specification

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

### 6.9 策略模式 (`RetryPolicy` / `Matcher` / `ErrorClassifier`)

**局限性:**

- 策略数量增加后,客户端需要知道所有策略实现才能选择,增加耦合
- 策略之间难以共享状态(如 `RetryPolicy` 需要知道当前错误码才能决定延迟)
- 策略的测试与业务逻辑耦合,单元测试覆盖成本高

**改进方向:**

- 用 **sealed interface** 限制策略实现集合,编译器防止未知策略注入
- 引入 **策略注册表 + 优先级机制**,由框架而非客户端选择策略
- 用 **Java 17+ 的 pattern matching** 简化策略选择逻辑(`switch` 表达式模式匹配)

**参考:**
- "Head First Design Patterns": Strategy Pattern
- JEP 441: Pattern Matching for switch (Java 21)

---

### 6.10 适配器模式 (`CloakBrowserAdapter`)

**局限性:**

- 适配器封装了第三方库的进阶能力(如网络拦截、请求重写),使用户无法访问
- 适配层出错时,需要同时理解目标接口和被适配者 API,调试困难
- Playwright/CloakBrowser 的 API 变化会直接冲击适配器层,维护成本高

**改进方向:**

- 引入 **Decorator** 分层(如 `LoggingBrowser`, `RateLimitBrowser`)分离横切关注点
- 在适配器层暴露 **配置对象**(`BrowserConfig`),减少硬编码参数
- 引入 **版本协商机制**:适配器声明支持的 API 版本,被适配者返回兼容实现

**参考:**
- "Design Patterns: Elements of Reusable Object-Oriented Software": Adapter + Decorator
- Playwright Java Official Documentation

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

| ADR | 决策 | 文档 |
|---|---|---|
| ADR-001 | BrowserLifecycle 适配器模式 | `docs/architecture/ADR-001.md` |
| ADR-002 | 使用 record 作为不可变值对象 | `docs/architecture/ADR-002.md` |
| ADR-003 | 错误码枚举 + 策略模式处理 | `docs/architecture/ADR-003.md` |
| ADR-004 | 静态工厂 + ServiceLoader 注册 Skill | `docs/architecture/ADR-004.md` |
| ADR-005 | Singleton 双检查锁 + 延迟加载 | `docs/architecture/ADR-005.md` |

(ADR 文件在对应模块实现时创建)
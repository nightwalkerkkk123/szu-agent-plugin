# 设计模式应用清单

> 本项目使用 **4 种设计模式**(经 ADR-0007 D1 从 5 模式 → 4 模式),每种均显式标注于对应类的第一行注释。
> 本文档作为报告的"设计模式"章节,可直接提交。

> ⚠️ **ADR 校准声明**:本文档的 5 模式 → 4 模式已按 **ADR-0001 D9 + ADR-0007 D1** 重选。
> - **删** Static Factory / `BrowserFactory`,改 `ConfigManager` 配置 `browser.kind` 注入(ADR-0007 D1)
> - Strategy 中 `ErrorClassifier` 已**删除**(枚举自带元数据)
> - Adapter 由 `CloakBrowserAdapter` 改为 **`PlaywrightBrowserAdapter`**
> - 删除原"(额外) 模板方法"
> - `JitteredBackoff` NoOp 占位删除(ADR-0007 D2),RetryPolicy 4 实现 → 3 实现
>
> 详细理由见 `docs/adr/0001-project-direction-recalibration.md` §D9 + `docs/adr/0007-architecture-deepening.md` D1/D2。

---

## 目录

1. [Builder 模式 (Builder)](#1-builder-模式-builder) — `BookingRequest.Builder`
2. [单例模式 (Singleton)](#2-单例模式-singleton) — `ConfigManager` / `Tracer`
3. [策略模式 (Strategy)](#3-策略模式-strategy) — `BookingStep` / `VenueSelector` / `RetryPolicy`
4. [适配器模式 (Adapter)](#4-适配器模式-adapter) — `PlaywrightBrowserAdapter`
5. ~~[静态工厂模式 (Static Factory)](#5-静态工厂模式-static-factory-已删除adr-0007-d1)~~ — 已删除
6. [动态回退包装器 (Decorator + Strategy)](#6-动态回退包装器-decorator--strategy-p1-阶段-1) — `ResilientScheduleClient` (P1 阶段 1)

> 5 模式 → **4 模式**(ADR-0007 D1):`BrowserFactory` / Static Factory 删除,改 `ConfigManager` 配置注入
> 详见 [§5 删除说明](#5-静态工厂模式-static-factory-已删除adr-0007-d1)
>
> **P1 阶段 1 补充**:为 `schedule_list` 真实抓取新增"动态回退包装器"组合
> (`ResilientScheduleClient` = Decorator + Strategy),见
> [§6 动态回退包装器 (Decorator + Strategy)](#6-动态回退包装器-decorator--strategy-p1-阶段-1)

---

## 1. Builder 模式 (Builder)

### 位置

```
com.szu.agent.domain.BookingRequest.Builder   (内部 Builder 类,static final)
com.szu.agent.cli.BookingCommand              (指导者,从 picocli options 构造)
```

### 模式角色

| 角色 | 实现 |
|---|---|
| **产品** | `BookingRequest` (Java 21 record,不可变值对象) |
| **Builder** | `BookingRequest.Builder` (内部 static final 类) |
| **指导者** | `BookingCommand.run()` 从 picocli options 构造 |

### 类图

```
BookingRequest                  (产品,不可变 record)
  - campus: Campus
  - sport: Sport
  - date: LocalDate
  - timeSlot: TimeSlot
  - preferredVenueIndex: int
  - displayHint: String
  ───────────────────────────────
  + builder(): Builder

Builder  ────────────── «inner static final class»
  + campus(Campus): Builder
  + sport(Sport): Builder
  + date(LocalDate): Builder
  + timeSlot(TimeSlot): Builder
  + preferredVenueIndex(int): Builder
  + displayHint(String): Builder
  + build(): BookingRequest          (校验:4 必填非空)
```

### 代码

```java
// Design Pattern: Builder
// 编程技术: 泛型 / 重载 / 抽象类 / record
// 详见 ADR-0006 domain 子决定:LocalDate 不用 int dayOffset,TimeSlot 不用 String
public record BookingRequest(
    Campus campus,
    Sport sport,
    LocalDate date,
    TimeSlot timeSlot,
    int preferredVenueIndex,
    String displayHint          // 给 Agent 看的备注,可选
) {
    /**
     * {@summary 创建不可变 BookingRequest 的 Builder}[^1].
     */
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Campus campus;
        private Sport sport;
        private LocalDate date;
        private TimeSlot timeSlot;
        private int preferredVenueIndex = 1;
        private String displayHint = "";

        public Builder campus(Campus v)            { this.campus = v; return this; }
        public Builder sport(Sport v)              { this.sport = v;  return this; }
        public Builder date(LocalDate v)           { this.date = v;   return this; }
        public Builder timeSlot(TimeSlot v)        { this.timeSlot = v; return this; }
        public Builder preferredVenueIndex(int v)  {
            if (v < 1) throw new IllegalArgumentException("venue index >= 1");
            this.preferredVenueIndex = v; return this;
        }
        public Builder displayHint(String v)       {
            this.displayHint = v == null ? "" : v; return this;
        }

        public BookingRequest build() {
            if (campus == null || sport == null || date == null || timeSlot == null) {
                throw new IllegalStateException("campus/sport/date/timeSlot required");
            }
            return new BookingRequest(campus, sport, date, timeSlot, preferredVenueIndex, displayHint);
        }
    }
}
```
}
```

### 为什么选它

- `BookingRequest` 有 6 个参数,其中 `campus`、`sport` 等是枚举,builder 避免了构造函数重载爆炸
- 链式调用 `builder().username("2023150090").campus("粤海").sport("网球").build()` 语义清晰
- 不可变产品:构造完成后不可修改,线程安全

### 局限性

- Builder 本身是可变对象,需确保 `build()` 前不会泄露给其他线程
- 参数校验在 `build()` 中延迟执行,构造函数不立即报错——可用 `verify()` 方法前置校验
- 改进方向:用 Java 21 的 **record 构造器参数命名**(`BookingRequest(String username, ...)`)部分替代 Builder,减少模板代码

---

## 2. 单例模式 (Singleton)

### 位置

```
edu.szu.agent.config.ConfigManager
edu.szu.agent.observability.Tracer
edu.szu.agent.skill.Skills
```

### 模式角色

| 角色 | 实现 |
|---|---|
| **单例** | `ConfigManager` / `Tracer` / `Skills` (双重检查锁,线程安全;`registerDefaultSkills()` 幂等) |

### 类图

```
ConfigManager  ──────────────── «singleton»
  - instance: ConfigManager (volatile)
  - config: Config
  - getInstance(): ConfigManager
  + load(): void
  + get(key): String
  + browser(): BrowserLifecycle          # ADR-0007 D1:按 browser.kind 配置注入

Tracer  ─────────────────────── «singleton»
  - instance: Tracer (volatile)
  - traceId: String
  - getInstance(): Tracer
  + generateTraceId(): String
  + currentTraceId(): String
  + recordFailure(ErrorCode, String, Optional<Path>)  # ADR-0007 D4:不接 Throwable

Skills  ─────────────────────── «singleton, @since 0.1.0 P1 落地,2026-06 升级»
  - instance: Skills (volatile)
  - registry: Map<String, Skill<?>>
  - getInstance(): Skills
  + register(Skill<?>): void
  + all(): List<Skill<?>>
  + findByName(String): Optional<Skill<?>>   # 2026-06 增,KBRouter 用
```

### 代码

```java
// Design Pattern: Singleton (双重检查锁)
// 编程技术: 枚举 / 泛型 / Lambda
public final class ConfigManager {
    private static volatile ConfigManager instance;

    private Config config;

    private ConfigManager() {}

    public static ConfigManager getInstance() {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager();
                }
            }
        }
        return instance;
    }

    public synchronized void load(String configPath) {
        this.config = new ConfigLoader().load(configPath);
    }

    public String get(String key) { return config.get(key); }
}
```

### 为什么选它

- 全局唯一配置:多个业务执行共享同一份配置,无需每次传递
- `Tracer` 贯穿一次执行的所有步骤,必须唯一才能保证 `trace_id` 不中断
- `Skills` 是 Skill 注册中心,8 内部 Skill + N 外部 Skill 都要查它(由 `MCPToolCallHandler.call` / `ToolSchema.toolsList` 复用),单例避免重复注册
- 延迟加载:第一次调用 `getInstance()` 时才创建,避免启动开销

### 局限性

- 单一实例难以测试(无法 mock 独立实例)
- 状态在实例中累积(如 `Tracer` 积累日志),长时间运行可能内存泄漏
- 改进方向:用 Spring 的 `ApplicationContext` / Jakarta CDI 替代手动单例,或用 **枚举单例**(`enum ConfigManager { INSTANCE; }`)防止反射攻击

---

## 3. 策略模式 (Strategy)

### 位置

```
com.szu.agent.retry.RetryPolicy             (策略接口,FunctionalInterface)
com.szu.agent.retry.FixedDelay              (策略A:固定间隔)
com.szu.agent.retry.ExponentialBackoff      (策略B:指数退避)
com.szu.agent.retry.NoRetry                 (策略C:不重试,单例)
com.szu.agent.retry.RetryPolicies           (工厂,defaultBooking/login/quickFix)

com.szu.agent.client.step.BookingStep       (策略接口,每步一个实现)
com.szu.agent.client.step.CasLoginStep      (登录)
com.szu.agent.client.step.NavigateToBookingStep
com.szu.agent.client.step.SelectCampusStep
com.szu.agent.client.step.SelectSportStep
com.szu.agent.client.step.SelectDateStep
com.szu.agent.client.step.SelectTimeSlotStep
com.szu.agent.client.step.SelectVenueStep
com.szu.agent.client.step.ConfirmBookingStep
com.szu.agent.client.step.VenueSelector     (内部 VenueSelector Strategy)
com.szu.agent.client.VenueBookingClient     (Pipeline 调用方)
```

> ADR-0007 D2:`JitteredBackoff` NoOp 占位删除,YAGNI;P1 真需要时 15 行加回

> **注**:ADR-0001 D9 已删除原 `ErrorClassifier` 类(枚举自身已带元数据,无需外部分类器)。

### 模式角色

| 策略接口 | 具体策略 |
|---|---|
| `BookingStep` | `CasLoginStep`, `SelectCampusStep`, `SelectSportStep`, `SelectDateStep`, `SelectTimeSlotStep`, `SelectVenueStep`, `ConfirmBookingStep` |
| `RetryPolicy` | `FixedDelay`, `ExponentialBackoff`, `NoRetry` |

### 类图

```
BookingStep  ────────────── «interface»  (@FunctionalInterface)
  + execute(BrowserLifecycle, BookingContext): StepOutcome

CasLoginStep             ──► BookingStep
NavigateToBookingStep    ──► BookingStep
SelectCampusStep         ──► BookingStep
SelectSportStep          ──► BookingStep
SelectDateStep           ──► BookingStep
SelectTimeSlotStep       ──► BookingStep
SelectVenueStep          ──► BookingStep
ConfirmBookingStep       ──► BookingStep
VenueSelector            ──► (内部 Strategy,为 Sport 选 VenueSelector)
CourtListSelector        ──► VenueSelector
CapacityVenueSelector    ──► VenueSelector

RetryPolicy  ────────────── «interface»  (@FunctionalInterface)
  + <T> T execute(Supplier<T> action): T
  + orElse(RetryPolicy): RetryPolicy  (default,组合)

FixedDelay              ──► RetryPolicy
ExponentialBackoff      ──► RetryPolicy
NoRetry                 ──► RetryPolicy
```

### 代码

```java
// Design Pattern: Strategy
// 编程技术: 泛型 / Lambda / FunctionalInterface
@FunctionalInterface
public interface RetryPolicy {
    /**
     * 执行 action,失败按策略重试;重试耗尽抛 {@code BookingException} 携带 {@code NETWORK_TIMEOUT}.
     * @throws BookingException 不可重试 / 重试耗尽时抛出
     * @since 0.1.0
     * @author 王子豪
     */
    <T> T execute(Supplier<T> action);

    /** 链式组合:先按 this 重试,耗尽后接 next. */
    default RetryPolicy orElse(RetryPolicy next) {
        return action -> {
            try { return this.execute(action); }
            catch (BookingException e) {
                if (next == null) throw e;
                return next.execute(action);
            }
        };
    }
}
```

```java
// Design Pattern: Strategy
// 编程技术: 重载 / 不可变性
public final class FixedDelay implements RetryPolicy {
    private final int maxAttempts;
    private final Duration delay;

    public FixedDelay(int maxAttempts, Duration delay) { /* 校验非负 */ }

    @Override
    public <T> T execute(Supplier<T> action) {
        BookingException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try { return action.get(); }
            catch (BookingException e) {
                last = e;
                if (!e.code().isRetryable()) throw e;   // 关键:对接 ErrorCode 元数据
                if (attempt == maxAttempts) break;
                Thread.sleep(delay.toMillis());
            }
        }
        throw new BookingException(ErrorCode.NETWORK_TIMEOUT,
            "FixedDelay 重试 " + maxAttempts + " 次耗尽", last);
    }
}
```

```java
// Design Pattern: Strategy
// 编程技术: 泛型 / Lambda / FunctionalInterface
@FunctionalInterface
public interface BookingStep {
    StepOutcome execute(BrowserLifecycle browser, BookingContext context);
}
```

### 为什么选它

- `BookingStep` 把预约流程拆成 7 个可独立测试、可替换的步骤,新增步骤只需实现一个接口
- `VenueSelector` 把不同场地选择策略(网球场列表 / 健身房容量)封装为 Strategy,`SelectVenueStep` 按 Sport 委派
- 组合不靠 `CompositeMatcher` 类(原设计),改用 Java 21 `default` 方法 + Lambda,代码短 50%
- `RetryPolicy` 不靠 `shouldRetry` + `nextDelayMs` 双方法(原设计),改为单一 `execute(Supplier<T>)`,业务层只 `policy.execute(() -> doStep())`
- 重试耗尽统一抛 `NETWORK_TIMEOUT`,**不**用 `last.code()` — 重试耗尽 = 升级错误码
- 步骤 + 重试策略通过工厂(`RetryPolicies`)和管道(`VenueBookingClient`)组装,业务层零 `new`

### 局限性

- 策略类数量随功能增加而线性增长,需要统一的注册/发现机制
- `RetryPolicy` 内部用 `Thread.sleep`,演示场景同步阻塞够用;并发场景需要切换到 `ScheduledExecutorService`(P2 再说)
- `BookingStep` 当前只支持同步执行,P0 不引入异步或撤销语义

---

## 4. 适配器模式 (Adapter)

### 位置

```
edu.szu.agent.browser.BrowserLifecycle         (目标接口)
edu.szu.agent.browser.PlaywrightBrowserAdapter (适配器,真演示唯一入口)
edu.szu.agent.browser.FakeBrowser               (测试适配器,仅单元测试夹具)
```

### 模式角色

| 角色 | 实现 |
|---|---|
| **目标接口** | `BrowserLifecycle` |
| **适配者(Adaptee)** | Playwright Java 绑定 |
| **适配器** | `PlaywrightBrowserAdapter` |
| **测试适配器** | `FakeBrowser`(仅测试用,不出现在课堂演示,见 ADR-0001 D4) |

### 类图

```
BrowserLifecycle  ────────────── «interface»  (10 methods, ADR-0002 D1)
  + open(): void
  + close(): void
  + navigateTo(url): void
  + click(selector): void
  + fill(selector, value): void
  + isVisible(selector): boolean
  + textOf(selector): String
  + allTextOf(selector): List<String>
  + currentUrl(): String
  + screenshot(absolutePath): void

PlaywrightBrowserAdapter  ──► BrowserLifecycle
  - playwright: Playwright
  - browser: Browser
  - page: Page

FakeBrowser  ──► BrowserLifecycle
  - mockState: Map<String, Object>
```

### 代码

```java
// Design Pattern: Adapter
// 编程技术: 接口 + 泛型 + Java 21 record
public interface BrowserLifecycle {
    void open();
    void close();
    void navigateTo(String url);
    void click(String selector);
    void fill(String selector, String value);
    boolean isVisible(String selector);
    String textOf(String selector);
    List<String> allTextOf(String selector);
    String currentUrl();
    void screenshot(String absolutePath);
}
```

```java
// Design Pattern: Adapter
// 编程技术: 不可变构造器注入 + 显式状态管理
public final class PlaywrightBrowserAdapter implements BrowserLifecycle {

    private final Playwright playwright;
    private Browser browser;
    private Page page;

    public PlaywrightBrowserAdapter(Playwright playwright) {
        this.playwright = Objects.requireNonNull(playwright, "playwright");
    }

    @Override
    public void open() {
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
    }

    @Override
    public void close() {
        if (page != null) { page.close(); page = null; }
        if (browser != null) { browser.close(); browser = null; }
    }

    @Override
    public void navigateTo(String url) { page.navigate(url); }

    @Override
    public void click(String selector) { page.locator(selector).click(); }

    @Override
    public void fill(String selector, String value) { page.locator(selector).fill(value); }

    @Override
    public boolean isVisible(String selector) { return page.locator(selector).isVisible(); }

    @Override
    public String textOf(String selector) {
        String text = page.locator(selector).textContent();
        return text == null ? "" : text;
    }

    @Override
    public List<String> allTextOf(String selector) { return page.locator(selector).allTextContents(); }

    @Override
    public String currentUrl() { return page.url(); }

    @Override
    public void screenshot(String absolutePath) {
        page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(absolutePath)));
    }
}
```

### 为什么选它

- 业务层只依赖 `BrowserLifecycle` 接口(10 方法),不感知 Playwright fluent API
- 业务层与第三方浏览器库解耦 — 换 Selenium / 其他实现只需新写一个 Adapter
- `FakeBrowser` 单元测试夹具,CI 环境无需安装 Playwright 即可跑业务测试
- ADR-0002 D2 统一异常映射:Playwright TimeoutError → `NETWORK_TIMEOUT`, selector 错 → `ELEMENT_NOT_FOUND`

### 局限性

- 适配器封装了第三方库的复杂性,但也隐藏了可用的进阶特性(如拦截网络请求、设置请求头)
- 适配层一旦有 bug,定位困难(需要同时理解目标接口和被适配者 API)
- 改进方向:在适配器层引入 **Decorator**(`LoggingBrowser`, `RetryableBrowser`)包装,分离横切关注点;或引入 `BrowserConfig` 配置对象,减少硬编码

---

## 5. ~~静态工厂模式 (Static Factory)~~ 已删除(ADR-0007 D1)

### 为什么删

`BrowserFactory.create(Kind)` 是 3 行 switch,实现 ≈ 接口复杂度;**seam 在错位置**(调用方被迫"选择" Kind)。
按 [LANGUAGE.md] 词汇,**One adapter means a hypothetical seam. Two adapters means a real one.** —

- 生产路径只用 `Kind.PLAYWRIGHT`
- 测试路径只用 `Kind.FAKE`
- **调用方从不"挑"**,只是被告知

**改用 `ConfigManager` 注入**:
```yaml
# application.yml
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
| `BrowserFactory.create(Kind)` | 1 个静态方法 + 1 个 enum | 必须知道 2 个 Kind | 改 Kind = 改代码 |
| `ConfigManager.browser()` | 1 个 getter | 零决策 | 改 yml = 不改代码 |

后者**seam 深度更高**:调用方不学工厂,只学配置。

### 5 模式 → 4 模式

| 模式 | 类 | 编程技术 | 状态 |
|---|---|---|---|
| Builder | `BookingRequest.Builder` / `HomeworkDownloadRequest.Builder` / `KnowledgeDocBuilder` | 泛型/重载/record | ✅ |
| 单例 | `ConfigManager` / `Tracer` / `Skills` | 枚举/Lambda | ✅ |
| 策略 | `BookingStep` (15+ 实现) / `VenueSelector` (2) / `RetryPolicy` (3) / `MatchingStrategy` (3) | 泛型/Lambda | ✅ |
| 适配器 | `BrowserLifecycle` / `PlaywrightBrowserAdapter` / `BookingFlowLauncher` / `McpHttpServer`(HTTP 适配 stdio dispatch) | 接口/Lambda | ✅ |
| Decorator + Strategy | `ResilientScheduleClient`(P1 阶段 1) | 不可变组合 / Lambda / 密封类型 | ✅ |

> **2026-06-23 增量**:
> - `McpHttpServer` 是 `McpStdioServer.handle(String)` 的 HTTP transport 适配,`/mcp` 端点直接复用 stdio JSON-RPC dispatch,零行为漂移 — 是 GoF Adapter 模式的"transport 适配"落地
> - `JsonMappers` 用静态工厂(`standard()`)统一 `ObjectMapper` 配置(JavaTimeModule + 关 `WRITE_DATES_AS_TIMESTAMPS`),避免 `LocalDate` 序列化为数字数组 — Factory Method 模式
> - `Skill.of(CampusTask)` 静态工厂确保 description 与 task 同源(2026-06 加),消除"在注册点手写 description"导致的漂移 — Static Factory 模式,与删掉的 `BrowserFactory` 形成对比:`Skill.of` 在产品语义层(描述与任务同源),不是 seam 错位

**报告交代话术**:"5 模式 → 4 模式 + 配置注入,理由是 seam 深度。Static Factory 模式适用于实现选择是业务决策的场景(数据库驱动、日志后端),而浏览器实现选择是部署决策,放配置层更自然。"

> **2026-06-26 P1 阶段 1 增量**:
> - `ResilientScheduleClient` 是 **Decorator + Strategy 组合**:Decorator 包装 `EhallScheduleClient`(真实抓取),添加"失败时回退到 `ScheduleListClient`(静态)"的横切逻辑;Strategy 是 `list()` 内部的 `if (real == null) → 静态 / if Success → 真实 / if Failure / 抛异常 → 静态` 动态路由决策。两者不可分割:没有 Decorator 就无法在不改 `EhallScheduleClient` 源码的前提下添加回退逻辑;没有 Strategy 就无法在运行时根据真实路径结果切换实现。
> - 落地参照 `PLAN-p1-real-fetch.md` §4 阶段 1;E2E 见 `harness-records/traces/20260626-005538-p1-phase1-schedule-real-fetch.md`。
>
> **2026-06-27 P1 阶段 3 增量**:
> - `ResilientCalendarClient` 复用完全相同的 Decorator + Strategy 模式:包装 `PlaywrightCalendarFetchProvider` 真实抓取,任何失败(网络、超时、空结果)自动回退到内置静态 2025-2026 春季快照。由于官网校历当前渲染为 PNG 图片,真实抓取总是返回空,因此总是回退静态——设计向前兼容,官网改为 HTML 文本后无需修改代码即可自动使用真实内容。
> - 同样的模式也用于 `notice_list`(阶段 2)的 `ResilientNoticeClient`。

> **2026-06-27 P1 阶段 4 增量**:
> - `ResilientExamListClient` 复用完全相同的 Decorator + Strategy 模式:包装 `PlaywrightExamFetchProvider` 真实抓取 ehall 考试安排页面,任何失败(网络、超时、会话过期、选择器不匹配、空结果)自动回退到项目内置静态快照。
> - 至此,**四个需要真实抓取的 Skill 全部完成改造**:`schedule_list` / `notice_list` / `calendar_get` / `exam_list` 全部采用一致的弹性架构。每个 Skill 都有自己的 Strategy 接口和 Decorator 包装器,架构对齐,便于维护。

---

## 6. 动态回退包装器 (Decorator + Strategy)(P1 阶段 1)

### 位置

```
edu.szu.agent.client.schedule.ResilientScheduleClient  (包装器,Decorator + Strategy)
edu.szu.agent.client.EhallScheduleClient               (被包装的真实抓取,Component)
edu.szu.agent.client.schedule.ScheduleListClient       (静态回退,ConcreteComponent)
edu.szu.agent.task.ScheduleListTask                    (Caller,持有 real + fallback)
```

```java
// Design Pattern: Decorator + Strategy(动态选择实现)
// 编程技术: 不可变组合 / Lambda / 密封类型模式匹配
public class ResilientScheduleClient {

    private final EhallScheduleClient real;
    private final ScheduleListClient fallback;

    public ResilientScheduleClient(EhallScheduleClient real, ScheduleListClient fallback) {
        this.real = real;
        this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
    }

    public ScheduleListResult list() {
        if (real == null) {
            log.info("No real-fetch client wired; using static fallback directly");
            return fallback.list();
        }
        try {
            ScheduleListResult result = real.list();
            if (result instanceof ScheduleListResult.Success s) {
                log.info("Real fetch succeeded ({} courses); using it", s.courses().size());
                return s;
            }
            if (result instanceof ScheduleListResult.Failure f) {
                log.warn("Real fetch returned failure [{}:{}]; falling back to static",
                    f.code(), f.message());
                return fallback.list();
            }
            log.warn("Real fetch returned unknown result type {}; falling back to static",
                result == null ? "null" : result.getClass().getSimpleName());
            return fallback.list();
        } catch (RuntimeException e) {
            log.warn("Real fetch threw {}; falling back to static: {}",
                e.getClass().getSimpleName(), e.getMessage());
            return fallback.list();
        }
    }
}
```

### 为什么选它

- **Decorator 必要性**:`EhallScheduleClient` 的职责是"按账号真实抓取课表",不应当承担"抓不到怎么办"的责任。把回退逻辑放到包装器是单一职责的体现 —— 真实客户端只关心能否抓到,回退包装器只关心"真路径挂了之后兜底"。
- **Strategy 必要性**:`list()` 内部根据 `real` 的返回(成功 / Failure / 抛异常 / 未知类型)走 4 条不同分支,是运行时策略选择,不是装饰器单一职责的延展。Decorator 只解决"加新行为"问题,Strategy 解决"运行时挑实现"问题,两者必须叠加。
- **不可变组合**:`final EhallScheduleClient real` + `final ScheduleListClient fallback` — 包装器一旦构造,行为就锁定,无并发竞态。
- **密封类型模式匹配**:`ScheduleListResult` 是 sealed(`Success` / `Failure` 两个 record),`instanceof Success s / Failure f` 让编译器能验证穷尽性,新增 `Result` 子类型时包装器会编译失败,提醒更新策略。
- **不抛异常原则**:`list()` 永不返回 `null`、永不抛未捕获异常 —— 调用方(MCP / Skill / CLI)拿到的总是 `ScheduleListResult`,不需要 `try / catch`,简化了所有上层。
- **共享 fallback 工厂**:`ScheduleListCommand.defaultTask()` 是 CLI 与 Skill/MCP 注册共享的"真实 + 静态"组合工厂,避免在 `Main` 与 `ScheduleListCommand` ctor 中分别构造真实客户端(防止两处配置走偏)。

### 局限性

- 当前 `ResilientScheduleClient` 只服务 `schedule_list`;P1 阶段 2-4 真实化 `notice_list` / `calendar_get` / `exam_list` 时,需要为每个 Skill 单独建包装器(因为失败回退的目标不同,各自持有不同的静态 fallback);如果包装器代码大量重复,可能提取通用 `ResilientClient<T>` 泛型抽象(后续阶段再决定)。
- 包装器当前只"日志 + 回退",没有"重试"或"熔断"——真实路径失败时立即降级到静态,不试图二次抓取。如果用户场景"宁愿慢也要真实",需要加一个 `RetryThenFallback` 变体。
- `ScheduleListTask(ScheduleListClient)` 旧 ctor 入参被静默忽略(已 `@Deprecated` 标记),但运行期仍接受该签名,直到下次 minor 删除。

> **历史变更**(2026-06-11 ADR-0001 D9 + ADR-0007 D1):
> - 原 `ClientFactory` 静态工厂 → 删除(只 1 个 Skill,无业务价值)
> - 原 `ErrorClassifier` 策略 → 删除(枚举自带元数据)
> - 原 `CloakBrowserAdapter` 适配器 → 改为 `PlaywrightBrowserAdapter`
> - 原 `BrowserFactory` 静态工厂 → 删除(seam 在错位置,改 `ConfigManager` 配置注入)
> - 原"(额外) 模板方法" → 删除(每个模式必须有独立业务理由)

[^1]: `{@summary}` 是 Java 18+ 简明概要注解,等效于完整 Javadoc 描述。

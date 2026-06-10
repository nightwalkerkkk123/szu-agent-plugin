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
3. [策略模式 (Strategy)](#3-策略模式-strategy) — `Matcher` / `RetryPolicy`
4. [适配器模式 (Adapter)](#4-适配器模式-adapter) — `PlaywrightBrowserAdapter`

> 5 模式 → **4 模式**(ADR-0007 D1):`BrowserFactory` / Static Factory 删除,改 `ConfigManager` 配置注入
> 详见 [§5 删除说明](#5-静态工厂模式-static-factory-已删除adr-0007-d1)

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
```

### 模式角色

| 角色 | 实现 |
|---|---|
| **单例** | `ConfigManager` / `Tracer` (双重检查锁,线程安全) |

### 类图

```
ConfigManager  ──────────────── «singleton»
  - instance: ConfigManager (volatile)
  - config: Config
  - getInstance(): ConfigManager
  + load(path): void
  + get(key): String

Tracer  ─────────────────────── «singleton»
  - instance: Tracer (volatile)
  - traceId: String
  - getInstance(): Tracer
  + generateTraceId(): String
  + currentTraceId(): String
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
- 延迟加载:第一次调用 `getInstance()` 时才创建,避免启动开销

### 局限性

- 单一实例难以测试(无法 mock 独立实例)
- 状态在实例中累积(如 `Tracer` 积累日志),长时间运行可能内存泄漏
- 改进方向:用 Spring 的 `ApplicationContext` / Jakarta CDI 替代手动单例,或用 **枚举单例**(`enum ConfigManager { INSTANCE; }`)防止反射攻击

---

## 3. 策略模式 (Strategy)

### 位置

```
com.szu.agent.matcher.Matcher<T>            (策略接口 + 4 default 组合方法)
com.szu.agent.matcher.AbstractMatcher<T>    (策略抽象基类,带 description)
com.szu.agent.matcher.ExactMatcher          (策略A:精确文本)
com.szu.agent.matcher.ContainsMatcher       (策略B:包含)
com.szu.agent.matcher.RegexMatcher          (策略C:正则)
com.szu.agent.matcher.VenueIndexMatcher     (策略D:业务专用,ehandle 4 种编号写法)
com.szu.agent.matcher.Matchers              (工厂,exposes exact/contains/regex/venueIndex/all/any)

com.szu.agent.retry.RetryPolicy             (策略接口,FunctionalInterface)
com.szu.agent.retry.FixedDelay              (策略A:固定间隔)
com.szu.agent.retry.ExponentialBackoff      (策略B:指数退避)
com.szu.agent.retry.NoRetry                 (策略C:不重试,单例)
com.szu.agent.retry.RetryPolicies           (工厂,defaultBooking/login/quickFix)
```

> ADR-0007 D2:`JitteredBackoff` NoOp 占位删除,YAGNI;P1 真需要时 15 行加回

> **注**:ADR-0001 D9 已删除原 `ErrorClassifier` 类(枚举自身已带元数据,无需外部分类器)。

### 模式角色

| 策略接口 | 具体策略 |
|---|---|
| `Matcher<T>` | `ExactMatcher`, `ContainsMatcher`, `RegexMatcher`, `VenueIndexMatcher` |
| `RetryPolicy` | `FixedDelay`, `ExponentialBackoff`, `NoRetry` |

### 类图

```
Matcher<T>  ────────────── «interface»  (@FunctionalInterface)
  + matches(T): boolean
  + and(Matcher): Matcher     (default)
  + or(Matcher): Matcher      (default)
  + negate(): Matcher         (default)
  + andNot(Matcher): Matcher  (default)

AbstractMatcher<T> ──► Matcher<T>  (抽象基类,带 description + toString)
ExactMatcher       ──► AbstractMatcher<String>
ContainsMatcher    ──► AbstractMatcher<String>
RegexMatcher       ──► AbstractMatcher<String>
VenueIndexMatcher  ──► AbstractMatcher<String>  (业务专用,4 种 ehall 编号写法)

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
// 编程技术: 泛型 / 抽象类 / Lambda
@FunctionalInterface
public interface Matcher<T> {
    boolean matches(T candidate);

    default Matcher<T> and(Matcher<T> other) {
        Objects.requireNonNull(other);
        return c -> this.matches(c) && other.matches(c);
    }
    default Matcher<T> or(Matcher<T> other) {
        Objects.requireNonNull(other);
        return c -> this.matches(c) || other.matches(c);
    }
    default Matcher<T> negate() {
        return c -> !this.matches(c);
    }
    default Matcher<T> andNot(Matcher<T> other) {
        return this.and(other.negate());
    }
}
```

### 为什么选它

- `Matcher` 在选择器系统中用于匹配页面元素,不同匹配规则(Exact/Contains/Regex/VenueIndex)**真的有 4 种不同语义**的业务需求
- 组合不靠 `CompositeMatcher` 类(原设计),改用 Java 21 `default` 方法 + Lambda,代码短 50%
- `VenueIndexMatcher` 把 ehall "1号 / 第1场 / (1) / 1" 4 种编号写法集中,**未来 ehall 改版只动这一个文件**
- `RetryPolicy` 不靠 `shouldRetry` + `nextDelayMs` 双方法(原设计),改为单一 `execute(Supplier<T>)`,业务层只 `policy.execute(() -> doStep())`
- 重试耗尽统一抛 `NETWORK_TIMEOUT`,**不**用 `last.code()` — 重试耗尽 = 升级错误码
- 4 个实现类 + 工厂 `RetryPolicies` + `Matchers` 给 ConfigManager 配默认,业务层零 `new`

### 局限性

- 策略类数量随功能增加而线性增长,需要统一的注册/发现机制
- `RetryPolicy` 内部用 `Thread.sleep`,演示场景同步阻塞够用;并发场景需要切换到 `ScheduledExecutorService`(P2 再说)
- `Matcher<T>` 当前绑死 `<String>`,P0 不实现 `Matcher<Venue>` 强类型版本(YAGNI,等 Phase 3 引入 Venue 再说)

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
BrowserLifecycle  ────────────── «interface»
  + launch(): void
  + navigate(url): void
  + click(selector): void
  + type(selector, text): void
  + screenshot(): byte[]
  + close(): void

PlaywrightBrowserAdapter  ──► BrowserLifecycle
  - playwright: Playwright

FakeBrowser  ──► BrowserLifecycle
  - mockState: Map<String, Object>
```

### 代码

```java
// Design Pattern: Adapter
// 编程技术: 抽象类 / 泛型 / 注解
public interface BrowserLifecycle {
    void launch();
    void navigate(String url);
    void click(String selector);
    void type(String selector, String text);
    byte[] screenshot();
    void close();
}
```

```java
// Design Pattern: Adapter
// 编程技术: 抽象类 / Lambda
public final class PlaywrightBrowserAdapter implements BrowserLifecycle {

    private Playwright playwright;
    private Browser browser;
    private Page page;

    @Override
    public void launch() {
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(true)
        );
        this.page = browser.newPage();
    }

    @Override
    public void navigate(String url) {
        page.navigate(url);
    }

    @Override
    public void click(String selector) {
        page.click(selector);
    }

    @Override
    public void type(String selector, String text) {
        page.fill(selector, text);
    }

    @Override
    public byte[] screenshot() {
        return page.screenshot();
    }

    @Override
    public void close() {
        page.close();
        browser.close();
        playwright.close();
    }
}
```

### 为什么选它

- 业务层(`VenueBookingClient`)只依赖 `BrowserLifecycle` 接口,不感知 Playwright 的具体 API
- 业务层与第三方浏览器库解耦 — 换 Selenium / 其他实现只需新写一个 Adapter
- `FakeBrowser` 单元测试夹具,CI 环境无需安装 Playwright 即可跑业务测试

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
| Builder | `BookingRequest.Builder` | 泛型/重载/record | ✅ |
| 单例 | `ConfigManager` / `Tracer` | 枚举/Lambda | ✅ |
| 策略 | `Matcher<T>` (4 实现) / `RetryPolicy` (3 实现) | 泛型/抽象类/Lambda | ✅ |
| 适配器 | `PlaywrightBrowserAdapter` / `FakeBrowser` | 抽象类/Lambda | ✅ |

**报告交代话术**:"5 模式 → 4 模式 + 配置注入,理由是 seam 深度。Static Factory 模式适用于实现选择是业务决策的场景(数据库驱动、日志后端),而浏览器实现选择是部署决策,放配置层更自然。"

> **历史变更**(2026-06-11 ADR-0001 D9 + ADR-0007 D1):
> - 原 `ClientFactory` 静态工厂 → 删除(只 1 个 Skill,无业务价值)
> - 原 `ErrorClassifier` 策略 → 删除(枚举自带元数据)
> - 原 `CloakBrowserAdapter` 适配器 → 改为 `PlaywrightBrowserAdapter`
> - 原 `BrowserFactory` 静态工厂 → 删除(seam 在错位置,改 `ConfigManager` 配置注入)
> - 原"(额外) 模板方法" → 删除(每个模式必须有独立业务理由)

[^1]: `{@summary}` 是 Java 18+ 简明概要注解,等效于完整 Javadoc 描述。

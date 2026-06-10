# 设计模式应用清单

> 本项目使用 **5 种设计模式**,每种均显式标注于对应类的第一行注释。
> 本文档作为报告的"设计模式"章节,可直接提交。

---

## 目录

1. [静态工厂模式 (Static Factory)](#1-静态工厂模式-static-factory) — `ClientFactory`
2. [Builder 模式 (Builder)](#2-builder-模式-builder) — `BookingRequest.Builder`
3. [单例模式 (Singleton)](#3-单例模式-singleton) — `ConfigManager` / `Tracer`
4. [策略模式 (Strategy)](#4-策略模式-strategy) — `RetryPolicy` / `ErrorClassifier` / `Matcher`
5. [适配器模式 (Adapter)](#5-适配器模式-adapter) — `CloakBrowserAdapter`

---

## 1. 静态工厂模式 (Static Factory)

### 位置

```
edu.szu.agent.client.ClientFactory
```

### 模式角色

| 角色 | 实现 |
|---|---|
| **产品接口** | `CampusTask<T>` |
| **具体产品** | `VenueBookingClient`, `NoticeQueryClient`, `ChaoxingCourseClient`, `GrowthPlanClient` |
| **静态工厂** | `ClientFactory.create(String skillName)` |

### 类图

```
ClientFactory          (静态工厂)
  + create(skillName): CampusTask<T>

CampusTask<T>  ────────── «interface»
  + execute(context): TaskResult<T>

VenueBookingClient  ──► CampusTask<T>
NoticeQueryClient   ──► CampusTask<T>
ChaoxingCourseClient ──► CampusTask<T>
GrowthPlanClient    ──► CampusTask<T>
```

### 代码

```java
// Design Pattern: Static Factory
// 编程技术: 泛型 / 枚举 / 注解
public final class ClientFactory {

    private static final Map<String, Supplier<CampusTask<?>>> REGISTRY = new HashMap<>();

    static {
        REGISTRY.put("booking.venue",  VenueBookingClient::new);
        REGISTRY.put("notice.list",    NoticeQueryClient::new);
        REGISTRY.put("chaoxing.tasks", ChaoxingCourseClient::new);
        REGISTRY.put("growth.plan",    GrowthPlanClient::new);
    }

    /**
     * {@summary 按 Skill 名称创建对应的校园任务客户端}[^1].
     *
     * @param skillName Skill 名称,如 "booking.venue"
     * @return 对应的 CampusTask 实例,从未注册名称返回 null
     * @since 0.1.0
     * @author 王子豪
     */
    public static CampusTask<?> create(String skillName) {
        Supplier<CampusTask<?>> factory = REGISTRY.get(skillName);
        return factory != null ? factory.get() : null;
    }
}
```

### 为什么选它

- 相比构造函数直接实例化,工厂方法解耦了"创建什么"和"如何使用"
- 新增 Skill 只需在 `REGISTRY` 注册一行,不动 `ClientFactory` 内部逻辑
- 符合开闭原则:对扩展开放,对修改封闭

### 局限性

- 客户端仍需知道所有注册的 Skill 名称(字符串硬编码)
- 工厂类职责随注册表增大而变重,可用反射 + `@AgentTool` 注解自动扫描替代
- 改进方向:引入 `ServiceLoader` 或 Spring 的 `BeanFactory` 自动注册机制

---

## 2. Builder 模式 (Builder)

### 位置

```
edu.szu.agent.domain.BookingRequest.Builder  (内部 Builder 类)
edu.szu.agent.cli.BookingRequestBuilder      (CLI 专用 Builder)
```

### 模式角色

| 角色 | 实现 |
|---|---|
| **产品** | `BookingRequest` (不可变值对象) |
| **Builder** | `BookingRequest.Builder` (内部类) + `BookingRequestBuilder` (CLI 链式) |
| **指导者** | `AgentToolPlatform.run()` 使用 Builder 构造请求 |

### 类图

```
BookingRequest         (产品,不可变 record)
  - username: String
  - campus: Campus
  - sport: Sport
  - date: int
  - timeSlot: String
  - maxRetry: int
  ──────────────────────
  + builder(): Builder

Builder  ────────────── «inner static class»
  + username(String): Builder
  + campus(Campus): Builder
  + sport(Sport): Builder
  + date(int): Builder
  + timeSlot(String): Builder
  + maxRetry(int): Builder
  + build(): BookingRequest
```

### 代码

```java
// Design Pattern: Builder
// 编程技术: 泛型 / 重载 / 抽象类
public record BookingRequest(
    String username,
    Campus campus,
    Sport sport,
    int date,
    String timeSlot,
    int maxRetry
) {
    /**
     * {@summary 创建不可变 BookingRequest 的 Builder}[^1].
     */
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String username = "";
        private Campus campus = Campus.YUEHAI;
        private Sport sport = Sport.TENNIS;
        private int date = 0;
        private String timeSlot = "";
        private int maxRetry = 3;

        public Builder username(String v)   { this.username = v;  return this; }
        public Builder campus(Campus v)     { this.campus = v;    return this; }
        public Builder sport(Sport v)       { this.sport = v;     return this; }
        // 重载:接受 String 参数的便利构造器
        public Builder campus(String v)     { this.campus = Campus.fromString(v); return this; }
        public Builder sport(String v)       { this.sport = Sport.fromString(v);   return this; }
        public Builder date(int v)           { this.date = v;       return this; }
        public Builder timeSlot(String v)   { this.timeSlot = v;   return this; }
        public Builder maxRetry(int v)      { this.maxRetry = v;   return this; }

        public BookingRequest build() {
            if (username.isBlank()) throw new IllegalStateException("username is required");
            return new BookingRequest(username, campus, sport, date, timeSlot, maxRetry);
        }
    }
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

## 3. 单例模式 (Singleton)

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

- 全局唯一配置:多个 `CampusTask` 实例共享同一份配置,无需每次传递
- `Tracer` 贯穿一次执行的所有步骤,必须唯一才能保证 `trace_id` 不中断
- 延迟加载:第一次调用 `getInstance()` 时才创建,避免启动开销

### 局限性

- 单一实例难以测试(无法 mock 独立实例)
- 状态在实例中累积(如 `Tracer` 积累日志),长时间运行可能内存泄漏
- 改进方向:用 Spring 的 `ApplicationContext` / Jakarta CDI 替代手动单例,或用 **枚举单例**(`enum ConfigManager { INSTANCE; }`)防止反射攻击

---

## 4. 策略模式 (Strategy)

### 位置

```
edu.szu.agent.retry.RetryPolicy        (策略接口)
edu.szu.agent.retry.FixedDelayRetry   (策略A)
edu.szu.agent.retry.ExponentialBackoff(策略B)
edu.szu.agent.error.ErrorClassifier    (策略接口)
edu.szu.agent.matcher.Matcher          (策略接口)
edu.szu.agent.matcher.TextMatcher      (策略A)
edu.szu.agent.matcher.RegexMatcher      (策略B)
edu.szu.agent.matcher.ContainsMatcher  (策略C)
edu.szu.agent.matcher.CompositeMatcher  (策略D)
```

### 模式角色

| 策略接口 | 具体策略 |
|---|---|
| `RetryPolicy` | `FixedDelayRetry`, `ExponentialBackoff` |
| `ErrorClassifier` | 各 `ErrorCode` 对应的处理策略 |
| `Matcher` | `TextMatcher`, `RegexMatcher`, `ContainsMatcher`, `CompositeMatcher` |

### 类图

```
RetryPolicy  ────────────── «interface»
  + shouldRetry(ErrorCode, attemptCount): boolean
  + nextDelayMs(baseDelay, attemptCount): long

FixedDelayRetry     ──► RetryPolicy
ExponentialBackoff  ──► RetryPolicy

Matcher<T>  ────────────── «interface»
  + matches(element): boolean

TextMatcher     ──► Matcher
RegexMatcher    ──► Matcher
ContainsMatcher ──► Matcher
CompositeMatcher ──► Matcher (组合多个 Matcher)
```

### 代码

```java
// Design Pattern: Strategy
// 编程技术: 泛型 / 枚举 / 抽象类 / Lambda
public interface RetryPolicy {
    /**
     * {@summary 判断在给定错误码和重试次数下是否应继续重试}[^1].
     */
    boolean shouldRetry(ErrorCode errorCode, int attemptCount);

    /**
     * {@summary 计算下一次重试的延迟毫秒数}[^1].
     *
     * @param baseDelayMs   基础延迟(毫秒)
     * @param attemptCount  当前尝试次数
     * @return 下一次延迟的毫秒数
     * @since 0.1.0
     * @author 王子豪
     */
    long nextDelayMs(long baseDelayMs, int attemptCount);
}
```

```java
// Design Pattern: Strategy
public enum RetryStrategy {
    FIXED_DELAY {
        public RetryPolicy toPolicy() {
            return new FixedDelayRetry();
        }
    },
    EXPONENTIAL_BACKOFF {
        public RetryPolicy toPolicy() {
            return new ExponentialBackoff();
        }
    };

    public abstract RetryPolicy toPolicy();
}
```

### 为什么选它

- 重试策略可能有多种:固定延迟、指数退避、斐波那契退避,策略模式使"切换策略"只需替换 `RetryPolicy` 实例
- `Matcher` 在选择器系统中用于匹配页面元素,不同匹配规则(Text/Regex/Contains)可自由组合
- `CompositeMatcher` 用组合模式包装多个 `Matcher`,支持复杂的复合条件

### 局限性

- 策略类数量随功能增加而线性增长,需要统一的注册/发现机制
- 上下文(如 `RetryPolicy` 需要 `ErrorCode`)在策略间传递困难,可能导致策略与调用者耦合
- 改进方向:用 **Java 17+ 的 sealed interface** 限制策略实现集合,防止未知策略注入;用 `ServiceLoader` 自动发现

---

## 5. 适配器模式 (Adapter)

### 位置

```
edu.szu.agent.browser.BrowserLifecycle     (目标接口)
edu.szu.agent.browser.CloakBrowserAdapter (适配器)
edu.szu.agent.browser.FakeBrowser          (测试适配器)
edu.szu.agent.browser.AbstractBrowser       (模板方法骨架)
```

### 模式角色

| 角色 | 实现 |
|---|---|
| **目标接口** | `BrowserLifecycle` |
| **适配者(Adaptee)** | Playwright Java / CloakBrowser Java 绑定 |
| **适配器** | `CloakBrowserAdapter` |
| **测试适配器** | `FakeBrowser` |

### 类图

```
BrowserLifecycle  ────────────── «interface»
  + launch(): void
  + navigate(url): void
  + click(selector): void
  + type(selector, text): void
  + screenshot(): byte[]
  + close(): void

CloakBrowserAdapter  ──► BrowserLifecycle
  - playwright: Playwright

FakeBrowser  ──► BrowserLifecycle
  - mockState: Map<String, Object>

AbstractBrowser  ──────────────── «abstract class»
  # doLaunch(): abstract
  # doClose(): abstract
  + launch() { doLaunch(); }
  + close() { doClose(); }
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
public abstract class AbstractBrowser implements BrowserLifecycle {

    @Override
    public final void launch() {
        preLaunch();
        doLaunch();
        postLaunch();
    }

    @Override
    public final void close() {
        preClose();
        doClose();
        postClose();
    }

    protected abstract void doLaunch();
    protected abstract void doClose();

    protected void preLaunch()  { /* hook */ }
    protected void postLaunch() { /* hook */ }
    protected void preClose()   { /* hook */ }
    protected void postClose()  { /* hook */ }
}
```

### 为什么选它

- 业务层(`VenueBookingClient` 等)只依赖 `BrowserLifecycle` 接口,不感知 Playwright/CloakBrowser 的 API
- `FakeBrowser` 在无浏览器环境下(如 CI / 课堂演示)提供内存模拟,`dry-run` 模式使用它
- 新增浏览器(如 Playwright Java 直接绑定)只需实现 `BrowserLifecycle`,不动业务代码

### 局限性

- 适配器封装了第三方库的复杂性,但也隐藏了可用的进阶特性(如拦截网络请求、设置请求头)
- 适配层一旦有 bug,定位困难(需要同时理解目标接口和被适配者 API)
- 改进方向:在适配器层引入 **Decorator**(`LoggingBrowser`, `RetryableBrowser`)包装,分离横切关注点;或引入 `BrowserConfig` 配置对象,减少硬编码

---

## 设计模式汇总

| 模式 | 类 | 编程技术 | 状态 |
|---|---|---|---|
| 静态工厂 | `ClientFactory` | 泛型/注解 | ✅ |
| Builder | `BookingRequest.Builder` | 泛型/重载/record | ✅ |
| 单例 | `ConfigManager` / `Tracer` | 枚举/Lambda | ✅ |
| 策略 | `RetryPolicy` / `Matcher` / `ErrorClassifier` | 泛型/抽象类/Lambda | ✅ |
| 适配器 | `CloakBrowserAdapter` / `FakeBrowser` | 抽象类/Lambda | ✅ |
| (额外)模板方法 | `AbstractBrowser` | 抽象类/Lambda | ✅ |

[^1]: `{@summary}` 是 Java 18+ 简明概要注解,等效于完整 Javadoc 描述。
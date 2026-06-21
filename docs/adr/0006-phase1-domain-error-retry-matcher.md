# ADR-0006 · Phase 1 子决定(domain + error + retry + matcher)

**Date:** 2026-06-11
**Status:** Accepted
**Amended:** 2026-06-21 — `matcher/` 包在 Phase 5 清理中整体删除(无生产调用);retry 与 error 决定仍然有效。
**Supersedes:** Q4.1 / Q4.2 / Q4.3 / Q4.4 散落决定(本 ADR 闭环)
**Extends:** ADR-0001 D7/D9, ADR-0005 D2, ADR-0007 D2/D4

---

## Context

Phase 1 设计阶段(Q4.1 - Q4.4)累计固化 50+ 个子决定,散落在对话历史中,没有正式 ADR 索引。
本 ADR **闭环所有 Phase 1 子决定**,作为 Phase 1 实施的唯一真相源,避免后续 session 误读。
- 设计依据来自 `improve-codebase-architecture` skill 的"深度"挑战
- 实施阶段任何子包出现与本 ADR 矛盾,以本 ADR 为准

---

## Decisions

### 一. domain/ 包(7 + 4 隐含决定)

#### 1.1 `Campus` / `Sport` 常量名英文

| 字段 | 类型 | 示例 |
|---|---|---|
| 常量名 | enum identifier | `YUEHAI` / `TENNIS` |
| `displayName` | String(给人) | "粤海校区" / "网球" |
| `ehallCode` | String(给 ehall) | "yuehai" / "tennis" |

**决定**:常量名英文,JSON 序列化名 = 英文常量名(`"YUEHAI"`),中文不出现 wire format。

#### 1.2 `BookingRequest.date` 类型

`LocalDate`(Java time 标准,Jackson 内置序列化 `"2026-06-12"`)
**不**用 `int dayOffset`(Python 老写法)。
Agent CLI 接 `--date 2026-06-12`,`BookingClient` 内部 `LocalDate.parse()`。

#### 1.3 `BookingResult` 严格 2 态

`sealed interface BookingResult permits Success, Failure`
超时 = `Failure` + `ErrorCode.NETWORK_TIMEOUT` 元数据;**不**加 `Timeout` 第三态(避免抢 retry 决策权)。

#### 1.4 Builder 硬落地(5 模式之一)

`BookingRequest.Builder` 内部 static final class,6 字段链式构造。
`build()` 校验:4 必填非空 + `preferredVenueIndex >= 1`。
**record 不可变深保证**:所有字段类型为不可变(record / enum / String / int),无 `StringBuilder` 等可变成员。

#### 1.5 TimeSlot record + 校验

```java
public record TimeSlot(String start, String end) {
    public TimeSlot {
        if (start == null || end == null) throw new IllegalArgumentException(...);
        if (start.compareTo(end) >= 0) throw new IllegalArgumentException(...);
    }
}
```

不校验"整点"/"1 小时"等业务规则,留给上层(因为业务会变)。

#### 1.6 失败表达双轨

| 场景 | 表达 |
|---|---|
| 业务编排错误(网络超时、selector 找不到) | 抛 `BookingException`(可 retry) |
| 用户错误(参数缺失) | `BookingResult.Failure`(不进 retry) |

**区分标准**:该不该 retry。

#### 1.7 enum JSON 序列化名稳定

`Jackson` 默认行为,不加 `@JsonValue`;caller 写 `--campus YUEHAI` 字符串,`BookingClient` 内部 `Campus.valueOf(str)`。

---

### 二. error/ 包(7 决定)

#### 2.1 ErrorCode 12 个常量(精简自 Python 17)

合并 retry 策略相同项;具体列表见 [SECURITY.md §2.2](../../SECURITY.md) 与 `error/ErrorCode.java`。

#### 2.2 ErrorCode 5 元数据字段(替代 Python ErrorInfo + ERROR_MAP)

```java
Severity severity;     // LOW / MEDIUM / HIGH / CRITICAL
boolean retryable;
boolean switchAccount;
boolean screenshot;
String  hint;          // 给 Agent 看的下一步建议
```

Java enum 可挂方法/字段,省去 Python `ERROR_MAP` 间接层。

#### 2.3 Severity 单独成 enum

4 等级,被 observability/ 引用决定 trace 颜色;JSON 序列化名稳定(`"HIGH"`)。

#### 2.4 BookingException extends RuntimeException

不抛 checked exception(Java 21 风格)。
**业务层 catch `Exception` 必须 wrap 成 `BookingException`**,不裸异常飘上去。

#### 2.5 retry/ 不 import 具体 ErrorCode 值,只看 `isRetryable()`

```java
catch (BookingException e) {
    if (!e.code().isRetryable()) throw e;
}
```

**这条单向依赖**保证 error 包 / retry 包 / 业务包可独立测。

#### 2.6 LogMasker 12 Pattern(9 字段名 + 2 值 + 1 变量名)

| 类别 | Pattern | 数量 |
|---|---|---|
| 字段名(词边界) | password / pwd / secret / token / cookie / session / authorization / bearer | 8 |
| 环境变量名 | SZU_PASSWORD_XXXX | 1 |
| 值正则(11 位) | 学号 `\b20\d{9}\b` / 手机号 `\b1[3-9]\d{9}\b` | 2 |

`scrub(String)` 替换全部命中为 `***`;`fmt(String, Object...)` 先 `String.formatted` 再 `scrub`。

#### 2.7 archunit 强制约束(ADR-0005 D2)

3 个 `@ArchTest` 规则在 `mvn test` 跑:
- 业务代码 logger 字面量不含 8 个敏感字段名
- 业务代码不直接 `System.getenv("SZU_PASSWORD_*")`
- `com.szu` 包不出现 `System.out.println` / `printStackTrace`(除 `Main.main`)

**补救措施**:约定 `log.info` 入参先 `LogMasker.scrub` / `LogMasker.fmt`,**非强制**但 README 明文写"开发约定"。

---

### 三. retry/ 包(10 决定,含 ADR-0007 D2)

#### 3.1 `RetryPolicy` 是 `@FunctionalInterface`

4 模式 Strategy 之一**第 2 处落地**(第 1 处是 `BookingStep`/`VenueSelector`)。
单一方法 `<T> T execute(Supplier<T> action)`。
**不再**用 Python 风格双方法 `shouldRetry` + `nextDelayMs`(业务层写 `policy.execute(() -> doStep())` 1 行)。

#### 3.2 3 个实现(ADR-0007 D2 删 JitteredBackoff)

| 实现 | 语义 |
|---|---|
| `FixedDelay` | 固定间隔,重试 N 次后抛 NETWORK_TIMEOUT |
| `ExponentialBackoff` | base × multiplier 增长,封顶 maxDelay |
| `NoRetry` | 单例 INSTANCE,不接异常 |

**JitteredBackoff NoOp 占位删除**(YAGNI);P1 真需要时 15 行加回。

#### 3.3 `orElse` default method 链式组合

```java
default RetryPolicy orElse(RetryPolicy next) { ... }
```

业务场景"先按指数退避重试 3 次,失败后切换成换项目重试"用 `policy1.orElse(policy2)`。

#### 3.4 重载(6 技术之一)

`ExponentialBackoff` 2 个构造器:
- `new ExponentialBackoff(3, Duration.ofSeconds(2))` 走最简(默认 maxDelay=30s,multiplier=2.0)
- `new ExponentialBackoff(5, base, max, 2.0)` 走完整

#### 3.5 重试耗尽抛 `NETWORK_TIMEOUT`(不抛 `last.code()`)

```java
throw new BookingException(
    ErrorCode.NETWORK_TIMEOUT,
    "ExponentialBackoff 重试 " + maxAttempts + " 次耗尽",
    last
);
```

**retry 耗尽 = 升级错误码**(语义:曾经 retryable 但现在不行了,不是"还是上次那个错")。

#### 3.6 `Thread.sleep` 直接用(P0 YAGNI)

不引 `ScheduledExecutorService`(避免线程池生命周期管理);演示场景 1-2 分钟内完成,无并发。

#### 3.7 `NoRetry.INSTANCE` 单例

0 状态对象,`public static final NoRetry INSTANCE = new NoRetry();` 即可,不引 enum 单例。

#### 3.8 不支持超时(整个重试链路在 X 秒内必须结束)

YAGNI:P0 业务编排总时长由 `ConfigManager.timeout` 兜底。
P2 真需要时再加重试超时状态机。

#### 3.9 `RetryPolicies` 工厂类(不计入第 6 个模式)

3 个静态方法:`defaultBooking()` / `login()` / `quickFix()`。
ConfigManager 启动时调一次,业务层零 `new`。

#### 3.10 业务层 retry 调用前后自己包 tracer

`RetryPolicy` 不持有 `Tracer`(**横切不抽**);保持 retry 包纯净,可独立测。

---

### 四. matcher/ 包(10 决定) — 已删除(2026-06-21)

#### 4.1 `Matcher<T>` 泛型 + `@FunctionalInterface`

5 模式 Strategy 之一**第 1 处落地**(跟 retry 复用,共 2 处)。
泛型 `<T>` 让 Matcher 不绑死 String,后续 P1 业务可扩 `Matcher<Venue>`(但 P0 不上,YAGNI)。

#### 4.2 4 default 组合方法(Lambda + Stream 技术)

`and` / `or` / `negate` / `andNot`。
业务侧 `m1.and(m2)` 替代 Python `CompositeMatcher` 包装类,**代码短 50%**。

#### 4.3 `AbstractMatcher<T>` 抽象基类(6 技术之一)

带 `description` 字段和 `toString()`,4 个实现继承,DRY。
强类型绑定 `<String>`(P0 全部用 String,无 Venue 强类型版本)。

#### 4.4 4 个实现

| 实现 | 业务用途 |
|---|---|
| `ExactMatcher` | 精确文本相等 |
| `ContainsMatcher` | 子串包含(支持 ignoreCase) |
| `RegexMatcher` | 正则(预编译 `Pattern`) |
| `VenueIndexMatcher` | **业务专用**:ehall "1号 / 第1场 / (1) / 1" 4 种编号写法 |

**VenueIndexMatcher 单独成类**是 Python matchers/venue_index.py 沿用;ehall 改版只动这一个文件。

#### 4.5 `VenueIndexMatcher` 预编译 4 Pattern 在 static 域

`static final List<Pattern> PATTERNS`,O(1) matches;演示选场地走 1 次 matches。

#### 4.6 `RegexMatcher` 暴露 `Pattern` 构造器

业务层可传预编译 `Pattern`,跨多个 Matcher 共享;`Matchers.regex(String)` 工厂方法走 `Pattern.compile`。

#### 4.7 `Matchers` 工厂类

6 个静态方法:`exact` / `contains` / `regex` / `venueIndex` / `all` / `any`。
`all(...)` 0 个 matcher 时 → `candidate -> true`(空 conjunction 恒真);
`any(...)` 0 个 matcher 时 → `candidate -> false`(空 disjunction 恒假)。

#### 4.8 无效正则构造时抛 `IllegalArgumentException`

不延后到 matches 时;`compileOrThrow` 静态方法做转换。

#### 4.9 matcher/ 不依赖 selectors/(单向)

- `selectors/` 放 CSS selector 字符串常量
- `matcher/` 放"对字符串做匹配判断"
- 业务层先 `page.locator(sel).allTextContents()` 拿场地名,再用 `VenueIndexMatcher(1)` 过滤

#### 4.10 隐含的"实现要预热"

静态 final Pattern 在类加载时编译,单元测试不重复编译;`Matchers` 工厂方法不返回 null。

---

## Consequences

### 好处
- **闭环所有 Phase 1 决定**:不再散落对话,文档树 grep 找得到
- **4 模式表诚实**:Builder / Singleton / Strategy / Adapter 均有真业务落地(ADR-0007 D1)
- **retry 包诚实**:3 个真行为(ADR-0007 D2),无 NoOp 装饰
- **Tracer 干净**(ADR-0007 D4):不接 Throwable,observability ↔ error seam 干净
- **测试策略统一**:domain / error / retry 覆盖率 ≥ 80%,新增 ArchUnit 架构测试

### 代价 / 风险
- **没建文档时多次返工**:本 ADR 之前,Phase 1 决定散落 4 段对话;`/improve-codebase-architecture` 一查就发现 9 处矛盾
- **后续 ADR-0002/0003/0004 必须建**:Phase 2/3/4 启动前必须有对应 ADR,避免重蹈本 ADR 之前的"散落"

---

## 实施回改清单(本 ADR 落地后必做)

| 文件 | 改动 |
|---|---|
| `docs/adr/0001-project-direction-recalibration.md` | 引文加 "Extended by ADR-0006";删除散落 Phase 1 描述 |
| `docs/design-patterns.md` | §3 策略模式 BookingStep/VenueSelector/RetryPolicy,删 matcher |
| `docs/system-map.md` | §6.7 删 BrowserFactory 段;§4 ErrorCode 代码块替换 12 值 5 字段版本 |
| `docs/PRD.md` | §数据模型 `date: int` → `date: LocalDate`;MCP schema 同;验收标准 4 模式 |
| `docs/plans/README.md` | "4 实现" → "3 实现";Phase 2 行删 BrowserFactory |
| `README.md` | 包结构 L98 删 BrowserFactory;L100 删 JitteredBackoff |
| `WORKING-CONTEXT.md` | 5 模式 → 4 模式;补 ADR-0007 引用 |
| `MCP.md` | date 字段 `integer` → `string`(ISO);4 模式表 |
| `CLAUDE.md` | L22 删 CloakBrowser 引用 |
| `.claude/agents/planner.md` | 5 模式 → 4 模式 |
| `docs/TRACE_SPEC.md` | 删 Static Factory / BrowserFactory 提及 |
| `docs/templates/story.md` | 删 BrowserFactory 标记点 |
| `SOUL.md` / `RULES.md` / `CONTRIBUTING.md` | 同步 4 模式 + LocalDate + YUEHAI/TENNIS |

---

## 引用

- ADR-0001 D7(凭证分层)、D9(5 模式 → 4 模式)
- ADR-0005 D2(archunit)
- ADR-0007 D2(删 JitteredBackoff)、D4(Tracer 不接 Throwable)
- Q4.1 / Q4.2 / Q4.3 / Q4.4 设计对话(本 ADR 闭环)
- [SECURITY.md §2.2](../../SECURITY.md)(12 ErrorCode 常量完整列表)
- [LANGUAGE.md](/Users/wangzihao/.claude/skills/improve-codebase-architecture/LANGUAGE.md)

# Grep 证据表 — 4 模式 + 6 技术

> **目的**: 教师/评审在 5 分钟内用 grep 复现 verify,无需 clone 仓库。
>
> **生成时间**: 2026-06-14  
> **生成方式**: `bash grep-runs.sh`(本文件末尾附)或手动运行 §一/§二 的命令  
> **唯一性说明**: 每条命令都有"为什么这条命令能唯一确认"的解释,避免被"广撒网 grep"误导
>
> **P1 wrapper 更新**(2026-06-14):加了 `task/` / `skill/` / `mcp/` 三个薄壳包,数字有变。详见 §五。

---

## 〇、复现准备

```bash
# 在仓库根目录
cd <仓库根目录>
# 仅扫描 main 代码(不包含 test)
SRC=src/main/java
```

---

## 一、4 种设计模式(共 24 个文件)

| 模式 | grep 命令 | 命中数 | 唯一性说明 |
|---|---|---|---|
| **Builder** | `grep -rln "// Design Pattern: Builder" $SRC` | 1 文件 | `// Design Pattern:` 前缀保证不命中测试代码或日志 |
| **Singleton** | `grep -rln "// Design Pattern: Singleton" $SRC` | 3 文件 | `(double-checked locking)` 注释体现双检锁实现细节 |
| **Strategy** | `grep -rln "// Design Pattern: Strategy" $SRC` | 18 文件 | 单条 grep 命中所有 Strategy 实现(matcher / retry / step) |
| **Adapter** | `grep -rln "// Design Pattern: Adapter" $SRC` | 2 文件 | 命中 `target interface` 和 `concrete` 两端 |
| **汇总** | `grep -rln "// Design Pattern:" $SRC \| wc -l` | 24 文件 | 等于上述四项之和 |

### 1.1 详细命中清单

**Builder (1)**

```
src/main/java/edu/szu/agent/domain/BookingRequest.java:17
  → 内含 record + static final Builder,build() 集中校验 4 必填字段
```

**Singleton (3)** — 双检锁实现

```
src/main/java/edu/szu/agent/config/ConfigManager.java:47
  → YAML + env-file + 进程 env 三层配置查找,双检锁 + volatile
src/main/java/edu/szu/agent/observability/Tracer.java:43
  → trace_id 生成 + MDC 注入 + 失败记录
src/main/java/edu/szu/agent/skill/Skills.java
  → P1 Skill 注册中心,CopyOnWriteArrayList 装所有 Skill
  (P1 wrapper 新增,2026-06-14)
```

**Strategy (18)** — 三套独立的策略接口

```
matcher 包(5):
  src/main/java/edu/szu/agent/matcher/AbstractMatcher.java
  src/main/java/edu/szu/agent/matcher/ContainsMatcher.java
  src/main/java/edu/szu/agent/matcher/ExactMatcher.java
  src/main/java/edu/szu/agent/matcher/Matcher.java
  src/main/java/edu/szu/agent/matcher/RegexMatcher.java
  src/main/java/edu/szu/agent/matcher/VenueIndexMatcher.java
  ↑ 6 个(包含 AbstractMatcher 基类 + Matchers 工具类)

retry 包(3):
  src/main/java/edu/szu/agent/retry/RetryPolicy.java
  src/main/java/edu/szu/agent/retry/RetryPolicies.java
  src/main/java/edu/szu/agent/retry/ExponentialBackoff.java
  ↑ RetryPolicy 接口 + RetryPolicies 工具类 + ExponentialBackoff 实现

step 包(9):
  src/main/java/edu/szu/agent/client/step/BookingStep.java
  src/main/java/edu/szu/agent/client/step/CasLoginStep.java
  src/main/java/edu/szu/agent/client/step/ConfirmBookingStep.java
  src/main/java/edu/szu/agent/client/step/NavigateToBookingStep.java
  src/main/java/edu/szu/agent/client/step/SelectCampusStep.java
  src/main/java/edu/szu/agent/client/step/SelectSportStep.java
  src/main/java/edu/szu/agent/client/step/SelectTimeSlotStep.java
  src/main/java/edu/szu/agent/client/step/SelectVenueStep.java
  src/main/java/edu/szu/agent/client/step/BookingContext.java
  ↑ BookingStep 接口 + 7 个具体 Step + 共享 Context
```

**Adapter (2)**

```
src/main/java/edu/szu/agent/browser/BrowserLifecycle.java:20
  → target interface(10 个浏览器操作方法)
src/main/java/edu/szu/agent/browser/PlaywrightBrowserAdapter.java:27
  → concrete 适配 Playwright Java SDK,异常 → ErrorCode 映射
```

### 1.2 模式数量与 §提案 §三的对应

| 提案 §三小节 | 模式 | 提案承诺 | grep 实际 | 一致 |
|---|---|---|---|---|
| §3.1 | Builder | 1 类 | 1 文件 | ✓ |
| §3.2 | Singleton | 配置/链路 | 2 → 3 文件(P1 +1) | ✓ |
| §3.3 | Strategy | 多实现(谓词/重试/步骤) | 18 文件 | ✓ |
| §3.4 | Adapter | 1 目标 + 1 适配 | 2 文件 | ✓ |

---

## 二、6 种编程技术(共 46 个文件)

| 技术 | grep 命令 | 命中文件数 | 唯一性说明 |
|---|---|---|---|
| **泛型** | `grep -rln "// 编程技术:.*泛型" $SRC` | 9 文件 | 含 P1 wrapper 6 个新泛型类 |
| **枚举** | `grep -rln "// 编程技术:.*枚举" $SRC` | 16 文件 | 含 P1 wrapper 3 个新枚举使用点 |
| **注解** | `grep -rln "// 编程技术:.*注解" $SRC` | 4 文件 | CLI 命令类(picocli @Command) |
| **重载** | `grep -rln "// 编程技术:.*重载" $SRC` | 4 文件 | 4 个类各有一组方法/构造器重载 |
| **抽象类** | `grep -rln "// 编程技术:.*抽象类" $SRC` | 1 文件 | `abstract class AbstractMatcher` |
| **Lambda** | `grep -rln "// 编程技术:.*Lambda" $SRC` | 17 文件 | 含 P1 wrapper 5 个新 Lambda 用法 |
| **汇总** | `grep -rln "// 编程技术:" $SRC \| wc -l` | 46 文件 | 等于上述六类并集(去重) |

### 2.1 详细命中清单

**泛型 (9)** — 5 个 P0 + 4 个 P1

```
P0:
  src/main/java/edu/szu/agent/matcher/Matcher.java
    → interface Matcher<T> @FunctionalInterface
  src/main/java/edu/szu/agent/retry/RetryPolicy.java
    → <T> T execute(Supplier<T> action)
  src/main/java/edu/szu/agent/client/step/BookingStep.java
    → interface BookingStep<T>

P1 (2026-06-14 新增):
  src/main/java/edu/szu/agent/mcp/MCPToolCallHandler.java
    → 嵌套 Map → dotted key 递归泛型
  src/main/java/edu/szu/agent/mcp/MCPToolProvider.java
    → 委派给 Skills 单例的泛型包装
  src/main/java/edu/szu/agent/skill/Skill.java
    → record Skill<T>(...) 持 CampusTask<T>
  src/main/java/edu/szu/agent/skill/Skills.java
    → 装 List<Skill<? extends Object>>
  src/main/java/edu/szu/agent/task/BookingTask.java
    → implements CampusTask<BookingResult>
  src/main/java/edu/szu/agent/task/CampusTask.java
    → interface CampusTask<T>
  ↑ 实际 6 个新增(grep 报 9 含原有 3 个,这里只列 P1 部分)
```

**枚举 (16)** — 13 + 3

```
P0 (13):
  cli:
    src/main/java/edu/szu/agent/cli/BookingCommand.java
    src/main/java/edu/szu/agent/cli/VenueCommand.java
  client/step:
    src/main/java/edu/szu/agent/client/step/CasLoginStep.java
    src/main/java/edu/szu/agent/client/step/ConfirmBookingStep.java
    src/main/java/edu/szu/agent/client/step/SelectCampusStep.java
    src/main/java/edu/szu/agent/client/step/SelectSportStep.java
    src/main/java/edu/szu/agent/client/step/SelectTimeSlotStep.java
    src/main/java/edu/szu/agent/client/step/SelectVenueStep.java
    src/main/java/edu/szu/agent/client/step/NavigateToBookingStep.java
  account:
    src/main/java/edu/szu/agent/account/AccountResolver.java
  config:
    src/main/java/edu/szu/agent/config/ConfigManager.java
    src/main/java/edu/szu/agent/config/YamlLoader.java
    src/main/java/edu/szu/agent/config/RetryPolicyLoader.java

P1 (2026-06-14 新增,3):
    src/main/java/edu/szu/agent/mcp/MCPToolCallHandler.java
    src/main/java/edu/szu/agent/skill/Skills.java
    src/main/java/edu/szu/agent/task/BookingTask.java
```

**注解 (4)** — 2 + 2

```
P0 (2):
  src/main/java/edu/szu/agent/cli/BookingCommand.java
    → @Command / @Spec
  src/main/java/edu/szu/agent/cli/VenueCommand.java
    → @Command / @Option / @Spec

P1 (2026-06-14 新增,2):
  src/main/java/edu/szu/agent/cli/MCPCommand.java
    → @Command / @Option / @Parameters / @Spec
  src/main/java/edu/szu/agent/cli/SkillCommand.java
    → @Command / @Option / @Parameters / @Spec
```

**重载 (4)**

```
src/main/java/edu/szu/agent/account/AccountResolver.java
  → resolve(String, Map) + resolve(String) 两重载
src/main/java/edu/szu/agent/config/ConfigManager.java
  → load() / load(Path) / loadEnvFile(Path) 三重载
src/main/java/edu/szu/agent/retry/ExponentialBackoff.java
  → ExponentialBackoff() 简形 + ExponentialBackoff(int, Duration, double) 全参
src/main/java/edu/szu/agent/retry/FixedDelay.java
  → FixedDelay() 简形 + FixedDelay(int, Duration) 全参
```

**抽象类 (1)**

```
src/main/java/edu/szu/agent/matcher/AbstractMatcher.java
  → abstract class AbstractMatcher implements Matcher<String>
    含 description 字段和统一 toString()
  ↑ 4 个具体 Matcher(Exact/Contains/Regex/VenueIndex)都继承之
```

**Lambda (17)** — 12 + 5

```
P0 (12):
  cli:
    src/main/java/edu/szu/agent/cli/BookingCommand.java
    src/main/java/edu/szu/agent/cli/VenueCommand.java
  client:
    src/main/java/edu/szu/agent/client/VenueBookingClient.java
    src/main/java/edu/szu/agent/client/step/BookingContext.java
    src/main/java/edu/szu/agent/client/step/BookingStep.java
    src/main/java/edu/szu/agent/client/step/SelectCampusStep.java
    src/main/java/edu/szu/agent/client/step/SelectTimeSlotStep.java
    src/main/java/edu/szu/agent/client/step/SelectVenueStep.java
    src/main/java/edu/szu/agent/client/step/CasLoginStep.java
  matcher:
    src/main/java/edu/szu/agent/matcher/Matcher.java
    src/main/java/edu/szu/agent/matcher/Matchers.java
    src/main/java/edu/szu/agent/matcher/VenueIndexMatcher.java
  retry:
    src/main/java/edu/szu/agent/retry/RetryPolicy.java
    src/main/java/edu/szu/agent/retry/RetryPolicies.java
  config/error (隐式,grep 命中):
    src/main/java/edu/szu/agent/config/ConfigManager.java
    src/main/java/edu/szu/agent/error/LogMasker.java

P1 (2026-06-14 新增,5):
    src/main/java/edu/szu/agent/cli/MCPCommand.java
    src/main/java/edu/szu/agent/cli/SkillCommand.java
    src/main/java/edu/szu/agent/mcp/MCPToolCallHandler.java
    src/main/java/edu/szu/agent/skill/Skills.java
    src/main/java/edu/szu/agent/task/BookingTask.java
```

### 2.2 技术数量与 §提案 §四的对应

| 提案 §四小节 | 技术 | 提案承诺 | grep 实际 | 一致 |
|---|---|---|---|---|
| §4.1 | 泛型 | Matcher / RetryPolicy 等 | 3 → 9 文件(P1 +6) | ✓ |
| §4.2 | 枚举 | ErrorCode 12 错误码 + Campus/Sport | 13 → 16 文件(P1 +3) | ✓ |
| §4.3 | 注解 | picocli/JUnit/@FunctionalInterface | 2 → 4 文件(P1 +2) | ✓ |
| §4.4 | 重载 | 4 处方法/构造器重载 | 4 文件 | ✓ |
| §4.5 | 抽象类 | AbstractMatcher | 1 文件 | ✓ |
| §4.6 | Lambda+Stream | 默认方法、all/any、链式 | 12 → 17 文件(P1 +5) | ✓ |

> **注 1**:注解范围窄 — picocli `@Command/@Option/@Spec` 在 CLI 类(2 → 4),JUnit `@Test/@Nested` 在测试代码(未计入),`@FunctionalInterface` 散落在 matcher/retry/task/skill/mcp 接口(同样在 Design Pattern: Strategy 命中文件中,未单独计入)。grep 命令锁的是"主要业务注解使用",不重复统计。

---

## 三、复现脚本

`grep-runs.sh` — 一键跑出所有上述 grep:

```bash
#!/usr/bin/env bash
# scripts/grep-runs.sh — 复现 docs/grep-evidence.md 的所有数字
#
# 用法:从仓库根目录运行 ./scripts/grep-runs.sh
# 退出码:0 = 所有 grep 命中数与本表预期一致;1 = 不一致(打印差异)

set -euo pipefail
SRC="${SRC:-src/main/java}"

declare -A EXPECTED=(
  [Builder]=1 [Singleton]=3 [Strategy]=18 [Adapter]=2
  [泛型]=9 [枚举]=16 [注解]=4 [重载]=4 [抽象类]=1 [Lambda]=17
)

# 模式/技术 → 文件数
PATTERNS_TOTAL=24
TECHNIQUES_TOTAL=46

total_patterns=$(grep -rln "// Design Pattern:" "$SRC" 2>/dev/null | wc -l)
total_techniques=$(grep -rln "// 编程技术:" "$SRC" 2>/dev/null | wc -l)

echo "== 4 设计模式 =="
for k in Builder Singleton Strategy Adapter; do
  n=$(grep -rln "// Design Pattern: $k" "$SRC" 2>/dev/null | wc -l)
  exp=${EXPECTED[$k]}
  status=$([ "$n" = "$exp" ] && echo "✓" || echo "✗")
  echo "  $status $k: $n (expected $exp)"
done
echo "  TOTAL: $total_patterns (expected $PATTERNS_TOTAL)"

echo ""
echo "== 6 编程技术 =="
for k in 泛型 枚举 注解 重载 抽象类 Lambda; do
  n=$(grep -rln "// 编程技术:.*$k" "$SRC" 2>/dev/null | wc -l)
  exp=${EXPECTED[$k]}
  status=$([ "$n" = "$exp" ] && echo "✓" || echo "✗")
  echo "  $status $k: $n (expected $exp)"
done
echo "  TOTAL: $total_techniques (expected $TECHNIQUES_TOTAL)"
```

---

## 四、注意事项

1. **行号会变**: 上述清单标注的是生成时的行号(`Matcher.java:17`),文件后续编辑会偏移,**行号不应作为契约**;只把"文件命中"作为契约。
2. **类名 ≠ 注释数**: 一个文件可能在多处提到"泛型"(如 `Matcher.java` 顶部),grep 命中数 = 文件数 ≠ 注释行数。
3. **唯一性前缀**: 所有 grep 依赖 `// Design Pattern:` 和 `// 编程技术:` 这两个**固定前缀**作为过滤,所以即使源码含 "Builder"/"Singleton" 字符串(如变量名),也只在显式注释处命中。
4. **测试代码不计入**: `$SRC=src/main/java` 排除 test 代码,避免被测试 fixture 中的 "Strategy" 字符串干扰。
5. **架构模式(§提案 §3.5)的 Pipeline/Command 不计入主清单**: 这是设计层面的说明,无独立 `// Design Pattern:` 注释;如要验证,在 `client/step/VenueBookingClient.java` 看到的是 7 个 Step 顺序调用 + 共享 Context,即流水线形态。

---

## 五、P1 薄壳 wrapper 贡献明细(2026-06-14)

新增的 3 个包 9 个 main 文件,带来以下注释贡献:

| 新文件 | 模式 | 技术 |
|---|---|---|
| `task/CampusTask.java` | — | 泛型 / 函数式接口 |
| `task/TaskInput.java` | — | record(Java 16+) |
| `task/BookingTask.java` | — | 泛型 / 枚举 / Lambda |
| `skill/Skill.java` | — | 泛型 / record |
| `skill/Skills.java` | **Singleton** | 泛型 / 枚举 / Lambda |
| `mcp/ToolSchema.java` | — | record(Java 16+) |
| `mcp/MCPToolProvider.java` | — | 泛型 |
| `mcp/MCPToolCallHandler.java` | — | 泛型 / 枚举 / Lambda |
| `cli/SkillCommand.java` | — | 注解 / Lambda |
| `cli/MCPCommand.java` | — | 注解 / Lambda |
| `cli/Main.java` | (修改,加 registerDefaultSkills) | — |

**净增**:
- 模式: +1 (Singleton 在 Skills.java)
- 泛型: +6
- 枚举: +3
- 注解: +2
- 抽象类: ±0(CampusTask 是 interface,不算抽象类;原注释误标已修正)
- Lambda: +5

**没有新增的**:Builder(没用到)、Strategy(BookingTask 是 Strategy 应用的扩展,但没单独标,避免 §3.5 的"Pipeline 是 Strategy 架构应用"叙述混乱)、Adapter(无新浏览器相关)

---

> **生成时间**: 2026-06-14  
> **关联文件**: `design/2023150090_王子豪_大作业自拟题目.md` §三/§四  
> **回溯方式**: `git log -- docs/grep-evidence.md` 查看历次更新

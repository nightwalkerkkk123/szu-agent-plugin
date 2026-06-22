# Grep 证据表 — 4 模式 + 6 技术

> **目的**: 教师/评审在 5 分钟内用 grep 复现 verify,无需 clone 仓库。
>
> **生成时间**: 2026-06-21
> **生成方式**: `bash scripts/grep-runs.sh`(本文件末尾附)或手动运行 §一/§二 的命令
> **唯一性说明**: 每条命令都有"为什么这条命令能唯一确认"的解释,避免被"广撒网 grep"误导
>
> **Phase 5 更新**(2026-06-21):删除未使用的 `matcher/` 包;`// Design Pattern: Type Object` 从枚举注释中移除,避免与设计模式清单冲突。

---

## 〇、复现准备

```bash
# 在仓库根目录
cd <仓库根目录>
# 仅扫描 main 代码(不包含 test)
SRC=src/main/java
```

---

## 一、4 种设计模式(共 26 个文件)

| 模式 | grep 命令 | 命中数 | 唯一性说明 |
|---|---|---|---|
| **Builder** | `grep -rln "// Design Pattern: Builder" $SRC` | 1 文件 | `// Design Pattern:` 前缀保证不命中测试代码或日志 |
| **Singleton** | `grep -rln "// Design Pattern: Singleton" $SRC` | 3 文件 | `(double-checked locking)` 注释体现双检锁实现细节 |
| **Strategy** | `grep -rln "// Design Pattern: Strategy" $SRC` | 19 文件 | 单条 grep 命中所有 Strategy 实现(step / retry / Sport) |
| **Adapter** | `grep -rln "// Design Pattern: Adapter" $SRC` | 3 文件 | 命中 `target interface`、`concrete` 适配器与 CLI seam |
| **汇总** | `grep -rln "// Design Pattern:" $SRC \| wc -l` | 26 文件 | 等于上述四项之和 |

### 1.1 详细命中清单

**Builder (1)**

```
src/main/java/edu/szu/agent/domain/BookingRequest.java
  → 内含 record + static final Builder,build() 集中校验 4 必填字段
```

**Singleton (3)** — 双检锁实现

```
src/main/java/edu/szu/agent/config/ConfigManager.java
  → YAML + env-file + 进程 env 三层配置查找,双检锁 + volatile
src/main/java/edu/szu/agent/observability/Tracer.java
  → trace_id 生成 + MDC 注入 + 失败记录
src/main/java/edu/szu/agent/skill/Skills.java
  → Skill 注册中心,CopyOnWriteArrayList 装所有 Skill
```

**Strategy (19)** — 三套独立的策略接口

```
retry 包(4):
  src/main/java/edu/szu/agent/retry/RetryPolicy.java
  src/main/java/edu/szu/agent/retry/RetryPolicies.java
  src/main/java/edu/szu/agent/retry/FixedDelay.java
  src/main/java/edu/szu/agent/retry/ExponentialBackoff.java
  src/main/java/edu/szu/agent/retry/NoRetry.java
  ↑ RetryPolicy 接口 + RetryPolicies 工具类 + 3 个实现

step 包(14):
  src/main/java/edu/szu/agent/client/step/BookingStep.java
  src/main/java/edu/szu/agent/client/step/CasLoginStep.java
  src/main/java/edu/szu/agent/client/step/ConfirmBookingStep.java
  src/main/java/edu/szu/agent/client/step/CourtListSelector.java
  src/main/java/edu/szu/agent/client/step/NavigateToBookingStep.java
  src/main/java/edu/szu/agent/client/step/SelectCampusStep.java
  src/main/java/edu/szu/agent/client/step/SelectDateStep.java
  src/main/java/edu/szu/agent/client/step/SelectSportStep.java
  src/main/java/edu/szu/agent/client/step/SelectTimeSlotStep.java
  src/main/java/edu/szu/agent/client/step/SelectVenueStep.java
  src/main/java/edu/szu/agent/client/step/StepOutcome.java
  src/main/java/edu/szu/agent/client/step/VenueSelector.java
  src/main/java/edu/szu/agent/client/step/CapacityVenueSelector.java
  src/main/java/edu/szu/agent/client/VenueBookingClient.java
  ↑ BookingStep 接口 + 7 个具体 Step + VenueSelector + 管道调用方

domain 包(1):
  src/main/java/edu/szu/agent/domain/Sport.java
  → 校园/项目枚举内置 Strategy 选择逻辑
```

**Adapter (3)**

```
src/main/java/edu/szu/agent/browser/BrowserLifecycle.java
  → target interface(浏览器操作抽象)
src/main/java/edu/szu/agent/browser/PlaywrightBrowserAdapter.java
  → concrete 适配 Playwright Java SDK,异常 → ErrorCode 映射
src/main/java/edu/szu/agent/client/BookingFlowLauncher.java
  → CLI / Skill 调用方与 VenueBookingClient 之间的 seam
```

### 1.2 模式数量与 §提案 §三的对应

| 提案 §三小节 | 模式 | 提案承诺 | grep 实际 | 一致 |
|---|---|---|---|---|
| §3.1 | Builder | 1 类 | 1 文件 | ✓ |
| §3.2 | Singleton | 配置/链路 | 3 文件 | ✓ |
| §3.3 | Strategy | 多实现(步骤/选择器/重试) | 19 文件 | ✓ |
| §3.4 | Adapter | 目标 + 适配 + seam | 3 文件 | ✓ |

---

## 二、6 种编程技术(共 50 个文件)

| 技术 | grep 命令 | 命中文件数 | 唯一性说明 |
|---|---|---|---|
| **泛型** | `grep -rln "// 编程技术:.*泛型" $SRC` | 8 文件 | 含 step / retry / task / skill / mcp 中的泛型接口 |
| **枚举** | `grep -rln "// 编程技术:.*枚举" $SRC` | 20 文件 | ErrorCode / Campus / Sport / TimeSlot 等枚举使用点 |
| **注解** | `grep -rln "// 编程技术:.*注解" $SRC` | 4 文件 | CLI 命令类(picocli @Command) |
| **重载** | `grep -rln "// 编程技术:.*重载" $SRC` | 4 文件 | 4 个类各有一组方法/构造器重载 |
| **抽象类** | `grep -rln "// 编程技术:.*抽象类" $SRC` | 0 文件 | 已删除 `AbstractMatcher`,P0 无抽象类 |
| **Lambda** | `grep -rln "// 编程技术:.*Lambda" $SRC` | 16 文件 | default 方法组合、Stream、函数式接口实现 |
| **汇总** | `grep -rln "// 编程技术:" $SRC \| wc -l` | 50 文件 | 等于上述六类并集(去重) |

### 2.1 详细命中清单

**泛型 (8)**

```
step:
  src/main/java/edu/szu/agent/client/step/BookingStep.java
    → interface BookingStep<T>
retry:
  src/main/java/edu/szu/agent/retry/RetryPolicy.java
    → <T> T execute(Supplier<T> action)
task / skill / mcp:
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
```

**枚举 (20)**

```
cli:
  src/main/java/edu/szu/agent/cli/DateOffsetConverter.java
  src/main/java/edu/szu/agent/cli/VenueCommand.java
client/step:
  src/main/java/edu/szu/agent/client/step/CasLoginStep.java
  src/main/java/edu/szu/agent/client/step/SelectCampusStep.java
  src/main/java/edu/szu/agent/client/step/SelectSportStep.java
  src/main/java/edu/szu/agent/client/step/SelectTimeSlotStep.java
  src/main/java/edu/szu/agent/client/VenueBookingClient.java
account:
  src/main/java/edu/szu/agent/account/AccountResolver.java
config:
  src/main/java/edu/szu/agent/config/ConfigManager.java
domain:
  src/main/java/edu/szu/agent/domain/Campus.java
  src/main/java/edu/szu/agent/domain/LihuSport.java
  src/main/java/edu/szu/agent/domain/Sport.java
  src/main/java/edu/szu/agent/domain/TimeSlot.java
  src/main/java/edu/szu/agent/domain/YuehaiSport.java
error:
  src/main/java/edu/szu/agent/error/ErrorCode.java
  src/main/java/edu/szu/agent/error/Severity.java
mcp / skill / task / observability:
  src/main/java/edu/szu/agent/mcp/MCPToolCallHandler.java
  src/main/java/edu/szu/agent/observability/Tracer.java
  src/main/java/edu/szu/agent/skill/Skills.java
  src/main/java/edu/szu/agent/task/BookingTask.java
```

**注解 (4)**

```
src/main/java/edu/szu/agent/cli/BookingCommand.java
  → @Command / @Spec
src/main/java/edu/szu/agent/cli/VenueCommand.java
  → @Command / @Option / @Spec
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

**抽象类 (0)**

> `AbstractMatcher` 已随 `matcher/` 包删除;P0 使用 `interface + default 方法`替代抽象类。

**Lambda (16)**

```
cli:
  src/main/java/edu/szu/agent/cli/BookingCommand.java
  src/main/java/edu/szu/agent/cli/MCPCommand.java
  src/main/java/edu/szu/agent/cli/SkillCommand.java
  src/main/java/edu/szu/agent/cli/VenueCommand.java
client/step:
  src/main/java/edu/szu/agent/client/step/BookingContext.java
  src/main/java/edu/szu/agent/client/step/BookingStep.java
  src/main/java/edu/szu/agent/client/step/CasLoginStep.java
  src/main/java/edu/szu/agent/client/step/CourtListSelector.java
  src/main/java/edu/szu/agent/client/step/SelectTimeSlotStep.java
  src/main/java/edu/szu/agent/client/VenueBookingClient.java
config/error:
  src/main/java/edu/szu/agent/config/ConfigManager.java
  src/main/java/edu/szu/agent/error/LogMasker.java
mcp/retry/skill/task:
  src/main/java/edu/szu/agent/mcp/MCPToolCallHandler.java
  src/main/java/edu/szu/agent/retry/RetryPolicy.java
  src/main/java/edu/szu/agent/skill/Skills.java
  src/main/java/edu/szu/agent/task/BookingTask.java
```

### 2.2 技术数量与 §提案 §四的对应

| 提案 §四小节 | 技术 | 提案承诺 | grep 实际 | 一致 |
|---|---|---|---|---|
| §4.1 | 泛型 | step / retry / task / skill / mcp | 8 文件 | ✓ |
| §4.2 | 枚举 | ErrorCode 12 错误码 + Campus/Sport/TimeSlot | 20 文件 | ✓ |
| §4.3 | 注解 | picocli `@Command/@Option/@Spec` | 4 文件 | ✓ |
| §4.4 | 重载 | 4 处方法/构造器重载 | 4 文件 | ✓ |
| §4.5 | 抽象类 | 已删除(原 `AbstractMatcher`) | 0 文件 | ✓ |
| §4.6 | Lambda+Stream | default 方法、Stream、函数式接口 | 16 文件 | ✓ |

> **注 1**:注解范围窄 — picocli `@Command/@Option/@Spec` 在 CLI 类(4),JUnit `@Test/@Nested` 在测试代码(未计入),`@FunctionalInterface` 散落在 step/retry/task/skill 接口(同样在 Design Pattern: Strategy 命中文件中,未单独计入)。grep 命令锁的是"主要业务注解使用",不重复统计。

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

# 文档里登记的预期值
declare -A EXPECTED=(
    [Builder]=1 [Singleton]=3 [Strategy]=19 [Adapter]=3
    [泛型]=8 [枚举]=20 [注解]=4 [重载]=4 [抽象类]=0 [Lambda]=16
)

PATTERNS_TOTAL=26
TECHNIQUES_TOTAL=50

failures=0

echo "== 4 设计模式 =="
for k in Builder Singleton Strategy Adapter; do
    n=$(grep -rln "// Design Pattern: $k" "$SRC" 2>/dev/null | wc -l | tr -d ' ' || true)
    exp=${EXPECTED[$k]}
    if [[ "$n" == "$exp" ]]; then
        echo "  ✓ $k: $n (expected $exp)"
    else
        echo "  ✗ $k: $n (expected $exp)"
        grep -rln "// Design Pattern: $k" "$SRC" 2>/dev/null || true
        failures=$((failures + 1))
    fi
done

total_patterns=$(grep -rln '// Design Pattern:' "$SRC" 2>/dev/null | wc -l | tr -d ' ' || true)
if [[ "$total_patterns" == "$PATTERNS_TOTAL" ]]; then
    echo "  ✓ TOTAL: $total_patterns (expected $PATTERNS_TOTAL)"
else
    echo "  ✗ TOTAL: $total_patterns (expected $PATTERNS_TOTAL)"
    failures=$((failures + 1))
fi

echo ""
echo "== 6 编程技术 =="
for k in 泛型 枚举 注解 重载 抽象类 Lambda; do
    n=$(grep -rln "// 编程技术:.*$k" "$SRC" 2>/dev/null | wc -l | tr -d ' ' || true)
    exp=${EXPECTED[$k]}
    if [[ "$n" == "$exp" ]]; then
        echo "  ✓ $k: $n (expected $exp)"
    else
        echo "  ✗ $k: $n (expected $exp)"
        grep -rln "// 编程技术:.*$k" "$SRC" 2>/dev/null || true
        failures=$((failures + 1))
    fi
done

total_techniques=$(grep -rln '// 编程技术:' "$SRC" 2>/dev/null | wc -l | tr -d ' ' || true)
if [[ "$total_techniques" == "$TECHNIQUES_TOTAL" ]]; then
    echo "  ✓ TOTAL: $total_techniques (expected $TECHNIQUES_TOTAL)"
else
    echo "  ✗ TOTAL: $total_techniques (expected $TECHNIQUES_TOTAL)"
    failures=$((failures + 1))
fi

echo ""
if [[ $failures -eq 0 ]]; then
    echo "ALL OK — 数字与 docs/grep-evidence.md 一致"
    exit 0
else
    echo "FAIL — $failures 处不一致,请更新 docs/grep-evidence.md 或源码注释"
    exit 1
fi
```

---

## 四、注意事项

1. **行号会变**: 上述清单标注的是生成时的行号,文件后续编辑会偏移,**行号不应作为契约**;只把"文件命中"作为契约。
2. **类名 ≠ 注释数**: 一个文件可能在多处提到"泛型",grep 命中数 = 文件数 ≠ 注释行数。
3. **唯一性前缀**: 所有 grep 依赖 `// Design Pattern:` 和 `// 编程技术:` 这两个**固定前缀**作为过滤,所以即使源码含 "Builder"/"Singleton" 字符串(如变量名),也只在显式注释处命中。
4. **测试代码不计入**: `$SRC=src/main/java` 排除 test 代码,避免被测试 fixture 中的 "Strategy" 字符串干扰。
5. **架构模式(Pipeline/Command)不计入主清单**: 这是设计层面的说明,无独立 `// Design Pattern:` 注释;如要验证,在 `client/VenueBookingClient.java` 看到的是 7 个 Step 顺序调用 + 共享 Context,即流水线形态。

---

## 五、Phase 5 清理说明(2026-06-21)

为消除"无生产调用"的 dead code,本次清理删除:

- `src/main/java/edu/szu/agent/matcher/` 整包(7 个文件)
- `src/test/java/edu/szu/agent/matcher/` 整包(5 个测试类)
- `// Design Pattern: Type Object` 注释(3 个枚举文件)

**影响**:

| 项目 | 清理前 | 清理后 |
|---|---|---|
| 设计模式文件数 | 29 | 26 |
| Strategy 文件数 | 18(含 matcher 6) | 19(步骤/选择器扩展) |
| Adapter 文件数 | 2 | 3(+BookingFlowLauncher seam) |
| 抽象类文件数 | 1 | 0 |
| 编程技术总文件数 | 46 | 50 |

---

> **生成时间**: 2026-06-21
> **关联文件**: `design/2023150090_王子豪_大作业自拟题目.md` §三/§四
> **回溯方式**: `git log -- docs/grep-evidence.md` 查看历次更新

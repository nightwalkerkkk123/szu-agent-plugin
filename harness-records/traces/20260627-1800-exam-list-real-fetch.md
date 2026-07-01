# Harness Trace: P1 phase 4 — `exam_list` 考试安排真实抓取改造

**Trace ID:** `20260627-1800-exam-list-real-fetch`
**Phase:** P1 真实抓取改造 · 阶段 4 (共 4)
**Date:** 2026-06-27
**Author:** 王子豪 (2023150090)

---

## 需求背景

完成 `schedule_list` / `notice_list` / `calendar_get` 三个技能的真实抓取改造后，继续进行 **phase 4: `exam_list` 考试安排真实抓取改造**。

设计原则与前三阶段完全一致：
1. **真实抓取优先**：使用已登录 CAS 会话通过 Playwright 抓取 ehall 考试安排页面
2. **弹性回退**：任何失败（网络、超时、会话过期、选择器不匹配、空结果）自动回退到项目内置静态快照
3. **环境开关**：`SZU_EXAM_REAL=0` 强制静态模式
4. **向后兼容**：保留原有构造器和 API，现有代码和测试无需修改

---

## 架构设计

复用与前三阶段完全相同的弹性架构：

| 模式 | 实现类 | 说明 |
|---|---|---|
| **Strategy** | `ExamFetchProvider` | 函数式接口，定义 HTML 抓取契约 |
| **Concrete Strategy** | `PlaywrightExamFetchProvider` | Playwright 实现，导航到 ehall 考试安排页返回 HTML |
| **Decorator + Strategy** | `ResilientExamListClient` | 弹性包装器，真实失败/空 → 自动回退静态快照 |
| **Factory Method** | `ExamCommand.defaultTask()` | 工厂方法创建完全配置好的任务实例，供 CLI/Skill/MCP 使用 |

### 类依赖图

```
┌─────────────────────────────────────────────────────────────┐
│  CLI / Skill / MCP                                          │
│           │                                                  │
│           ▼                                                  │
│  ExamCommand.defaultTask()  [Factory Method]                │
│           │                                                  │
│           ▼                                                  │
│  ExamListTask(realSupplier, fallbackSupplier)               │
│           │                                                 │
│           ▼  (if !staticOnly)                               │
│  ResilientExamListClient [Decorator + Strategy]            │
│           │                                                 │
│           ├─→ realSupplier: PlaywrightExamFetchProvider    │
│           │            .fetchAndParse() → List<ExamSchedule>
│           │                 (throws on failure)
│           └─→ fallbackSupplier: ExamListClient::list       │
│                           (static snapshot from JAR)
│
│  Result:  real success → real;  real fail/empty → fallback
└─────────────────────────────────────────────────────────────┘
```

---

## 文件变更

### 新增生产文件 (4)

| 文件 | 设计模式 | 编程技术 |
|---|---|---|
| `src/main/java/edu/szu/agent/client/exam/ExamFetchProvider.java` | Strategy | 函数式接口 / Lambda / 泛型 |
| `src/main/java/edu/szu/agent/client/exam/PlaywrightExamFetchProvider.java` | Strategy | 依赖注入 / 不可变 / 异常处理 |
| `src/main/java/edu/szu/agent/client/exam/ResilientExamListClient.java` | Decorator + Strategy | 函数式接口 / Supplier 注入 / 不可变性 / 异常处理 |
| `src/main/java/edu/szu/agent/error/ExamListException.java` | N/A | unchecked 异常 / 错误码携带 |

### 修改生产文件 (3)

| 文件 | 修改内容 |
|---|---|
| `src/main/java/edu/szu/agent/error/ErrorCode.java` | 新增 2 个错误码：`EXAM_FETCH_FAILED` / `EXAM_TIMEOUT` |
| `src/main/java/edu/szu/agent/task/ExamListTask.java` | 重构支持 `realSupplier` + `fallbackSupplier` 依赖注入，保留旧构造器向后兼容 |
| `src/main/java/edu/szu/agent/cli/ExamCommand.java` | 添加 `defaultTask()` 工厂方法供 Skill/MCP 注册使用 |

### 新增测试文件 (1)

| 文件 | 测试覆盖 |
|---|---|
| `src/test/java/edu/szu/agent/client/exam/ResilientExamListClientTest.java` | 7 个测试：<br>1. real 返回非空 → 使用 real<br>2. real 返回空 → 回退 static<br>3. real 抛出异常 → 回退 static<br>4. real 返回 null → 回退 static<br>5. fallback 惰性求值（real 成功不调用）<br>6. 构造器 fail-fast 拒接 null real<br>7. 构造器 fail-fast 拒接 null fallback |

### 修改测试文件 (1)

| 文件 | 修改内容 |
|---|---|
| `src/test/java/edu/szu/agent/task/ExamListTaskTest.java` | 新增 3 个弹性路由测试：<br>- `realSupplierThrowsFallsBackToStatic`<br>- `realSupplierEmptyFallsBackToStatic`<br>- `realSupplierNonEmptyUsesRealResult` |

### 修改测试文件 (1 个测试修复)

| 文件 | 修改内容 |
|---|---|
| `src/test/java/edu/szu/agent/error/ErrorCodeTest.java` | 更新期望常量计数：42 → 44 (新增 2 个) |

---

## 设计模式标注（可 grep 验证）

```bash
$ grep -r "Design Pattern:" src/main/java/edu/szu/agent/ | grep -i exam
src/main/java/edu/szu/agent/client/exam/ExamFetchProvider.java:  // Design Pattern: Strategy
src/main/java/edu/szu/agent/client/exam/ResilientExamListClient.java:// Design Pattern: Decorator + Strategy(动态选择实现)
src/main/java/edu/szu/agent/cli/ExamCommand.java:    // Design Pattern: Factory Method
```

共 3 处设计模式标注，符合项目要求。

## 编程技术标注（可 grep 验证）

```bash
$ grep -r "// 编程技术:" src/main/java/edu/szu/agent/ | grep -C 1 exam
src/main/java/edu/szu/agent/client/exam/ExamFetchProvider.java:  // 编程技术: 函数式接口 / Lambda / 泛型
src/main/java/edu/szu/agent/client/exam/PlaywrightExamFetchProvider.java:  // 编程技术: 依赖注入 / 不可变 / 异常处理
src/main/java/edu/szu/agent/client/exam/ResilientExamListClient.java:  // 编程技术: 函数式接口 / Supplier 注入 / 不可变性 / 异常处理
src/main/java/edu/szu/agent/error/ExamListException.java:  // 编程技术:  unchecked 异常 / 错误码携带
src/main/java/edu/szu/agent/task/ExamListTask.java:  // 编程技术: 泛型 / 枚举 / Lambda / 依赖注入 / 不可变性
```

共 5 处编程技术标注，符合项目要求。

---

## 不可变性验证

所有核心类的字段都声明为 `final`：

| 类 | 字段 | final |
|---|---|:---:|
| `PlaywrightExamFetchProvider` | `browser`, `examUrl`, `tableSelector` | ✅ |
| `ResilientExamListClient` | `realSupplier`, `fallbackSupplier` | ✅ |
| `ExamListTask` | `staticClient`, `realSupplier`, `fallbackSupplier`, `staticOnly` | ✅ |
| `ExamListException` | `errorCode` | ✅ |
| `ExamSchedule` (record) | 所有字段自动 final | ✅ |

遵循项目不可变性原则：始终创建新对象，从不修改现有对象。

---

## 测试结果

### 相关模块测试

```
[INFO] Running edu.szu.agent.task.ExamListTaskTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running edu.szu.agent.error.ErrorCodeTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running edu.szu.agent.client.exam.ResilientExamListClientTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0

[INFO] Results:
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Java 代码审查结果

`java-reviewer` agent 审查结论：**APPROVED**

| Severity | Count | Status |
|----------|-------|--------|
| CRITICAL | 0     | pass   |
| HIGH     | 0     | pass   |
| MEDIUM   | 0     | pass   |
| LOW      | 0     | pass   |

所有检查项通过：
- 设计模式标注正确 ✓
- 编程技术标注正确 ✓
- Java 21 编码规范符合 ✓
- 不可变性原则遵循 ✓
- 错误处理正确 ✓
- Javadoc 完整（@since + @author）✓
- 无违反项目规范 ✓

---

## P1 四阶段完成总结

| 阶段 | Skill | 完成日期 | 状态 |
|---|---|---|---|
| 1 | `schedule_list` (课表查询) | 2026-06-xx | ✅ 完成 |
| 2 | `notice_list` (公文通) | 2026-06-xx | ✅ 完成 |
| 3 | `calendar_get` (校历) | 2026-06-xx | ✅ 完成 |
| 4 | **`exam_list` (考试安排)** | 2026-06-27 | ✅ **本次完成** |

四个技能全部完成改造，架构完全一致：
- ✅ 默认真实抓取 + 静态回退
- ✅ 环境变量 `SZU_*_REAL=0` 强制静态模式
- ✅ 完全向后兼容（原有 API/测试全部保留）
- ✅ 完整单元测试覆盖所有回退场景
- ✅ 统一设计模式：Strategy + Decorator + Factory Method

---

## 摩擦与决策

本次改造无重大摩擦。由于严格遵循前三阶段验证过的架构，直接复用模式，代码一次编译通过，测试一次通过（仅 `ErrorCodeTest` 计数错误，一分钟修复）。

**决策：** 保持与 `schedule_list` / `notice_list` / `calendar_get` 完全相同的包结构和命名，便于维护和理解。

---

## 下一步

更新 `docs/system-map.md` 和 `docs/design-patterns.md` 记录新增的模式和类结构。

---

## 验证命令

```bash
mvn -Dtest=ErrorCodeTest,ResilientExamListClientTest,ExamListTaskTest test
# → BUILD SUCCESS，Tests run: 29, Failures: 0
```

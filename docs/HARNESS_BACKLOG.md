# Harness Backlog

> 当 Agent 遇到摩擦、混淆或需要新验证命令时，记录到此处。
> 这是 harness 改进的输入源。

## Growth Rule

当 Agent 遇到以下情况时，必须记录：
- 混淆（不知道读什么）
- 重复手动推理（应该自动化）
- 需要新的验证命令
- 发现缺失的规则
- 看到重复的失败模式

## Backlog Item Format

```markdown
## [ID-XXX] Short pain name

**Date:** YYYY-MM-DD
**Status:** proposed | accepted | done
**Lane:** tiny | normal | high-risk
**Predicted impact:** [what improvement should achieve, measurable]
**Outcome:** [actual measured result, filled when closed]
**Friction type:** context-missing | context-stale | tool-missing | tool-broken | pattern-unclear | dependency-confusion | other

**Pain:** What was hard or confusing.

**Proposed fix:** How to improve the harness to address this pain.

---
```

## Backlog Items

<!-- Add new items below -->

## [ID-001] OQ1: Skill wrapper 凭证注入契约(D5 × D6)

**Date:** 2026-06-11
**Status:** resolved-by-ADR-0005
**Lane:** normal
**Predicted impact:** 消除 Skill/MCP wrapper 凭证注入歧义,避免演示日 `.env` 找不到导致失败
**Outcome:** 已由 **ADR-0005 D1** 解决:Skill wrapper 显式传 `--env-file <path>` 给 CLI,jar 不依赖 cwd
**Friction type:** pattern-unclear

**Pain:** D5 要求 Skill wrapper 在 `exec` jar 前 `cd $SKILL_DIR` 或注入 env;
D6 优先级是 env var > cwd `.env` > skill 目录 `.env`。若 wrapper 既不切 cwd 也不显式注入,
D6 的查找链会在没有 `.env` 的目录上断掉。当前 ADR 未明确 Skill wrapper 应采取哪种约定。

**Proposed fix (原始):** 在 ADR-0004 (Phase 4) 中显式写"Skill 必须保证 jar 进程 `pwd` 包含正确的 `.env`,
或显式 `export` 全部 `SZU_PASSWORD_XXX`"。

**Resolution (2026-06-11 升级为 ADR-0005):**
- 凭证路径不再依赖 cwd,改由 Skill wrapper 显式传 `--env-file <path>`
- `AccountResolver` 三层查找:进程 env → `--env-file` 指向的 .env → Skill 注入
- ADR-0005 D1 Accepted,本条目 close
- skill-author-guide.md 在 Phase 4 启动前补充(跟 ADR-0004 同步)

---

## [ID-002] OQ2: 演示后场地清理策略(D3)

**Date:** 2026-06-11
**Status:** proposed
**Lane:** tiny
**Predicted impact:** 避免演示成功后占位场地未清理,影响真实用户预约
**Outcome:** TBD
**Friction type:** other

**Pain:** D3 假设同账号可轮换项目做多次演示,但未说明**演示成功后留下的真实预约**由谁清理。
D8 演示兜底清单当前只覆盖"演示前不预约"和"准备录屏",未覆盖"演示后清理"。

**Proposed fix:** 并入 D8 演示兜底清单,演示日脚本加一条"演示后 5 分钟内 ehall 手工取消占位场地"。
如借他人账号演示,提前与账号主人沟通清理责任。

---

## [ID-003] OQ3: ErrorClassifier 删除后分类能力归属(D9)

**Date:** 2026-06-11
**Status:** resolved-by-ADR-0001-D9
**Lane:** normal
**Predicted impact:** 确认 `ErrorCode` 枚举 + `BookingException` 可独立支撑分类需求
**Outcome:** `ErrorCode` 12 个常量已携带 `severity / retryable / switchAccount / screenshot / hint` 5 元数据,`BookingException` 统一封装异常;Python `ErrorClassifier` 判定表已等价映射到 Java 枚举,无需额外 Classifier。详见 `docs/adr/0001-project-direction-recalibration.md` D9 + `docs/adr/0006-phase1-domain-error-retry-matcher.md` §2。
**Friction type:** pattern-unclear

**Pain:** D9 删 Python `ErrorClassifier`,理由是"枚举自带元数据"。需在 Phase 1 (`error/` 包)
实施前确认 Java 端 `BookingErrorCode` 枚举 + `BookingException` 密封继承结构,能否覆盖
Python 原分类器全部能力(网络/登录/选场/提交/未知 5 类)。

**Proposed fix:** Phase 1 起步前列 Python `ErrorClassifier` 判定表 → Java 枚举等价映射,
留 1-2 小时预算。覆盖不全则在 `error/` 内补一个薄分类工具(不叫 Classifier 即可)。

---

## [ID-004] OQ4: 真实预约流有界重试策略(D2 × D7)

**Date:** 2026-06-11
**Status:** proposed
**Lane:** normal
**Predicted impact:** 演示日真跑时,短暂性失败(网络抖动、点击未命中)可控重试;已点提交后绝不再点
**Outcome:** TBD
**Friction type:** pattern-unclear

**Pain:** D2 要求真跑 Playwright 端到端;D7 假定 CAS 稳定无验证码。但网络抖动、点击未命中、
CAS 偶发重定向等**短暂性失败**如何处理,ADR 没写。两种极端都不可取:
- 任意失败都重试到死 → 验证码触发时浪费大量时间,演示翻车
- 任何失败都中止 → 一次抖动就翻车

**Proposed fix:** Phase 1 `retry/` 包设计时定义**有界重试**(≤2 次,
仅对"未触发 CAS 验证码、未点击提交"的状态)+ 状态机("已点提交后绝不再点")。
此决策可能升格为 ADR-0005。

---

## Closed Items

<!-- Completed items move here with outcome -->

---

## Friction Log

快速记录发现的摩擦（可转换为 backlog item）：

| Date | Friction | Category | Story/Context |
|---|---|---|---|
| 2026-06-11 | ADR-0001 接受后回看,发现 D5/D6 凭证注入、D3 清理、D9 分类、D2/D7 重试 4 处决策未闭环 | pattern-unclear | ADR-0001 |
| 2026-06-11 | 多个核心文档(PRD/design-patterns/system-map/README/CONTRIBUTING)仍按旧决策编写,与 ADR-0001 不一致 | context-stale | ADR-0001 校准 |
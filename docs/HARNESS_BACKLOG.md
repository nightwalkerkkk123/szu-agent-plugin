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

## Closed Items

<!-- Completed items move here with outcome -->

---

## Friction Log

快速记录发现的摩擦（可转换为 backlog item）：

| Date | Friction | Category | Story/Context |
|---|---|---|---|
| | | | |
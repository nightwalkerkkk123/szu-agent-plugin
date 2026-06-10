# Harness

> 本项目使用 Harness 工作流——人-Agent 协作的操作系统。
> 参考 [repository-harness](https://github.com/hoangnb24/repository-harness) 范式。

The app is what users touch. The harness is what agents touch.

## Mental Model

```
------------------+
| Human intent    |
+------------------+
         |
         v
+------------------+
| Feature intake   |  ← 每个任务前分类
+------------------+
         |
         v
+------------------+
| Story packet     |  ← Normal/High-risk 创建 story
+------------------+
         |
         v
+------------------+
| Agent work loop  |  ← 按 Context Rules 读文档
+------------------+
         |
         v
+------------------+
| Validation       |  ← mvn test + mvn package
+------------------+
         |
         v
+------------------+
| Record trace     |  ← 记录到 harness-records/traces/
+------------------+
         |
         v
+------------------+
| Harness growth   |  ← 摩擦记录到 HARNESS_BACKLOG.md
+------------------+
```

## Every Task Has Two Outputs

1. **Product delta**: app code, tests, API shape, data model, or product docs
2. **Harness delta**: docs, templates, validation expectations, or decision records that make the next task easier

## Lane Definitions

| Lane | Use when | Requirements |
|---|---|---|
| **Tiny** | Low-risk docs, copy, names, narrow edits | Record intake, direct patch, keep docs current |
| **Normal** | Story-sized behavior with bounded blast radius | Create story packet, link docs, implement vertical slice |
| **High-Risk** | Security, data, scope, contracts, multi-role | Use high-risk template, ask for confirmation, record decision |

## Source Hierarchy

```
User-provided spec or prompt
  → input material for first buildout

docs/PRD.md, docs/design-patterns.md, docs/system-map.md
  → current product contract

docs/stories/*
  → story-sized work packets and historical evidence

harness-records/traces/*
  → behavior-to-proof records

docs/decisions/*
  → why the contract changed
```

Before implementation, product docs describe intent. After implementation, product docs plus executable tests become the living contract.

## Done Definition

A task is done only when:
- The requested change is completed or the blocker is documented
- Relevant docs, stories, and test matrix entries remain current
- `mvn test` passed (or documented why not)
- A trace has been recorded in `harness-records/traces/`
- Missing harness capabilities were recorded in `docs/HARNESS_BACKLOG.md`
- The final response says what changed and what was not attempted

## Growth Rule

When an agent is confused, repeats manual reasoning, needs a new validation command, discovers a missing rule, or sees a recurring failure pattern, it must either:
- Improve the harness directly (if the fix is simple)
- Record the friction in `docs/HARNESS_BACKLOG.md` (if the fix is out of scope)

## Future Validation Ladder

```text
validate:quick
  format, lint, typecheck, unit tests, architecture check

test:integration
  backend, database, provider, or service checks as the stack requires

test:e2e
  user-visible end-to-end flows

test:platform
  shell, mobile, desktop, or deployment smoke checks as the stack requires
```

Agents must not claim these commands pass until they exist and have been run.
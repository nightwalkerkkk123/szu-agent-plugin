# Context Engineering Rules

> 上下文规则帮助 Agent 决定读什么、什么时候读、什么时候停。
> 目标是最大化正确信息的投放，而不是最大化上下文。

## Context Phases

### Intake Phase

读来分类请求、找到受影响的 surface、选择车道。

| Document | Tiny | Normal | High-Risk |
|---|---|---|---|
| `AGENTS.md` / `CLAUDE.md` | Must | Must | Must |
| `docs/FEATURE_INTAKE.md` | Must | Must | Must |
| `README.md` | Should | Must | Must |
| `docs/HARNESS.md` | Should | Must | Must |
| `docs/system-map.md` | Skip | Should | Must |
| `docs/design-patterns.md` | Skip | Should | Must |
| `docs/PRD.md` | Skip | Should | Must |

### Planning Phase

读来决定最小安全方法和预期证明。

| Document | Tiny | Normal | High-Risk |
|---|---|---|---|
| Current files to edit | Must | Must | Must |
| `docs/templates/story.md` | Skip | Must when creating story | Should |
| `docs/system-map.md` | Skip | Should for code changes | Must |
| `docs/design-patterns.md` | Skip | Should | Must |
| Relevant decisions | Skip | Should | Must |
| `docs/plans/README.md` | Skip | Should | Must |

### Implementation Phase

读来做变更。保持此阶段局限于直接影响选定 story 的文件。

| Document | Tiny | Normal | High-Risk |
|---|---|---|---|
| Files being changed | Must | Must | Must |
| Adjacent files with same pattern | Should | Must | Must |
| Relevant product docs | Skip if copy-only | Must if behavior changes | Must |
| `docs/design-patterns.md` | Skip | Should for pattern reference | Must |

### Validation Phase

读来证明变更并避免声称不支持的完成。

| Document | Tiny | Normal | High-Risk |
|---|---|---|---|
| Story acceptance criteria | Should | Must | Must |
| `docs/PRD.md` 验收标准 | Should | Must | Must |
| `mvn test` | Must | Must | Must |
| `mvn package` | Should | Must | Must |

### Trace Phase

读来为下一个 Agent 留下有用的证据。

| Document | Tiny | Normal | High-Risk |
|---|---|---|---|
| `docs/TRACE_SPEC.md` | Should | Must | Must |
| Changed-file list (`git status --short`) | Must | Must | Must |
| Validation command output | Should | Must | Must |

## Retrieval Triggers

| Trigger | Action |
|---|---|
| Task touches 认证/授权/安全代码 | Treat as high-risk |
| Task changes public API shape | Read `docs/PRD.md` API contract section |
| Task introduces new design pattern | Read `docs/design-patterns.md` pattern guidelines |
| Task discovers repeated confusion | Record in `harness-records/friction/` |
| Task makes a claim | Verify against `docs/PRD.md` acceptance criteria |

## Token Budget Guidance

| Lane | Target | Read Shape |
|---|---|---|
| Tiny | ~2K tokens | CLAUDE.md + 变更的文件 |
| Normal | ~5K tokens | CLAUDE.md + system-map + design-patterns + PRD + 变更的文件 |
| High-Risk | ~8K tokens | Full intake + all relevant docs + 变更的文件 |

## Review Checklist

Before implementation:
- [ ] Lane is chosen from `docs/FEATURE_INTAKE.md`
- [ ] Relevant product docs identified
- [ ] Any high-risk trigger handled

Before final response:
- [ ] Validation evidence has been read
- [ ] `mvn test` passes (or documented why not)
- [ ] Final trace includes files read, files changed, outcome, friction when applicable
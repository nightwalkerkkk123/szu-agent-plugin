# ECC Rule Layer — Project Local

> Rules consumed by Claude Code sessions in this repository.
> **Source:** `E:\CODE\ECC` (upstream, do not edit from this project).
> **This copy:** `.claude/rules/ecc/` (local, editable overrides only).

## Active rule sets

| Set | Path | Purpose |
|---|---|---|
| Common (en) | `.claude/rules/ecc/common/` | agents, code-review, coding-style, development-workflow, git-workflow, hooks, patterns, performance, security, testing |
| Java | `.claude/rules/ecc/java/` | coding-style, hooks, patterns, security, testing |
| 简体中文 | `.claude/rules/ecc/zh/` | 同 common 中文翻译,用于报告与中文对话 |

## Per-language routing

| File type | Active rules |
|---|---|
| `*.java`, `pom.xml` | `common/` + `java/` |
| `*.md`, `*.yml`, `*.puml` | `common/` |
| 中文报告 / 对话 | `zh/` (overrides common for Chinese) |

## Override policy

- **Do not edit** the files in `common/`, `java/`, `zh/` — they are shared.
- For project-specific overrides, add `-local.md` files here and reference them
  from the top of the relevant canonical file.
- Run `git status` before committing to ensure no rule file in `E:\CODE\ECC`
  was accidentally modified.
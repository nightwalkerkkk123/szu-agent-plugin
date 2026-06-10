# Project Filetree

_手动维护。实际存在的文件列出，存在但为空的标注 (empty)。不存在的不列出。_

## (root)/

- `CLAUDE.md` — 项目入口配置
- `CONTRIBUTING.md` — 教师评分指南（面向助教和教师）
- `FILETREE.md` — 本文件
- `MCP.md` — MCP 协议接口文档
- `README.md` — 项目自述文件
- `RULES.md` — 项目规则汇总
- `SECURITY.md` — 项目安全策略
- `SOUL.md` — 项目灵魂文档
- `WORKING-CONTEXT.md` — 工作上下文模板
- `pom.xml` — Maven 构建配置 (empty，待创建)
- `mvnw` / `mvnw.cmd` — Maven Wrapper (empty，待创建)

## configs/

- `configs/config.yaml` — 业务配置模板 (empty)
- `configs/.env.example` — 环境变量模板 (empty)

## design/

- `design/2023150090_王子豪_大作业自拟题目.md` — 提案文档 (empty，待创建)

## docs/

- `docs/PRD.md` — 产品需求文档
- `docs/design-patterns.md` — 设计模式应用清单
- `docs/system-map.md` — 系统地图 + 局限性分析
- `docs/FEATURE_INTAKE.md` — Feature intake 三车道分类（tiny/normal/high-risk）
- `docs/CONTEXT_RULES.md` — 上下文阶段规则（intake → implementation → trace）
- `docs/TRACE_SPEC.md` — Trace 记录规范
- `docs/HARNESS_BACKLOG.md` — Harness 改进 backlog
- `docs/HARNESS.md` — Harness 工作流说明（待创建）
- `docs/plans/` — 功能规划目录
  - `docs/plans/README.md` — 项目计划说明
- `docs/templates/` — 模板目录
  - `docs/templates/story.md` — Story packet 模板
- `docs/stories/` — Story packets（待填充）
- `docs/decisions/` — Decision records（待填充）

## harness-records/

持久化记录目录（类比 repository-harness 的 SQLite durable layer，但使用 JSON 文件）：
- `harness-records/traces/` — 每次任务的 trace 记录
- `harness-records/friction/` — 摩擦记录（可转为 backlog item）

## scripts/

- `scripts/filetree.py` — 文件树生成脚本

## src/

源代码目录（待实现）：
- `src/main/java/edu/szu/agent/` — Java 源码
- `src/main/resources/` — 资源配置
- `src/test/java/edu/szu/agent/` — 测试代码

## .claude/agents/

- `.claude/agents/build-error-resolver.md`
- `.claude/agents/doc-writer.md`
- `.claude/agents/implementer.md`
- `.claude/agents/java-reviewer.md`
- `.claude/agents/planner.md`
- `.claude/agents/security-reviewer.md`
- `.claude/agents/tdd-guide.md`
- `.claude/agents/tester.md`

## .claude/contexts/

- `.claude/contexts/dev.md`
- `.claude/contexts/research.md`
- `.claude/contexts/review.md`

## .claude/rules/ecc/common/

- `.claude/rules/ecc/common/agents.md`
- `.claude/rules/ecc/common/code-review.md`
- `.claude/rules/ecc/common/coding-style.md`
- `.claude/rules/ecc/common/development-workflow.md`
- `.claude/rules/ecc/common/git-workflow.md`
- `.claude/rules/ecc/common/hooks.md`
- `.claude/rules/ecc/common/patterns.md`
- `.claude/rules/ecc/common/performance.md`
- `.claude/rules/ecc/common/security.md`
- `.claude/rules/ecc/common/testing.md`

## .claude/rules/ecc/java/

- `.claude/rules/ecc/java/coding-style.md`
- `.claude/rules/ecc/java/hooks.md`
- `.claude/rules/ecc/java/patterns.md`
- `.claude/rules/ecc/java/security.md`
- `.claude/rules/ecc/java/testing.md`

## .claude/rules/ecc/zh/

- `.claude/rules/ecc/zh/README.md`
- `.claude/rules/ecc/zh/agents.md`
- `.claude/rules/ecc/zh/code-review.md`
- `.claude/rules/ecc/zh/coding-style.md`
- `.claude/rules/ecc/zh/development-workflow.md`
- `.claude/rules/ecc/zh/git-workflow.md`
- `.claude/rules/ecc/zh/hooks.md`
- `.claude/rules/ecc/zh/patterns.md`
- `.claude/rules/ecc/zh/performance.md`
- `.claude/rules/ecc/zh/security.md`
- `.claude/rules/ecc/zh/testing.md`

## .claude/rules/ecc/

- `.claude/rules/ecc/_manifest.md`

## .claude/skills/ecc/

- `.claude/skills/ecc/agent-harness-construction/SKILL.md`
- `.claude/skills/ecc/architecture-decision-records/SKILL.md`
- `.claude/skills/ecc/coding-standards/SKILL.md`
- `.claude/skills/ecc/context-budget/SKILL.md`
- `.claude/skills/ecc/continuous-learning-v2/SKILL.md`
- `.claude/skills/ecc/error-handling/SKILL.md`
- `.claude/skills/ecc/git-workflow/SKILL.md`
- `.claude/skills/ecc/java-coding-standards/SKILL.md`
- `.claude/skills/ecc/mcp-server-patterns/SKILL.md`
- `.claude/skills/ecc/springboot-patterns/SKILL.md`

## .claude/skills/engineering/

- `.claude/skills/engineering/README.md`
- `.claude/skills/engineering/diagnose/SKILL.md`
- `.claude/skills/engineering/engineering-change-descriptions/SKILL.md`
- `.claude/skills/engineering/engineering-code-review/SKILL.md`
- `.claude/skills/engineering/engineering-emergency-changes/SKILL.md`
- `.claude/skills/engineering/engineering-review-comments/SKILL.md`
- `.claude/skills/engineering/engineering-review-feedback/SKILL.md`
- `.claude/skills/engineering/engineering-small-prs/SKILL.md`
- `.claude/skills/engineering/grill-with-docs/SKILL.md`
- `.claude/skills/engineering/grill-with-docs/ADR-FORMAT.md`
- `.claude/skills/engineering/grill-with-docs/CONTEXT-FORMAT.md`
- `.claude/skills/engineering/improve-codebase-architecture/SKILL.md`
- `.claude/skills/engineering/improve-codebase-architecture/DEEPENING.md`
- `.claude/skills/engineering/improve-codebase-architecture/HTML-REPORT.md`
- `.claude/skills/engineering/improve-codebase-architecture/INTERFACE-DESIGN.md`
- `.claude/skills/engineering/improve-codebase-architecture/LANGUAGE.md`
- `.claude/skills/engineering/prototype/SKILL.md`
- `.claude/skills/engineering/prototype/LOGIC.md`
- `.claude/skills/engineering/prototype/UI.md`
- `.claude/skills/engineering/setup-matt-pocock-skills/SKILL.md`
- `.claude/skills/engineering/setup-matt-pocock-skills/domain.md`
- `.claude/skills/engineering/setup-matt-pocock-skills/issue-tracker-github.md`
- `.claude/skills/engineering/setup-matt-pocock-skills/issue-tracker-gitlab.md`
- `.claude/skills/engineering/setup-matt-pocock-skills/issue-tracker-local.md`
- `.claude/skills/engineering/setup-matt-pocock-skills/triage-labels.md`
- `.claude/skills/engineering/tdd/SKILL.md`
- `.claude/skills/engineering/tdd/deep-modules.md`
- `.claude/skills/engineering/tdd/interface-design.md`
- `.claude/skills/engineering/tdd/mocking.md`
- `.claude/skills/engineering/tdd/refactoring.md`
- `.claude/skills/engineering/tdd/tests.md`
- `.claude/skills/engineering/to-issues/SKILL.md`
- `.claude/skills/engineering/to-prd/SKILL.md`
- `.claude/skills/engineering/triage/SKILL.md`
- `.claude/skills/engineering/triage/AGENT-BRIEF.md`
- `.claude/skills/engineering/triage/OUT-OF-SCOPE.md`
- `.claude/skills/engineering/zoom-out/SKILL.md`

## .claude/skills/deprecated/

- `.claude/skills/deprecated/README.md`
- `.claude/skills/deprecated/design-an-interface/SKILL.md`
- `.claude/skills/deprecated/qa/SKILL.md`
- `.claude/skills/deprecated/request-refactor-plan/SKILL.md`
- `.claude/skills/deprecated/ubiquitous-language/SKILL.md`

## .claude/skills/in-progress/

- `.claude/skills/in-progress/README.md`
- `.claude/skills/in-progress/review/SKILL.md`
- `.claude/skills/in-progress/writing-beats/SKILL.md`
- `.claude/skills/in-progress/writing-fragments/SKILL.md`
- `.claude/skills/in-progress/writing-shape/SKILL.md`

## .claude/skills/misc/

- `.claude/skills/misc/README.md`
- `.claude/skills/misc/git-guardrails-claude-code/SKILL.md`
- `.claude/skills/misc/migrate-to-shoehorn/SKILL.md`
- `.claude/skills/misc/scaffold-exercises/SKILL.md`
- `.claude/skills/misc/setup-pre-commit/SKILL.md`

## .claude/skills/personal/

- `.claude/skills/personal/README.md`
- `.claude/skills/personal/edit-article/SKILL.md`
- `.claude/skills/personal/obsidian-vault/SKILL.md`

## .claude/skills/productivity/

- `.claude/skills/productivity/README.md`
- `.claude/skills/productivity/caveman/SKILL.md`
- `.claude/skills/productivity/grill-me/SKILL.md`
- `.claude/skills/productivity/handoff/SKILL.md`
- `.claude/skills/productivity/teach/SKILL.md`
- `.claude/skills/productivity/teach/GLOSSARY-FORMAT.md`
- `.claude/skills/productivity/teach/LEARNING-RECORD-FORMAT.md`
- `.claude/skills/productivity/teach/MISSION-FORMAT.md`
- `.claude/skills/productivity/teach/RESOURCES-FORMAT.md`
- `.claude/skills/productivity/write-a-skill/SKILL.md`
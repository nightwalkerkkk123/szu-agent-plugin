# CLAUDE.md

> Project-local entry for Claude Code sessions in this repository.
> Extends global ECC rules in `C:\Users\王子豪\.claude\rules\ecc\common\`.

---

## Project identity

| Field | Value |
|---|---|
| **Type** | 面向对象高级编程 — 个人大作业 |
| **Author** | 学号 2023150090 / 姓名 王子豪 |
| **Stack** | Java 21 · Maven · picocli · Jackson · Logback · JUnit 5 + AssertJ · ArchUnit · JaCoCo |
| **Form factor** | CLI 工具 + 常驻 HTTP daemon(可选) + Skill/MCP 插件,供外部 AI Agent 调用 |
| **Current scale** | 8 个业务 Skill · 14 个 Java 包 · 93 个 main + 53 个 test 源文件 · 4 模式 · 6 编程技术 |
| **Business reference** | `E:\CODE\szu-sports-booking\` (Python) |
| **Rule source** | `E:\CODE\ECC\rules\` (已复制到 `.claude/rules/ecc/`,勿编辑上游) |

## What this is — and isn't

- ✅ CLI 工具 + 常驻 HTTP daemon + Skill 插件 + MCP 工具导出(8 工具),供外部 Agent 调用
- ✅ 一个常驻 JVM 可同时服务 Skill `curl` 与 MCP 宿主(`scripts/serve.sh --background`)
- ✅ 外部 Skill 通过 `SZU_SKILL_PATH` 加载,无需改 Java 代码
- ✅ 通过 `BrowserLifecycle` 适配器封装 Obscura(底层通过 Playwright SDK + `connectOverCDP` 通信)
- ✅ Obscura daemon 由 `ObscuraLauncher` 在首次 Skill 调用时自动拉起,shutdown hook 清理
- ✅ 30 天会话复用(ADR-0008)— `~/.szu-agent/sessions/<username>.json`
- ❌ **不是** AI Agent — 无 NLU / 意图识别 / 对话管理
- ❌ 不绕过验证码、不高频访问、不发送敏感邮件

## Quick commands

```bash
mvn -q -DskipTests package              # 构建
mvn -Pobscura-skip-download test        # 跑测试(离线模式,跳过 70 MB 二进制下载)
java -jar target/szu-agent-plugin.jar booking venue \
  --username 2023150090 --campus YUEHAI --sport TENNIS \
  --date 2026-06-24 --time-slot 19:00-20:00 --format json
java -jar target/szu-agent-plugin.jar skill list --format json
scripts/serve.sh --background          # 启动常驻 HTTP daemon(端口 8765)
curl localhost:8765/health             # {"status":"ok"}

# Obscura daemon 诊断
curl http://127.0.0.1:9222/json/version  # Obscura CDP 端点 UA
cat ~/.szu-agent/obscura.pid             # 当前 daemon PID
tail -f ~/.szu-agent/obscura.log         # daemon stdout/stderr
```

> 平台相关：Windows 本机 Maven / JDK 路径见 [`docs/setup/windows-maven.md`](docs/setup/windows-maven.md)；
> macOS / Linux 路径见 [`docs/setup/mac-maven.md`](docs/setup/mac-maven.md)。
> 本机已知 Maven 位于 `E:\tools\apache-maven-3.9.16\bin\mvn`，JDK 21 在 `E:\tools\jdk-21`。

## Code conventions

- 显式标注设计模式: `// Design Pattern: Strategy` (报告要能 grep)
- 显式标注编程技术: `// 编程技术: 泛型/枚举/注解/重载/抽象类/Lambda`
- 公开方法必须有 Javadoc,含 `@since 0.1.0` 和 `@author 王子豪`
- 生产代码禁用 `System.out.println`,用 SLF4J/Logback
- 敏感信息(密码/Cookie/Token)不写入日志,`LogMasker` 集中脱敏
- `mvn test` 必须通过才能 commit

## Active rule layers (loaded in order)

1. `~/.claude/rules/ecc/common/` (全局)
2. `~/.claude/rules/ecc/zh/` (全局,中文对话)
3. `.claude/rules/ecc/common/` (本项目,直接复制)
4. `.claude/rules/ecc/java/` (本项目,直接复制)
5. `.claude/rules/ecc/zh/` (本项目,直接复制)

冲突时:语言专属规则 > 通用规则;项目本地覆盖需显式引用。

---

## Harness Context (Agent Operating Model)

> 本项目使用 Harness 工作流——每次任务经过 intake → implementation → trace 的闭环。
> 参考 `docs/HARNESS.md` 了解完整的人-Agent 协作模型。

### Before Work: Feature Intake

每个实现任务在代码变更前先经过 intake gate：

1. **Classify** — 使用 `docs/FEATURE_INTAKE.md` 判断输入类型和车道（tiny/normal/high-risk）
2. **Find affected docs** — 识别需要阅读的产品文档和 story 文件
3. **Run risk checklist** — 检查 Auth/Authorization/Data model/Audit 等硬门
4. **Choose lane** — tiny=低风险改动, normal=故事级行为, high-risk=安全/数据/多域

### During Work: Context Rules

按阶段阅读文档（详见 `docs/CONTEXT_RULES.md`）：

| Phase | Must read |
|---|---|
| Intake | `CLAUDE.md`, `docs/FEATURE_INTAKE.md` |
| Planning | `docs/system-map.md`, `docs/design-patterns.md` |
| Implementation | 变更的文件 + 相邻同模式文件 |
| Validation | `docs/PRD.md` 验收标准, `mvn test` |
| Trace | `docs/TRACE_SPEC.md`, `git status --short` |

### After Work: Record Trace

Normal/High-risk 任务完成后记录 trace 到 `harness-records/traces/YYYYMMDD-HHMMSS-story-id.md`：
- 列出变更的文件和阅读的文件
- 记录验证结果（`mvn test` 输出）
- 记录设计模式和编程技术
- 记录摩擦（如有）
- 记录决策（如有）

### Harness Growth Rule

当遇到摩擦、混淆或需要新验证命令时：
1. 直接修复（如果简单）
2. 或记录到 `docs/HARNESS_BACKLOG.md`

---

## Available skills

### 仓库内置 skill

- `.claude/skills/szu-agent/` — **自动激活**,只要用户提到深大/SZU 的校历、课表、公文通、选课/食堂/图书馆等校园信息查询、订场馆/订球场、查作业,或提到 szu-agent 服务起没起、连不上 MCP,就触发该 skill。其内部会先探活 `localhost:8765/health`、必要时启动 daemon,再按 schema 调用 8 个工具。

### 装载的工程 skills(自 `/Users/wangzihao/.claude/skills/`)

- **代码开发阶段**: `/feature-dev-loop` 规划功能 → `/engineering-code-review` 审查
- **PR 阶段**: `/engineering-change-descriptions` 写 PR 描述 → `/engineering-review-feedback` 处理评论
- **大功能**: `/engineering-small-prs` 拆分为小 PR 序列
- **文档报告**: `/article-to-html` 将 markdown 转为精美 HTML(报告提交用)
- **紧急修复**: `/engineering-emergency-changes` 判断是否走 hotfix 通道

## ECC skills (from `E:\CODE\ECC\skills\`)

10 个精选 skills 装载到 `.claude/skills/ecc/`,用于 Java 项目开发与 Agent 平台架构。

| Skill | 用途 |
|---|---|
| `java-coding-standards` | Java 21 编码规范、命名、格式、DocJava |
| `springboot-patterns` | Spring Boot 设计模式参考(Builder/Factory/Adapter 等落地示例) |
| `mcp-server-patterns` | MCP 工具导出契约设计(tools/list + tools/call) |
| `agent-harness-construction` | Agent 工具平台整体架构设计(与本项目直接对应) |
| `coding-standards` | 通用编码标准(跨语言) |
| `architecture-decision-records` | ADR 写作规范(本项目 docs/architecture/ 用) |
| `git-workflow` | commit 规范与 PR 工作流 |
| `error-handling` | 错误码设计模式参考 |
| `context-budget` | 上下文窗口管理(长会话优化) |
| `continuous-learning-v2` | 持续学习机制(可选,项目复盘用) |

---

## Behavioral guidelines

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
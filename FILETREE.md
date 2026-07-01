# Project Filetree

_手动维护。实际存在的文件列出,存在但为空的标注 (empty)。不存在的不列出。_
_最近更新:2026-06-25(`4f06045`,MCP HTTP daemon + 8 工具 + 外部 Skill loader 落库)。_

## (root)/

- `CLAUDE.md` — 项目入口配置(含 Harness 上下文)
- `CONTRIBUTING.md` — 教师评分指南(面向助教和教师)
- `FILETREE.md` — 本文件
- `MCP.md` — MCP 协议接口文档(8 工具 + stdio/HTTP 双 transport)
- `README.md` — 项目自述文件
- `RULES.md` — 项目规则汇总
- `SECURITY.md` — 项目安全策略
- `SERVICE.md` — 常驻 HTTP daemon 部署与调用面指南
- `SOUL.md` — 项目灵魂文档
- `WORKING-CONTEXT.md` — 工作上下文
- `pom.xml` — Maven 构建配置
- `dependency-reduced-pom.xml` — Maven shade 插件精简配置
- `.env.example` — 环境变量模板
- `.mcp.json` — Claude Code 项目级 MCP server 注册(指向 `localhost:8765/mcp`)
- `logs/` — 运行时日志目录(`serve.log` / `serve.pid` 等)

## docs/

- `docs/PRD.md` — 产品需求文档(已同步 ADR-0008/0009/0010)
- `docs/design-patterns.md` — 设计模式应用清单(4 模式)
- `docs/system-map.md` — 系统地图 + 局限性分析
- `docs/FEATURE_INTAKE.md` — Feature intake 三车道分类(tiny/normal/high-risk)
- `docs/CONTEXT_RULES.md` — 上下文阶段规则(intake → implementation → trace)
- `docs/TRACE_SPEC.md` — Trace 记录规范
- `docs/HARNESS_BACKLOG.md` — Harness 改进 backlog
- `docs/HARNESS.md` — Harness 工作流说明
- `docs/HANDOFF.md` — 跨 PR 交接文档(US-008 等)
- `docs/HANDOFF-2026-06-15.md` — 历史交接快照
- `docs/final-report.md` — 期末报告
- `docs/grep-evidence.md` — 设计模式/编程技术 grep 证据
- `docs/plans/` — 功能规划目录
  - `docs/plans/README.md` — 项目计划说明
  - `docs/plans/PLAN-homework-and-session.md` — 畅课作业 + 会话持久化 plan
  - `docs/plans/PLAN-schedule.md` — 课表模块 plan
  - `docs/plans/external-skill-mcp-plan.md` — 外部 Skill + MCP server plan
  - `docs/plans/BACKLOG-remaining-tasks.md` — 剩余任务 backlog
- `docs/templates/` — 模板目录
  - `docs/templates/story.md` — Story packet 模板
- `docs/stories/` — Story packets
  - `docs/stories/US-006-chaoxing-homework-list.md`
  - `docs/stories/US-007-session-persistence.md`
  - `docs/stories/US-008-homework-attachment-download.md`
  - `docs/stories/US-009-schedule-list.md`
  - `docs/stories/US-010-knowledge-base.md`
- `docs/architecture/` — 架构细化文档
  - `docs/architecture/schedule/page-analysis.md`
- `docs/superpowers/` — superpowers 工作流的 spec/plan/research
  - `docs/superpowers/plans/2026-06-14-us-007-session-persistence.md`
  - `docs/superpowers/plans/2026-06-18-us-008-attachment-download.md`
  - `docs/superpowers/research/2026-06-17-lms-findings.md`
  - `docs/superpowers/research/2026-06-17-lms-har.har` — 真实 LMS 抓包
  - `docs/superpowers/specs/2026-06-14-us-007-session-persistence-design.md`
  - `docs/superpowers/specs/2026-06-18-us-008-attachment-download-design.md`
- `docs/tools/` — 工具操作文档
  - `docs/tools/booking-venue.md` — `booking_venue` 完整参数/枚举/自然语言映射
- `docs/adr/` — Architecture Decision Records
  - `docs/adr/0001-project-direction-recalibration.md` — 方向校准(Accepted)
  - `docs/adr/0002-browser-lifecycle-and-playwright-adapter.md` — BrowserLifecycle(Accepted)
  - `docs/adr/0005-credential-and-logging-enforcement.md` — 凭证 + 日志强制(Accepted)
  - `docs/adr/0006-phase1-domain-error-retry-matcher.md` — Phase 1 子决定(Accepted)
  - `docs/adr/0007-architecture-deepening.md` — 架构深化(4 模式收敛,Accepted)
  - `docs/adr/0008-session-persistence.md` — 登录态持久化(Accepted)
  - `docs/adr/0009-schedule-module-design.md` — 课表模块设计(Accepted)

## external/(可独立分发组件)

- `external/README.md` — 外部组件总览
- `external/mcp-server/` — 独立 Node.js MCP server(不依赖 Java 源码)
  - `external/mcp-server/README.md`
- `external/skills/` — 独立 Skill 规范与示例
  - `external/skills/README.md`
  - `external/skills/szu-campus/` — 仓库内置的 szu_campus skill,转发到 HTTP daemon
    - `external/skills/szu-campus/skill.yaml`
    - `external/skills/szu-campus/run`
    - `external/skills/szu-campus/run.bat`
  - `external/skills/example-greet/` — 多语言问候示例
  - `external/skills/template/` — 最小 skill 模板

## design/

- `design/2023150090_王子豪_大作业自拟题目.md` — 课程提案文档

## sdd/

- `sdd/progress.md` — spec-driven development 进度追踪

## scripts/

- `scripts/serve.sh` — 启动常驻 HTTP daemon(macOS/Linux)
- `scripts/serve.bat` — 启动常驻 HTTP daemon(Windows)
- `scripts/filetree.py` — 文件树生成脚本
- `scripts/demo.sh` — 课堂演示 4 步流程(历史)
- `scripts/grep-runs.sh` — 设计模式/技术 grep 静态守卫(历史)

## src/

### src/main/java/edu/szu/agent/(14 包,93 个 main 源文件)

- `src/main/java/edu/szu/agent/Main.java` — picocli 入口 + `registerDefaultSkills()`
- `src/main/java/edu/szu/agent/account/` — 凭证层(ADR-0005 D1)
  - `Account.java`、`AccountResolver.java`、`AccountResolutionException.java`
- `src/main/java/edu/szu/agent/browser/` — 浏览器抽象(Adapter,ADR-0002)
  - `BrowserLifecycle.java` — 12 方法接口
  - `PlaywrightBrowserAdapter.java` — 真演示唯一入口
- `src/main/java/edu/szu/agent/cli/` — picocli 子命令(Main + Booking/Homework/Schedule/Calendar/Notice/Exam/Knowledge/Skill/MCP/Venue/DateOffsetConverter/CommandOutput)
- `src/main/java/edu/szu/agent/client/` — 业务客户端
  - `VenueBookingClient.java`、`BookingFlowLauncher.java`
  - `ChaoxingHomeworkClient.java`、`ChaoxingAttachmentDownloadClient.java`、`EhallScheduleClient.java`
  - `client/cache/` — `CacheEnvelope` / `CacheKey` / `CacheStore`
  - `client/exam/` — `ExamListClient` / `ExamListParser`
  - `client/homework/` — `HomeworkListExtractor` / `AttachmentListExtractor`
  - `client/homework/attachment/` — `FilenameSanitizer`
  - `client/notice/` — `NoticeListClient` / `NoticeListParser`
  - `client/schedule/` — `ScheduleListClient` / `ScheduleListExtractor` / `PeriodMapping` / `WeekRangeParser`
  - `client/session/` — `SessionStore` / `SessionProbe` / `SessionResult`(ADR-0008)
  - `client/step/` — BookingStep 管线(Strategy,8+ 实现)
    - `BookingStep.java`、`BookingContext.java`、`StepOutcome.java`、`VenueSelector.java`
    - `CacheLookupStep.java`、`CacheWriteStep.java`、`CachePipelineBuilder.java`
    - `CasLoginStep.java`、`NavigateToBookingStep.java`、`SelectCampusStep.java`
    - `SelectSportStep.java`、`SelectDateStep.java`、`SelectTimeSlotStep.java`
    - `SelectVenueStep.java`、`CapacityVenueSelector.java`、`CourtListSelector.java`
    - `ConfirmBookingStep.java`、`RestoreSessionStep.java`、`PersistSessionStep.java`
    - `NavigateToHomeworkStep.java`、`NavigateToHomeworkDetailStep.java`
    - `NavigateToScheduleStep.java`、`ParseHomeworkListStep.java`、`ParseAttachmentsStep.java`
    - `ParseScheduleStep.java`、`DownloadFilesStep.java`
- `src/main/java/edu/szu/agent/config/` — `ConfigManager`(Singleton)
- `src/main/java/edu/szu/agent/domain/` — 不可变值对象
  - `Campus.java`、`Sport.java`、`TimeSlot.java`、`BookingRequest.java`、`BookingResult.java`
  - `Homework.java`、`HomeworkAttachment.java`、`HomeworkDownloadRequest.java`、`HomeworkDownloadResult.java`、`HomeworkListResult.java`
  - `CourseEntry.java`、`Period.java`、`ScheduleListResult.java`、`Weekday.java`、`WeekRange.java`
  - `LihuSport.java`、`YuehaiSport.java`
  - `domain/calendar/` — `AcademicEvent` / `AcademicEventType`
  - `domain/exam/` — `ExamSchedule`
  - `domain/notice/` — `Notice` / `NoticeCategory`
- `src/main/java/edu/szu/agent/error/` — 错误层
  - `ErrorCode.java`(枚举带 5 元数据)、`Severity.java`、`BookingException.java`、`LogMasker.java`
- `src/main/java/edu/szu/agent/json/` — `JsonMappers`(集中 ObjectMapper 工厂,JavaTimeModule)
- `src/main/java/edu/szu/agent/knowledge/` — 本地 KB
  - `KnowledgeCategory.java`、`KnowledgeRepository.java`、`KnowledgeResult.java`
  - `KnowledgeDoc.java`、`KnowledgeDocBuilder.java`
  - `MatchingStrategy.java` + 3 实现(`ContainsMatchingStrategy` / `ExactMatchingStrategy` / `RegexMatchingStrategy`)
- `src/main/java/edu/szu/agent/mcp/` — MCP 协议层
  - `McpStdioServer.java`、`McpHttpServer.java`(2026-06-23 新增)
  - `MCPToolCallHandler.java`、`ToolSchema.java`
- `src/main/java/edu/szu/agent/observability/` — `Tracer` / `RunRecord`
- `src/main/java/edu/szu/agent/retry/` — `RetryPolicy` + 3 实现(FixedDelay / ExponentialBackoff / NoRetry) + `RetryPolicies` 工厂
- `src/main/java/edu/szu/agent/skill/` — Skill 注册中心
  - `Skill.java`(record,@since 0.1.0,2026-06 加 `Skill.of(CampusTask)` 静态工厂)
  - `Skills.java`(Singleton)
  - `skill/external/` — 外部 Skill 加载器
    - `ExternalSkill.java`、`ExternalSkillLoader.java`、`ExternalSkillManifest.java`
- `src/main/java/edu/szu/agent/task/` — `CampusTask<T>` 任务抽象 + 8 个实现
  - `BookingTask.java`、`CalendarTask.java`、`ExamListTask.java`、`HomeworkDownloadTask.java`
  - `HomeworkTask.java`、`KnowledgeTask.java`、`NoticeTask.java`、`ScheduleListTask.java`
  - `CampusTask.java`、`TaskInput.java`、`TaskInputSchema.java`

### src/main/resources/

- `src/main/resources/application.yml` — `browser.kind: PLAYWRIGHT` + cache TTL + retry 默认值
- `src/main/resources/knowledge/01-campus-basics.md` 等 5 个 Markdown — KB 数据源

### src/test/java/edu/szu/agent/(53 个测试类)

- `src/test/java/edu/szu/agent/` — 镜像 main 包结构的测试
  - 含 `account/` / `architecture/` / `browser/` / `cli/` / `client/` / `client/cache/` / `client/exam/` / `client/homework/` / `client/homework/attachment/` / `client/notice/` / `client/schedule/` / `client/session/` / `client/step/` / `config/` / `domain/` / `error/` / `json/` / `knowledge/` / `mcp/` / `observability/` / `packaging/` / `retry/` / `skill/` / `task/`

### src/test/resources/

- `src/test/resources/application.yml`

## harness-records/

持久化记录目录(类比 repository-harness 的 SQLite durable layer,但使用 JSON 文件):
- `harness-records/traces/` — 每次任务的 trace 记录
  - `20260613-204600-phase5-cleanup.md`
  - `20260614-184300-phase5-step3-refactor.md`
  - `20260614-184300-select-date-fix.md`
  - `20260615-013855-US-007.md`
  - `20260619-013627-US-009.md`
  - `20260623-015500-improve-architecture-5-deepening.md`
- `harness-records/friction/`
  - `20260613-2050-ssh-key-push-blocked.md`

## .claude/

### .claude/agents/

- `.claude/agents/build-error-resolver.md`
- `.claude/agents/doc-writer.md`
- `.claude/agents/implementer.md`
- `.claude/agents/java-reviewer.md`
- `.claude/agents/planner.md`
- `.claude/agents/security-reviewer.md`
- `.claude/agents/tdd-guide.md`
- `.claude/agents/tester.md`

### .claude/contexts/

- `.claude/contexts/dev.md`
- `.claude/contexts/research.md`
- `.claude/contexts/review.md`

### .claude/skills/(本项目)

- `.claude/skills/szu-agent/SKILL.md` — Claude Code 内置技能,自动探活 daemon + 调用 8 工具
- `.claude/skills/szu-agent/evals/evals.json` — skill eval fixture

### .claude/skills/deprecated/engineering/

- `.claude/skills/deprecated/design-an-interface/SKILL.md`
- `.claude/skills/deprecated/qa/SKILL.md`
- `.claude/skills/deprecated/request-refactor-plan/SKILL.md`
- `.claude/skills/deprecated/ubiquitous-language/SKILL.md`
- `.claude/skills/deprecated/README.md`

### .claude/skills/ecc/(ECC 装载)

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

### .claude/skills/engineering/

(同前,内容不变 — 列出略)

### .claude/skills/in-progress/

(同前,内容不变 — 列出略)

### .claude/skills/misc/ / personal/ / productivity/

(同前,内容不变 — 列出略)

## .claude/rules/ecc/(项目本地规则,从 `E:\CODE\ECC\rules\` 复制,勿编辑上游)

### .claude/rules/ecc/common/(en)

- `agents.md` / `code-review.md` / `coding-style.md` / `development-workflow.md` / `git-workflow.md` / `hooks.md` / `patterns.md` / `performance.md` / `security.md` / `testing.md`

### .claude/rules/ecc/java/

- `coding-style.md` / `hooks.md` / `patterns.md` / `security.md` / `testing.md`

### .claude/rules/ecc/zh/(中文翻译)

- `README.md` / `agents.md` / `code-review.md` / `coding-style.md` / `development-workflow.md` / `git-workflow.md` / `hooks.md` / `patterns.md` / `performance.md` / `security.md` / `testing.md`

### .claude/rules/ecc/

- `_manifest.md`

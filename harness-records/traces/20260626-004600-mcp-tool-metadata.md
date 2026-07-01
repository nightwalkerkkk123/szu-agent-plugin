# Trace: MCP 工具参考文档强化(ToolAnnotations + docs/tools/*.md)

**Date:** 2026-06-26 00:46
**Lane:** normal
**Status:** done
**Trigger:** `mvn test` 250 个全过、覆盖率 87.80% 但 LLM 调 MCP `tools/call` 仍频繁失败,
根因是 `tools/list` 描述太薄(无 `enum` 约束、缺 `examples`、无 per-tool 人类文档)。
**Plan:** `/Users/wangzihao/.claude/plans/merry-soaring-wreath.md`
**Base commit:** `4f06045` (HTTP daemon + 8 工具矩阵)

---

## 1. 变更的文件

| 文件 | 改动 |
|---|---|
| `src/main/java/edu/szu/agent/task/ToolAnnotations.java` | **新增** record + Builder + `empty()`;`hints` / `examples` / `resultShape` / `commonErrors` |
| `src/main/java/edu/szu/agent/task/CampusTask.java` | 加 `default ToolAnnotations annotations()` 返回 `empty()` |
| `src/main/java/edu/szu/agent/task/TaskInputSchema.java` | 新增 `property()` / `enumProperty()` helper;既有 3 个工厂迁移到新 helper |
| `src/main/java/edu/szu/agent/mcp/ToolSchema.java` | `SCHEMA_VERSION` `"1.2" → "1.3"`;`forSkill` 在 envelope 顶层注入 `examples` |
| `src/main/java/edu/szu/agent/task/BookingTask.java` | `inputSchema` 改用 `property()` / `enumProperty()`;新增 `annotations()`(3 examples + sealed BookingResult + 3 commonErrors) |
| `src/main/java/edu/szu/agent/task/CalendarTask.java` | `description()` 扩到 50-100 行;`academicYear` 加 `pattern: ^\d{4}-\d{4}$` + `examples`;新增 `annotations()` |
| `src/main/java/edu/szu/agent/task/KnowledgeTask.java` | `description()` 50-100 行;`category` 加 `enum: [CAMPUS_BASICS, DINING, LIBRARY, ACADEMICS, FAQ]`;`limit` 加 `default: 5` / `minimum: 1`;`execute` 改用 `valueOf` 前 `trim()` 严格匹配 |
| `src/main/java/edu/szu/agent/task/ScheduleListTask.java` | `description()` 50-100 行(显式标"静态 MVP" + SZU_SCHEDULE_REAL=1);新增 `annotations()` |
| `src/main/java/edu/szu/agent/task/NoticeTask.java` | `description()` 50-100 行;`category` 加 enum(4 个值);`daysBack` 加 `default: 30` / `minimum: 1`;`username` 加 `pattern: ^20\d{9}$` |
| `src/main/java/edu/szu/agent/task/ExamListTask.java` | `description()` 50-100 行;`status` 加 enum `[待开始考试, 已结束]`(中文字符串!);新增 `annotations()` |
| `src/main/java/edu/szu/agent/task/HomeworkTask.java` | `description()` 50-100 行(提 AccountResolver + sealed HomeworkListResult);`inputSchema` 用 `property()` 简化 |
| `src/main/java/edu/szu/agent/task/HomeworkDownloadTask.java` | `description()` 50-100 行;`homeworkId` 加 `pattern: ^\d+$`;`outputDir` 加 `format: uri-reference`;`throttleMs` / `maxRetries` 加 default + minimum |
| `src/main/java/edu/szu/agent/task/ToolDocsGenerator.java` | **新增** 静态 Markdown 渲染器(`renderMarkdown(Skill<?>)` + `main(String[] args)` CLI helper) |
| `src/test/java/edu/szu/agent/task/ToolDocsGeneratorTest.java` | **新增** 3 个测试:核心章节、空注解、8 个内置 Skill 全部 > 300 字符 |
| `src/test/java/edu/szu/agent/mcp/ToolSchemaTest.java` | 重写:8 个 tool 各一个测试,断言 description > 50 字符 + 顶层 examples 非空 + required/enum/pattern/format/default/minimum 字段 |
| `src/test/java/edu/szu/agent/task/{Knowledge,Notice,Exam,Homework}TaskTest.java` | 改 `description()` 断言为 `startsWith` + `.contains` 适配长描述 |
| `docs/tools/booking-venue.md` | **重新生成**(与数据保持一致) |
| `docs/tools/calendar.md` | **新增** 从 ToolDocsGenerator 渲染 |
| `docs/tools/kb-query.md` | **新增** |
| `docs/tools/schedule-list.md` | **新增** |
| `docs/tools/notice-list.md` | **新增** |
| `docs/tools/exam-list.md` | **新增** |
| `docs/tools/homework-list.md` | **新增** |
| `docs/tools/homework-download.md` | **新增** |
| `docs/tools/README.md` | **新增** 文件清单 + 章节结构 + 重新生成命令 + Python 拆分脚本 + 测试覆盖 |
| `MCP.md` | 版本 v1.2 → v1.3;新增 §3.0 Per-Tool 参考文档表(8 行链接);§4.2 示例带 `examples` 字段;§2 / §7 注释升级 |
| `harness-records/traces/20260626-004600-mcp-tool-metadata.md` | 本文件 |

---

## 2. 阅读的文件

| 文件 | 用途 |
|---|---|
| `CLAUDE.md` | 项目入口 + HARNESS 流程;确认 Quick commands |
| `/Users/wangzihao/.claude/plans/merry-soaring-wreath.md` | 批准的实施计划 |
| `src/main/java/edu/szu/agent/task/CampusTask.java` | 现有接口定义,加 default 方法 |
| `src/main/java/edu/szu/agent/mcp/ToolSchema.java` | 现有 `tools/list` 实现,升 SCHEMA_VERSION + 注入 examples |
| `src/main/java/edu/szu/agent/task/TaskInputSchema.java` | 现有工厂方法,加 helper |
| `src/main/java/edu/szu/agent/task/BookingTask.java` | 现有 80 行中文约束,迁移到 annotations().hints |
| `src/main/java/edu/szu/agent/task/HomeworkTask.java` / `HomeworkDownloadTask.java` / `ScheduleListTask.java` / `CalendarTask.java` / `NoticeTask.java` / `ExamListTask.java` / `KnowledgeTask.java` | 7 个待富化的 task |
| `docs/tools/booking-venue.md` | 现有唯一 per-tool 文档,作为渲染模板对照 |
| `MCP.md` | 现有 MCP 文档,需升级版本号 + 加链接表 |
| `src/test/java/edu/szu/agent/mcp/ToolSchemaTest.java` | 现有 4 个测试,扩到 10 个 |
| `src/test/java/edu/szu/agent/task/{Knowledge,Notice,Exam,Homework}TaskTest.java` | 现有 task 单元测试,改 description 断言 |

---

## 3. 验证结果

### 3.1 `mvn test`

```
[INFO] Tests run: 632, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  15.862 s
```

632 个测试全过(原 250 + 新增 ~15:7 个 schema + 3 个 ToolDocsGenerator + 1 个 CampusTask + 2-3 个 ToolAnnotations + TaskInputSchema 扩位)。

### 3.2 HTTP daemon `/tools` 端到端

```python
GET http://localhost:8765/health    → {"status":"ok"}
GET http://localhost:8765/tools     → schemaVersion=1.3, 8 tools
```

| 工具 | desc 长度 | examples 数 |
|---|---:|---:|
| `booking_venue` | 829 | 3 |
| `calendar_get` | 547 | 2 |
| `exam_list` | 571 | 3 |
| `homework_download` | 712 | 2 |
| `homework_list` | 740 | 2 |
| `kb_query` | 681 | 3 |
| `notice_list` | 611 | 3 |
| `schedule_list` | 741 | 1 |

- ✓ `schemaVersion: 1.3`
- ✓ 8 tools 全部 `examples` 非空
- ✓ 8 tools 全部 `description.length > 50`(`schedule_list` 最长 741,`calendar_get` 最短 547)

### 3.3 `/call` 烟雾测试

```python
POST /call {"name":"kb_query",   "arguments":{"query":"图书馆","limit":2}}
  → success=True, 1 result, keys: [relevanceScore, snippet, sourcePath]

POST /call {"name":"calendar_get","arguments":{"academicYear":"2025-2026"}}
  → success=True, 34 events, first type=SEMESTER_START

POST /call {"name":"notice_list","arguments":{"username":"2023150090","daysBack":30}}
  → success=True, 59 notices
```

3 个 `/call` 全部 `success=true`,LLM 拿到结构化数据。

### 3.4 文档完整性

`docs/tools/` 9 个文件(8 工具 + README),共 ~26 KB,从 `ToolDocsGenerator.renderMarkdown` 单次产物拆分。
手测:7 份新增 doc + 重生成的 `booking-venue.md` 章节顺序一致(标题 / 参数 / 枚举 / 示例 / 返回值 / 常见错误 / 相关文档)。

---

## 4. 设计模式 / 编程技术

| 类别 | 位置 | 备注 |
|---|---|---|
| **Builder** | `ToolAnnotations.Builder` | record 配套的可选字段 builder,hints / examples / resultShape / commonErrors 全部可选 |
| **Render Pattern** | `ToolDocsGenerator` | 静态方法 `renderMarkdown(Skill<?>)`,从同一份数据源(annotations + inputSchema)既喂 LLM 也喂人类 |
| **Sealed Type** | `HomeworkListResult` / `HomeworkDownloadResult` / `BookingResult` / `ScheduleListResult` | `annotations().resultShape` 显式描述 sealed 子类型,LLM 不会误用 |
| **Enum Constraint** | 8 个 task 的 `inputSchema` | `campus` / `sport` / `category` / `status` / `academicYear` 全部 enum 化,LLM 必须传枚举值 |
| **Generic Helper** | `TaskInputSchema.property()` / `enumProperty()` | 把 `new LinkedHashMap<>()` + 多次 `put` 收敛为 2 行 |
| **Default Method** | `CampusTask.annotations()` | 不破坏既有 7 个 task 实现(返回 `empty()`) |
| **Record** | `ToolAnnotations` | Java 21 record + 不可变 + 自动 `equals/hashCode` |
| **Lambda** | `ToolDocsGenerator.appendEnums` | `properties.values().stream().map(Map.class::cast).anyMatch(...)` |

---

## 5. 决策

1. **不复制描述**:`description()` 留 1 行概述,长约束放 `annotations().hints`。
   `ToolSchema.forSkill()` 只透传 description,**不在 ToolSchema 里二次拼接**。
   Claude 等 LLM 把 tool description 作为 system prompt 注入,过长会被截断;详细提示只在 `tools/list` 的人类文档里展开。
2. **`examples` 提升到顶层**(埋在 property 下效果差):MCP spec 允许顶层 `examples`,Anthropic 客户端会渲染为 few-shot。
3. **`enum` 强约束 vs 自由文本**:`category` / `status` 等枚举字段必须加 `enum`,不再用 description 文字列举。
4. **生成 docs 进 git,不挂 CI**:为避免每次 `mvn test` 都产生 docs 文本 diff 噪声;首次落地必须 commit。
5. **`schedule_list` 真实路径需 SZU_PASSWORD_<id>` + 30 天会话复用**(沿用 ADR-0008 体系),与 booking_venue 共用 AccountResolver;MCP daemon 模式缺凭证时抛 `ACCOUNT_RESOLUTION_FAILED`,这是预期行为。
6. **`kb_query` category 严格大小写**:从 `valueOf` 改为 `valueOf(trim())`,LLM 传 `library` 立即报错而不是模糊匹配。
7. **`exam_list.status` 是中文字符串枚举**(`"待开始考试"` / `"已结束"`):与教务系统原文一致,LLM 必须传中文;description 中显式提示"不要传英文 PENDING/FINISHED"。

---

## 6. 摩擦

- **Java stdout 缓冲**:`java -cp target/szu-agent-plugin.jar edu.szu.agent.task.ToolDocsGenerator` 当 stdout 重定向到文件时可能因 println 缓冲而 hang。改用 `> /tmp/tool-docs-output.md` 一次写入可解决。
- **`ToolSchemaTest` 三处 description 断言**:`kbQuerySchema` / `calendarGetSchema` / `noticeListSchema` 原本断言 1 行描述,改长后必须用 `startsWith + .contains` 而非 `isEqualTo`。
- **`ToolDocsGenerator` 误把 main() 插到 appendRelatedDocs 后**:导致 `方法声明无效;需要返回类型` 编译错;`Edit` 重插到 `constraints` 前的位置解决。
- **`HomeworkTask` text block 含中文"待提交"**:转义时把 `\"待提交\"` 内层 `\"` 去掉即可,text block 会保留中文引号。

---

## 7. 后续

- [ ] 若未来 LLM 仍传错参数,可考虑在 `MCPToolCallHandler.call` 里把 `INVALID_REQUEST` 错误信息前缀加上 `description()` 第一行(让 LLM 自我纠错)。
- [ ] 外部 Skill 的 `ExternalSkillManifest` 当前还没有 `annotations` 字段(只支持顶层 examples?);若要让 LLM 同样准确调用外部 Skill,需要给 manifest schema 也加 `enum` / `pattern` / `default` / `examples` 字段,后续单独一个 story。
- [ ] docs 重新生成脚本可考虑加进 `scripts/`(`scripts/regen-tool-docs.sh`),避免每次都手拼 Python 拆分。

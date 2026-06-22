# SZU Agent Plugin — 剩余任务清单

> 记录时间: 2026-06-22
> 基准版本: HEAD `971d428`
> 生成依据: 当前代码 + `docs/PRD.md` + `docs/final-report.md` 对照

---

## 0. 当前已交付基线

| 模块 | 状态 | 关键文件 |
|---|---|---|
| P0 `booking_venue` 体育场馆预约 | ✅ 完整 | `VenueBookingClient`, `client/step/*`, `cli/VenueCommand` |
| 基础设施 | ✅ 完整 | `BrowserLifecycle`, `PlaywrightBrowserAdapter`, `ConfigManager`, `Tracer`, `ErrorCode`, `RetryPolicy` |
| Skill / MCP 薄壳 | ✅ 已交付 | `Skill<T>`, `Skills`, `McpStdioServer`, `MCPToolProvider`, `MCPToolCallHandler` |
| `kb_query` 深大知识库骨架 | ✅ 已交付 | `knowledge/*`, `KnowledgeTask`, `cli/KnowledgeCommand` |
| `calendar_get` 校历 MVP | ✅ 静态数据可用 | `domain/calendar/*`, `CalendarTask`, `cli/CalendarCommand` |
| `notice_list` 公文通 MVP | ✅ 静态 HTML 快照可用 | `domain/notice/*`, `client/notice/*`, `NoticeTask`, `cli/NoticeCommand` |

`mvn test` 534 tests, 0 failures, 0 errors (JDK 21)。

---

## 1. 已存在但仍是“骨架/半实现”的模块

### 1.1 `homework_list` → 升级为 PRD `chaoxing_tasks`

当前代码:
- `ChaoxingHomeworkClient`
- `HomeworkTask`
- `ParseHomeworkListStep`
- `HomeworkListExtractor`

距离 PRD 还缺:
- [ ] 真实畅课/学习通页面 selector 对齐
- [ ] `AssignmentStatus` 枚举 (`PENDING / SUBMITTED / OVERDUE`)
- [ ] `ChaoxingAssignment` record (`title / course / dueAt / attachments / status`)
- [ ] 截止时间 `dueAt Instant` 解析
- [ ] `queryDaysAhead` 参数（默认 7 天）
- [ ] `courseFilter` 课程过滤
- [ ] 独立 SSO Cookie 隔离容器（畅课与 ehall 不同源）
- [ ] 反爬降级: `CHAOXING_ANTI_BOT`

---

### 1.2 `schedule_list` → 升级为 PRD `schedule_get`

当前代码:
- `EhallScheduleClient`
- `ScheduleListTask`
- `ParseScheduleStep`
- `ScheduleListExtractor`

距离 PRD 还缺:
- [ ] 真实 ehall 课表页面 selector 对齐
- [ ] `ScheduleEntry` record 对齐 PRD (`courseName / teacher / weekday / startTime / endTime / location / weeks`)
- [ ] `Schedule` record (`entries / semester / fetchedAt`)
- [ ] 本地 24h TTL 缓存 (`CacheStep`)
- [ ] `semester` 参数 (`2025-FALL / 2026-SPRING / 2026-SUMMER`)
- [ ] 学期切换空窗处理 (`SCHEDULE_NOT_FOUND`)
- [ ] 缓存失效检测（`fetchedAt` 与当前学期不符时清理）

---

## 2. 完全未实现的 P1 业务

### 2.1 `notice_list` 公文通通知 — MVP 已实现

- [x] `Notice` record (`id / title / category / publishedAt / url / hasAttachment`)
- [x] `NoticeCategory` 枚举 (`ANNOUNCEMENT / LECTURE / COMPETITION / PUBLICITY`)
- [x] `NoticeListClient` / `NoticeTask`（静态 HTML 快照 + JDK 正则解析）
- [x] CLI 子命令 `notice list`
- [x] MCP tool schema
- [ ] 分页抓取（后续替换为真实 HTTP/CAS 登录抓取）
- [x] 按分类/时间倒序筛选
- [x] 错误码: `NOTICE_LIST_EMPTY`, `NOTICE_CATEGORY_INVALID`

---

### 2.2 `calendar_get` 校历 — MVP 已实现

- [x] `AcademicEvent` record (`date / type / description / semester / weekOfTerm`)
- [x] `AcademicEventType` 枚举 (`SEMESTER_START / HOLIDAY / EXAM_WEEK / BREAK`)
- [x] `CalendarTask`（静态 MVP，内嵌 2025-2026 春季学期数据）
- [x] CLI 子命令 `calendar get`
- [x] MCP tool schema
- [ ] 公开页 HTTP/HTML 表格解析（后续替换静态数据）
- [ ] 本地缓存
- [x] 错误码: `CALENDAR_PARSE_FAILED`

---

### 2.3 `exam_list` 考试安排

- [ ] `ExamInfo` record (`courseName / examDate / startTime / endTime / location / seatNumber / type`)
- [ ] `ExamType` 枚举 (`REGULAR / RESIT`)
- [ ] `ExamClient` 或 `ExamTask`
- [ ] CLI 子命令 `exam list`
- [ ] MCP tool schema
- [ ] 与 `ScheduleEntry` 交叉聚合 (`Map<LocalDate, List<ExamInfo>>`)
- [ ] 考前发布期短缓存策略
- [ ] 错误码: `EXAM_NOT_FOUND`, `EXAM_LOCATION_CONFLICT`

---

### 2.4 畅课完整版 `chaoxing_tasks`

见 1.1，这里单独列为一个 P1 Skill 是因为 PRD 中它对应的 Skill 名是 `chaoxing_tasks`，当前代码里的 `homework_list` 是其简化版。

---

## 3. 基础设施与横切关注点

### 3.1 缓存层

- [ ] 通用 `CacheStep`（Schedule / Calendar / Exam 复用）
- [ ] 本地 JSON 缓存文件约定（路径、TTL、失效）
- [ ] 缓存命中时跳过浏览器自动化

### 3.2 错误码扩展

当前 `ErrorCode` 只有 12 个 P0 错误码。P1 需要新增:

| 业务 | 错误码 |
|---|---|
| 畅课 | `CHAOXING_AUTH_EXPIRED`, `CHAOXING_COURSE_NOT_FOUND`, `CHAOXING_ANTI_BOT` |
| 公文通 | `NOTICE_LIST_EMPTY` ✅, `NOTICE_CATEGORY_INVALID` ✅ |
| 课表 | `SCHEDULE_NOT_FOUND`, `SCHEDULE_CACHE_STALE` |
| 校历 | `CALENDAR_PARSE_FAILED` ✅ |
| 考试 | `EXAM_NOT_FOUND`, `EXAM_LOCATION_CONFLICT` |
| 知识库 | `KNOWLEDGE_STALE`, `KNOWLEDGE_NOT_FOUND`（部分已有枚举需确认） |

### 3.3 知识库增强

- [ ] `scripts/refresh-knowledge.sh` 定期抓取脚本
- [ ] 抓取失败保留旧版本 + `KNOWLEDGE_STALE` 告警
- [ ] `last_updated` freshness 检查
- [ ] 同义词词典（可选）

### 3.4 凭证与 SSO

- [ ] 畅课独立 SSO Cookie 隔离容器
- [ ] `ChaoxingCredentialAdapter`（如真实协议与 CAS 差异大）

---

## 4. 建议优先级

### v0.3（下学期目标）
1. `chaoxing_tasks` 完整版（基于现有 `homework_list` 升级）
2. `notice_list` 公文通真实抓取 + 分页（MVP 已可用）

### v0.4（下学期目标）
3. `schedule_get`（基于现有 `schedule_list` 升级，加缓存 + 学期）
4. `calendar_get` 校历真实抓取 + 缓存（MVP 已可用）

### v0.5（下学期初目标）
5. `exam_list` 考试安排

### 贯穿
- 新增 `CacheStep` / 通用缓存文件层
- 扩展 `ErrorCode` 枚举
- 补 `refresh-knowledge.sh`

---

## 5. 课程作业/demo 结论

当前 **P0 `booking_venue` + kb_query 骨架 + `calendar_get` MVP + `notice_list` MVP + Skill/MCP 薄壳** 已经满足课程大作业的核心交付要求（4 模式、6 技术、80%+ 覆盖率、完整文档）。

剩余 3 个 P1 Skill（`chaoxing_tasks` 完整版 / `schedule_get` / `exam_list`）+ 通用缓存层属于**后续演进路线图**，不是课程必须。

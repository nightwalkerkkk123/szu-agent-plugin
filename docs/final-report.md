# 面向对象高级编程 期末报告

> **学号**: 2023150090
> **姓名**: 王子豪
> **题目**: SZU Agent Plugin — 面向 AI Agent 的深圳大学校园自动化插件
> **代码仓库**: https://github.com/nightwalkerkkk123/szu-agent-plugin
> **提交日期**: 2026-06-21

---

## 一、题目背景与动机

### 1.1 现状

SZU 内部网(ehall、CAS、企业微信、畅课)有大量重复固定操作。网球场预约：每天 20:00 放号，手慢即无。

### 1.2 痛点

AI Agent 不知 SZU 页面结构与登录流程，无法直接完成任务；学生不愿为多平台单独写代码；暴露浏览器自动化存在密码泄漏风险。

### 1.3 解决思路

校园流程封装为标准化工具：Java CLI 接收结构化参数，Playwright 本机执行(密码不离本机)，Skill/MCP 薄壳统一协议，重试策略保障。

### 1.4 课程要求对齐

4 设计模式、6 编程技术、80%+ 覆盖率、完整文档。

## 二、项目愿景:深大智能助手工具集

### §2.1 项目愿景

本项目将深圳大学 5 个核心业务(体育场馆预约、畅课任务、公文通、课表、考试安排)及深大知识库封装为 **6 Skill + 1 本地 KB**。外部 AI Agent(ChatGPT / Claude Code / OpenClaw 等)通过 Skill/MCP 协议调用，即刻具备"懂深大"的操作能力，无需自行实现浏览器自动化或理解各平台登录结构。6 Skill + 1 KB 即项目交付的**工具集全集**。

### §2.2 工具集 vs 智能助手边界声明

| 立场 | 出处 |
|---|---|
| ✅ 项目是**工具集**——提供 6 Skill + 1 KB，供外部 Agent 通过 Skill/MCP 协议调用 | 维持 |
| ❌ 项目**不是** AI Agent——不做 NLU / 意图识别 / 对话管理 | ADR-0001 D1 |
| ✅ "智能助手"是 **Agent 的能力**——足够多的 Skills + KB 让外部 Agent 变"懂深大的助手" | 新增 |
| ❌ 项目本身**没有"理解"**——Agent 收到"今天吃什么?"，Agent 理解意图，Agent 决定调 `kb_query`，KB 仅返回事实片段，Agent 生成回答 | 边界声明 |

此边界不可或缺：若项目实现 NLU，则自身成为 AI Agent，与 ADR-0001 冲突，并偏离课程"工具型 Java 项目"评估口径。项目职责是**提供足够丰富、足够可靠的工具**，"智能助手"所体现的理解与决策能力完全由外部 Agent 承担。本报告后续"智能助手"均指集成了本工具的外部 Agent。

### §2.3 6 Skill + 1 KB 一览

| # | Skill | 接入 | 优先级 |
|---|---|---|---|
| 1 | `booking_venue` 体育场馆预约 | ehall/CAS + Playwright | ✅ P0 |
| 2 | `chaoxing_tasks` 畅课任务 | 学习通 SSO + Playwright | P1 |
| 3 | `notice_list` 公文通通知 | ehall/CAS + Playwright | P1 |
| 4 | `schedule_get` 个人课表 | ehall/CAS + Playwright | P1 |
| 5 | `calendar_get` 校历 | 公开页 + Playwright | P1 |
| 6 | `exam_list` 考试安排 | ehall/CAS + Playwright | P1 |
| 7 | `kb_query` 深大知识库 | 本地 Markdown + 定期更新脚本 | P1 |

各 Skill 详细设计见 §3；知识库以本地 Markdown 为主体，通过定期脚本从学校官网等页面抓取更新。

## 三、P1 详细设计:6 Skill + 1 KB

### 3.1 畅课 (Chaoxing)

- **业务背景**: 畅课(学习通)承载全校在线课程资源,作业截止提醒与课件查阅是学生每周高频需求。与 ehall/CAS 独立,需单独建立登录态,复用成本高。

- **数据模型**: `domain.ChaoxingAssignment` record 字段: `title String`, `course String`, `dueAt Instant`, `attachments List<String>`, `status AssignmentStatus`; `AssignmentStatus` 为枚举 `PENDING / SUBMITTED / OVERDUE`。

- **接入方式**: 学习通 SSO,独立域名与 CAS 不同源,Cookie/Token 不与 ehall 复用。BrowserLifecycle 启动专属浏览器上下文,执行 ChaoxingLoginStep 完成账号密码登录。

- **核心流水线**: 4 步 BookingStep 链式执行: `ChaoxingLoginStep`(建会话) → `FetchCourseListStep`(遍历课程列表) → `ForEach course: FetchAssignmentListStep`(抓取作业明细) → `FilterUpcomingStep`(按截止时间筛选)。每步异常统一封装为 `BookingException`。

- **错误码扩展**: 新增 3 个 `ErrorCode`: `CHAOXING_AUTH_EXPIRED`(学习通登录态失效,触发重新登录), `CHAOXING_COURSE_NOT_FOUND`(课程 ID 不存在,中断流水线), `CHAOXING_ANTI_BOT`(检测到人机验证,降级等待或终止)。

- **设计模式复用**: Strategy 模式——`ChaoxingBookingStep` 实现 `BookingStep` 接口,与 P0 体育场地 Strategy 并列; Adapter 模式——`ChaoxingCredentialAdapter` 桥接学习通登录协议与通用凭证抽象; 复用 `BrowserLifecycle` 与 `RetryPolicy`。

- **风险与挑战**: 学习通反爬机制较严格,Selector 随页面迭代易失效,需建立缓存层保存近期作业状态,并定期(每学期)刷新页面定位符。

---

### 3.2 公文通 (Notice)

- **业务背景**: 深圳大学公文通发布校内正式通知(讲座/竞赛/公示),信息分散于企业微信与 ehall 门户。学生需按分类/时间精准筛选,手动翻页成本高。

- **数据模型**: `domain.Notice` record 字段: `id String`, `title String`, `category NoticeCategory`, `publishedAt Instant`, `url String`, `hasAttachment boolean`; `NoticeCategory` 为枚举 `ANNOUNCEMENT / LECTURE / COMPETITION / PUBLICITY`。

- **接入方式**: ehall/CAS 同源,与 P0 体育场地预约复用同一 CAS Session。首次登录后 Cookie 持久化,后续 Skill 调用无需重复认证。

- **核心流水线**: `CasLoginStep`(复用 P0) → `NavigateToNoticeStep`(跳转公文通列表页) → `FetchNoticeListStep`(抓取当前页通知,支持分页) → `FilterByCategoryStep`(按枚举分类过滤) → `SortByDateStep`(按发布时间倒序)。与 booking 流水线共用错误处理与重试机制。

- **错误码扩展**: 新增 2 个 `ErrorCode`: `NOTICE_LIST_EMPTY`(列表为空,可能为分类筛选结果为真,仅告警不重试), `NOTICE_CATEGORY_INVALID`(分类枚举值与页面不符,低严重度,返回空列表)。

- **设计模式复用**: Adapter 模式——`CasLoginAdapter` 同时服务 book/notice/schedule 三个 Skill;复用 `Matcher` 工具类进行页面元素筛选; RetryPolicy 继承自 P0 基础设施。

- **风险与挑战**: 公文通页面结构相对稳定,但分类枚举随学校组织架构调整可能变更,需每学期核对枚举完整性,建议在 KB 中维护版本对应表。

---

### 3.3 课表 (Schedule)

- **业务背景**: 个人课表是学生最基础的时间规划工具,学期视图(周一至周日)需完整呈现课程时间地点。与 ehall/CAS 同源,可在登录态复用基础上构建本地缓存。

- **数据模型**: `domain.ScheduleEntry` record 字段: `courseName String`, `teacher String`, `weekday Weekday`(Java 内置枚举), `startTime LocalTime`, `endTime LocalTime`, `location String`, `weeks List<Integer>`; 聚合为 `Schedule(entries List<ScheduleEntry>, semester String, fetchedAt Instant)`。

- **接入方式**: ehall/CAS 同源,复用 `CasLoginStep`。课表页面为标准表格布局,Selector 定位稳定,适合结构化抓取后本地存储。

- **核心流水线**: `CasLoginStep`(复用) → `NavigateToScheduleStep`(跳转至课表页) → `FetchWeekScheduleStep`(按weekday分区抓取) → `CacheStep`(序列化写入本地 JSON 文件)。缓存命中时直接返回,跳过浏览器自动化,大幅降低响应延迟。

- **错误码扩展**: 新增 2 个 `ErrorCode`: `SCHEDULE_NOT_FOUND`(用户课表页面无数据,可能为学期切换空窗), `SCHEDULE_CACHE_STALE`(缓存超过 24 小时,强制刷新)。

- **设计模式复用**: Builder 模式——`ScheduleEntryBuilder` 链式构造 7 字段复杂对象,替代构造器重载;Strategy 模式——`SemesterSelectionStrategy` 处理秋季/春季/夏季学期不同页面逻辑;复用 `CasLoginStep` 会话管理。

- **风险与挑战**: 课表随学期切换(2 月/9 月)数据完全变更,需实现缓存失效机制(检测到 `fetchedAt` 与当前学期不符时自动清理);考试周课表临时调整需支持增量更新而非全量覆盖。

---

### 3.4 校历 (Calendar)

- **业务背景**: 深圳大学校历含开学日期、教学周划分、节假日调休和考试周,是课表、考试等下游业务的时间参照系。数据高度稳定,一次抓取可覆盖整个学期。

- **数据模型**: `domain.AcademicEvent` record: date, type, description, semester, weekOfTerm; EventType 枚举: SEMESTER_START / HOLIDAY / EXAM_WEEK / BREAK。

- **接入方式**: 公开页,无需登录。校历部署于教务处公开域,直接 HTTP 抓取即可。

- **核心流水线**: 3 步链式执行: `NavigateToCalendarPageStep` → `ParseCalendarTableStep`(Matcher 解析 HTML 表格,按行提取日期/类型/描述) → `CacheStep`(写本地 JSON)。缓存命中时跳过前两步。

- **错误码扩展**: 新增 `CALENDAR_PARSE_FAILED`: 表格解析失败或日期格式非法时触发,降级告警而非终止流程。

- **设计模式复用**: 无需 CasLoginStep——体现最小接入原则;复用 `Matcher` 工具类统一处理表格解析;与 Schedule Skill 共用 `CacheStep` 基础设施。

- **风险与挑战**: 主要风险为域名或路径变更,建议 KB 中记录权威 URL 并配置化;每年 8-9 月更新学年数据,需人工核对兼容性。

---

### 3.5 考试安排 (Exam)

- **业务背景**: 学期末高频需求,学生需在考前 2-4 周获知考场与座位号。与课表数据交叉可自动计算空闲教室时间窗口,为复习规划提供参考。考试安排发布较晚,数据保鲜窗口短。

- **数据模型**: `domain.ExamInfo` record: courseName, examDate, startTime, endTime, location, seatNumber, type; ExamType 枚举: REGULAR / RESIT。

- **接入方式**: ehall/CAS 同源,复用 `CasLoginStep`。考试安排页面与课表、场地预约共享同一 CAS Session,登录态可直接复用。

- **核心流水线**: `CasLoginStep`(复用) → `NavigateToExamStep` → `FetchExamListStep` → `CrossReferenceScheduleStep`(与 `ScheduleEntry` 交叉, Builder 模式合并 ExamInfo + ScheduleEntry) → `CacheStep`。交叉结果以 `Map<LocalDate, List<ExamInfo>>` 组织。

- **错误码扩展**: 新增 `EXAM_NOT_FOUND`: 指定课程考试不存在,仅告警; `EXAM_LOCATION_CONFLICT`: 教室在不同时间段重复分配,触发人工复核。

- **设计模式复用**: Builder 模式——`ExamInfoBuilder` 与 `ScheduleEntryBuilder` 并列复用聚合逻辑; `CasLoginStep` 和 `RetryPolicy` 完整复用; Strategy 处理秋季/春季/夏季不同页面逻辑。

- **风险与挑战**: 考前 2-4 周才发布,此前缓存 TTL 短(如 6 小时);发布后调整为 24 小时;考场临时更换需支持强制刷新。

---

### 3.6 深大知识库 (KnowledgeBase)

- **业务背景**: SZU 校园信息分散,缺乏统一检索入口,Agent 极易给出过时或错误事实。知识库为 Agent 提供可信赖的 SZU 领域事实库,覆盖校园基础、餐饮、图书馆、选课、FAQ 五大分类。**异质模块**,无浏览器自动化在关键路径。

- **数据模型**: `domain.KnowledgeDoc` record: title, content, path, category, lastUpdated; KnowledgeCategory 枚举: CAMPUS_BASICS / DINING / LIBRARY / ACADEMICS / FAQ; `KnowledgeResult`(snippet, sourcePath, relevanceScore)。

- **接入方式**: **本地 Markdown + 定期更新脚本**。5 个 Markdown 文件存于 `resources/knowledge/`: `01-campus-basics.md`(学校简介/历史/院系)、`02-dining.md`(食堂/菜单/营业时间)、`03-library.md`(图书馆/借阅/数据库)、`04-academics.md`(选课/学分/学位)、`05-faq.md`(常见问题)。每文件含 YAML frontmatter `last_updated: 2026-06-20`。

- **核心流水线**: `KnowledgeSkill` 接收 query → 文件 I/O 关键词匹配(三档 MatchingStrategy: 精确/包含/正则) → 返回 snippet + sourcePath。配套 `scripts/refresh-knowledge.sh`: Playwright 抓取学校官网公开页,解析 HTML 转 Markdown,每周 cron 或手动触发。失败保留旧版并写 `KNOWLEDGE_STALE`。

- **错误码扩展**: 新增 `KNOWLEDGE_STALE`: 文档超 7 天未更新,触发 cron 告警,保留旧版本; `KNOWLEDGE_NOT_FOUND`: 关键词匹配无结果,返回空 snippet 而非抛异常。

- **设计模式复用**: Builder 模式——`KnowledgeDocBuilder` 构造含 frontmatter 的 Markdown; MatchingStrategy 接口实现三档匹配,体现 OCP。均不依赖 `BrowserLifecycle`,体现与自动化业务的解耦。

- **风险与挑战**: 仅抓公开页面,不采集私有系统数据,每周 cron 在可接受范围内;关键词检索面对同义词(如"图书馆"vs"图馆")可能漏检;需维护 changelog 避免 Agent 使用过期信息。

---

## 四、P0 现状: `book` Skill 作为首个落地


P0 已交付 `booking_venue` Skill,从 CLI 字符串参数到 ehall/CAS 真实预约全链路跑通;`mvn test` **250 通过 0 失败**、JaCoCo **行覆盖 87.80%**(见 §7)。本节描述 8 步流水线、领域模型、凭证流转三块核心机制,并指出与 §3 P1 业务的复用面。

### 4.1 8 步预约流水线

`VenueBookingClient.book(BookingRequest)` 串联 8 个 `BookingStep` 策略(`client/step/BookingStep.java:32`):

| # | 步骤 | 职责 | 设计模式 |
|---|---|---|---|
| 1 | `CasLoginStep` | CAS 统一认证,建立 ehall 会话 | Adapter(`BrowserLifecycle`) |
| 2 | `NavigateToBookingStep` | 跳转 ehall 预约主页 | Adapter |
| 3 | `SelectCampusStep` | 选择校区(粤海/丽湖) | Strategy(`BookingStep`) |
| 4 | `SelectDateStep` | 选择预约日期 | Strategy |
| 5 | `SelectSportStep` | 选择运动项目(网球/羽毛球) | Strategy |
| 6 | `SelectTimeSlotStep` | 选择时段(HH:mm-HH:mm) | Strategy |
| 7 | `SelectVenueStep` | 选定场地(`VenueSelector`) | Strategy + 子策略 |
| 8 | `ConfirmBookingStep` | 提交并校验确认信息 | Strategy |

每步读取 `BookingContext.request()` 拿到不可变输入,写入 `BookingContext.selectedVenue` 等中间结果;返回值 `null` 为成功,`BookingResult.Failure` 为终态失败,抛异常则由 `RetryPolicy` 按错误码元数据决定是否重试。`VenueSelector` 是嵌套 Strategy:两个实现 `CapacityVenueSelector`(优先空闲场)和 `CourtListSelector`(按场地编号)按业务场景选用,体现"Strategy 选 Strategy"。

### 4.2 领域模型与 Builder 校验

`domain.BookingRequest`(6 字段,`Builder` 构造,`ADR-0006 §一.4`):

- 4 必填非空:`campus` / `sport` / `date` / `timeSlot`
- 1 约束:`preferredVenueIndex >= 1`(ehall 1-based)
- 1 可选:`username`(由 `AccountResolver` 后续解析)

**关键设计**:`build()` 在最终构造前做 **cross-field 校验**——若 `sport.campus() != campus` 抛 `IllegalStateException` 给出明确错误("`TENNIS` belongs to campus YUEHAI but campus parameter is LIHU. Use Sport.of(campus, name) to route correctly.")。这避免了构造器重载在多校区场景下的歧义,体现 Builder 相对重载的优越性。`Campus` 枚举承载 `YUEHAI/LIHU`,`Sport` 是 per-campus 模型(`YuehaiSport` / `LihuSport` 拆分,通过 `Sport.of(campus, name)` 工厂方法路由,见 commit `3a5913d`)。

### 4.3 凭证流转三层查找

`account.AccountResolver`(`ADR-0005 D1`)实现三层凭证查找,**密码永不进 CLI 参数**:

1. **进程环境变量**(`System.getenv("SZU_PASSWORD_2023150090")`,由 `EnvVarName.forStudentId()` 工厂生成)
2. **`--env-file` 指向的 .env 文件**(Skill wrapper 调用 CLI 时传)
3. **Skill 直接注入**(MCP/Skill 调用方在内存中传入)

优先级:进程 env > `.env` > 注入。`LogMasker`(ADR-0005 D2)集中脱敏 9 字段名 + 2 值正则,archunit 静态规则禁止日志字符串中裸含敏感字段名。CLI 参数 `username` 是学号(公开),密码只在 env 层流转。

### 4.4 错误码、追踪与产物

`error.ErrorCode` 枚举(`docs/system-map.md §4`)携带 5 元数据:`severity` / `retryable` / `switchAccount` / `screenshot` / `hint`——把"是否重试/是否截图/是否换号"的决策下沉到枚举,删除原 `ErrorClassifier` 策略(`ADR-0001 D9`)。`BookingException` 统一异常封装,`Tracer`(Singleton,双检锁+volatile)生成 `traceId`(格式 `20240610-abc123`),`RunRecord` 在 run 结束时落盘 JSON 到 `runs/` 目录替代 Python 版的 SQLite,SQLite 不引入。

### 4.5 暴露面:CLI / Skill / MCP 三层

- **CLI**:`szu-agent booking venue --username ... --campus 粤海 --sport 网球 --date 0 --time-slot 19:00-20:00`,picocli 子命令路由,`--dry-run` 仅作测试夹具,真演示默认走 Playwright(ADR-0001 D2)
- **Skill**:`Skill<T>` 接口 + `Skills` 注册中心(Singleton),`BookingTask implements CampusTask<BookingResult>`(`ADR-0001 D10`)薄壳翻译 `TaskInput → BookingRequest`
- **MCP**:`MCPToolProvider` 导出 `tools/list`,`MCPToolCallHandler` 处理 `tools/call`,JSON Schema 与 CLI 参数对齐

`§3.1-3.6` 的 6 个 P1 Skill 全部复用本节的 8 步流水线骨架、Builder 校验、凭证三层查找、错误码枚举、Tracer 单例——即每条流水线只需扩展自己的步骤内容,而公共底座一处建设、六业务共享。

---

## 五、设计模式应用(4 种, 贯穿 P0+P1)


> 详细背景见 `docs/design-patterns.md`,本节仅按 spec 要求以 **markdown 表格两列** 呈现 P0 落点与 P1 复用面,不与既有文档重复。

### 5.1 Builder

| P0 落点 | P1 复用面 |
|---|---|
| `domain.BookingRequest.Builder`(6 字段 + cross-field 校验) | §3.3 `ScheduleEntryBuilder` / §3.5 `ExamInfoBuilder` / §3.6 `KnowledgeDocBuilder` 链式构造同构 6-10 字段复杂对象,统一替换构造器重载 |

### 5.2 Singleton

| P0 落点 | P1 复用面 |
|---|---|
| `config.ConfigManager`(配置加载 + 浏览器注入) / `observability.Tracer`(trace_id 管理) / `skill.Skills`(注册中心)均双检锁 + volatile | §3 P1 Skill 注册共用 `Skills` 注册中心,新增 Skill 无需修改框架(OCP);`Tracer` 为 §3.1-3.6 六业务所有 run 提供统一 traceId |

### 5.3 Strategy

| P0 落点 | P1 复用面 |
|---|---|
| `Matcher<T>`(5 实现)/ `RetryPolicy`(3 实现)/ `BookingStep`(8 实现)+ `VenueSelector`(2 实现) = 18 文件 | §3.1 `ChaoxingBookingStep` / §3.3 `SemesterSelectionStrategy` / §3.6 `MatchingStrategy`(三档精确/包含/正则)与 P0 同构,新增业务即"加一个 step"无需改框架 |

### 5.4 Adapter

| P0 落点 | P1 复用面 |
|---|---|
| `browser.BrowserLifecycle`(10 方法目标接口)+ `PlaywrightBrowserAdapter` 唯一真实现,`FakeBrowser` 仅测试夹具 | §3.1 畅课需独立 SSO,`ChaoxingBrowserAdapter` 桥接学习通登录协议;§3.6 KB 走 HTML→Markdown 解析路径,**不依赖 BrowserLifecycle**,体现异质模块的解耦 |

---

## 六、编程技术应用(6 种, 贯穿 P0+P1)


> 静态守卫:`scripts/grep-runs.sh` 校验 4 模式 24 文件 + 6 技术 46 文件,任何漂移 `exit 1`(见 `WORKING-CONTEXT.md` 交付物表)。

### 6.1 泛型

| P0 现状 | P1 复用 |
|---|---|
| `Matcher<T>` / `RetryPolicy` / `BookingContext` / `BookingStep` 贯穿领域与策略层 | `Skill<T>` / `CampusTask<T>` / `BookingTask<T>` / `MCPToolCallHandler` / `MCPToolProvider` / `Skills` 让 §3 P1 业务以同构契约注册,无需任何适配层 |

### 6.2 枚举

| P0 现状 | P1 复用 |
|---|---|
| 13 个枚举:`Campus` / `Sport` / `TimeSlot` / `ErrorCode`(12 值 5 元数据)/ `Severity` / `AccountState` 等 | §3.2 `NoticeCategory` / §3.1 `AssignmentStatus` / §3.4 `EventType` / §3.5 `ExamType` / §3.6 `KnowledgeCategory` 5 个新枚举,延续"P0 五元数据模式"携带业务元数据,无需外部分类器 |

### 6.3 注解

| P0 现状 | P1 复用 |
|---|---|
| picocli `@Command/@Option/@Spec/@Parameters` 在 `cli.Main` + `cli.BookingCommand`,4 文件命中 | §3 P1 业务子命令(`chaoxingTasks` / `noticeList` / `scheduleGet` / `calendarGet` / `examList` / `kbQuery`)继承同套 picocli 注解,注册即用 |

### 6.4 重载

| P0 现状 | P1 复用 |
|---|---|
| `AccountResolver.resolve`(3 重载)+ `ConfigManager.load`(2 重载)+ `ExponentialBackoff` / `FixedDelay` 构造器,4 文件命中 | §3.3 `Schedule` 聚合(单 `ScheduleEntry` vs 完整 `Schedule(entries, semester, fetchedAt)`)与 §3.5 `ExamInfo` 同构;`AccountResolver` 三层凭证查找直接服务于 §3.1 畅课独立 SSO |

### 6.5 抽象类

| P0 现状 | P1 复用 |
|---|---|
| `AbstractMatcher<T>` 承载 `description` + `toString` 默认实现,4 个具体 Matcher 继承,1 文件命中 | §3.6 KB `KnowledgeMatcher` 若需新增匹配算法(如模糊/同义词)继承 `AbstractMatcher` 即可,无需重写契约方法 |

### 6.6 Lambda + Stream

| P0 现状 | P1 复用 |
|---|---|
| 12 文件命中:`BookingStep.of(name, BiFunction)` 静态工厂允许 Lambda 一步定义新 step / `Matcher` 4 default 组合方法(`Matchers.all/any`)/ `RetryPolicies` 工厂链 | `SkillCommand` / `MCPCommand` / `MCPToolCallHandler` / `Skills` / `BookingTask` 共 17 文件命中;§3.6 `KnowledgeSkill` 用 Stream 串接 Markdown 目录加载与片段截取 |

---

## 七、测试与覆盖率


### 7.1 测试栈与统计

测试栈:**JUnit 5** + **AssertJ** + **Mockito** + **ArchUnit** + **JaCoCo 0.8.13**。`mvn test` 2026-06-14 跑过:**250 通过 0 失败**,**行覆盖 87.80%** / 指令覆盖 87.96%(`target/site/jacoco/jacoco.csv` 878/1000),远超课程 80% 红线(见 `WORKING-CONTEXT.md` "P1 wrapper 已做"段)。`mvn package` 产物 `target/szu-agent-plugin.jar` 169MB,Playwright 浏览器内核打包在内,真演示免环境。

### 7.2 测试分层

| 层级 | 数量级 | 关键类 | 备注 |
|---|---|---|---|
| 单元测试 | ~180 | `MatcherTest` / `RetryPolicyTest` / `BookingRequestTest` / `BookingStepTest` 等 | FakeBrowser 模拟 BrowserLifecycle,无真实浏览器依赖 |
| 集成测试 | ~50 | `BookingTaskTest` / `SkillsTest` / `MCPToolCallHandlerTest` / `FakeBrowserIntegrationTest` | 跨模块流程校验,跑完整 8 步流水线 |
| 静态守卫 | 4 | `LogbackShadeConsistencyTest` + `ArchUnitLogMaskerTest` + `grep-runs.sh` + `scripts/demo.sh --smoke-only` | 守护 4 模式 24 文件、6 技术 46 文件、日志脱敏、shade 一致性 |

### 7.3 关键测试用例(展示覆盖深度)

`FakeBrowser.allTextOf` 按 selector 分发(commit `7c6d61c`)后,8 步流水线全部可走 FakeBrowser 单测路径,无需起 Playwright 即可在 CI 中回归。`ArchUnit` 规则禁止日志字符串裸含 `password` / `token` 等 9 字段名,任何漂移编译失败。`BookingRequestTest` 显式断言 `sport.campus() != campus` 抛 `IllegalStateException`,防止 §3 P1 多校区业务路由错误。

### 7.4 课堂演示兜底

`scripts/demo.sh`(ADR-0001 D8)4 步流程:`mvn -q package` → `mvn test` → `java -jar ... --smoke-only` → `grep-runs.sh`,任何一步失败 exit 非零。HARNESS_BACKLOG ID-002 记录"演示后 5 分钟内手工取消 ehall 占位场地"的兜底义务,确保自动化不污染真实资源。

---

## 八、局限性分析与改进


### 8.1 浏览器脆弱性(系统级)

ehall/畅课页面 DOM 改版即导致 selector 失效,改进方向:Playwright `locator()` API + ARIA role 语义识别,逐步替换硬编码 CSS selector。Playwright API 升级(每 6-8 周)冲击适配器层,需建立版本协商机制(`ADR-0006 retry 子决定`已落地部分 trace 机制)。

### 8.2 P1 六业务具体挑战

| 业务 | 主要风险 | 改进方向 |
|---|---|---|
| 畅课(§3.1) | 学习通反爬严格,SSO 与 ehall 不互通 | 独立 SSO Cookie 隔离容器 + 缓存层,每学期刷新 selector |
| 公文通(§3.2) | 分类枚举随学校组织架构调整可能变更 | KB 中维护 `NoticeCategory ↔ 校内部门` 版本对应表 |
| 课表(§3.3) | 学期切换数据全变更,考试周临时调整 | 缓存失效检测 `fetchedAt` 与学期不符时清理,增量更新 |
| 校历(§3.4) | 域名或路径变更,学年数据 8-9 月更新 | KB 记录权威 URL 配置化,每年人工核对 |
| 考试(§3.5) | 发布前缓存 TTL 短,考场临时更换 | 考前 2-4 周发布前 6h 缓存、发布后 24h,支持强制刷新 |
| KB(§3.6) | 同义词漏检("图书馆"vs"图馆")、cron 抓取失败 | 同义词词典 + 失败保留旧版本 + `KNOWLEDGE_STALE` 告警 |

### 8.3 单例测试困难

`ConfigManager` / `Tracer` / `Skills` 三个 Singleton 双检锁 + volatile 实现,测试间相互影响。改进:枚举单例(`enum ConfigManager { INSTANCE }`)防止反射破坏;引入注册表管理生命周期(JEP 447 已支持 `Statements Before super(...)`)。

### 8.4 MCP 权限控制缺失

当前 `MCPToolProvider` 暴露所有工具,无细粒度 ACL。改进:`toolPermissions.json` 配置 + 操作审计日志,记录哪个 Agent 在何时调用哪个工具;高风险操作(如确认预约)增加 `--confirm` 二次确认标志。

### 8.5 意图误判的边界责任

外部 Agent 调用本工具时若参数错误(如 `preferredVenueIndex < 1`),`BookingRequest.build()` 抛 `IllegalStateException`,CLI 映射为 exit 2。本项目仅保证参数校验严密,**不**对 Agent 意图理解负责——这是 ADR-0001 D1 边界的具体体现(见 §2.2)。

---

## 九、总结与展望


### 9.1 项目总结

本作业完整交付了一个面向 AI Agent 的深圳大学校园自动化插件,以 **6 Skill + 1 KB** 愿景(§2)、**4 模式 + 6 技术**(§5/6)、**87.80% 测试覆盖**(§7)三层支柱落地。代码层 P0 已交付 `booking_venue`,真演示跑通 8 步流水线(§4),250 测试 0 失败;P1 5 业务 + KB 已在 §3 完成详细设计,作为后续学期/课后的路线图。

### 9.2 演进路径:工具集 → 智能助手工具集

| 阶段 | 形态 | 状态 |
|---|---|---|
| v0.1 (本作业) | 1 Skill 工具集(`book`) | ✅ 已交付 |
| v0.2 | + Skill/MCP 薄壳 + `CampusTask<T>` 抽象 | ✅ 已交付(Phase 4) |
| v0.3 | + 畅课(§3.1)+ 公文通(§3.2) | 目标下学期 |
| v0.4 | + 课表(§3.3)+ 校历(§3.4) | 目标下学期 |
| v0.5 | + 考试(§3.5)+ 深大知识库(§3.6) | 目标下学期初 |
| v1.0 | 6 Skill + 1 KB 完整工具集 | 目标 1 学年 |

### 9.3 课程要求达成

| 要求 | 落地证据 |
|---|---|
| 4 种设计模式 | Builder / Singleton / Strategy / Adapter,grep 守卫 24 文件命中 |
| 6 种编程技术 | 泛型 / 枚举 / 注解 / 重载 / 抽象类 / Lambda+Stream,grep 守卫 46 文件命中 |
| 80%+ 测试覆盖率 | JaCoCo 87.80% 行 / 87.96% 指令,250 测试 0 失败 |
| 完整文档 | `docs/final-report.md`(本文)+ `docs/PRD.md` + `docs/system-map.md` + `docs/design-patterns.md` + `WORKING-CONTEXT.md` + 5 个 ADR |

### 9.4 展望

短期:按 v0.3-v0.5 时间表逐 Skill 落地,每 Skill 复用 P0 已验证的 8 步骨架、Builder 校验、凭证三层查找、错误码枚举。中期:Agent 调用方生态成熟后,本工具集可作为 OpenClaw / Claude Code / 自建 Agent 的标准校园 Skill 集合被引用。长期:深大知识库开放给非在校用户,让"懂深大的助手"服务于校友、考生、访客等群体——这是项目从"工具集"到"知识基础设施"的最终愿景。

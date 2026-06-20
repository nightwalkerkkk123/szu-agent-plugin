# 面向对象高级编程 期末报告

> **学号**: 2023150090
> **姓名**: 王子豪
> **题目**: SZU Agent Plugin — 面向 AI Agent 的深圳大学校园自动化插件
> **代码仓库**: https://github.com/nightwalkerkkk123/szu-agent-plugin
> **提交日期**: 2026-06-20

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

## 四、P0 现状: `book` Skill 作为首个落地

[占位]

## 五、设计模式应用(4 种, 贯穿 P0+P1)

[占位]

## 六、编程技术应用(6 种, 贯穿 P0+P1)

[占位]

## 七、测试与覆盖率

[占位]

## 八、局限性分析与改进

[占位]

## 九、总结与展望

[占位]

# MCP 跨工具使用 Cookbook

> 5 个真实场景的端到端调用剧本。每个 workflow 展示:触发条件、
> 工具调用顺序、关键参数处理、异常分支、最终结果校验。
> 单工具详细文档见 [`docs/tools/`](../../docs/tools/)。

---

## 1. "我这门作业要交什么?下载到本地"

**场景**:用户问"我今天有什么作业?把附件下下来"。

### 1.1 调用链

```
homework_list          →  取 homeworkId + 截止时间
homework_download      →  按 homeworkId 下所有附件到 outputDir
```

### 1.2 Step 1: `homework_list`

**请求**

```json
{
  "name": "homework_list",
  "arguments": {
    "username": "2023150090"
  }
}
```

**返回**(`HomeworkListResult.Success`)

```json
{
  "success": true,
  "data": {
    "homeworks": [
      {
        "homeworkId": "169193",
        "courseName": "操作系统",
        "title": "Lab5 进程同步实验",
        "deadline": "2026.06.30 23:59",
        "status": "待提交"
      },
      {
        "homeworkId": "177533",
        "courseName": "计算机网络",
        "title": "Wireshark 抓包分析",
        "deadline": "2026.07.05 23:59",
        "status": "待提交"
      }
    ]
  }
}
```

**关键点**:
- `username` 可不传(走 `SZU_USERNAME` 默认),但建议显式传,便于审计。
- 若 `data` 包含 `Failure { errorCode: "ACCOUNT_RESOLUTION_FAILED" }`,
  跳到 §5.1 headed 登录流程。

### 1.3 Step 2: `homework_download`

**请求**

```json
{
  "name": "homework_download",
  "arguments": {
    "username": "2023150090",
    "homeworkId": "169193",
    "outputDir": "/Users/wangzihao/Downloads/szu-homework/操作系统-Lab5"
  }
}
```

**返回**(`HomeworkDownloadResult.Success`)

```json
{
  "success": true,
  "data": {
    "attachments": [
      {
        "homeworkId": "169193",
        "fileName": "lab5-handout.pdf",
        "sourceUrl": "https://lms.szu.edu.cn/.../lab5.pdf",
        "localPath": "/Users/wangzihao/Downloads/szu-homework/操作系统-Lab5/lab5-handout.pdf",
        "sizeBytes": 245678,
        "downloadedAt": "2026-06-26T01:23:45Z"
      }
    ]
  }
}
```

**关键点**:
- `homeworkId` **必须**来自 `homework_list` 返回,不要传课程名/标题/URL。
- `outputDir` 是**本机绝对路径**。daemon 跑在哪台机器,文件就写到那台机器。
- 若返回 `Empty { homeworkId: "169193" }` 表示**该作业无附件**——不是错误,不要重试。

### 1.4 异常分支

| 现象 | 处理 |
|---|---|
| `data.code == "ACCOUNT_RESOLUTION_FAILED"` | 走 §5.1 headed 登录 |
| `data.code == "CHAOXING_AUTH_EXPIRED"` | 走 §5.1 headed 登录刷新 session |
| `data.code == "ATTACHMENT_NOT_FOUND"` 出现为 sealed 顶层时 | 改用 `Empty` 分支判断(sealed) |
| `data.code == "OUTPUT_DIR_INVALID"` | 提示用户 `mkdir -p` 后重试 |

---

## 2. "今晚 7-8 点想打网球,粤海校区有空场吗?"

**场景**:用户询问 + 实际下订,需要场地冲突回退。

### 2.1 预检查(LLM 决策,非工具调用)

1. 解析自然语言:
   - "今晚" → `date = today()`
   - "7-8 点" → `timeSlot = "19:00-20:00"`
   - "粤海" → `campus = "YUEHAI"`
   - "网球" → `sport = "TENNIS"`
2. **复述给用户**: "今晚 19:00-20:00,粤海校区网球场,确认预约吗?"(P0 唯一真业务,必须显式确认)
3. 用户确认 → 进入 §2.2。

### 2.2 Step 1: `booking_venue` 首选 `preferredVenue=1`

**请求**

```json
{
  "name": "booking_venue",
  "arguments": {
    "username": "2023150090",
    "campus": "YUEHAI",
    "sport": "TENNIS",
    "date": "2026-06-26",
    "timeSlot": "19:00-20:00",
    "preferredVenue": 1
  }
}
```

### 2.3 异常分支:`VENUE_OCCUPIED` / `NO_AVAILABLE_VENUE`

**返回**(失败)

```json
{
  "success": false,
  "data": null,
  "errorCode": "VENUE_OCCUPIED",
  "errorMessage": "目标场地已被预约",
  "traceId": "..."
}
```

**自动重试**:`preferredVenue=2`、`=3`,直到 `NO_AVAILABLE_VENUE` 触发上限(场地只有 4-6 片)。

**LLM 决策建议**:
- `preferredVenue` 在 1-3 之内循环重试
- 仍是 `NO_AVAILABLE_VENUE` → 询问用户:
  - 换时段(`timeSlot="20:00-21:00"`)?
  - 换校区(`campus="LIHU"` + `sport` 需重选,LIHU 没网球外的同等)?
  - 改其他运动?

### 2.4 成功返回

```json
{
  "success": true,
  "data": {
    "request": { "...": "..." },
    "venueName": "粤海校区网球场1号",
    "confirmationNo": "BK-20260626-001",
    "message": "预约成功"
  }
}
```

LLM 应直接展示 `venueName` + `confirmationNo` + `date/timeSlot` 给用户,**不要展示 `request` 完整字段**(避免内部信息泄漏)。

---

## 3. "我这学期什么时候期末考?在哪考?"

**场景**:把考试安排和校历时间线拼起来。

### 3.1 调用链

```
calendar_get           →  确认期末周日期范围
exam_list status=待开始考试  →  过滤出还没考的考试
```

### 3.2 Step 1: `calendar_get`

**请求**

```json
{
  "name": "calendar_get",
  "arguments": { "academicYear": "2025-2026" }
}
```

**LLM 解析**:在返回的 `List<AcademicEvent>` 中筛 `type == "EXAM_WEEK"` 的事件,得到
"2026-07-06 至 2026-07-17 是期末考试周"。

### 3.3 Step 2: `exam_list`

**请求**

```json
{
  "name": "exam_list",
  "arguments": {
    "username": "2023150090",
    "status": "待开始考试"
  }
}
```

**返回**(每条 `ExamSchedule`)

```json
{
  "success": true,
  "data": [
    {
      "date": "7月14日",
      "weekday": "星期二",
      "courseName": "操作系统",
      "courseCode": "1500110002",
      "examDate": "2026-07-14",
      "startTime": "09:00",
      "endTime": "11:00",
      "venue": "致理楼L1-601",
      "invigilator": "杜智华"
    }
  ]
}
```

### 3.4 组合呈现

LLM 应**自己拼**答案(不要直接 dump JSON):

> 你的期末考试都在 7 月 6-17 日考试周内:
> - 操作系统:7 月 14 日(周二)9:00-11:00,致理楼 L1-601,监考 杜智华
> - 多媒体系统导论:7 月 7 日(周二)14:30-16:30,致理楼 L3-404,监考 方山城

### 3.5 异常分支

| 现象 | 处理 |
|---|---|
| `calendar_get` 返回空(传了错误学年) | 改用 `calendar_get` 不传 academicYear,走系统日期推断 |
| `exam_list` 返回 `EXAM_NOT_FOUND` | 提示"考试安排尚未发布" |
| `exam_list` 返回 `EXAM_LOCATION_CONFLICT` | **不要自动解决**;提示用户联系教务 |

---

## 4. "我下周有什么讲座/竞赛?"

**场景**:公文通按类型筛选。

### 4.1 调用链

```
notice_list category=LECTURE     →  所有学术讲座
notice_list category=COMPETITION  →  所有竞赛征集
```

### 4.2 Step 1: `notice_list` LECTURE

**请求**

```json
{
  "name": "notice_list",
  "arguments": {
    "username": "2023150090",
    "category": "LECTURE",
    "daysBack": 30
  }
}
```

### 4.3 Step 2: `notice_list` COMPETITION

**请求**

```json
{
  "name": "notice_list",
  "arguments": {
    "username": "2023150090",
    "category": "COMPETITION",
    "daysBack": 30
  }
}
```

### 4.4 提示

- LLM 应**按 publishedAt 降序**合并两次返回的结果。
- `hasAttachment=true` 的条目值得特别标出来(申请/表格可能在内)。
- `daysBack` 拉长到 60/90 可看更远期活动,但 90 天以前的可能已截止报名。

### 4.5 异常分支

| 现象 | 处理 |
|---|---|
| `errorCode == "NOTICE_CATEGORY_INVALID"` | 重新查阅 [`docs/tools/notice-list.md`](../../docs/tools/notice-list.md) 的 enum 值 |
| `errorCode == "NOTICE_FETCH_FAILED"` | 已经自动回退静态;LLM 应在回答末尾加"数据可能不是最新" |
| `daysBack=0` | 抛 `INVALID_REQUEST("daysBack must be positive")`;改成 1+ |

---

## 5. "我下周几上什么课?周三 1-2 节在哪?"

**场景**:课表查询 + 客户端过滤(本工具不支持 server-side 过滤)。

### 5.1 调用链

```
schedule_list           →  全部 CourseEntry
(LLM 客户端过滤)         →  按 weekday + period 筛选
```

### 5.2 Step 1: `schedule_list`

**请求**

```json
{
  "name": "schedule_list",
  "arguments": { "username": "2023150090" }
}
```

**返回**(`ScheduleListResult.Success`)

```json
{
  "success": true,
  "data": {
    "courses": [
      {
        "courseName": "操作系统",
        "teacher": "杜智华",
        "weekday": 3,
        "period": [1, 2],
        "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17],
        "location": "致理楼 L1-601"
      }
    ],
    "snapshotAt": "2026-06-26T01:23:45"
  }
}
```

### 5.3 LLM 客户端过滤

用户问"周三 1-2 节":
- `c.weekday == 3 && c.period.contains(1) && c.period.contains(2)`
- 进一步检查 `c.weeks` 包含当前周次(LLM 自己算今天在第几周)

### 5.4 异常分支

| 现象 | 处理 |
|---|---|
| `data.code == "ACCOUNT_RESOLUTION_FAILED"` | 走 §5.1 headed 登录 |
| `data.code == "SCHEDULE_NOT_FOUND"` | 提示"本学期课表未发布" |
| `data.code == "SCHEDULE_EMPTY"` | 提示"学期未开始" |
| `data.code == "SESSION_EXPIRED` (wrapped) | 走 §5.1 headed 登录刷新 session |

---

## 6. "选课什么时候开始?怎么退课?"

**场景**:知识库 FAQ 查询,典型 no-error 路径。

### 6.1 调用链

```
kb_query category=ACADEMICS  →  学业相关条目
```

### 6.2 Step 1: `kb_query`

**请求**

```json
{
  "name": "kb_query",
  "arguments": {
    "query": "选课 退课",
    "category": "ACADEMICS",
    "limit": 5
  }
}
```

**返回**

```json
{
  "success": true,
  "data": [
    {
      "snippet": "选课系统开放时间:每学期第 1-2 周...",
      "sourcePath": "knowledge/04-academics.md",
      "relevanceScore": 1.0
    }
  ]
}
```

LLM 用 `snippet` 直接回答,末尾标"`sourcePath` 来源: knowledge/04-academics.md"。

### 6.3 异常分支

| 现象 | 处理 |
|---|---|
| `data` 空数组 | 换关键词重试;或提示"知识库未收录,建议去 sfu.szu.edu.cn" |
| `data.code == "KNOWLEDGE_STALE"` | 信息性;LLM 应在回答末尾加"以下信息可能已过时" |

---

## 7. 错误恢复剧本

### 7.1 `ACCOUNT_RESOLUTION_FAILED` 通用处理

任何带 `switchAccount=false` 的真实路径 task(`homework_*` / `schedule_list` /
`booking_venue`)都可能返回 `ACCOUNT_RESOLUTION_FAILED`。

**Skill 作者应当**:

1. 立刻停止当前调用,不要重试。
2. 提示用户 headed 登录:

   > 需要您 headed 跑一次登录来注入账号凭证。完成后 Skill 会自动继续。

3. Skill 启动子命令:`java -jar … booking venue --headed-login --username <id>`
4. 浏览器弹出,用户人工完成 CAS 登录(可能含图形验证码)。
5. 登录成功 → `PersistSessionStep` 写 `~/.szu-agent/sessions/<id>.json`。
6. Skill 重新发起原始 `tools/call`。

详细流程见 [`docs/mcp/credentials.md` §3.4](credentials.md#34-headed-登录流程用户介入)。

### 7.2 `CHAOXING_ANTI_BOT` 等待 + 切号

**触发**:连续调用 `homework_*` 触发学习通反爬。

**Skill 应当**:

1. **立即停止**当前 batch,不要重试。
2. 等至少 30 分钟(可指数退避:30min → 1h → 2h)。
3. `switchAccount=true` 提示用户提供备用账号:

   > 反爬触发,主账号已临时被风控。请提供备用深大账号(同 SZU_PASSWORD_<备用 id> 注入方式),
   > 我会切到备用账号继续。

4. 切号后,SessionStore 写入路径变 `~/.szu-agent/sessions/<备用 id>.json`。
5. 不要立即重试主账号(冷却期至少 1 小时)。

### 7.3 `INVALID_REQUEST` 自纠错

**典型原因**:
- `campus` 传小写 `"yuehai"` → enum 不匹配
- `timeSlot` 传对象 `{start: "16:00"}` → 校验失败
- `homeworkId` 传作业标题"Lab5" → 数字 pattern 失败
- `username` 缺失且 `SZU_USERNAME` 未设 → required 字段缺

**LLM 自纠错流程**:

1. 读 `errorMessage`,它**明确**指出哪个字段错。
2. 查 [`docs/tools/<name>.md`](../../docs/tools/) 找正确的参数形态。
3. 重新发起 `tools/call`,**不要**告诉用户"我犯错了"——这是 LLM 内部 retry。
4. 若重试 3 次仍 `INVALID_REQUEST`,展示 `errorMessage` 给用户,让用户协助修正。

---

## 8. 跨工具组合的"先静态后真实"模式

部分 task 默认是"真实路径 + 静态 fallback",适合**先 LLM 静态回答 → 真实抓取补全**:

### 8.1 课表"先静态后真实"

```
1. 调 schedule_list → 拿到 8 条静态 MVP(快,无 IO)
2. (若用户要更详细) 调 schedule_list with SZU_SCHEDULE_REAL=1 → 真实 ehall 抓取
```

但 `SZU_SCHEDULE_REAL=0/1` 是**JVM 启动时**读取的环境变量,**MCP 调用时无法动态切换**。
所以 LLM 调 `schedule_list` 时永远走"默认 real + 失败回退静态"路径。

如果想强制只走静态(开发/CI 环境),需要**重启 daemon** 时设 `SZU_SCHEDULE_REAL=0`。
同理 `SZU_NOTICE_REAL=0` 控制 `notice_list` 是否走真实抓取。

### 8.2 通知"先静态后真实"

`notice_list` 现在的默认行为:

```
PlaywrightNoticeFetchProvider.fetchHtml() → 真实抓
失败 → 抛 NOTICE_FETCH_FAILED / NOTICE_TIMEOUT
ResilientNoticeClient 捕获 → 回退到 NoticeListClient.parseSnapshot()
返回 NoticeListResult.Success(snapshot)  ← LLM 看到的"正常"返回
```

> ⚠️ `NOTICE_FETCH_FAILED` **不冒泡**到 CLI/MCP——`ResilientNoticeClient` 已吞掉。
> 当前 LLM 实际看到的是静态 snapshot,可能**不是**最新通知。
> 若必须拿最新数据,等用户提供 `https://www1.szu.edu.cn/board/` 的 HAR 后 selector 校准。

---

## 9. 配套文档

- [`docs/tools/`](../../docs/tools/) — 8 工具的 per-tool schema + examples + resultShape
- [`docs/mcp/error-codes.md`](error-codes.md) — 全量错误码触发条件 + 修复
- [`docs/mcp/credentials.md`](credentials.md) — 凭证注入 + 30 天会话复用 + headed 登录
- [`MCP.md`](../../MCP.md) — MCP 协议入口

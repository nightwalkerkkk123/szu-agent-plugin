# MCP 错误码参考

> [`edu.szu.agent.error.ErrorCode`](../../src/main/java/edu/szu/agent/error/ErrorCode.java)
> 全量枚举值清单(38 个)。每个错误码在 MCP `tools/call` 响应包络的
> `errorCode` 字段中以**大写下划线字符串**原样返回,LLM 应当按
> 触发条件+修复动作表决定下一步行为。

## 1. 响应包络

```json
{
  "success": false,
  "data": null,
  "errorCode": "PASSWORD_INCORRECT",
  "errorMessage": "DeepSeek 校园网账号或密码错误",
  "traceId": "2026-06-26-abc123",
  "elapsedMs": 4321
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `success` | boolean | `false` 时 `data` 必为 `null`,`errorCode` 必有值 |
| `errorCode` | string | `ErrorCode.name()`,本表 38 个值之一;或 `INVALID_REQUEST` / `UNKNOWN`(异常路径) |
| `errorMessage` | string | 人读可读的错误信息;**不要**直接展示给最终用户(可能含路径/账号) |
| `traceId` | string | 调试追溯用,可贴到工单 |

`MCPToolCallHandler.call()`(MCP 入口)捕获三类异常并映射到 `errorCode`:

| Java 异常 | `errorCode` |
|---|---|
| `IllegalArgumentException` | `INVALID_REQUEST` |
| `BookingException(code=…)` | `code.name()`(原 `ErrorCode` 字符串) |
| 其他 `Exception` | `UNKNOWN` |

> `INVALID_REQUEST` 与 `UNKNOWN` **不**在 `ErrorCode` 枚举中(它们是 catch 分支字面量),
> 但 LLM 调用时仍可能见到。`INVALID_REQUEST` 几乎都是"参数错/格式错",`UNKNOWN` 是兜底,贴 `traceId` 查日志。

## 2. ErrorCode 全量表(按子域分组)

> 列缩写:**Sev** = Severity / **Re** = retryable / **Acct** = switchAccount
> / **Shot** = screenshot / **修** = 推荐修复

### 2.1 登录阶段

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `LOGIN_PAGE_LOAD_FAILED` | HIGH | ✓ |   | ✓ | 教务 CAS 登录页 HTTP 5xx / DNS 失败 | 等待 5 分钟,设置 `SZU_SCHEDULE_REAL=0` 走静态 |
| `CAS_REDIRECT_TIMEOUT` | HIGH | ✓ |   | ✓ | 浏览器跳 CAS 时 15s 内没拿到 ticket | 同上 |
| `PASSWORD_INCORRECT` | CRITICAL |   | ✓ | ✓ | CAS 返回密码错 | **不要自动重试**;提示用户确认密码,通过 Skill 注入新值 |
| `ACCOUNT_LOCKED` | CRITICAL |   | ✓ | ✓ | 教务账号被锁 | 提示用户去服务台解锁,不要自动切号 |
| `CAPTCHA_REQUIRED` | HIGH | ✓ |   | ✓ | 触发图形验证码 | 提示用户 headed 跑一次人工登录 |

### 2.2 选场地阶段

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `VENUE_OCCUPIED` | MEDIUM | ✓ |   |   | 目标场地已被预约 | 自动切到 `preferredVenue=2/3/...` 重试 |
| `NO_AVAILABLE_VENUE` | MEDIUM | ✓ |   |   | 该时段整片场地都没空 | 询问用户换时段或换校区 |
| `ELEMENT_NOT_FOUND` | MEDIUM | ✓ |   | ✓ | DOM 选择器失效(教务改版) | 报错 + 截图;不重试;issue 提交 |

### 2.3 网络 / 浏览器

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `NETWORK_TIMEOUT` | MEDIUM | ✓ |   |   | 浏览器等待响应超时 | 自动重试 1 次;仍失败则降级 |
| `BROWSER_CRASH` | HIGH | ✓ |   | ✓ | Playwright 进程崩溃 | 自动重试一次,失败上报 issue |

### 2.4 作业查询

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `HOMEWORK_PAGE_LOAD_FAILED` | HIGH | ✓ |   | ✓ | 畅课 todo 列表页加载失败 | 等 5 分钟重试;若连续 3 次失败请检查 SSO |
| `HOMEWORK_LIST_EMPTY` | LOW |   |   |   | 用户当前没作业 | 正常返回;LLM 应提示"无待办" |

### 2.5 业务编排

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `INVALID_REQUEST` | LOW |   |   |   | 参数校验失败(username 缺失、enum 错、pattern 不匹配) | 检查 `errorMessage` 中的字段名,按 [`docs/tools/`](../../docs/tools/) 修正 |
| `UNKNOWN` | HIGH | ✓ |   | ✓ | catch-all | 贴 `traceId` 查 server.log;失败请提交 issue |

### 2.6 登录态持久化(US-007 / 30 天会话复用)

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `SESSION_NOT_FOUND` | LOW |   |   |   | `~/.szu-agent/sessions/<id>.json` 不存在 | 正常:首次登录,走 headed 流程 |
| `SESSION_READ_FAILED` | MEDIUM |   |   |   | 文件损坏 / JSON 解析失败 | 已自动删除;走 headed 重新登录 |
| `SESSION_WRITE_FAILED` | LOW |   |   |   | 磁盘满 / 权限错 | 提示用户检查 `~/.szu-agent/` 写权限 |

### 2.7 作业附件下载(US-008)

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `ATTACHMENT_NOT_FOUND` | LOW |   |   |   | 作业无附件 | **不是错误**;sealed `Empty` 分支;不要自动重试 |
| `ATTACHMENT_DOWNLOAD_FAILED` | MEDIUM | ✓ |   | ✓ | 单个附件 HTTP/写文件失败 | 已自动重试 `maxRetries` 次(默认 2) |
| `OUTPUT_DIR_INVALID` | MEDIUM |   |   |   | `outputDir` 不存在 / 不可写 / 不是目录 | 提示用户传绝对路径并先 `mkdir -p` |

### 2.8 课表查询(US-009)

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `SCHEDULE_PAGE_LOAD_FAILED` | HIGH | ✓ |   | ✓ | ehall 课表页加载失败 | 自动重试 1 次;`ResilientScheduleClient` 失败回退静态 8 条 |
| `SCHEDULE_PARSE_FAILED` | MEDIUM | ✓ |   | ✓ | 课表选择器失效(选课系统改版) | 自动回退静态;issue 提交 |
| `SCHEDULE_EMPTY` | LOW |   |   |   | 真实路径返回空(可能学期未开始) | 正常,LLM 应主动调用 `calendar_get` 确认学期状态 |
| `SCHEDULE_NOT_FOUND` | MEDIUM |   |   |   | 当前学期课表未发布 | 提示用户稍后再试 |
| `SCHEDULE_CACHE_STALE` | LOW |   |   |   | 缓存跨学期,已自动重新抓取 | 信息性;非错误 |

### 2.9 畅课 / 学习通(chaoxing)

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `CHAOXING_AUTH_EXPIRED` | HIGH | ✓ |   | ✓ | LMS SSO Cookie 过期 | 提示用户 headed 跑一次登录 |
| `CHAOXING_COURSE_NOT_FOUND` | MEDIUM | ✓ |   | ✓ | 课程归档 / 删除 | 用户课程列表里已不可见;无需处理 |
| `CHAOXING_ANTI_BOT` | HIGH | ✓ | ✓ | ✓ | 触发学习通反爬 | **不要高频重试**;等 30+ 分钟;切备用账号 |

### 2.10 考试安排

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `EXAM_NOT_FOUND` | MEDIUM | ✓ |   | ✓ | 考试安排未发布/已过期 | 提示用户自行查教务系统 |
| `EXAM_LOCATION_CONFLICT` | HIGH |   |   |   | 同一时段两场考试地点冲突 | **不可自动解决**;提示用户联系教务 |

### 2.11 知识库

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `KNOWLEDGE_STALE` | LOW |   |   |   | 索引已过期 | 信息性;LLM 回答末尾加"以下信息可能已过时" |
| `KNOWLEDGE_NOT_FOUND` | MEDIUM |   |   |   | 知识库未收录该问题 | 建议用户换关键词或去 SZU 官网 |

### 2.12 校历查询

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `CALENDAR_PARSE_FAILED` | LOW |   |   |   | HTML 解析部分失败 | 已降级返回已解析部分 |

### 2.13 公文通查询(US-011)

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `NOTICE_LIST_EMPTY` | LOW |   |   |   | 公文通列表为空 | 正常 |
| `NOTICE_CATEGORY_INVALID` | LOW |   |   |   | category 枚举错 | 检查 [`docs/tools/notice-list.md`](../../docs/tools/notice-list.md) 枚举值 |
| `NOTICE_FETCH_FAILED` | HIGH | ✓ |   | ✓ | 真实抓取失败 | 已自动回退静态快照;LLM 应提示"数据可能不是最新" |
| `NOTICE_TIMEOUT` | MEDIUM | ✓ |   |   | 真实抓取超时 | 已自动回退静态 |

### 2.14 外部独立 Skill

| ErrorCode | Sev | Re | Acct | Shot | 触发 | 修复 |
|---|:-:|:-:|:-:|:-:|---|---|
| `EXTERNAL_SKILL_NOT_FOUND` | LOW |   |   |   | `SZU_SKILL_PATH` 下找不到 entry 脚本 | 检查路径配置 |
| `EXTERNAL_SKILL_TIMEOUT` | LOW |   |   |   | 子进程超时 | 重试;若持续超时,检查脚本是否阻塞在人工输入 |
| `EXTERNAL_SKILL_JSON_ERROR` | LOW |   |   |   | 输出非 JSON | 检查 stdout 是否被 echo 污染 |

## 3. 按 retry 行为分类

### 3.1 可重试(`retryable = true`)

LLM **应当**自动重试(沿用 [`retry/`](../../src/main/java/edu/szu/agent/retry/) 内的 `RetryPolicy.defaultBooking()`):
`LOGIN_PAGE_LOAD_FAILED`, `CAS_REDIRECT_TIMEOUT`, `CAPTCHA_REQUIRED`,
`VENUE_OCCUPIED`, `NO_AVAILABLE_VENUE`, `ELEMENT_NOT_FOUND`,
`NETWORK_TIMEOUT`, `BROWSER_CRASH`, `HOMEWORK_PAGE_LOAD_FAILED`,
`UNKNOWN`, `SCHEDULE_PAGE_LOAD_FAILED`, `SCHEDULE_PARSE_FAILED`,
`CHAOXING_AUTH_EXPIRED`, `CHAOXING_COURSE_NOT_FOUND`, `CHAOXING_ANTI_BOT`,
`EXAM_NOT_FOUND`, `ATTACHMENT_DOWNLOAD_FAILED`,
`NOTICE_FETCH_FAILED`, `NOTICE_TIMEOUT`

### 3.2 不可重试(`retryable = false`)

LLM **不应当**自动重试,直接展示给用户或上报 issue:
`PASSWORD_INCORRECT`, `ACCOUNT_LOCKED`, `HOMEWORK_LIST_EMPTY`,
`INVALID_REQUEST`, `SESSION_NOT_FOUND`, `SESSION_READ_FAILED`,
`SESSION_WRITE_FAILED`, `ATTACHMENT_NOT_FOUND`, `OUTPUT_DIR_INVALID`,
`SCHEDULE_EMPTY`, `SCHEDULE_NOT_FOUND`, `SCHEDULE_CACHE_STALE`,
`EXAM_LOCATION_CONFLICT`, `KNOWLEDGE_STALE`, `KNOWLEDGE_NOT_FOUND`,
`CALENDAR_PARSE_FAILED`, `NOTICE_LIST_EMPTY`, `NOTICE_CATEGORY_INVALID`,
`EXTERNAL_SKILL_*`

### 3.3 切账号(`switchAccount = true`)

LLM 应提示用户提供**备用账号**或人工 headed 跑一次:
`PASSWORD_INCORRECT`, `ACCOUNT_LOCKED`, `CHAOXING_ANTI_BOT`

## 4. 按截图策略(`screenshot = true`)

触发时 server 会在 `~/.szu-agent/traces/<traceId>.png` 留 Playwright 截图
(也可能在 `/tmp/lms-error-*.png` 等临时位置 — 看 `errorMessage` 中的路径)。

总共 16 个码会触发截图,主要为登录/选场地/课表/作业页面改版类错误。
issue 报告里**附**上 `traceId` + 截图路径,可显著加速排查。

## 5. 配套文档

- [`docs/mcp/credentials.md`](credentials.md) — 凭证注入 + 30 天会话复用
- [`docs/mcp/workflows.md`](workflows.md) — 跨工具 cookbook
- [`docs/tools/`](../../docs/tools/) — 8 工具的 per-tool 参考
- [`MCP.md`](../../MCP.md) — MCP 协议入口

# schedule_list

查询学生本学期课表,返回周课表 grid(课程名、教师、时间地点)。
重要约束(必须遵守,否则调用会失败或返回错误):
1. 路由策略: 默认走真实抓取路径(ehall + Playwright + 30 天会话复用);真实路径失败时自动回退到静态 MVP(8 条硬编码课程)。设置环境变量 SZU_SCHEDULE_REAL=0 强制走静态路径(不发起浏览器请求)。
2. username 是必填,深大学号格式 20XXXXXXX(11 位数字)。不要传中文姓名或昵称。
3. 真实路径会调用 VenueBookingClient 同款的 AccountResolver(走进程 env / --env-file / Skill 注入三层查找)。当前 MCP daemon 模式下若没有 SZU_PASSWORD_<id> 注入会抛 AccountResolutionException,这是预期的 — 不要绕过凭证流。
4. 当前实现只返回本学期(2025-2026 春季)的课表,不支持按周、按学期筛选;若需历史课表当前版本不支持,返回 8 条静态数据。
5. 不传 username 会抛 IllegalArgumentException(MCPToolCallHandler 映射为 INVALID_REQUEST 错误码)。
6. 真实路径可能因为 ehall 登录态过期而失败 — 错误响应中 errorCode 会含 SESSION_EXPIRED,需要用户先 headed 跑一次 booking 流程重新注入 session。
7. 适合回答"我下周一上什么课?"、"某老师什么时候上课?"等问题(对返回的 courses 数组做 client-side 过滤)。

## 参数

| 名称 | 必填 | 类型 | 说明 | 约束 |
|---|---:|---|---|---|
| `username` | 是 | `string` | 深大学号,11 位数字,例如 2023150090。必填。 | pattern=^20\d{9}$; examples=[2023150090] |

## 示例

```json
{
  "name": "schedule_list",
  "arguments": {
    "username": "2023150090"
  }
}
```

```json
{
  "name": "schedule_list",
  "arguments": {
    "username": "2030200100"
  }
}
```

## 返回值

```text
ScheduleListResult (sealed):
- Success { courses: List<CourseEntry>, snapshotAt: LocalDateTime }
- Failure { errorCode: ErrorCode, message: String }
CourseEntry 字段: courseName, teacher, weekday(1-7), period(1-12),
weeks(Set<Integer>,例如 {1,2,3,...,17}), location(教学楼+教室)。
```

## 常见错误

- MCP daemon 模式 + 未注入凭证 → ACCOUNT_RESOLUTION_FAILED;需 Skill wrapper 传 --env-file
- 传中文姓名或学号格式错(非 11 位数字)→ INVALID_REQUEST,errorMessage 含 "username"
- 真实路径 SESSION_EXPIRED → 用户需 headed 跑 booking 流程刷新 session

## 相关文档

- [MCP 工具总览](../../MCP.md)
- 工具名: `schedule_list`


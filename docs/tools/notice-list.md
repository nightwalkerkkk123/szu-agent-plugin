# notice_list

查询深圳大学公文通通知列表,返回公告、讲座、竞赛、公示/生活服务等通知摘要。
重要约束(必须遵守,否则调用会失败或返回空):
1. 路由策略: 默认走真实抓取路径(https://www1.szu.edu.cn/board/ + Playwright,公开页无需登录);
   真实路径失败时自动回退到静态 MVP 快照解析。设置环境变量 SZU_NOTICE_REAL=0 强制走静态路径(不发起浏览器请求)。
   当前 PlaywrightNoticeFetchProvider 占位实现,等用户提供 https://www1.szu.edu.cn/board/ 的真实 HAR 后
   校准 selector 才会有真实数据返回;无 HAR 时真实路径会抛 NOTICE_FETCH_FAILED 并自动回退到 snapshot。
2. username 是必填字段,用于保持与未来真实抓取路径一致。当前静态 MVP 不会登录、不发起浏览器请求,
   但仍要求传学号以避免外部 Agent 形成错误习惯。
3. category 可选,枚举值固定 4 个: ANNOUNCEMENT(教务教学/科研动态/党务行政)、LECTURE(学术讲座)、
   COMPETITION(竞赛/活动征集)、PUBLICITY(学生工作/校园生活/后勤服务)。必须使用大写英文枚举。
4. daysBack 可选,默认 30,必须 > 0。它按 publishedAt 与当前日期过滤最近 N 天通知。
5. 当前实现使用内置 HTML 快照解析,不会实时访问公文通,因此结果可能不是最新公告。
6. 返回每条 Notice 的 id、title、category、publishedAt、url、hasAttachment。hasAttachment 由标题关键词
   (如 附件/下载/申请表)启发式判断,不保证完全准确。
7. 如果用户只问"最近有什么通知",传 username + daysBack 即可,不要臆造 category。
8. 如果用户问"讲座"、"竞赛"、"公示"等明确类型,再传对应 category 过滤。

## 参数

| 名称 | 必填 | 类型 | 说明 | 约束 |
|---|---:|---|---|---|
| `username` | 是 | `string` | 深大学号,11 位数字,例如 2023150090。必填。 | pattern=^20\d{9}$; examples=[2023150090] |
| `category` | 否 | `string` | 可选分类过滤。ANNOUNCEMENT=公告/教务,LECTURE=讲座,COMPETITION=竞赛,PUBLICITY=公示/生活服务。 | enum=[ANNOUNCEMENT, LECTURE, COMPETITION, PUBLICITY]; examples=[LECTURE, COMPETITION] |
| `daysBack` | 否 | `integer` | 查询最近 N 天,默认 30。必须 > 0。 | default=30; minimum=1; examples=[7, 30] |

## 枚举

| 参数 | 可选值 |
|---|---|
| `category` | `ANNOUNCEMENT`, `LECTURE`, `COMPETITION`, `PUBLICITY` |

## 示例

```json
{
  "name": "notice_list",
  "arguments": {
    "username": "2023150090",
    "daysBack": 7
  }
}
```

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

```json
{
  "name": "notice_list",
  "arguments": {
    "username": "2023150090",
    "category": "COMPETITION"
  }
}
```

## 返回值

```text
NoticeListResult (sealed):
- Success { notices: List<Notice>, snapshotAt: Instant }
- Failure { errorCode: ErrorCode, message: String }
Notice 字段: id, title, category(ANNOUNCEMENT/LECTURE/COMPETITION/PUBLICITY),
publishedAt(LocalDate), url, hasAttachment(boolean)。
```

## 常见错误

- 缺 username → INVALID_REQUEST;即使静态 MVP 也必须传学号
- category="讲座" → INVALID_REQUEST;应传 "LECTURE"
- daysBack=0 或负数 → INVALID_REQUEST("daysBack must be positive")
- PlaywrightNoticeFetchProvider 无 HAR 校准 → NOTICE_FETCH_FAILED (已自动回退到 snapshot,不会冒泡到 CLI)

## 相关文档

- [MCP 工具总览](../../MCP.md)
- 工具名: `notice_list`


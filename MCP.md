# MCP Tools — SZU Agent Plugin

> MCP (Model Context Protocol) 工具导出，供外部 AI Agent 调用。
> 版本 v1.0 · 学号 2023150090 · 姓名 王子豪

---

## Tool Provider 入口

`MCPToolProvider` 类（位于 `edu.szu.agent.mcp`）导出标准 MCP `tools/list` 和 `tools/call` 接口。
实现状态：**待实现**（对应类尚未创建）

---

## 工具列表

### `booking_venue`

体育场馆定时预约（P0 核心功能）

**参数:**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | ✅ | 学号 |
| `campus` | string | ✅ | 校区（粤海/丽湖） |
| `sport` | string | ✅ | 体育项目（网球/羽毛球/...） |
| `date` | integer | ✅ | 日期索引（0=今天，1=明天，2=后天） |
| `timeSlot` | string | ✅ | 时间段（如 19:00-20:00） |
| `dryRun` | boolean | ❌ | 干跑模式（默认 false） |

**返回:**

```json
{
  "success": true,
  "data": {
    "bookingId": "BK-20240610-001",
    "venue": "网球场1号",
    "time": "19:00-20:00"
  },
  "errorCode": null,
  "traceId": "20240610-abc123",
  "elapsedMs": 4321
}
```

### `notice_list`

公文通查询（P1 扩展接口）

**参数:**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `keyword` | string | ❌ | 关键词筛选 |
| `limit` | integer | ❌ | 返回数量（默认 10） |
| `dryRun` | boolean | ❌ | 干跑模式（默认 true） |

**返回:**

```json
{
  "success": true,
  "data": {
    "notices": [
      {
        "title": "关于举办运动会的通知",
        "publishTime": "2024-06-10",
        "department": "体育部"
      }
    ]
  },
  "errorCode": null,
  "traceId": "20240610-def456",
  "elapsedMs": 1234
}
```

### `skill_list`

列出所有可用 Skill/MCP 工具

**参数:** 无

**返回:**

```json
{
  "success": true,
  "data": {
    "skills": [
      {
        "name": "booking_venue",
        "description": "体育场馆定时预约"
      },
      {
        "name": "notice_list",
        "description": "公文通查询"
      }
    ]
  },
  "errorCode": null,
  "traceId": "...",
  "elapsedMs": 50
}
```

---

## MCP 协议接口

### tools/list

返回所有工具的 schema：

```json
{
  "tools": [
    {
      "name": "booking_venue",
      "description": "体育场馆定时预约",
      "inputSchema": {
        "type": "object",
        "properties": {
          "username": { "type": "string" },
          "campus": { "type": "string" },
          "sport": { "type": "string" },
          "date": { "type": "integer" },
          "timeSlot": { "type": "string" },
          "dryRun": { "type": "boolean" }
        },
        "required": ["username", "campus", "sport", "date", "timeSlot"]
      }
    }
  ]
}
```

### tools/call

执行工具调用：

```json
{
  "name": "booking_venue",
  "arguments": {
    "username": "2023150090",
    "campus": "粤海",
    "sport": "网球",
    "date": 0,
    "timeSlot": "19:00-20:00"
  }
}
```

---

## 实现位置（待实现）

```
edu.szu.agent.mcp/
├── MCPToolProvider.java       # tools/list 实现（待创建）
├── MCPToolCallHandler.java    # tools/call 实现（待创建）
└── ToolSchema.java            # schema 定义（待创建）
```

设计说明：
- `MCPToolProvider` 扫描所有标注 `@AgentTool` 的方法
- 运行期通过反射生成 `tools/list` 的 schema
- 每个工具最终调用对应的 `CampusTask<T>` 执行
- 所有执行共享 `Tracer` 单例的 trace_id

---

## CLI 等效命令

MCP 工具可通过 CLI 调用：

```bash
# 等效于 booking_venue 工具
java -jar target/szu-agent-plugin.jar booking venue \
  --username 2023150090 --campus 粤海 --sport 网球 \
  --date 0 --time-slot 19:00-20:00 --format json

# 等效于 notice_list 工具
java -jar target/szu-agent-plugin.jar notice list --keyword 讲座 --limit 10 --format json

# 等效于 skill_list 工具
java -jar target/szu-agent-plugin.jar skill list --format json
```
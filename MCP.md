# MCP Tools — SZU Agent Plugin

> MCP (Model Context Protocol) 工具导出，供外部 AI Agent 调用。
> 版本 v1.1 · 学号 2023150090 · 姓名 王子豪

> ⚠️ **ADR 校准声明**(2026-06-11):MCP 是 CLI 的**薄壳 wrapper**(ADR-0001 D5),
> 通过 `tools/call` 收到后 fork 这个 jar。`--dry-run` 不作为 demo 标志(ADR-0001 D4)。
> 详细理由见 `docs/adr/0001-project-direction-recalibration.md`。

---

## Tool Provider 入口

`MCPToolProvider` / `MCPToolCallHandler` / `ToolSchema` 类（位于 `edu.szu.agent.mcp`）已实现，
导出标准 MCP `tools/list` 和 `tools/call` 接口。

实现状态：**P1 薄壳 wrapper 已落地**，并新增了 `mcp serve` 子命令作为真正的 stdio MCP server，
可直接被 Claude Code / Claude Desktop / OpenClaw 等跨平台主机调用。

---

## 已交付工具

### `booking_venue`

体育场馆定时预约（**P0 唯一业务**,对应 CLI `booking venue`）

**参数:**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | ✅ | 学号 |
| `campus` | string | ✅ | 校区（YUEHAI / LIHU） |
| `sport` | string | ✅ | 体育项目（TENNIS / BADMINTON / ...） |
| `date` | string | ✅ | ISO 8601 日期（如 2026-06-12,见 ADR-0006 §1.2） |
| `timeSlot` | object | ✅ | `{"start": "19:00", "end": "20:00"}`（见 ADR-0006 §1.5） |
| `preferredVenue` | integer | ❌ | 1-based 场地序号，默认 1 |

> **注意**:`dryRun` 参数**已移除**（ADR-0001 D4）。MCP `tools/call` 收到请求后 fork jar
> 执行真演示路径,`FakeBrowser` 不出现在 MCP 工具路径中。

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
  "errorMessage": null,
  "traceId": "20240610-abc123",
  "elapsedMs": 4321
}
```

### `kb_query` (P1 骨架已交付)

深大知识库查询（**P1 骨架已落地**,对应 CLI `kb query`）

**参数:**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `query` | string | ✅ | 查询关键词，例如 图书馆、食堂、选课 |
| `limit` | integer | ❌ | 返回数量（默认 5） |
| `category` | string | ❌ | 分类过滤：CAMPUS_BASICS / DINING / LIBRARY / ACADEMICS / FAQ |

**返回:**

```json
{
  "success": true,
  "data": {
    "count": 2,
    "results": [
      {
        "snippet": "深圳大学图书馆开放时间...",
        "sourcePath": "knowledge/03-library.md",
        "relevanceScore": 0.85
      }
    ]
  },
  "errorCode": null,
  "traceId": "20240610-def456",
  "elapsedMs": 12
}
```

---

## MCP 协议接口

### tools/list

返回所有工具的 schema：

```json
{
  "schemaVersion": "1.1",
  "tools": [
    {
      "name": "booking_venue",
      "description": "体育场馆定时预约",
      "inputSchema": { ... }
    },
    {
      "name": "kb_query",
      "description": "深大知识库查询",
      "inputSchema": { ... }
    }
  ]
}
```

### tools/call

执行工具调用：

```json
{
  "name": "kb_query",
  "arguments": {
    "query": "图书馆开放时间",
    "limit": 3
  }
}
```

---

## 跨平台 MCP Server 使用方式

`mcp serve` 以 JSON-RPC 2.0 + stdio 方式对外暴露工具，纯 Java 实现，无需平台专属脚本。

### Claude Desktop 配置示例（macOS / Windows / Linux）

```json
{
  "mcpServers": {
    "szu-agent": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/szu-agent-plugin.jar",
        "mcp",
        "serve"
      ]
    }
  }
}
```

### Claude Code 配置示例

```json
{
  "mcpServers": {
    "szu-agent": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/szu-agent-plugin.jar", "mcp", "serve"]
    }
  }
}
```

> 日志统一输出到 `stderr`，`stdout` 仅用于 JSON-RPC 消息流，避免污染传输协议。

---

## CLI 等效命令

MCP 工具可通过 CLI 调用：

```bash
# 等效于 booking_venue 工具
java -jar target/szu-agent-plugin.jar booking venue \
  --username 2023150090 --campus YUEHAI --sport TENNIS \
  --date 2026-06-12 --time-slot 19:00-20:00 --format json

# 等效于 kb_query 工具
java -jar target/szu-agent-plugin.jar kb query --query 图书馆 --limit 5 --format json

# 等效于 tools/list
java -jar target/szu-agent-plugin.jar mcp list

# 运行 stdio MCP server
java -jar target/szu-agent-plugin.jar mcp serve
```

---

## 实现位置

```
edu.szu.agent.mcp/
├── MCPToolProvider.java       # tools/list 实现
├── MCPToolCallHandler.java    # tools/call 实现
├── ToolSchema.java            # schema 定义
└── McpStdioServer.java        # stdio JSON-RPC server (新增)
```

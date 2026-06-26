# MCP Tools — SZU Agent Plugin

> MCP (Model Context Protocol) 工具导出,供外部 AI Agent 调用。
> 版本 v1.3 · 学号 2023150090 · 姓名 王子豪
> 最近更新:2026-06-26(8 工具 `tools/list` 注入 `examples` + 50-100 行中文 `description` + `docs/tools/*.md` 全量落地)

> ⚠️ **ADR 校准声明**(2026-06-11):MCP 是 CLI 的**薄壳 wrapper**(ADR-0001 D5),
> 通过 `tools/call` 收到后 fork 这个 jar。`--dry-run` 不作为 demo 标志(ADR-0001 D4)。
> 详细理由见 `docs/adr/0001-project-direction-recalibration.md`。

---

## 1. 两种传输

| 传输 | 启动命令 | 端点 | 适用场景 |
|---|---|---|---|
| **stdio** | `java -jar szu-agent-plugin.jar mcp serve` | stdin/stdout JSON-RPC | Claude Desktop、Cursor、Cline 等单宿主 |
| **HTTP** | `java -jar szu-agent-plugin.jar mcp serve --http --port 8765` | `POST /mcp`、`GET /tools`、`POST /call`、`GET /health` | 多调用方共享一个热 JVM,毫秒级响应 |

> 一行推荐:`scripts/serve.sh --background`(macOS/Linux)或 `scripts\serve.bat --new-window`(Windows)
> 会启动 HTTP daemon,默认端口 8765;调用面细节见 [`SERVICE.md`](SERVICE.md)。

---

## 2. Tool Provider 入口

`edu.szu.agent.mcp` 包下:

| 类 | 职责 |
|---|---|
| `McpStdioServer` | stdio JSON-RPC server;`handle(String)` 提取为可复用单方法,被 `McpHttpServer` 直接复用做 MCP `POST /mcp` 分发 |
| `McpHttpServer` | 基于 `com.sun.net.httpserver.HttpServer` 的常驻 HTTP daemon;4 端点(health / tools / call / mcp) |
| `MCPToolCallHandler` | `tools/call` 实现,从 `Skills` 单例查找 Skill 并执行 |
| `ToolSchema` | `tools/list` 入口;`SCHEMA_VERSION = "1.3"`(在 v1.2 基础上把 `examples` 提升到 envelope 顶层);委托 `CampusTask#inputSchema()`(外部 Skill 用 manifest schema) |
| `JsonMappers` | 集中 `ObjectMapper` 工厂,统一开启 `JavaTimeModule` + 关 `WRITE_DATES_AS_TIMESTAMPS`,避免 `LocalDate` 序列化为数字数组 |

实现状态:**stdio 与 HTTP 两种 transport 均已落地**;HTTP daemon 是 2026-06-23 后
新增的"一个进程两个面"关键能力,服务启动时一次性注册 8 个内部 Skill + 全部
外部 Skill(由 `ExternalSkillLoader` 扫描 `SZU_SKILL_PATH`)。

---

## 3. 已交付工具(8 个)

`Main.registerDefaultSkills()` 在启动时按以下顺序注册 8 个内部 Skill:

| 工具 | 必填参数 | 是否需凭证/浏览器 | 内部 Task |
|---|---|---|---|
| `calendar_get` | — | 否(静态 2025-2026 校历) | `CalendarTask` |
| `kb_query` | `query` | 否(本地知识库) | `KnowledgeTask` |
| `schedule_list` | `username` | 否(静态 MVP) | `ScheduleListTask` |
| `notice_list` | `username` | 否(静态 MVP) | `NoticeTask` |
| `exam_list` | `username` | 视实现 | `ExamListTask` |
| `homework_list` | `username` | 是(畅课登录 + 会话复用) | `HomeworkTask` |
| `homework_download` | `username`,`homeworkId`,`outputDir` | 是 | `HomeworkDownloadTask` |
| `booking_venue` | `username`,`campus`,`sport`,`date`,`timeSlot` | 是(Playwright + .env) | `BookingTask` |

> 调用顺序:HTTP 路径走 `MCPToolCallHandler.call(name, arguments)`;CLI `mcp call` 也复用同一处理逻辑。
> 8 工具全部经 `CampusTask#inputSchema()` 暴露 JSON Schema,无 switch 派发(见 `ToolSchema.toolsList`)。

### 3.0 Per-Tool 参考文档(从数据生成)

每个工具的**人类阅读**版本(参数/枚举/示例/返回值/常见错误)由 `ToolDocsGenerator.renderMarkdown(Skill<?>)` 从 `CampusTask` 的 `description()` / `inputSchema()` / `annotations()` 直接渲染,与 `tools/list` JSON 始终保持一致。重新生成方式见 [`docs/tools/README.md`](docs/tools/README.md) 或运行 `mvn -q -DskipTests package && java -cp target/szu-agent-plugin.jar edu.szu.agent.task.ToolDocsGenerator`。

| 工具 | 详细文档 | 渲染源 |
|---|---|---|
| `calendar_get` | [`docs/tools/calendar.md`](docs/tools/calendar.md) | `CalendarTask` |
| `kb_query` | [`docs/tools/kb-query.md`](docs/tools/kb-query.md) | `KnowledgeTask` |
| `schedule_list` | [`docs/tools/schedule-list.md`](docs/tools/schedule-list.md) | `ScheduleListTask` |
| `notice_list` | [`docs/tools/notice-list.md`](docs/tools/notice-list.md) | `NoticeTask` |
| `exam_list` | [`docs/tools/exam-list.md`](docs/tools/exam-list.md) | `ExamListTask` |
| `homework_list` | [`docs/tools/homework-list.md`](docs/tools/homework-list.md) | `HomeworkTask` |
| `homework_download` | [`docs/tools/homework-download.md`](docs/tools/homework-download.md) | `HomeworkDownloadTask` |
| `booking_venue` | [`docs/tools/booking-venue.md`](docs/tools/booking-venue.md) | `BookingTask` |

### 3.1 `booking_venue`(P0 唯一真业务)

体育场馆定时预约(对应 CLI `booking venue`)

**参数:**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | ❌ | 学号,默认 `SZU_USERNAME` 环境变量 |
| `campus` | string | ✅ | 校区(`YUEHAI` / `LIHU`) |
| `sport` | string | ✅ | 体育项目(枚举名,需与 campus 匹配) |
| `date` | string | ✅ | ISO 8601 日期(如 `2026-06-24`) |
| `timeSlot` | string | ✅ | `HH:mm-HH:mm` 格式,只支持整点 1 小时时段 |
| `preferredVenue` | integer | ❌ | 1-based 场地序号,默认 1 |

> 完整枚举与自然语言映射见 [`docs/tools/booking-venue.md`](docs/tools/booking-venue.md)。

> **注意**:`dryRun` 参数**已移除**(ADR-0001 D4)。MCP `tools/call` 收到请求后 fork jar
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

### 3.2 `kb_query`

深大知识库查询(对应 CLI `kb query`)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `query` | string | ✅ | 查询关键词 |
| `limit` | integer | ❌ | 返回数量,默认 5 |
| `category` | string | ❌ | 分类:`CAMPUS_BASICS` / `DINING` / `LIBRARY` / `ACADEMICS` / `FAQ` |

### 3.3 `calendar_get`

校历查询(静态 MVP,无浏览器,2025-2026 学年)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `academicYear` | string | ❌ | 学年(如 `2025-2026`),默认从系统日期推断 |

### 3.4 `schedule_list`

学生课表查询(MVP,静态快照)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | ✅ | 学号 |

### 3.5 `notice_list`

公文通通知列表(MVP,静态)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | ✅ | 学号 |
| `category` | string | ❌ | `ANNOUNCEMENT` / `LECTURE` / `COMPETITION` / `PUBLICITY` |
| `daysBack` | integer | ❌ | 查询最近 N 天,默认 30 |

### 3.6 `exam_list`

考试安排列表(MVP,静态)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | ✅ | 学号 |
| `status` | string | ❌ | `待开始考试` / `已结束` |

### 3.7 `homework_list`

畅课作业列表(真跑 Playwright + 30 天会话复用,见 ADR-0008)

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | ❌ | 学号,默认 `SZU_USERNAME` |

### 3.8 `homework_download`

畅课作业附件下载

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | ❌ | 学号,默认 `SZU_USERNAME` |
| `homeworkId` | string | ✅ | 作业编号(例如 `169193`) |
| `outputDir` | string | ✅ | 本地输出目录绝对路径 |
| `throttleMs` | integer | ❌ | 下载间隔毫秒,默认 500 |
| `maxRetries` | integer | ❌ | 最大重试次数,默认 2 |

---

## 4. MCP 协议接口

### 4.1 `initialize`(stdio / HTTP `POST /mcp`)

返回 JSON-RPC `result`:

```json
{
  "protocolVersion": "2024-11-05",
  "serverInfo": { "name": "szu-agent-plugin", "version": "0.2.0" },
  "capabilities": { "tools": { "listChanged": false } }
}
```

### 4.2 `tools/list`

```json
{
  "schemaVersion": "1.3",
  "tools": [
    { "name": "booking_venue",      "description": "...", "inputSchema": { ... }, "examples": [ ... ] },
    { "name": "kb_query",           "description": "...", "inputSchema": { ... }, "examples": [ ... ] },
    { "name": "calendar_get",       "description": "...", "inputSchema": { ... }, "examples": [ ... ] },
    { "name": "schedule_list",      "description": "...", "inputSchema": { ... }, "examples": [ ... ] },
    { "name": "notice_list",        "description": "...", "inputSchema": { ... }, "examples": [ ... ] },
    { "name": "exam_list",          "description": "...", "inputSchema": { ... }, "examples": [ ... ] },
    { "name": "homework_list",      "description": "...", "inputSchema": { ... }, "examples": [ ... ] },
    { "name": "homework_download",  "description": "...", "inputSchema": { ... }, "examples": [ ... ] }
  ]
}
```

> HTTP 路径: `GET /tools`(或 `POST /mcp` 的 `tools/list` 消息)。
> schema 委托 `CampusTask#inputSchema()`,无 switch 派发;外部 Skill 用 manifest schema。

### 4.3 `tools/call`

请求:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "kb_query",
    "arguments": { "query": "图书馆开放时间", "limit": 3 }
  }
}
```

返回(包成 MCP `content` 数组,text 内容为本仓库业务信封):

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [{ "type": "text", "text": "{\"success\":true,\"data\":{...}}" }],
    "isError": false
  }
}
```

> HTTP 路径的等价调用: `POST /call`,请求体 `{"name":"kb_query","arguments":{...}}`,
> 直接返回业务信封(无 MCP `content` 包装),便于 curl / 脚本直接解析。

---

## 5. 跨平台 MCP Server 使用方式

### 5.1 stdio(默认,一宿主一进程)

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

### 5.2 HTTP(常驻,多调用方共享)

仓库根 `.mcp.json`:

```json
{ "mcpServers": { "szu-agent": { "type": "http", "url": "http://localhost:8765/mcp" } } }
```

> 启动: `scripts/serve.sh --background`(macOS/Linux)或 `scripts\serve.bat --new-window`(Windows)
> 默认端口 8765;切换端口后记得同步 `.mcp.json` 的 URL。
> Claude Code 首次会提示**批准**项目级 MCP server,批准 `szu-agent` 后 `/mcp` 显示 8 工具。

### 5.3 第三方独立 Node MCP Server(可选)

仓库还带一个**不依赖 Java 源码**的 Node.js MCP server(`external/mcp-server/`),
通过 `java -jar ... mcp list` / `java -jar ... skill call` 转发到 jar,适合纯 Node 环境。
详见 [`external/mcp-server/README.md`](external/mcp-server/README.md)。

> 日志统一输出到 `stderr`,`stdout` 仅用于 JSON-RPC 消息流,避免污染传输协议。

---

## 6. CLI 等效命令

```bash
# 等效于 booking_venue 工具
java -jar target/szu-agent-plugin.jar booking venue \
  --username 2023150090 --campus YUEHAI --sport TENNIS \
  --date 2026-06-12 --time-slot 19:00-20:00 --format json

# 等效于 kb_query 工具
java -jar target/szu-agent-plugin.jar kb query --query 图书馆 --limit 5 --format json

# 等效于 tools/list
java -jar target/szu-agent-plugin.jar mcp list

# 等效于 tools/call
java -jar target/szu-agent-plugin.jar mcp call kb_query --args query=图书馆 --args limit=3

# 运行 stdio MCP server
java -jar target/szu-agent-plugin.jar mcp serve

# 启动常驻 HTTP daemon
java -jar target/szu-agent-plugin.jar mcp serve --http --port 8765
```

---

## 8. 进阶参考文档

| 文档 | 用途 |
|---|---|
| [`docs/mcp/error-codes.md`](docs/mcp/error-codes.md) | 全量 `ErrorCode` 清单:触发条件 + 修复动作 + retryable 标志 + severity + 截图标志 |
| [`docs/mcp/credentials.md`](docs/mcp/credentials.md) | 凭证注入 + 三层查找 + 30 天会话复用 + headed 登录流程 + 安全约束 |
| [`docs/mcp/workflows.md`](docs/mcp/workflows.md) | 跨工具 cookbook(5 个真实场景):调用顺序 + 参数处理 + 异常分支 + 最终呈现 |
| [`docs/tools/`](docs/tools/) | 每个工具的详细参考(参数/枚举/示例/返回值/常见错误) |

---

## 7. 实现位置

```
edu.szu.agent.mcp/
├── MCPToolProvider.java       # 早期薄壳 wrapper(已合并到 ToolSchema)
├── MCPToolCallHandler.java    # tools/call 核心:从 Skills 单例查找 + 执行
├── ToolSchema.java            # tools/list(SCHEMA_VERSION="1.3",顶层 examples + 委托 task.inputSchema())
├── McpStdioServer.java        # stdio JSON-RPC server
└── McpHttpServer.java         # HTTP daemon(2026-06-23 新增,4 端点)
```

> `McpStdioServer.handle(String)` 是 JSON-RPC 分发的核心单方法,
> `McpHttpServer` 直接复用它实现 `POST /mcp` 端点,**零行为漂移**。

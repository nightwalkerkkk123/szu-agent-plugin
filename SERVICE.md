# 作为常驻服务运行 — SZU Agent Plugin

> 把整个 jar 跑成一个**常驻 HTTP 服务**,让 Skill 与 MCP 共用同一个热进程,
> 调用毫秒级、无重复 JVM 冷启动。任何装了 Java 21 的机器都能跑通。

---

## 一张图看懂

```
                       ┌──────────────────────────────────────┐
   Skill  ── curl ───▶ │  POST /call   {name, arguments}        │
                       │  POST /mcp    JSON-RPC 2.0(给 MCP 宿主)│  一个常驻 JVM
   Claude Code ─HTTP─▶ │  GET  /tools  工具发现                  │  端口 8765
   (MCP)              │  GET  /health 探活                     │
                       └──────────────────────────────────────┘
```

一个进程,四个端点,两类调用方。核心代码:`edu.szu.agent.mcp.McpHttpServer`
(纯 JDK `com.sun.net.httpserver`,无第三方依赖),复用既有的
`McpStdioServer.handle()`(JSON-RPC 分发)与 `MCPToolCallHandler.call()`(工具执行)。

---

## 前置条件(关键:钉死 Java 21)

| 用途 | Java 版本 | 说明 |
|---|---|---|
| **运行 daemon** | Java 21+ runtime | 只要 `java -version` 是 21+,jar 直接跑 |
| **构建 / `mvn test`** | **必须 Java 21** | 高版本 JDK(如 26)会让 Mockito 插桩失败,43 个测试报错 |

构建与测试前显式指定 21,避免被系统默认的高版本 JDK 顶替:

```bash
# macOS (Homebrew openjdk@21 为例)
export JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || echo /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home)"
java -version    # 确认 21.x
```

> 现象:不指定时 `mvn test` 可能用到 Java 26 → `Mockito cannot mock this class`。
> 这不是代码缺陷,是工具链版本错配。固定 `JAVA_HOME=21` 即全绿(594+ 测试)。

---

## 三步跑起来

```bash
# 1. 构建 fat jar(首次会下载依赖)
JAVA_HOME=<java21> mvn -q -DskipTests package      # 产出 target/szu-agent-plugin.jar

# 2. 启动常驻服务(前台,Ctrl-C 停)
scripts/serve.sh                  # 默认端口 8765
scripts/serve.sh --port 9000      # 自定义端口
scripts/serve.sh --background     # 后台启动,PID 写入 logs/serve.pid
scripts/serve.sh --stop           # 停止后台服务

# Windows:
scripts\serve.bat                 # 默认 8765
set SZU_AGENT_PORT=9000 && scripts\serve.bat

# 3. 探活
curl http://localhost:8765/health     # {"status":"ok"}
```

启动脚本不写死任何绝对路径——jar 路径相对仓库根解析,端口可经
`--port` 或环境变量 `SZU_AGENT_PORT` 覆盖。**换机器零改动**。

---

## 调用面 1:Skill(curl,毫秒级)

外部 Agent / 脚本直接打 `/call`,请求体就是 `{name, arguments}`:

```bash
# 校历(无需凭证)
curl -s localhost:8765/call -H 'Content-Type: application/json' \
  -d '{"name":"calendar_get","arguments":{}}'

# 知识库
curl -s localhost:8765/call -H 'Content-Type: application/json' \
  -d '{"name":"kb_query","arguments":{"query":"图书馆","limit":3}}'
```

仓库内置的 skill 包装见 `external/skills/szu-campus/`(`run` / `run.bat`),
daemon 地址由 `SZU_AGENT_URL` 配置(默认 `http://localhost:8765`):

```bash
echo '{"name":"notice_list","arguments":{"username":"2023150090"}}' \
  | external/skills/szu-campus/run
```

## 调用面 2:MCP(HTTP,Claude Code / Desktop)

项目根 `.mcp.json` 已注册(纯 URL,无绝对路径,可移植):

```json
{ "mcpServers": { "szu-agent": { "type": "http", "url": "http://localhost:8765/mcp" } } }
```

在本项目目录启动 `claude` 后,首次需**批准**该项目级 MCP server
(安全机制)。批准后 `booking_venue` / `kb_query` / `calendar_get` 等
8 个工具即可在对话中直接调用。

> 备选传输:`java -jar target/szu-agent-plugin.jar mcp serve`(stdio)。
> stdio 是每宿主一进程,不与 curl 共享;HTTP 才能"一个进程两个面"。

---

## 工具清单与凭证需求

| 工具 | 必填参数 | 是否需凭证/浏览器 |
|---|---|---|
| `calendar_get` | — | 否(静态) |
| `kb_query` | `query` | 否(本地知识库) |
| `schedule_list` | `username` | 否(静态 MVP) |
| `notice_list` | `username` | 否(静态 MVP) |
| `exam_list` | `username` | 视实现 |
| `homework_list` | `username` | 是(畅课登录) |
| `homework_download` | `username`, `homeworkId`, `outputDir` | 是 |
| `booking_venue` | `username`, `campus`, `sport`, `date`, `timeSlot` | 是(Playwright + .env) |

无凭证的四个(`calendar_get` / `kb_query` / `schedule_list` / `notice_list`)
适合零风险演示;带浏览器的需 `.env` 注入密码并下载 Chromium。

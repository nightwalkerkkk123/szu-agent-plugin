---
name: szu-agent
description: >
  调用深圳大学(SZU)校园自动化能力——校历、公文通通知、学生课表、校园知识库、
  考试安排、畅课作业(列表/下载)、体育场馆预约——通过本仓库的 szu-agent 常驻
  HTTP 服务完成。也用于启动、健康检查、排障该后台服务。只要用户提到深大/SZU 的
  校历、课表、公文通、选课/食堂/图书馆等校园信息查询、订场馆/订球场、查作业,
  或提到 szu-agent 服务起没起、连不上 MCP,就用这个 skill,即使用户没有显式说
  "调用工具"或"用 szu-agent"。
---

# SZU Agent — 校园能力调用

本仓库把一组深大校园操作封装成 8 个工具,由一个**常驻 HTTP 服务**(一个热 JVM)
对外提供。调用前先确保服务在跑,再按工具 schema 发起调用。服务的价值在于:一个
进程被 Skill(curl)与 MCP 宿主共享,调用毫秒级、无重复 JVM 冷启动。

## 第 0 步:确保服务在运行(每次调用前必做)

服务是个壳,真正的能力在后台那个 Java 进程里。先探活:

```bash
curl -s --max-time 2 http://localhost:8765/health    # 期望 {"status":"ok"}
```

若不通,说明 daemon 没起。在**仓库根目录**启动它(后台模式):

```bash
scripts/serve.sh --background        # macOS/Linux;Windows 用 scripts\serve.bat
# 端口可配:scripts/serve.sh --background --port 9000(同时改 .mcp.json 的 URL)
```

启动需要先有 jar(`target/szu-agent-plugin.jar`)。若 jar 不存在,先构建——
**务必用 Java 21**,更高版本(如 26)会让构建/测试失败:

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)" mvn -q -DskipTests package
```

## 第 1 步:选工具

| 工具名 | 用途 | 必填参数 | 需凭证/浏览器 |
|---|---|---|---|
| `calendar_get` | 查深大校历 | — | 否 |
| `kb_query` | 校园知识库(食堂/图书馆/选课等) | `query` | 否 |
| `schedule_list` | 学生课表 | `username` | 否 |
| `notice_list` | 公文通通知列表 | `username` | 否 |
| `exam_list` | 考试安排 | `username` | 视实现 |
| `homework_list` | 畅课作业列表 | `username` | 是(畅课登录) |
| `homework_download` | 下载作业附件 | `username`,`homeworkId`,`outputDir` | 是 |
| `booking_venue` | 体育场馆预约 | `username`,`campus`,`sport`,`date`,`timeSlot` | 是(Playwright+.env) |

无凭证的四个(`calendar_get`/`kb_query`/`schedule_list`/`notice_list`)随时可调。
带凭证的需用户在 `.env` 配 `SZU_PASSWORD_<学号>`,且 `booking_venue` 会启动真实浏览器。

## 第 2 步:调用

**优先**:若当前会话已连上名为 `szu-agent` 的 MCP server(工具形如
`mcp__szu-agent__calendar_get`),直接调用对应工具,参数按上表填。

**回退**(MCP 未连接,或在纯命令行环境):curl 打 `/call`,请求体就是
`{"name": 工具名, "arguments": {参数对象}}`:

```bash
# 校历(无参)
curl -s http://localhost:8765/call -H 'Content-Type: application/json' \
  -d '{"name":"calendar_get","arguments":{}}'

# 知识库
curl -s http://localhost:8765/call -H 'Content-Type: application/json' \
  -d '{"name":"kb_query","arguments":{"query":"图书馆开放时间","limit":3}}'

# 课表
curl -s http://localhost:8765/call -H 'Content-Type: application/json' \
  -d '{"name":"schedule_list","arguments":{"username":"2023150090"}}'
```

仓库还内置了一个 skill 包装脚本 `external/skills/szu-campus/run`,从 stdin 读
`{name,arguments}` 转发给服务,daemon 地址由 `SZU_AGENT_URL` 配置(默认 8765)。

## 返回格式(统一信封)

所有调用返回同一结构,据此判断成败、向用户复述:

```json
{
  "success": true,
  "data": { ... },              // 工具结果;失败时为 null
  "errorCode": null,            // 失败时如 INVALID_REQUEST / ELEMENT_NOT_FOUND
  "errorMessage": null,
  "traceId": "20260623-N9RZVV", // 可用于追溯日志
  "elapsedMs": 4
}
```

日期字段是 ISO-8601 字符串(如 `"2026-03-04"`)。读到 `success:false` 时,
把 `errorMessage` 翻译成用户能懂的话,别直接抛原始错误。

## 排障

- **`/health` 不通** → daemon 没起,执行 `scripts/serve.sh --background`。
- **`errorCode: INVALID_REQUEST` 且提示 Missing required parameter** → 缺必填参数,
  对照上表补齐(多数是 `username`)。
- **`mvn` 报 `Mockito cannot mock`** → 用了高版本 JDK,固定 `JAVA_HOME` 指向 Java 21。
- **MCP `/mcp` 连不上但 curl `/call` 能通** → daemon 正常,问题在 MCP 注册;检查
  `.mcp.json` 的 URL 端口是否与 daemon 实际端口一致,并在本项目目录启动 `claude` 批准。

完整部署与端点契约见仓库根 [`SERVICE.md`](../../../SERVICE.md)。

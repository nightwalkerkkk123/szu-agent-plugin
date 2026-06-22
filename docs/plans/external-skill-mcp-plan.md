# 外部可分发 Skill 与 MCP Server 开发计划

> 目标：让 `szu-agent-plugin` 的 Skill 可以脱离 Java 源码独立分发，并让 MCP Server 可以作为独立进程被外部客户端连接。

---

## 1. 背景与目标

### 1.1 现状

- `szu-agent-plugin` 是一个 Java 21 + Maven 的 CLI 工具集。
- Skill 与 MCP 当前都内嵌在项目中：
  - Skill 通过 `Skills` 单例注册，必须实现 `CampusTask<T>` 接口。
  - MCP server 通过 `McpStdioServer` 以 stdio 方式运行，位于主 jar 内部。
- 外部 AI 客户端只能通过 `java -jar szu-agent-plugin.jar mcp serve` 连接。

### 1.2 新目标

1. **外部 MCP Server**：提供一个不依赖 Java 源码、可独立安装/运行的 MCP server，支持 stdio 与 SSE 两种传输，可被 Claude Desktop / Cline / Cursor 等直接连接。
2. **独立分发 Skill**：Skill 可以作为独立文件夹（含元数据 + 可执行脚本）发布，不需要重新编译 Java 核心即可被加载使用。

---

## 2. 总体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     外部 MCP 客户端                              │
│  Claude Desktop / Cline / Cursor / 浏览器 / 其他 MCP host        │
└─────────────┬───────────────────────────────────────────────────┘
              │ MCP protocol (stdio 或 SSE)
┌─────────────▼───────────────────────────────────────────────────┐
│              szu-agent-mcp (Node.js, 独立进程)                  │
│  - 解析 MCP JSON-RPC 请求                                        │
│  - tools/list  -> 调用 java -jar ... mcp list                   │
│  - tools/call   -> 调用 java -jar ... skill call <name>         │
└─────────────┬───────────────────────────────────────────────────┘
              │ CLI 调用
┌─────────────▼───────────────────────────────────────────────────┐
│           szu-agent-plugin.jar (Java 21)                        │
│  - 内部 Skill (booking_venue, notice_list, calendar_get, ...)   │
│  - 外部 Skill 加载器 (扫描 SZU_SKILL_PATH)                       │
│  - Skills 单例统一注册                                           │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        内部 Java Task    外部命令调用      外部命令调用
        (编译进 jar)      (bash/python)    (bash/python)
```

---

## 3. 外部 MCP Server 设计

### 3.1 位置与依赖

- 代码位置：`external/mcp-server/`
- 技术栈：Node.js 18+，`@modelcontextprotocol/sdk`，`express`
- 唯一外部依赖：`szu-agent-plugin.jar`（运行时指定路径）

### 3.2 文件清单

| 文件 | 说明 |
|------|------|
| `package.json` | npm 包定义，bin 入口 |
| `szu-agent-mcp.js` | MCP server 主程序，支持 stdio / SSE |
| `README.md` | 安装与配置说明 |
| `claude-desktop-config.json` | Claude Desktop 配置示例 |

### 3.3 命令行接口

```bash
node szu-agent-mcp.js [options]
  --jar, -j <path>       jar 路径（也可通过 SZU_AGENT_JAR 指定）
  --transport, -t <mode> stdio（默认）或 sse
  --port, -p <number>    SSE 端口（默认 3000）
```

### 3.4 环境变量

| 变量 | 说明 |
|------|------|
| `SZU_AGENT_JAR` | jar 文件绝对路径 |
| `SZU_AGENT_JAVA` | Java 可执行文件路径 |
| `SZU_AGENT_JAVA_OPTS` | 额外 JVM 参数 |

### 3.5 MCP 协议实现

- `initialize`：返回协议版本 `2024-11-05`，serverInfo 为 `szu-agent-mcp/0.1.0`
- `tools/list`：
  1. 调用 `java -jar <jar> mcp list`
  2. jar 返回 `{ schemaVersion, tools: [...] }`
  3. 转换为 MCP 标准 `{ tools: [...] }`
- `tools/call`：
  1. 将 `arguments` 扁平化为 `k=v` 形式
  2. 调用 `java -jar <jar> skill call <name> --args k=v ...`
  3. 将 CLI 返回的 JSON 作为 `text` 内容返回给客户端

### 3.6 扁平化规则

嵌套对象展开为点号键：

```json
{"timeSlot": {"start": "19:00", "end": "20:00"}}
```

→

```
--args timeSlot.start=19:00 --args timeSlot.end=20:00
```

数组直接序列化为 JSON 字符串。

### 3.7 SSE 模式端点

| 端点 | 作用 |
|------|------|
| `GET /sse` | 建立 SSE 连接 |
| `POST /message?sessionId=<id>` | 发送 JSON-RPC 消息 |
| `GET /health` | 健康检查 |

### 3.8 分发方式

- 作为 npm 包发布：`npm install -g szu-agent-mcp`
- 直接运行：`npx szu-agent-mcp --jar /path/to/szu-agent-plugin.jar`
- 源码 zip：复制 `external/mcp-server/` 到目标机器，执行 `npm install`

---

## 4. 独立 Skill 规范

### 4.1 目录结构

一个独立 Skill 是一个文件夹：

```
my-skill/
├── skill.yaml      # 元数据 + 参数 schema
├── run             # Linux/macOS 入口
└── run.bat         # Windows 入口（可选）
```

### 4.2 skill.yaml 字段

```yaml
name: example_greet          # 唯一标识，snake_case
version: "0.1.0"             # 版本
description: 示例 Skill       # 简短描述
author: 王子豪                # 作者
license: MIT                 # 许可证
runtime: python3             # 执行环境提示（可选）

inputSchema:                 # MCP JSON Schema
  type: object
  properties:
    name:
      type: string
      description: 名字
    language:
      type: string
      description: 语言
      enum: [zh, en, jp]
      default: zh
  required: [name]
```

### 4.3 执行接口

Java 核心调用外部 Skill 的命令格式：

```bash
<skillDir>/run <skillName>
```

- 参数 1：Skill 名称
- 标准输入（stdin）：调用参数的 JSON 字符串

标准输出必须是统一 JSON 信封：

成功：

```json
{
  "success": true,
  "data": { ... }
}
```

失败：

```json
{
  "success": false,
  "errorCode": "INVALID_REQUEST",
  "errorMessage": "..."
}
```

### 4.4 示例 Skill

#### 4.4.1 template_echo（最小模板）

- 位置：`external/skills/template/`
- 功能：原样回显输入参数
- 运行环境：bash +（可选）jq/python3

#### 4.4.2 example_greet（多语言问候）

- 位置：`external/skills/example-greet/`
- 功能：根据 `language` 返回不同语言的问候
- 运行环境：python3

### 4.5 安装与发现

1. 把 Skill 文件夹复制到 `~/.szu-agent/skills/` 或任意目录。
2. 设置环境变量：

```bash
export SZU_SKILL_PATH="$HOME/.szu-agent/skills"
```

3. Java 核心启动时扫描 `SZU_SKILL_PATH`，加载所有 `skill.yaml` 并注册到 `Skills` 单例。

### 4.6 分发方式

- zip/tar.gz 发布到 GitHub Release
- 放入共享网络目录
- 作为 npm/pip 包发布脚本

---

## 5. Java 核心改造计划

### 5.1 新增模块

位置：`src/main/java/edu/szu/agent/skill/external/`

| 类 | 职责 |
|----|------|
| `ExternalSkillManifest` | 保存 `skill.yaml` 解析结果 |
| `ExternalSkill` | 实现 `CampusTask<Map<String, Object>>`，执行外部命令 |
| `ExternalSkillLoader` | 扫描 `SZU_SKILL_PATH`，加载外部 Skill |

### 5.2 ExternalSkillManifest

字段：

- `String name()`
- `String version()`
- `String description()`
- `String author()`
- `String license()`
- `String runtime()`
- `Map<String, Object> inputSchema()`
- `Path directory()`

通过 Jackson + YAML 解析 `skill.yaml`。

### 5.3 ExternalSkill

实现 `CampusTask<Map<String, Object>>`：

- `name()`：返回 manifest.name
- `description()`：返回 manifest.description
- `execute(TaskInput input)`：
  1. 查找当前平台对应的入口脚本（优先 `run`，Windows 回退 `run.bat`）
  2. 将参数序列化为 JSON
  3. 使用 `ProcessBuilder` 执行：`run <name>`，并将 JSON 写入 stdin
  4. 捕获 stdout，解析为 JSON
  5. 若 `success=true` 返回 `data`；否则抛出 `BookingException`

超时控制：默认 60 秒，可通过 `SZU_SKILL_TIMEOUT` 环境变量调整。

### 5.4 ExternalSkillLoader

- 从环境变量 `SZU_SKILL_PATH` 读取路径（多个路径用 `;` 或 `:` 分隔）
- 遍历每个目录下的子目录
- 若子目录存在 `skill.yaml`，解析并注册
- 若入口脚本不存在，记录 warning 并跳过
- 名称冲突时，外部 Skill 优先覆盖内部 Skill（或报错，待决定）

### 5.5 ToolSchema 改造

当前 `schemaFor(String)` 通过 `switch` 返回内部 Skill 的 schema。改造后：

1. 优先从 `ExternalSkillManifest` 查找外部 Skill schema
2. 若找不到，再使用内部 switch
3. 默认返回 `{ type: "object", additionalProperties: true }`

需要 `ToolSchema` 能访问外部 Skill 注册信息。可选方案：

- 方案 A：在 `Skills` 单例中同时保存内部和外部 Skill，`ToolSchema` 直接查询
- 方案 B：`ToolSchema` 增加一个 `ExternalSkillRegistry` 依赖

采用方案 A：外部 Skill 也注册到 `Skills` 单例，`Skill` 的 `task` 字段就是 `ExternalSkill` 实例。

### 5.6 Main 改造

在 `registerDefaultSkills()` 之后调用：

```java
ExternalSkillLoader.loadFromEnvironment();
```

### 5.7 错误码

新增错误码：

| 常量 | 含义 |
|------|------|
| `EXTERNAL_SKILL_NOT_FOUND` | 入口脚本缺失 |
| `EXTERNAL_SKILL_TIMEOUT` | 外部 Skill 执行超时 |
| `EXTERNAL_SKILL_JSON_ERROR` | 外部 Skill 输出非法 JSON |

---

## 6. 测试计划

### 6.1 单元测试

- `ExternalSkillManifestTest`：解析 `skill.yaml`
- `ExternalSkillLoaderTest`：扫描临时目录并注册 Skill
- `ExternalSkillTest`：
  - 调用成功返回 data
  - 调用失败抛出 BookingException
  - 超时场景
- `ToolSchemaTest`：外部 Skill schema 被正确返回

### 6.2 集成测试

- 构建 jar 后，在临时目录安装 `example_greet` Skill
- 执行 `java -jar szu-agent-plugin.jar skill list`，验证外部 Skill 出现
- 执行 `java -jar szu-agent-plugin.jar skill call example_greet --args name=Alice`，验证返回
- 启动 `szu-agent-mcp` stdio server，使用 `mcp list` / `mcp call` 验证

### 6.3 回归测试

- 全部现有测试继续通过（534+）
- 覆盖率保持 ≥ 80%

---

## 7. 文件变更清单

### 7.1 新增文件

```
external/
├── README.md
├── mcp-server/
│   ├── package.json
│   ├── szu-agent-mcp.js
│   ├── README.md
│   └── claude-desktop-config.json
└── skills/
    ├── README.md
    ├── template/
    │   ├── skill.yaml
    │   ├── run
    │   └── run.bat
    └── example-greet/
        ├── skill.yaml
        ├── run
        └── run.py

src/main/java/edu/szu/agent/skill/external/
├── ExternalSkillManifest.java
├── ExternalSkill.java
└── ExternalSkillLoader.java

src/test/java/edu/szu/agent/skill/external/
├── ExternalSkillManifestTest.java
├── ExternalSkillLoaderTest.java
└── ExternalSkillTest.java

docs/plans/external-skill-mcp-plan.md
```

### 7.2 修改文件

- `src/main/java/edu/szu/agent/cli/Main.java`
  - 注册默认 Skill 后加载外部 Skill
- `src/main/java/edu/szu/agent/mcp/ToolSchema.java`
  - `schemaFor` 支持外部 Skill schema
- `src/main/java/edu/szu/agent/error/ErrorCode.java`
  - 新增外部 Skill 相关错误码

---

## 8. 分发方案

### 8.1 npm 包（MCP Server）

```bash
cd external/mcp-server
npm install
npm pack          # -> szu-agent-mcp-0.1.0.tgz
npm publish       # 可选
```

用户安装：

```bash
npm install -g szu-agent-mcp
szu-agent-mcp --jar /path/to/szu-agent-plugin.jar
```

### 8.2 独立 Skill 包

打包模板：

```bash
cd external/skills/example-greet
zip -r example-greet-0.1.0.zip .
```

安装：

```bash
mkdir -p ~/.szu-agent/skills
unzip example-greet-0.1.0.zip -d ~/.szu-agent/skills/example_greet
export SZU_SKILL_PATH="$HOME/.szu-agent/skills"
```

### 8.3 完整发行包

未来可以打包：

```
szu-agent-distribution-0.1.0/
├── szu-agent-plugin.jar
├── mcp-server/
│   ├── package.json
│   └── szu-agent-mcp.js
└── skills/
    ├── example_greet/
    └── template_echo/
```

---

## 9. 风险与决策

### 9.1 已做决定

1. 外部 Skill 通过命令行调用，参数以 JSON 字符串传入 stdout 返回 JSON。
2. 外部 Skill 也注册到 `Skills` 单例，对 MCP / CLI 透明。
3. MCP Server 使用 Node.js 实现，不依赖 Java 源码。
4. 外部 Skill 名称冲突时，外部优先（待实现时确认）。

### 9.2 待确认事项

1. 是否允许外部 Skill 覆盖内部 Skill？
   - 建议：允许，但打印 warning。
2. 外部 Skill 超时默认值？
   - 建议：60 秒。
3. 多路径分隔符？
   - Windows 用 `;`，Unix 用 `:`，与 PATH 一致。
4. 是否需要在 `application.yml` 中配置 `skill.path`？
   - 建议：P1 先支持环境变量，后续再加 YAML 配置。

### 9.3 已知限制

- 外部 Skill 运行依赖目标机器有对应的运行时（python3 / bash / node）。
- Windows 下默认入口为 `run.bat`，若无则回退 `run`。
- 外部 Skill 输出必须合法 JSON，否则返回 `EXTERNAL_SKILL_JSON_ERROR`。

---

## 10. 实施顺序

1. ✅ 创建独立 Node.js MCP Server
2. ✅ 创建外部 Skill 规范与示例
3. ⬜ 实现 Java 外部 Skill 加载器
4. ⬜ 修改 Main / ToolSchema 支持外部 Skill
5. ⬜ 编写单元测试与集成测试
6. ⬜ 全量构建验证
7. ⬜ 更新 README 与 BACKLOG

---

## 11. 验收标准

- [ ] `node external/mcp-server/szu-agent-mcp.js --jar target/szu-agent-plugin.jar` 可以 stdio 启动
- [ ] 通过 MCP `tools/list` 能看到内部 Skill
- [ ] 通过 MCP `tools/call` 能调用内部 Skill
- [ ] 设置 `SZU_SKILL_PATH` 后，`skill list` 能看到外部 Skill
- [ ] 能成功调用 `example_greet` 外部 Skill
- [ ] `mvn test` 全量通过，覆盖率 ≥ 80%
- [ ] 文档完整，包含安装、配置、分发说明

# szu-agent-mcp

独立可运行的 MCP（Model Context Protocol）服务端，为 `szu-agent-plugin` 提供外部接入能力。

这个目录下的代码**不依赖 Java 项目源码**，只需要一个打包好的 `szu-agent-plugin.jar` 即可运行。支持 stdio 与 SSE 两种传输方式，可被 Claude Desktop、Cline、Cursor 等任意 MCP 客户端连接。

## 1. 前提

- Node.js ≥ 18
- `szu-agent-plugin.jar`（通过 `mvn package -DskipTests` 生成）
- Java 21 运行时

## 2. 安装

```bash
cd external/mcp-server
npm install
```

## 3. 使用方式

### 3.1 stdio（默认，用于 Claude Desktop / Cline / Cursor）

```bash
node szu-agent-mcp.js --jar /path/to/szu-agent-plugin.jar
```

或者设置环境变量：

```bash
export SZU_AGENT_JAR=/path/to/szu-agent-plugin.jar
node szu-agent-mcp.js
```

### 3.2 SSE（用于远程或浏览器客户端）

```bash
node szu-agent-mcp.js --transport sse --port 3000
```

端点：

- `GET  /sse`      — SSE 连接
- `POST /message`  — JSON-RPC 消息
- `GET  /health`   — 健康检查

## 4. Claude Desktop 配置示例

把以下内容加入 Claude Desktop 的 `claude_desktop_config.json`：

```json
{
  "mcpServers": {
    "szu-agent": {
      "command": "node",
      "args": [
        "E:/CODE/szu-agent-plugin/external/mcp-server/szu-agent-mcp.js",
        "--jar",
        "E:/CODE/szu-agent-plugin/target/szu-agent-plugin.jar"
      ],
      "env": {
        "SZU_AGENT_JAVA": "E:/tools/jdk-21/bin/java.exe"
      }
    }
  }
}
```

Windows 路径请使用双反斜杠或正斜杠。

## 5. 工作原理

- `tools/list` → 调用 `java -jar szu-agent-plugin.jar mcp list`
- `tools/call` → 调用 `java -jar szu-agent-plugin.jar skill call <name> --args k=v...`

因此**新增的内部 Skill 会自动暴露**，无需修改本 MCP server。

## 6. 打包分发

可以发布为 npm 包：

```bash
npm pack
npm publish
```

发布后用户只需：

```bash
npx szu-agent-mcp --jar /path/to/szu-agent-plugin.jar
```

## 7. 独立 Skill

如果想让本 server 也能加载**项目外部**的独立 Skill，请在 Java 核心中启用外部 Skill 目录（见 `external/skills/README.md`），本 server 会通过 `mcp list` 自动发现它们。

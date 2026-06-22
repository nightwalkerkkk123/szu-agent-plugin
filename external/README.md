# 外部可分发组件

本目录包含 `szu-agent-plugin` 的**外部可分发组件**，不依赖 Java 项目源码即可独立使用。

## 目录

```
external/
├── mcp-server/          # 独立 MCP Server（Node.js）
├── skills/              # 独立 Skill 模板与示例
└── README.md            # 本文件
```

## 1. 独立 MCP Server

`external/mcp-server/` 是一个基于 Node.js + `@modelcontextprotocol/sdk` 的 MCP 服务端。

### 特点

- **不依赖 Java 源码**：只需要 `szu-agent-plugin.jar`
- **支持 stdio + SSE**：stdio 用于 Claude Desktop / Cline / Cursor；SSE 用于远程/浏览器客户端
- **自动发现 Skill**：内部 Skill 与外部 Skill 都通过 jar 的 CLI 暴露

### 快速开始

```bash
cd external/mcp-server
npm install
node szu-agent-mcp.js --jar /path/to/szu-agent-plugin.jar
```

详细说明见 [`mcp-server/README.md`](mcp-server/README.md)。

## 2. 独立 Skill

`external/skills/` 定义了独立 Skill 的规范，并提供两个示例。

### 一个 Skill 就是一组文件

```
my-skill/
├── skill.yaml      # 元数据 + 参数 schema
├── run             # Linux/macOS 入口
└── run.bat         # Windows 入口（可选）
```

### 快速开始

```bash
export SZU_SKILL_PATH="$HOME/.szu-agent/skills"
mkdir -p "$SZU_SKILL_PATH"
cp -r external/skills/example-greet "$SZU_SKILL_PATH/example_greet"
java -jar target/szu-agent-plugin.jar skill list
java -jar target/szu-agent-plugin.jar skill call example_greet --args name=Alice --args language=en
```

详细规范见 [`skills/README.md`](skills/README.md)。

## 3. 分发方式

| 组件 | 分发方式 |
|------|----------|
| MCP Server | npm pack / npm publish，或 zip 源码包 |
| Skill | zip/tar.gz，或 npm/pip 包 |
| 完整套装 | 包含 jar + mcp-server + skills 的发布压缩包 |

## 4. 开发计划

详见 [`docs/plans/external-skill-mcp-plan.md`](../docs/plans/external-skill-mcp-plan.md)。

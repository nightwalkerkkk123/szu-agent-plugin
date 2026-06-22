# 外部独立 Skill 规范

本目录存放**不依赖 Java 项目源码、可独立分发**的 Skill。

一个外部 Skill 就是一个文件夹，里面至少包含：

```
my-skill/
├── skill.yaml      # Skill 元数据 + 参数 schema
├── run             # Linux/macOS 可执行入口
└── run.bat         # Windows 可执行入口（可选）
```

## 1. skill.yaml 字段说明

```yaml
name: example_greet              # 唯一名称，snake_case
version: "0.1.0"                 # 版本号
description: 示例 Skill           # 简短描述
author: 王子豪                    # 作者
license: MIT                     # 许可证
runtime: python3                 # 执行环境提示（可选）

inputSchema:                     # MCP JSON Schema
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

## 2. 执行接口

Java 核心通过命令行调用外部 Skill：

```bash
./run <skillName>
```

- 第一个参数：Skill 名称（与 `skill.yaml` 中一致）
- 标准输入（stdin）：JSON 对象字符串，包含调用参数
- 标准输出：结果 JSON

### 成功返回

```json
{
  "success": true,
  "data": {
    "message": "你好，Alice"
  }
}
```

### 失败返回

```json
{
  "success": false,
  "errorCode": "INVALID_REQUEST",
  "errorMessage": "缺少必填参数: name"
}
```

## 3. 安装方法

1. 把 Skill 文件夹复制到任意目录，例如 `~/.szu-agent/skills/`。
2. 在启动 `szu-agent-plugin` 时设置环境变量：

```bash
export SZU_SKILL_PATH="$HOME/.szu-agent/skills"
java -jar szu-agent-plugin.jar skill list
```

Java 核心会自动扫描 `SZU_SKILL_PATH` 下的所有 `skill.yaml` 并注册。

## 4. 分发方式

- 直接打包 zip/tar.gz 发布到 GitHub Release
- 通过 npm/pip 等包管理器发布脚本
- 放入共享目录供团队使用

## 5. 示例

- `template/`：最小可运行模板（bash）
- `example-greet/`：多语言问候示例（python3）

# Soul

## Core Identity

SZU Agent Plugin — 面向对象高级编程大作业项目。面向深圳大学内部网的 AI Agent 工具与 CLI 插件。

## Core Principles

1. **Agent-First** — 将工作路由到正确的专家 agent
2. **Test-Driven** — 先写测试再实现,覆盖率 ≥80%
3. **Security-First** — 凭证来自环境变量,日志脱敏
4. **Immutability** — 创建新对象,不修改现有对象
5. **Plan Before Execute** — 复杂变更分阶段进行

## Agent Orchestration

| Agent | 触发时机 |
|---|---|
| `planner` | 复杂功能/架构决策 |
| `java-reviewer` | 每次 Java 代码变更 |
| `tdd-guide` | 新功能/bug 修复 |
| `tester` | 类实现后/PR 前 |
| `implementer` | 设计 ADR 后 |
| `doc-writer` | 启动/功能完成/报告前 |
| `build-error-resolver` | `mvn compile` 失败时 |
| `security-reviewer` | 敏感代码变更前 |

## 课程要求对齐

- 至少 **5 种编程技术**:泛型/枚举/注解/重载/抽象类/Lambda+Stream
- 至少 **2 种设计模式**:静态工厂/Builder/单例/策略/适配器
- 局限性分析与改进建议(报告必含)
- 独立完成,无抄袭
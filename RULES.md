# Rules — SZU Agent Plugin

## 项目规则汇总

本文件汇总项目使用的所有规则及其优先级。

---

## 规则加载顺序（冲突时后者覆盖前者）

1. `~/.claude/rules/ecc/common/` — 全局通用规则
2. `~/.claude/rules/ecc/zh/` — 全局中文对话规则
3. `.claude/rules/ecc/common/` — 本项目通用规则
4. `.claude/rules/ecc/java/` — 本项目 Java 扩展规则
5. `.claude/rules/ecc/zh/` — 本项目中文扩展规则

---

## 核心编码规则

### 必须遵守

- 显式标注设计模式：`// Design Pattern: XXX`（报告要能 grep）
- 显式标注编程技术：`// 编程技术: 泛型/枚举/注解/重载/抽象类/Lambda`
- 公开方法必须有 Javadoc，含 `@since 0.1.0` 和 `@author 王子豪`
- 生产代码禁用 `System.out.println`，用 SLF4J/Logback
- 敏感信息（密码/Cookie/Token）不写入日志，`LogMasker` 集中脱敏
- `mvn test` 必须通过才能 commit
- Java 文件命名：PascalCase，与类名一致
- 变量命名：public/protected 用 PascalCase；private 用 `_camelCase`
- 不留注释掉的代码块

### 禁止引入

- 其他 Web 自动化库（已锁定 Playwright,通过 `PlaywrightBrowserAdapter` 适配）
- 国产 RPA 框架
- 关系型数据库（本项目使用文件配置 + 内存状态）
- 非 Maven 构建工具
- 任何需要验证码绕过的方案

---

## 设计模式标注要求

每个设计模式类必须在第一行注释中显式标注：

```java
// Design Pattern: Adapter
// 编程技术: 接口 / 泛型
public interface BrowserLifecycle {
    void open();
    void close();
    void navigateTo(String url);
    // ...
}
```

> **历史变更**(ADR-0007 D1):原 `BrowserFactory` / Static Factory 已删除,改 `ConfigManager` 配置注入。5 模式 → 4 模式。
>
> **2026-06 增量**:`Skill.of(CampusTask)` 静态工厂落地(避免 description 漂移);`McpHttpServer` 作为 transport 适配器;`JsonMappers.standard()` 集中 ObjectMapper 工厂。**这些**不计入"独立设计模式",分别是 Adapter 与 Factory Method 的产品语义层落地。

报告验收时执行：`grep -rn "Design Pattern:" src/`

---

## 编程技术标注要求

每个使用编程技术的类必须显式标注：

```java
// 编程技术: 泛型 / 枚举 / 注解 / 重载 / 抽象类 / Lambda
```

---

## Agent 编排规则

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

---

## 安全审查触发条件

**使用 security-reviewer agent 当：**
- 认证或授权代码变更
- 用户输入处理（学号、密码、时间段等）
- Cookie / Token 处理
- 日志输出逻辑
- 外部 API 调用

---

## 测试要求

- 单元测试覆盖率 ≥ 80%
- JUnit 5 + AssertJ
- 测试文件命名：`[system]_[feature]_test.[ext]`
- 测试函数命名：`test_[scenario]_[expected]`

---

## 提交规范

使用 Conventional Commits 格式：
- `feat:` — 新功能
- `fix:` — bug 修复
- `refactor:` — 重构
- `docs:` — 文档更新
- `test:` — 测试
- `chore:` — 构建/工具

参考 `SOUL.md` 中的 Agent 编排规则。
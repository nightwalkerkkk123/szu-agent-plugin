# Trace Specification

> 每次任务完成后记录 trace，为下一个 Agent 提供有用的上下文。

## Trace Purpose

1. **传承上下文** — 下一个 Agent 能理解之前做了什么、为什么
2. **衡量改进** — 通过 friction 记录识别 harness 需要改进的地方
3. **验证证据** — 记录证明完成工作的验证结果

## When to Record

| Lane | Required |
|---|---|
| Tiny | Optional, but record if friction found |
| Normal | Required |
| High-Risk | Required, with full context |

## Trace Location

`harness-records/traces/YYYYMMDD-HHMMSS-story-id.md`

命名格式：`{date}-{time}-{story-id}.md`
例如：`20250610-143000-US-001-venue-booking.md`

## Trace Template

```markdown
# Trace: [Story ID] - [Brief Title]

**Date:** YYYY-MM-DD HH:MM:SS
**Lane:** tiny | normal | high-risk
**Story:** US-XXX
**Outcome:** success | blocked | partial

---

## Summary

What was done in this session (2-3 sentences).

## Files Changed

- `src/main/java/edu/szu/agent/domain/BookingRequest.java` — 添加 Builder 模式
- `docs/design-patterns.md` — 更新 Builder 模式说明

## Files Read

列出被阅读的文件（不仅仅是 changed）：
- `docs/PRD.md` — 确认 API 契约
- `docs/design-patterns.md` — 参考 Builder 模式示例
- `src/main/java/edu/szu/agent/domain/Campus.java` — 理解枚举模式

## Validation

```bash
mvn test  # ✅ Passed (23 tests, 0 failures)
mvn package  # ✅ Built szu-agent-plugin-0.1.0.jar
```

## Design Patterns Applied

- `// Design Pattern: Builder` — `BookingRequest.java`
- `// Design Pattern: Singleton` — `ConfigManager.java` / `Tracer.java`

## Programming Techniques

- 泛型 (`<T>`, `TaskResult<T>`)
- 枚举 (`TaskStatus`, `AccountState`)
- 注解 (`@AgentTool`)
- 重载 (Builder.campus(String) / campus(Campus))
- 抽象类 (Browser 基类 / `BookingException` 密封继承)
- Lambda + Stream (`accounts.stream().filter(...)`)

## Friction (if any)

任何在实现过程中遇到的困难或混淆：

- **Context**: 需要反复查看 `docs/system-map.md` 才能理解模块拓扑
- **Tool**: `mvn test` 第一次运行时失败，需要修复 imports

## Harness Improvement (if any)

如果发现 harness 需要改进的地方：

```markdown
## Harness Improvement

**Pain:** 每次实现新功能时需要手动查找设计模式示例
**Proposal:** 在 `docs/design-patterns.md` 添加每种模式的"实现检查清单"
```

## Decisions Made

此任务中做出的任何重要决策：

- 使用 Builder 模式而非重载构造函数，因为参数超过 4 个
- 保持 `BrowserLifecycle` 接口简单，不添加额外方法

## Next Steps

如果 story 未完成，列出下一步：

- [ ] 实现 `Matcher` 策略族
- [ ] 添加单元测试覆盖 `BookingRequest.Builder`
```

## Friction Categories

| Category | Use when |
|---|---|
| `context-missing` | 需要的文档不存在或不清晰 |
| `context-stale` | 文档已过时，导致误导 |
| `tool-missing` | 需要但缺少的命令或脚本 |
| `tool-broken` | 存在的命令或脚本不工作 |
| `pattern-unclear` | 设计模式应用不明确 |
| `dependency-confusion` | 依赖关系不清晰 |
| `other` | 其他摩擦 |

## Score

Trace 完成后，计算质量分数（0-100）：

| Factor | Weight | Description |
|---|---|---|
| Files listed | 10 | 列出而非省略 |
| Validation evidence | 20 | 有测试结果或手动验证 |
| Design patterns noted | 15 | 显式标注 |
| Techniques noted | 10 | 显式标注 |
| Friction recorded | 15 | 如实记录困难 |
| Decisions documented | 15 | 记录决策而非结果 |
| Next steps clear | 15 | 未完成时有清晰的下一步 |

---

## CLI Integration

完成工作后，运行：

```bash
# 记录 trace（手动创建文件）
# 格式: harness-records/traces/YYYYMMDD-HHMMSS-story-id.md
```

未来可扩展为 Rust CLI 的轻量替代品（使用 Python 脚本或纯 shell）。
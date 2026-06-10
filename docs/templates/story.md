# Story: [US-XXX Feature Name]

**Lane:** normal | high-risk
**Created:** YYYY-MM-DD
**Status:** proposed | in-progress | done | blocked

---

## Overview

One paragraph summary of what this story delivers and why it matters.

## User Intent

What the user asked for (original prompt or extracted intent).

## Acceptance Criteria

- [ ] Criteria 1
- [ ] Criteria 2
- [ ] Criteria 3

## Affected Docs

- `docs/PRD.md` — 产品需求
- `docs/design-patterns.md` — 设计模式（如果引入新模式）
- `docs/system-map.md` — 系统地图（如果改变架构）

## Design Patterns Used

列出此 story 中使用的设计模式（如适用）：
- `// Design Pattern: Static Factory` in `ClientFactory.java`
- `// Design Pattern: Builder` in `BookingRequest.java`

## Programming Techniques

列出此 story 中使用的编程技术（如适用）：
- 泛型 / 枚举 / 注解 / 重载 / 抽象类 / Lambda+Stream

## Validation

验证此 story 的方法：

```bash
mvn test                      # 单元测试
mvn package                   # 构建 jar
java -jar target/... --dry-run  # 功能验证
```

## Notes

实现过程中的任何特殊考虑或决策。

## Trace

完成后记录 trace 到 `harness-records/traces/YYYYMMDD-HHMMSS-story-id.md`
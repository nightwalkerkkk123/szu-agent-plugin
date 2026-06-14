# Story: US-006 畅课作业列表查询

**Lane:** normal  
**Created:** 2026-06-14  
**Status:** in-progress  

---

## Overview

新增 `szu-agent homework list` 子命令，登录深圳大学 LMS (`https://lms.szu.edu.cn/user/index`) 后抓取首页“待办”区域中的作业列表，以 JSON 形式返回课程名、作业标题、截止时间、提交状态。

## User Intent

用户需要增加一个“作业功能”，登录方式与体育场馆一致（均走学校 CAS / 统一身份认证），用于查询畅课（LMS）上的作业待办。

## Acceptance Criteria

- [ ] `java -jar szu-agent-plugin.jar homework list --username <id> --env-file .env --format json` 输出合法 JSON
- [ ] JSON `data` 数组中每项包含 `homeworkId`、`courseName`、`title`、`deadline`、`status`
- [ ] 仅抓取类型为作业（`#todo-homework`）的待办项
- [ ] 登录失败、页面加载失败、列表为空均有明确错误码
- [ ] `skill list` / `mcp list` 包含 `homework_list`
- [ ] `mvn test` 通过，覆盖率 ≥80%

## Affected Docs

- `docs/system-map.md` — 增加 `ChaoxingHomeworkClient` / `HomeworkTask` 模块
- `docs/design-patterns.md` — 记录 Strategy / Adapter 在新流程中的落点

## Design Patterns Used

- `// Design Pattern: Strategy` in `HomeworkListExtractor`（选择器策略） / `Matcher<T>`
- `// Design Pattern: Adapter` in `PlaywrightBrowserAdapter`
- `// Design Pattern: Singleton` in `Skills`（注册新 Skill）

## Programming Techniques

- `record` — `Homework` 不可变值对象
- `sealed interface` — `HomeworkListResult`
- `Lambda` — JS 脚本构造、JSON 解析
- `正则表达式` — 从混合文本中提取截止时间

## Validation

```bash
mvn test
mvn -q -DskipTests package
java -jar target/szu-agent-plugin.jar homework list \
  --username 2023150090 --env-file .env --format json
```

## Notes

- 登录复用 `CasLoginStep`，但起始导航 URL 改为 `https://lms.szu.edu.cn/user/index`
- 页面结构（来自用户提供的 HTML）：
  - 列表容器：`.todo-list-container`
  - 单项：`.todo-item`
  - 标题：`.todo-title .text-too-long`
  - 状态：`.todo-status div`（当前示例为 `wait-submit` / 待提交）
  - 课程名：`.todo-course .text-too-long`
  - 截止时间：`.todo-datetime` 文本，格式 `YYYY.MM.DD HH:mm`
- `homeworkId` 优先从 `.todo-actions a.todo-link` 的 `href` 中 activity ID 提取；提取失败时使用索引兜底。

## Trace

完成后记录 trace 到 `harness-records/traces/YYYYMMDD-HHMMSS-US-006.md`

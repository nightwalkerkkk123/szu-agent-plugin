# Plan: 畅课作业查询 + CAS 登录状态持久化

**Created:** 2026-06-14  
**Stories:** US-006, US-007  
**Order:** US-006 → US-007（方案 C）

---

## 背景

用户希望增加“作业功能”，登录方式与现有体育场馆一致（CAS）。经 intake 评估，拆分为两个标准车道 story：

1. **US-006**：先实现畅课作业列表查询（复用现有登录流程）。
2. **US-007**：再实现 CAS 登录状态持久化（Playwright `storageState`），使所有校园任务受益。

---

## US-006 实现步骤

| 步骤 | 文件 | 说明 |
|---|---|---|
| 1 | `src/main/java/edu/szu/agent/domain/Homework.java` | 作业记录：homeworkId, courseName, title, deadline, status |
| 2 | `src/main/java/edu/szu/agent/domain/HomeworkListResult.java` | Sealed interface：Success / Failure |
| 3 | `src/main/java/edu/szu/agent/client/ChaoxingHomeworkClient.java` | 业务编排：open → CAS 登录 → 导航 LMS → 提取作业 → close |
| 4 | `src/main/java/edu/szu/agent/client/step/NavigateToHomeworkStep.java` | 导航到 `https://lms.szu.edu.cn/user/index` |
| 5 | `src/main/java/edu/szu/agent/client/step/ParseHomeworkListStep.java` | 调用 `HomeworkListExtractor` 解析页面 |
| 6 | `src/main/java/edu/szu/agent/client/homework/HomeworkListExtractor.java` | 构造 JS 脚本，从 `.todo-list-container` 提取作业数组 |
| 7 | `src/main/java/edu/szu/agent/task/HomeworkTask.java` | `CampusTask<HomeworkListResult>` 实现 |
| 8 | `src/main/java/edu/szu/agent/cli/HomeworkCommand.java` | `homework` 父命令 |
| 9 | `src/main/java/edu/szu/agent/cli/HomeworkListCommand.java` | `homework list` 子命令 |
| 10 | `src/main/java/edu/szu/agent/cli/Main.java` | 注册 `homework_list` Skill |
| 11 | `src/main/java/edu/szu/agent/error/ErrorCode.java` | 新增 `HOMEWORK_PAGE_LOAD_FAILED`、`HOMEWORK_LIST_EMPTY` |
| 12 | 测试文件 | `ChaoxingHomeworkClientTest`、`HomeworkTaskTest`、`HomeworkListCommandTest`、`HomeworkListExtractorTest` |

## US-007 实现步骤

| 步骤 | 文件 | 说明 |
|---|---|---|
| 1 | `src/main/java/edu/szu/agent/browser/BrowserLifecycle.java` | 新增 `loadState(Path)` / `saveState(Path)` |
| 2 | `src/main/java/edu/szu/agent/browser/PlaywrightBrowserAdapter.java` | 原生实现 storageState 加载/保存 |
| 3 | `src/main/java/edu/szu/agent/session/SessionManager.java` | Singleton：路径、过期检查、加载、保存、清除 |
| 4 | `src/main/java/edu/szu/agent/client/step/CasLoginStep.java` | 先尝试 session 恢复，失败再表单登录 |
| 5 | `src/main/java/edu/szu/agent/client/VenueBookingClient.java` | `finally` 中保存 session |
| 6 | `src/main/java/edu/szu/agent/client/ChaoxingHomeworkClient.java` | `finally` 中保存 session |
| 7 | `src/main/java/edu/szu/agent/cli/VenueCommand.java` / `HomeworkListCommand.java` | 新增 `--clear-session` flag |
| 8 | `src/test/java/edu/szu/agent/browser/FakeBrowser.java` | 新增 `loadState` / `saveState` stub |
| 9 | 测试文件 | `SessionManagerTest`、`CasLoginStepTest` 补充 session 路径 |
| 10 | `docs/adr/0008-session-persistence.md` | 记录决策与风险 |

---

## 依赖与风险

- US-006 依赖用户已提供 LMS 页面 HTML，选择器基于真实 DOM。
- US-007 涉及认证凭证落地，需 security-reviewer 审查并通过 archunit/脱敏检查。
- 两个 story 均不得降低测试覆盖率。

# Feature Intake

> 每个实现任务在代码变更前先经过 intake gate。
> 人类不需要分类——harness 来分类。

## Intake Flow

```
User prompt
    |
    v
Classify input type
    |
    v
Restate as work item
    |
    v
Find affected product docs and stories
    |
    v
Run risk checklist
    |
    v
Choose lane: tiny, normal, or high-risk
```

## Input Types

| Type | Use when | Typical artifact |
|---|---|---|
| **New spec** | 用户提供项目 spec，转为 harness-ready docs | Product docs, decisions |
| **Spec slice** | 从已有 spec 实现选中的行为 | Story packet |
| **Change request** | 改变、修复或优化已有行为 | Story packet or direct patch |
| **New initiative** | 添加需要多个 story 的大型产品区域 | Initiative notes + story packets |
| **Maintenance request** | 技术、依赖或操作性变更 | Story packet, validation report |
| **Harness improvement** | 改进人-Agent 协作方式 | Direct docs update or backlog item |

## Lanes

### Tiny（微车道）

用于低风险文档、copy、命名或窄编辑。

也用于初始项目设置：当工作限于安装声明的依赖、连接服务器入口、添加健康检查端点，而不创建域 schema、CRUD 行为、auth、authorization 或数据迁移时。

**要求：**
- 在实现前记录 intake row
- 直接打补丁
- 保持受影响 docs 最新
- 运行可用的 quick checks

### Normal（标准车道）

用于有界爆炸半径的故事级行为。

**要求：**
- 从 `docs/templates/story.md` 创建或更新一个 story 文件
- 链接相关 product docs
- 添加或更新验证期望
- 实现最小的垂直切片

### High-Risk（高风险车道）

当工作可能影响安全、数据、范围、契约或多个角色/平台时使用。

**要求：**
- 使用 `docs/templates/high-risk-story/` 创建 story 文件夹
- 填写 `execplan.md`、`overview.md`、`design.md`、`validation.md`
- 方向模糊时请求人类确认
- 当行为、架构、授权、数据所有权、API shape 或验证要求发生有意义变化时记录 durable decision

## Risk Checklist

每个检查项标记适用情况：

| Risk flag | Applies when |
|---|---|
| Auth | login、logout、sessions、JWT、password、refresh token |
| Authorization | roles、permissions、tenant or company scope |
| Data model | schema、migrations、uniqueness、deletion、retention |
| Audit/security | audit logs、privacy、sensitive data、access logs |
| External systems | email、payments、cloud services、provider SDKs、queues、webhooks |
| Public contracts | API shape、response envelope、client-visible behavior |
| Cross-platform | desktop/mobile/browser split、native shell behavior |
| Existing behavior | already implemented or test-covered behavior changes |
| Weak proof | unclear or missing tests around the affected area |
| Multi-domain | more than one product domain changes at once |

## Classification

```
0-1 flags:
  tiny or normal, based on code impact

2-3 flags:
  normal with stronger validation

4+ flags:
  high-risk

Any hard gate:
  high-risk unless the human explicitly narrows scope
```

Hard gates:
- Auth
- Authorization
- Data loss or migration
- Audit/security
- External provider behavior
- Removing or weakening validation requirements

## Output

At the end of intake, the agent should be able to say:

```
Lane: normal
Reason: touches authorization, API contract, and audit behavior.
Docs: permissions, account-settings, audit-log.
Story: docs/stories/US-XXX-feature-name.md
Validation: unit, integration, E2E.
```

---

## 面向本项目的调整

本项目为 Java 单项目 + 课程作业，简化如下：

- **Durable layer**: 使用 JSON 文件（`harness-records/`）而非 SQLite，减少依赖
- **Story packet**: 简化为单个 markdown 文件（`docs/stories/US-XXX-name.md`）
- **High-risk template**: 使用简化模板（无 execplan/design/validation 分离）
- **Trace**: 使用 JSON 文件记录每次任务（`harness-records/traces/`）

完整模板见 `docs/templates/`。
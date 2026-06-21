# Story: US-010 深大校园知识库答疑 (Knowledge Base)

**Lane:** normal
**Created:** 2026-06-19
**Status:** proposed
**ADR:** (待创建) ADR-0010 · 知识库模块架构设计
**Related:** [US-009 课表模块](../adr/0009-schedule-module-design.md) · [CLAUDE.md](../../CLAUDE.md) · [PRD.md](../PRD.md)

---

## 1. Problem Statement

深大学生日常会遇到大量**重复、零散、需要查文档**的问题:

- **新生**入学流程繁琐: 报到 / 选课 / 医保 / 校园卡补办 / 宿舍入住
- **老生**高频问题: 奖学金评定 / 考研流程 / 图书馆开放时间 / 就业手续 / 考试安排
- 这些信息分散在 `szu.edu.cn`、公文通、学生手册、ehall 后台等多个来源
- 现状: 学生要么多次访问不同网页,要么只能问学长或翻 QQ 群历史消息

**核心痛点**: 没有一个聚合的、机器可调用的本地知识入口。学生希望**通过 agent 直接问、agent 直接答**,无需自己打开 5 个网页找信息。

## 2. Solution

在 `szu-agent-plugin` 中新增 `kb` 子命令 + `knowledge_ask` Skill + MCP tool,封装一份 **本地 YAML 知识库** (8 类 × 5 条 = 40 条预制 FAQ)。

**查询流程:**
1. 关键字全文检索(标题 / 关键字列表 / 正文子串匹配),按命中度排序
2. **若 KB 无命中**,根据 query 命中硬编码路由表(课表 / 作业 / 场地预约)→ 自动调用对应 Skill,结果拼装到 answer
3. **都没命中** → 返回 `NoMatch` 信号,Agent 端提示用户

**架构约束**: 项目 CLAUDE.md 明确"**不是 AI Agent** — 无 NLU / 意图识别 / 对话管理"。本模块只做**知识查询工具**,自然语言包装由外部 Agent 负责。

## 3. User Stories

1. As 新生,我可以问"新生怎么选课",agent 返回结构化的选课流程说明
2. As 新生,我可以问"校园卡丢了怎么办",agent 返回补办流程与办公地点
3. As 新生,我可以问"报到要带什么材料",agent 返回报到清单
4. As 新生,我可以问"宿舍怎么申请",agent 返回宿舍申请流程
5. As 新生,我可以问"医保怎么激活",agent 返回医保激活步骤
6. As 老生,我可以问"国家奖学金怎么申请",agent 返回申请条件与流程
7. As 老生,我可以问"考研预报名什么时候",agent 返回考研时间节点
8. As 老生,我可以问"就业推荐表怎么开",agent 返回就业手续流程
9. As 任意学生,我可以问"图书馆周末几点开门",agent 返回图书馆开放时间
10. As 任意学生,我可以问"校园网怎么充值",agent 返回校园网缴费方式
11. As 任意学生,我可以问"考场在哪里查",agent 返回考场查询路径
12. As 任意学生,我可以问"挂科怎么办",agent 返回补考 / 重修规定
13. As 任意学生,我可以问"我明天上什么课",agent 自动路由到 `schedule_list`,返回我的课表
14. As 任意学生,我可以问"我还有什么作业没交",agent 自动路由到 `homework_list`,返回作业列表
15. As 任意学生,我可以问"明天 8 点羽毛球能订吗",agent 自动路由到 `booking_venue`
16. As 开发者,我可以用 `kb list --category=SCHOLARSHIP --audience=SOPHOMORE` 列出条目
17. As 开发者,我可以用 `skill list` 看到 `knowledge_ask` 已注册
18. As 开发者,我可以用 `mcp list` 看到 `knowledge_ask` 的 inputSchema
19. As Agent 平台,我可以用 JSON 解析 `kb ask` 输出,自行组织自然语言回复
20. As QA,我可以执行 `mvn test`,所有 KB 单元测试 + 路由测试通过,行覆盖 ≥ 80%

## 4. Implementation Decisions

### 4.1 Package 划分(镜像 schedule / homework 模块)

```
edu.szu.agent.domain.knowledge/
  KnowledgeCategory (enum)     — 8 类
  Audience           (enum)     — FRESHMAN / SOPHOMORE / ALL
  KnowledgeEntry     (record)   — id, title, category, audience, keywords, body, sourceUrl, lastUpdated
  KnowledgeQuery     (record)   — query, audience?, category?, maxResults?
  KnowledgeAnswer    (sealed)   — Answer{kbHits, routedSkill} | NoMatch{query, reason}

edu.szu.agent.client.knowledge/
  KnowledgeBase              — load + validate + index + search (Deep Module)
  KnowledgeSkillRouter       — 硬编码路由表 + Skills 单例分发 (Deep Module)
  KnowledgeBaseClient        — 编排器: KB 搜索 → 路由 → 拼装
  KnowledgeYamlLoader        — Jackson YAML 反序列化 + schema 校验

edu.szu.agent.task/
  KnowledgeAskTask  — CampusTask<KnowledgeAnswer> 薄壳

edu.szu.agent.cli/
  KbCommand         — `kb` 父命令
  KbAskCommand      — `kb ask "..."`
  KbListCommand     — `kb list --category=X --audience=Y`

edu.szu.agent.mcp/
  ToolSchema        — schemaFor() 新增 knowledge_ask / knowledge_list

edu.szu.agent.error/
  ErrorCode         — 新增 KNOWLEDGE_LOAD_FAILED / KNOWLEDGE_SCHEMA_INVALID / KNOWLEDGE_ROUTING_FAILED
```

### 4.2 Deep Modules 设计要点

| 模块 | 责任 | 公开 API | 测试覆盖 |
|---|---|---|---|
| `KnowledgeBase` | 加载 + 校验 + 索引 + 检索,封装 YAML 生命周期 | `load(URL)`、`validate()`、`search(KnowledgeQuery)`、`list(Audience?, Category?)` | 加载失败、重复 id、空 KB、关键字命中、audience/category 过滤 |
| `KnowledgeSkillRouter` | 跨 Skill 路由,3 个硬编码 target | `Optional<RoutedSkill> tryRoute(String query)` | 路由命中、未命中、目标未注册、递归防护(knowledge_ask 不在表内) |
| `KnowledgeBaseClient` | 编排器 | `KnowledgeAnswer ask(KnowledgeQuery)` | KB 命中 alone / 路由 alone / 组合 / 空 |
| `KnowledgeAskTask` | CampusTask 薄壳 | `execute(TaskInput)` | TaskInput 解析异常、client 调用 |

### 4.3 跨 Skill 路由硬编码表

```java
// KnowledgeSkillRouter.java — 3 路由目标,knowledge_ask 自我排除
Map<List<String>, String> ROUTES = Map.of(
    List.of("课程", "课表", "今天上什么", "本周课", "明天上什么"), "schedule_list",
    List.of("作业", "deadline", "待提交", "作业清单", "未交作业"),    "homework_list",
    List.of("预约", "场地", "明天打球", "订场", "羽毛球"),            "booking_venue"
);
```

**递归防护**: `KnowledgeSkillRouter.ROUTES` 显式不含 `knowledge_ask`;若 query 自身被路由表命中后,该路由路径不再二次调用 `knowledge_ask`,杜绝无限递归。

**目标 Skill 未注册 graceful degrade**: `tryRoute` 通过 `Skills.getInstance().all()` 遍历匹配,目标未注册时返回 `Optional.empty()`,主流程降级为纯 KB 路径,不报错。

### 4.4 知识条目格式(YAML,单一文件)

`src/main/resources/knowledge/szu-kb.yaml`:

```yaml
- id: course-selection-freshman-001
  title: 新生如何选课?
  category: COURSE_SELECTION
  audience: FRESHMAN
  keywords: [选课, 新生, 怎么选课, 选课系统]
  body: |
    1. 登录 ehall.szu.edu.cn
    2. 进入"我的选课"模块
    3. ...
  source_url: https://ehall.szu.edu.cn/
  last_updated: 2026-06-19
```

### 4.5 8 个类目(40 条目)

`COURSE_SELECTION` / `SCHOLARSHIP` / `LIBRARY` / `DORMITORY` / `HEALTH_INSURANCE` / `REGISTRATION` / `CAREER` / `EXAM_GRADUATE`,每类 5 条。

### 4.6 不动现有代码

- 不修改 `Skill` / `Skills` / `CampusTask` / `TaskInput` / `CommandOutput` 公共接口
- 若需要 `Skills` 按 name 查询,新增 `Optional<Skill<?>> findByName(String)`,不破坏 `all()`

### 4.7 不引入新依赖

`jackson-dataformat-yaml` 已在 pom.xml,无需新增依赖。

### 4.8 复用 `CommandOutput`

复用 US-009 PR-3 抽出的 `edu.szu.agent.cli.CommandOutput.formatResult()` / `exitCodeFor()`。若 US-009 尚未合入,本模块负责抽出,US-009 PR-3 直接复用本模块的抽取。

## 5. Testing Decisions

**外部行为优先**: 测试 KB 加载 / 校验 / 检索的外部行为,不测试 YAML 字符串细节;测试路由决策路径,不测试 Skill 内部实现。

| 模块 | 测试类 | 覆盖场景 |
|---|---|---|
| `KnowledgeBase` | `KnowledgeBaseTest` | 加载成功 / 失败、schema 校验失败、id 重复、空 KB、关键字命中(单 / 多)、命中度排序、audience 过滤、category 过滤、`@ParameterizedTest` 关键字命中 5+ 变体 |
| `KnowledgeSkillRouter` | `KnowledgeSkillRouterTest` | 路由命中 3 类、未命中、目标未注册 graceful degrade、递归防护断言(knowledge_ask 不在表内)、`Skills` 单例 mock |
| `KnowledgeBaseClient` | `KnowledgeBaseClientTest` | KB 命中 alone / 路由 alone / 组合 / 空结果、`KnowledgeAnswer` sealed 形态断言 |
| `KnowledgeAskTask` | `KnowledgeAskTaskTest` | `TaskInput` 缺 query 异常、client 调用、薄壳透传 |
| `KbAskCommand` / `KbListCommand` | `KbAskCommandTest` / `KbListCommandTest` | format json / human、exit code 映射、ErrorCode 路径 |
| `ToolSchema` | `ToolSchemaTest`(增量) | `knowledge_ask` / `knowledge_list` schema 结构、必填字段 |
| `KnowledgeYamlLoader` | `KnowledgeYamlLoaderTest` | YAML 解析、schema 失败、id 重复、空文件 |

**Prior art**: 镜像 `HomeworkListExtractorTest` / `ChaoxingHomeworkClientTest` / `HomeworkListCommandTest` 的 AAA 模式 + `@ParameterizedTest` + `@CsvSource`。

**覆盖率**: 行覆盖 ≥ 80%(JaCoCo `verify` 阶段 enforce)。

## 6. Out of Scope

- ❌ NLU / 意图识别 / LLM 直答(由外部 Agent 负责)
- ❌ 公文通实时抓取 / `szu.edu.cn` 网页抓取(启动期不联网)
- ❌ RAG / 向量检索 / 语义匹配
- ❌ 知识库热更新(只读 jar 内 YAML)
- ❌ 用户级扩展目录 `~/.szu-agent/kb/`(预留 P1 扩展点)
- ❌ 多语言(只中文)
- ❌ KB 编辑 / 写入条目(只读)
- ❌ ICS / RSS 推送
- ❌ 与 US-009 课表模块深度联动(只路由,不做"作业 deadline 临近时建议预约时间"等复合业务)
- ❌ 时间敏感字段计算(如 `effective_from` / `effective_to`)
- ❌ 跨 Skill 路由结果的可配置化(YAML 化,留 P1)

## 7. Further Notes

### 7.1 与 US-009 共用基础设施

- 若 US-009 PR-3 `CommandOutput` 已落地,本模块直接复用
- 若未落地,本模块负责抽出(代码净增量 ≤ 30 行),US-009 后续 PR 直接复用

### 7.2 Skills 单例需要新方法?

`Skills` 目前只暴露 `all()`。`KnowledgeSkillRouter` 需要按 name 找 Skill。方案:

```java
// Skills.java 新增方法(不破坏现有 API)
public Optional<Skill<?>> findByName(String name);
```

PR 包含该 5 行新增 + 1 个单测。

### 7.3 路由目标 Skill 状态

| 目标 Skill | 状态 | 路由兼容性 |
|---|---|---|
| `homework_list` | ✅ 已上链 | 路由可直接调用 |
| `booking_venue` | ✅ 已上链 | 路由可直接调用 |
| `schedule_list` | ⚠️ US-009 进行中 | 路由目标未注册时 graceful degrade,KB 主路径不受影响 |

### 7.4 实施路径(3 个 PR)

```
PR-1 [0.5d] Domain + Loader       KnowledgeCategory/Audience/Entry/Query/Answer + KnowledgeYamlLoader + KnowledgeBase(纯加载/校验/索引)
PR-2 [0.5d] Matcher + Router      KnowledgeMatcher(关键字评分) + KnowledgeSkillRouter + Skills.findByName
PR-3 [1.0d] Client + CLI + MCP    KnowledgeBaseClient + KnowledgeAskTask + KbCommand/KbAskCommand/KbListCommand + ToolSchema case + Main 注册 + ErrorCode 3 个
PR-4 [0.5d] YAML 内容 + Trace     写 40 条 FAQ + mvn verify + 真实账号跑 kb ask(可选,验证 graceful degrade)
```

每个 PR 完成后:
- `mvn test` 必须绿
- `mvn verify` JaCoCo 整体 ≥ 80%
- 不能跨 PR 提 commit

### 7.5 验证命令

```bash
# 1. 单元测试 + 覆盖率
mvn -q test
mvn verify

# 2. CLI 启动校验(测试 YAML 加载)
java -jar target/szu-agent-plugin.jar kb list --format json

# 3. Skill 列表
java -jar target/szu-agent-plugin.jar skill list --format json | jq '.skills[].name'

# 4. MCP 工具列表
java -jar target/szu-agent-plugin.jar mcp list --format json | jq '.tools[].name'

# 5. KB 关键字查询
java -jar target/szu-agent-plugin.jar kb ask "新生怎么选课" --format json

# 6. KB 路由查询(命中 schedule_list)
java -jar target/szu-agent-plugin.jar kb ask "我明天上什么课" --format json

# 7. KB 路由查询(命中 homework_list)
java -jar target/szu-agent-plugin.jar kb ask "还有什么作业" --format json

# 8. human format
java -jar target/szu-agent-plugin.jar kb ask "新生怎么选课" --format human
```

### 7.6 Trace

完成后记录 trace 到 `harness-records/traces/YYYYMMDD-HHMMSS-US-010.md`。

## 8. References

- **CLAUDE.md** — 项目入口,含架构约束("不是 AI Agent")
- **docs/PRD.md** — 项目级 PRD,F2 / F3 / F4 与本模块共用基础设施
- **ADR-0009 课表模块架构设计** — 模块镜像模板,D1-D10 全部可类比
- **ADR-0007 架构深度审视** — BookingContext 膨胀相关讨论,与本模块无直接耦合(只读模块不需要 Context)
- **docs/plans/PLAN-schedule.md** — 4-PR 拆分模式参考
- **docs/stories/US-006-chaoxing-homework-list.md** — Story 格式参考
- **docs/design-patterns.md** — 4 模式登记(本模块复用既有 4 模式)
- **Coding Style** `.claude/rules/ecc/java/coding-style.md` — record / sealed / Optional 规范
- **Testing** `.claude/rules/ecc/java/testing.md` — JUnit 5 + AssertJ + Mockito + JaCoCo ≥ 80%

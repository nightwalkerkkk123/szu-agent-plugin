# ADR-0001 · 项目方向校准(Grill 共识)

**Date:** 2026-06-11
**Status:** Accepted
**Supersedes:** PRD §3 / §8 中关于"dry-run 默认演示模式"与"Skill 工厂"的条款
**Extended by:** ADR-0005(凭证流转)、ADR-0006(Phase 1 子决定)、ADR-0007(架构深度审视)

---

## Context

Phase 1 文档完成时,PRD 把项目定位成"给 Agent 调用的 CLI/Skill/MCP 工具",但**演示模式默认是 `--dry-run` 配 `FakeBrowser`**,真预约流程被设计成"仅技术验证,不在作业演示中触发"。这与项目核心卖点(让 Agent 真的帮你订到场地)矛盾,且为凑齐 5 设计模式而硬塞了 `ClientFactory`(只注册 1 个 Skill)和 `ErrorClassifier`(枚举已自带元数据)两个无业务意义的类。

通过一次 grill 拷问,把项目方向重新对齐到"真演示 + 业务自然生长"的设计模式选择。后续经 ADR-0007 深度审视,5 模式 → 4 模式(删 Static Factory),见 D9。

---

## Decisions

### D1 项目定位:Agent 工具,CLI 是核心工作单元
不是 AI Agent,是给 Agent(OpenClaw / Claude / ChatGPT)调用的工具。CLI 是第一性工作单元,Skill/MCP 是**薄壳 wrapper**。

### D2 真演示:跑完整预约流程
课堂演示必须真打开 Playwright,真登录 ehall,真选场地,**真点提交,真占场地**。不允许"演示版只跑到提交按钮前停下"。

### D3 演示重复性:项目轮换
同账号同日同时段同项目预约成功一次后无法重复。**靠不同体育项目轮换**(网球 / 羽毛球 / 健身房)实现多次演示。不实现"取消预约"逆向 API。

### D4 Fake/dry-run 降级
`FakeBrowser` 与 `--dry-run` **仅作单元测试夹具**,不出现在课堂演示,不是默认模式。命令行默认 `java -jar ... book ...` 直接走 Playwright 真跑。

### D5 Agent 协议:CLI 优先,Wrapper 跟随
工作单元只做 CLI(`java -jar` 接受 `--key value` 参数,stdout 输出 JSON,退出码 0-4)。各 Agent 协议各做一层薄 wrapper:
- Claude/OpenClaw: `.skill/SKILL.md` 教 Agent 怎么 `exec` 这个 jar
- MCP server: 单独的 Node/Java 进程,`tools/call` 收到后 fork 这个 jar

CLI 不嵌 MCP server,jar 是无状态的。

### D6 凭证分层
CLI 读密码的优先级:
1. 进程环境变量(`SZU_PASSWORD_XXXX`)
2. cwd 的 `.env`(直接 `java -jar` 时)
3. Skill 目录的 `.env`(Skill wrapper fork CLI 前 `cd $SKILL_DIR` 或注入 env)

jar 永远不含凭证。使用 `io.github.cdimascio:dotenv-java`。

### D7 CAS 登录视为稳定
认定深大 CAS 登录无图形验证码 / 无账号锁定 / session 可复用。后续如遇验证码,触发"局限性分析"章节,不在 P0 处理。

### D8 提交物
课程报告(局限性分析必含) + 现场演示/答辩 + Java 源码仓库。**老师会 `grep "// Design Pattern"`** 数模式数量。

### D9 设计模式重选:业务自然生长,不硬塞

| 模式 | 落点 | 业务理由 |
|---|---|---|
| Builder | `BookingRequest.Builder` | 6 参数请求,链式构造 |
| Singleton | `ConfigManager` / `Tracer` | 配置 / trace 全局唯一 |
| Strategy | `Matcher<T>` (4 实现) + `RetryPolicy` (3 实现) | 真有精确/包含/正则/业务 4 种匹配 + 固定/退避/不重试 3 种重试 |
| Adapter | `PlaywrightBrowserAdapter` | 包装 Playwright API 适配 `BrowserLifecycle`(教科书深度:把链式 API 收成直接方法) |

**删除**:
- `ClientFactory`(只 1 个 Skill)、`ErrorClassifier`(枚举已带元数据)、`AgentToolPlatform` Facade(CLI 本身就是入口)
- **`BrowserFactory` / Static Factory 模式**(经 ADR-0007 深度审视确认):工厂 3 行 switch,实现 ≈ 接口;改 `ConfigManager` 配 `browser.kind` 注入,seam 深度提升;5 模式 → 4 模式,每个模式都有 2+ 处真业务落地

### D10 P1 业务延后,接口预留
代码层只实现 `book`(体育馆预约)一个业务。`CampusTask<T>` 接口保留作为扩展点,但**不算第 6 个设计模式**。报告里写"扩展方法"小节,把公文通 / 畅课 / 成长方案 / 企业微信 / 邮件草稿列为 roadmap。Booking 跑稳后再逐个实现。

---

## Consequences

### 好处
- **演示更震撼**:老师看到 Agent 真的帮你订到场地,比看 Fake 跑 JSON 印象深 10 倍
- **代码更朴素**:从原计划 ~30 类降到 ~15 类,ddl 压力小
- **答辩更稳**:每个设计模式都能答出业务理由,不会被"为什么需要这个工厂"问倒
- **路径更清晰**:CLI → Skill → MCP 三层套娃,任何 Agent 协议火了都能补一个 wrapper

### 代价 / 风险
- **真预约一次性**:演示当天每个项目只能预约 1 次,需要演示前不预约任何项目,留 3-4 个项目作为子弹
- **演示日依赖网络**:ehall 校园网 + Playwright + CAS 任何一环挂掉,演示翻车
- **凭证管理责任在用户**:演示前必须确认 `.env` 或 env var 已就绪,CLI 不交互式提示
- **`CampusTask<T>` 接口存在但只有 1 个实现**:老师可能问"为什么写接口" → 答"P1 扩展点,booking 稳后扩展"

### 演示兜底
- 提前 1-2 天在演示账号上"故意不预约任何项目"留弹药
- 准备 2-3 个备用项目 + 备用时段
- 准备一段录屏 backup,真演示失败时切录屏
- `.env` 文件演示前手工 `cat` 验证

---

## 实施路径(5 天)

```
Phase 0 [0.5d]  骨架       pom.xml + 包结构 + Logback + dotenv-java
Phase 1 [1.0d]  无依赖基础  domain/ + error/ + retry/ + matcher/
Phase 2 [1.0d]  浏览器抽象  browser/ (+BrowserFactory) + selectors/
Phase 3 [1.0d]  业务编排    task/ + client/ + config/ + observability/ + account/
Phase 4 [1.0d]  CLI + Wrapper  cli/ + skill/ + mcp/
Phase 5 [0.5d]  收尾        测试补 80% + 报告 + 演示脚本
```

每个 Phase 完成 → `mvn test` 必须绿 → 才进下一个。

---

## Open questions (post-acceptance review 2026-06-11)

在 ADR 接受后,基于与 Python 参考实现及 Java 包结构的对照,发现 4 个待
落地前需要回答的问题。**不改变已接受的决策**,仅在对应 Phase 开始前明确。

### OQ1 — Skill wrapper 凭证注入契约(D5 × D6)
D5 要求 Skill wrapper 在 `exec` jar 前 `cd $SKILL_DIR` 或注入 env;
D6 优先级是 env var > cwd `.env` > skill 目录 `.env`。若 wrapper
既不切 cwd 也不显式注入,D6 的查找链会在没有 `.env` 的目录上断掉。
**解决**:在 ADR-0004 (Phase 4) 中显式写"Skill 必须保证 jar 进程
`pwd` 包含正确的 `.env`,或显式 `export` 全部 `SZU_PASSWORD_XXX`"。

### OQ2 — 演示后场地清理(D3)
D3 假设同账号可轮换项目做多次演示,但未说明**演示成功后留下的真实
预约**由谁清理。两种情形需要分别处理:
- 自己账号演示 → 演示后手工取消(D8 说不做取消 API,只能 ehall 手工)
- 借账号演示 → 与账号主人的沟通机制
**解决**:并入 D8 演示兜底清单,演示日脚本加一条"演示后 5 分钟内
ehall 手工取消占位场地"。

### OQ3 — ErrorClassifier 删除后分类能力归属(D9)
D9 删 Python `ErrorClassifier`,理由是"枚举自带元数据"。需在
Phase 1 (`error/` 包) 实施前确认 Java 端 `BookingErrorCode` 枚举
+ `BookingException` 密封继承结构,能否覆盖 Python 原分类器全部
能力(网络/登录/选场/提交/未知 5 类)。覆盖 → 一笔带过;覆盖不全
→ 在 `error/` 内补一个薄分类工具(不叫 Classifier 即可)。
**解决**:Phase 1 起步前列 Python `ErrorClassifier` 判定表 → Java
枚举等价映射,留 1-2 小时预算。

### OQ4 — 真实预约流的失败重试策略(D2 × D7)
D2 要求真跑 Playwright 端到端;D7 假定 CAS 稳定无验证码。但网络抖动、
点击未命中、CAS 偶发重定向等**短暂性失败**如何处理,ADR 没写。
两种极端都不可取:
- 任意失败都重试到死 → 验证码触发时浪费大量时间,演示翻车
- 任何失败都中止 → 一次抖动就翻车
**解决**:Phase 1 `retry/` 包设计时定义**有界重试**(≤2 次,
仅对"未触发 CAS 验证码、未点击提交"的状态)+ 状态机("已点提交后
绝不再点")。此决策可能升格为 ADR-0005。

---

## 后续 ADR 索引(预留)

- ADR-0002: `BrowserLifecycle` 接口设计与 Playwright 适配细节(Phase 2 开始时写)
- ADR-0003: `CampusTask<T>` 接口与扩展模式(P1 开始时写)
- ADR-0004: Skill/MCP wrapper 协议契约(Phase 4 开始时写)
- ADR-0005: 真实预约流有界重试策略(Phase 1 开始时写,由 OQ4 升格)

---

## 引用

- 原 PRD: `docs/PRD.md`
- 原设计模式清单: `docs/design-patterns.md`
- 系统地图: `docs/system-map.md`
- Python 参考: `/Users/wangzihao/Code/登录体育馆_cloak/src/booking/`

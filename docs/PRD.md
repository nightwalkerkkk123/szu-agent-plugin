# PRD — SZU Agent Plugin

> 深圳大学内部网 AI Agent 工具与 CLI 插件
>
> 版本 v1.0 · 学号 2023150090 · 姓名 王子豪

> ⚠️ **ADR 校准声明**:本 PRD 的 §3(F1.4 dry-run / F3 通用框架 / F6 多账号 / F7 Skill/MCP)和 §8(风险与权衡)
> 中关于"dry-run 默认演示模式"与"Skill 工厂"的条款,已被 **ADR-0001** (2026-06-11, Accepted) 校准。
> 实施以 ADR-0001 为准 — 见 `docs/adr/0001-project-direction-recalibration.md`。

---

## 1. 背景与目标

### 1.1 背景

深圳大学内部网存在大量**重复、固定、流程化**的操作:体育场馆预约、公文通查询、畅课任务查看、成长方案查询、企业微信消息总结、邮件草稿生成。传统方式需要用户手动登录、点击菜单、筛选信息、填写表单。

### 1.2 目标

将这些固定流程**封装成可被 AI Agent 调用的标准化工具**:

- 提供 **CLI 工具**:每个内部网操作对应一个子命令,接受结构化输入,返回 JSON 结果
- 提供 **Skill 插件接口**:第三方 Agent 框架(如 OpenClaw)可加载本项目作为 Skill
- 提供 **MCP 工具导出**:符合 MCP 协议的 server 可直接调用本项目 CLI
- 用户通过对话让 Agent 发起任务,Agent 解析后调用本 CLI,本 CLI 控制本地浏览器(Playwright,通过 `PlaywrightBrowserAdapter` 适配)完成操作,返回结果

### 1.3 非目标

- ❌ 本项目**不是**一个 AI Agent —— 不做自然语言理解、意图识别、对话管理
- ❌ 本项目**不绕过**学校内部网(不破解验证码、不攻击风控、不高频异常访问)
- ❌ 本项目**不发送**敏感邮件 / 消息,只生成草稿由用户确认

---

## 2. 用户与场景

| 用户 | 场景 |
|---|---|
| **普通学生** | 通过 ChatGPT Agent / OpenClaw 微信端,口语化表达"帮我预约明晚 8 点羽毛球" |
| **技术学生** | 自行部署本项目 CLI,在自己的 Agent 脚本中 `exec` 调用 |
| **开发者** | 扩展新 Skill:实现 `CampusTask<T>` 接口 + 标注 `@AgentTool` + 注册到 `SkillManager` |

外部 Agent(OpenClaw / ChatGPT Agent / 自建 Agent)通过以下方式之一调用本项目:

1. **直接 exec CLI** —— 最简单,适合任何 Agent 框架
2. **MCP 协议** —— `MCPToolProvider` 暴露工具 schema,MCP client 直接调用
3. **Java 库内嵌** —— 在同进程 Agent 中通过 `CampusTask<T>` API 调用

---

## 3. 功能需求

### 3.1 P0 — 必做(本作业核心)

#### F1. CLI 入口

- **F1.1** 提供单一可执行 jar,通过子命令路由不同 Skill
- **F1.2** CLI 接受 `--format json` 输出结构化结果;默认人类可读
- **F1.3** 退出码语义:0=成功,1=业务失败,2=参数错误,3=环境错误,4=浏览器错误
- **F1.4** `--dry-run` 仅作单元测试夹具(`FakeBrowser`),**不作为课堂演示模式**(见 ADR-0001 D2/D4)

#### F2. 体育场馆定时预约(核心 demo Skill)

- **F2.1** 支持配置预约账号、校区、项目、日期(`LocalDate` ISO 格式 `2026-06-12`)、时间段、场地偏好;重试通过 `RetryPolicy` 配置(默认 `ExponentialBackoff(3, 2s)`,见 ADR-0006 §3)
- **F2.2** 通过 `PlaywrightBrowserAdapter` 控制本地浏览器完成:登录 → 选校区 → 选项目 → 选时段 → 选场地 → 确认
- **F2.3** 支持**有界重试**(默认 ≤3 次,见 ADR-0001 D9 + ADR-0006 retry 子决定),仅对未触发 CAS 验证码、未点击提交的状态重试
- **F2.4** 每次执行生成唯一 `trace_id`,关联日志、截图、错误码
- **F2.5** 失败时保存必要调试信息(失败步骤 + trace_id + 截图路径)

#### F3. 浏览器适配器(适配器模式)

- **F3.1** `BrowserLifecycle` 抽象接口:`launch / navigate / click / type / screenshot / close`
- **F3.2** `PlaywrightBrowserAdapter` 实现该接口,内部封装 Playwright Java 绑定,**真演示唯一入口**
- **F3.3** `FakeBrowser` 内存实现,**仅供单元测试夹具使用,不出现在课堂演示**
- **F3.4** 业务层(`VenueBookingClient`)只依赖 `BrowserLifecycle`,不感知具体浏览器

#### F4. 配置 / 日志 / 追踪 / 错误

- **F4.1** `ConfigManager` 单例,从 YAML + 进程环境变量 + dotenv-java(`--env-file` 参数指向的 .env 文件)加载配置(凭证层详见 ADR-0005 D1)
- **F4.2** 结构化日志:任务开始 / 登录 / 跳转 / 查询 / 提交 / 成功 / 失败
- **F4.3** `Tracer` 单例,生成与管理 `trace_id`,贯穿一次执行
- **F4.4** `ErrorCode` 枚举,12 个常量携带:`severity / retryable / switchAccount / screenshot / hint`;**不再单独设 `ErrorClassifier` 类**(策略由枚举自身元数据承担,见 ADR-0001 D9 + ADR-0006 error 子决定)
- **F4.5** 敏感信息(密码 / Cookie / Token)**不写日志**

#### F5. CLI 入口与薄壳 Wrapper(P0)

- **F5.1** CLI 是第一性工作单元,`java -jar` 接受 `--key value`,stdout 输出 JSON,退出码 0-4
- **F5.2** 默认 `java -jar ... booking venue` 直接走 Playwright 真跑,**不带 `--dry-run`**
- **F5.3** Skill/MCP 是 CLI 的**薄壳 wrapper**(非 P0 核心,见 ADR-0001 D5):
  - Skill:`.skill/SKILL.md` 教 Agent 怎么 `exec` 这个 jar
  - MCP:独立 Node/Java 进程,`tools/call` 收到后 fork jar
- **F5.4** CLI 不嵌 MCP server,jar 保持无状态

> **说明**:F3 通用任务框架 (`CampusTask<T>`) 仅作为 P1 扩展点保留(见 ADR-0001 D10);
> 多账号调度 (`AccountState` 状态机) 非 P0,P0 仅单账号 + 项目轮换。
> `@AgentTool` 反射 / `SkillManager` / `MCPToolProvider` 推迟到 P1。

### 3.2 P1 — 扩展接口(本作业部分实现)

| Skill | 范围 |
|---|---|
| **公文通查询** | 列表 / 筛选 / 摘要;只设计接口,实现基础 list |
| **畅课任务查询** | 作业 / 课件 / 截止;只设计接口 |
| **成长方案查询** | 学分 / 完成情况;只设计接口 |
| **企业微信消息摘要** | 用户主动粘贴消息 → 提取待办;基础实现 |
| **邮件草稿生成** | 模板化草稿 + 用户确认;基础实现 |

### 3.3 P2 — 不做

校园小巴、电费充值、报修系统、Dashboard、多账号调度器、任务队列、Docker 部署。

---

## 4. 非功能需求

| 维度 | 指标 |
|---|---|
| **可运行** | `mvn package && java -jar ...` 跑通,核心 demo `booking venue` 真跑 Playwright 占场地(见 ADR-0001 D2) |
| **可测试** | 单元测试覆盖率 ≥ 80%,JUnit 5 + AssertJ |
| **可扩展** | 新增 Skill 只需实现 `CampusTask<T>` + 标注 `@AgentTool` + 注册,不动现有代码 |
| **可观测** | 每次执行有 trace_id,日志、错误码、截图、耗时齐全 |
| **安全合规** | 不绕过验证码、不破解风控、不发送敏感邮件、敏感信息脱敏 |

---

## 5. CLI / Skill / MCP 契约

### 5.1 CLI 调用形式

```bash
szu-agent <skill-name> <action> [--key value ...] [--format json|human] [--dry-run]
```

- **stdin**: 无,所有参数走命令行
- **stdout**: JSON(或人类可读文本)
- **stderr**: 日志
- **退出码**: 0/1/2/3/4

### 5.2 JSON 输出 Schema

```json
{
  "success": true,
  "data": { "...": "..." },
  "errorCode": null,
  "errorMessage": null,
  "traceId": "20240610-abc123",
  "elapsedMs": 4321
}
```

### 5.3 退出码

| 码 | 含义 |
|---|---|
| 0 | 成功 |
| 1 | 业务失败(如无可用时段) |
| 2 | 参数错误 |
| 3 | 环境错误(配置缺失、依赖缺失) |
| 4 | 浏览器错误(启动失败、崩溃) |

### 5.4 MCP tools/list 输出

```json
{
  "tools": [
    {
      "name": "booking_venue",
      "description": "体育场馆定时预约",
      "inputSchema": {
        "type": "object",
        "properties": {
          "username": { "type": "string" },
          "campus":   { "type": "string" },
          "sport":    { "type": "string" },
          "date":     { "type": "string", "format": "date", "description": "ISO 8601 格式,例如 2026-06-12" },
          "timeSlot": { "type": "object", "properties": { "start": {"type":"string"}, "end": {"type":"string"} } }
        },
        "required": ["username", "campus", "sport", "date", "timeSlot"]
      }
    }
  ]
}
```

---

## 6. 数据模型(关键类)

```
BookingRequest
  - campus:   Campus (enum,YUEHAI/LIHU/HOUHAI,见 ADR-0006 §1.1)
  - sport:    Sport  (enum,TENNIS/BADMINTON/GYM/TABLE_TENNIS/BASKETBALL)
  - date:     LocalDate (ISO 8601,例如 2026-06-12,见 ADR-0006 §1.2)
  - timeSlot: TimeSlot  (record,start + end,见 ADR-0006 §1.5)
  - preferredVenueIndex: int (>=1,默认 1)
  - displayHint: String  (给 Agent 看的备注,可选)

TaskResult<T>
  - success:     boolean
  - data:        T
  - errorCode:   ErrorCode
  - errorMessage: String
  - traceId:     String
  - elapsedMs:   long
```

---

## 7. 验收标准

- [ ] `mvn test` 全部通过(目标 50+ 测试用例,行覆盖 ≥ 80%)
- [ ] `mvn package` 生成可执行 jar
- [ ] `java -jar szu-agent-plugin.jar booking venue --format json` **真跑** Playwright 完整流程,退出码 0
- [ ] 4 种设计模式在代码中**显式可见**(注释 + 文档):Builder / Singleton / Strategy / Adapter(见 ADR-0001 D9 + ADR-0007 D1,Static Factory 已删改 ConfigManager 注入)
- [ ] 6 种编程技术(泛型/枚举/注解/重载/抽象类/Lambda-Stream)实际使用
- [ ] `docs/class-diagram.puml` 完整
- [ ] README + PRD + design-patterns + system-map + 局限性分析齐全
- [ ] 课堂演示**真占场地**(不演示 dry-run),通过体育项目轮换支持多次演示(ADR-0001 D2/D3)

---

## 8. 风险与权衡

| 风险 | 应对 |
|---|---|
| 页面结构变化导致选择器失效 | 适配器层集中维护;失败时记录 trace + 截图,便于人工修复 |
| Playwright Java 绑定不可用 | 抽象 `BrowserLifecycle` 接口,`FakeBrowser` 仅作单元测试夹具 |
| 真实跑通预约可能违反校规 | **真演示**仍允许(ADR-0001 D2),依赖有界重试(≤2)避免高频异常;演示日期见 ADR-0001 演示兜底 |
| 增量开发 | 提案 75 需求中,P0 仅 `book` 一个业务跑通;P1 `CampusTask<T>` 扩展点保留为 roadmap |

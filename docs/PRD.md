# PRD — SZU Agent Plugin

> 深圳大学内部网 AI Agent 工具与 CLI 插件
>
> 版本 v1.0 · 学号 2023150090 · 姓名 王子豪

---

## 1. 背景与目标

### 1.1 背景

深圳大学内部网存在大量**重复、固定、流程化**的操作:体育场馆预约、公文通查询、畅课任务查看、成长方案查询、企业微信消息总结、邮件草稿生成。传统方式需要用户手动登录、点击菜单、筛选信息、填写表单。

### 1.2 目标

将这些固定流程**封装成可被 AI Agent 调用的标准化工具**:

- 提供 **CLI 工具**:每个内部网操作对应一个子命令,接受结构化输入,返回 JSON 结果
- 提供 **Skill 插件接口**:第三方 Agent 框架(如 OpenClaw)可加载本项目作为 Skill
- 提供 **MCP 工具导出**:符合 MCP 协议的 server 可直接调用本项目 CLI
- 用户通过对话让 Agent 发起任务,Agent 解析后调用本 CLI,本 CLI 控制本地浏览器(CloakBrowser / Playwright)完成操作,返回结果

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
- **F1.4** 支持 `--dry-run` 模拟运行(使用 `FakeBrowser`,不访问真实系统)

#### F2. 体育场馆定时预约(核心 demo Skill)

- **F2.1** 支持配置预约账号、校区、项目、日期(0=今天/1=明天...)、时间段、重试次数
- **F2.2** 通过 `CloakBrowserAdapter` 控制本地浏览器完成:登录 → 选校区 → 选项目 → 选时段 → 选场地 → 确认
- **F2.3** 支持有限次数的失败重试(默认 3 次,指数退避)
- **F2.4** 每次执行生成唯一 `trace_id`,关联日志、截图、错误码
- **F2.5** 失败时保存必要调试信息(失败步骤 + trace_id + 截图路径)

#### F3. 通用任务框架

- **F3.1** `CampusTask<T>` 抽象接口:任意 Skill 都实现该接口,对外行为一致
- **F3.2** `TaskResult<T>` 统一返回:成功标志 + 数据 + 错误码 + 耗时
- **F3.3** `TaskStatus` 枚举:PENDING / RUNNING / SUCCESS / FAILED / RETRYING / CANCELLED
- **F3.4** `TaskExecutor` 通用执行器,统一处理重试、超时、状态记录

#### F4. CloakBrowser 适配器(适配器模式)

- **F4.1** `BrowserLifecycle` 抽象接口:`launch / navigate / click / type / screenshot / close`
- **F4.2** `CloakBrowserAdapter` 实现该接口,内部封装 Playwright Java 绑定
- **F4.3** `FakeBrowser` 内存实现,用于测试与无浏览器环境
- **F4.4** 业务层(`VenueBookingClient` 等)只依赖 `BrowserLifecycle`,不感知具体浏览器

#### F5. 配置 / 日志 / 追踪 / 错误

- **F5.1** `ConfigManager` 单例,从 YAML + 环境变量加载配置
- **F5.2** 结构化日志:任务开始 / 登录 / 跳转 / 查询 / 提交 / 成功 / 失败
- **F5.3** `Tracer` 单例,生成与管理 `trace_id`,贯穿一次执行
- **F5.4** `ErrorCode` 枚举,每个错误码携带:`message / isRetryable / shouldSwitchAccount / hint`
- **F5.5** 敏感信息(密码 / Cookie / Token)**不写日志**

#### F6. 账号管理

- **F6.1** 支持多账号配置(从环境变量 `SZU_PASSWORD_{后4位}` 注入)
- **F6.2** `AccountState` 状态机:AVAILABLE → COOLDOWN → LOCKED
- **F6.3** 失败次数超过阈值进入冷却,冷却结束后恢复

#### F7. Skill / MCP 最小原型

- **F7.1** `@AgentTool` 注解标记可被 Agent 调用的方法
- **F7.2** 运行期反射扫描 `@AgentTool` 方法,生成工具 schema(name / description / params)
- **F7.3** `SkillManager` 注册 / 加载 / 执行 Skill
- **F7.4** `MCPToolProvider` 导出符合 MCP 规范的 `tools/list` 和 `tools/call`

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
| **可运行** | `mvn package && java -jar ...` 跑通,核心 demo `booking venue` 至少在 dry-run 模式跑通 |
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
          "date":     { "type": "integer" },
          "timeSlot": { "type": "string" }
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
  - username: String
  - campus:   Campus (enum)
  - sport:    Sport  (enum)
  - date:     int   (0=today)
  - timeSlot: String
  - maxRetry: int

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
- [ ] `java -jar szu-agent-plugin.jar booking venue --dry-run --format json` 输出合法 JSON 且退出码 0
- [ ] 5 种设计模式在代码中**显式可见**(注释 + 文档)
- [ ] 6 种编程技术(泛型/枚举/注解/重载/抽象类/Lambda-Stream)实际使用
- [ ] `docs/class-diagram.puml` 完整
- [ ] README + PRD + design-patterns + system-map + 局限性分析齐全

---

## 8. 风险与权衡

| 风险 | 应对 |
|---|---|
| 页面结构变化导致选择器失效 | 适配器层集中维护;失败时记录 trace + 截图,便于人工修复 |
| CloakBrowser Java 绑定不可用 | 抽象 `BrowserLifecycle` 接口,提供 `FakeBrowser` 用于无浏览器演示 |
| 真实跑通预约可能违反校规 | dry-run 模式为默认演示模式;真实模式仅作技术验证,不在作业演示中触发 |
| 增量开发 | 提案 75 需求中,P0 全部实现、P1 至少 2 个 Skill 有最小实现 |

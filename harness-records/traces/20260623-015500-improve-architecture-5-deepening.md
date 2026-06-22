# Trace: 5 项架构深化（improve-codebase-architecture）

## Story

improve-codebase-architecture skill 识别了 5 个深度不足的模块，按 deletion test
和 Depth 原则排序，全部交付：

1. 删除 MCPToolProvider（纯委派，deletion test 失败）
2. ToolSchema schema 分发从 switch 移到 CampusTask.inputSchema()
3. CacheConfig 并入 CacheStore.Builder，降 cache 包为 3 模块
4. EhallScheduleClient 抽出 CachePipelineBuilder
5. BookingContext 按 pipeline 分组字段（降级路径：注释 + 字段分组）

## 变更文件

### 改动 1 — 删除 MCPToolProvider
- `src/main/java/edu/szu/agent/mcp/MCPToolProvider.java` — **删除**
- `src/main/java/edu/szu/agent/mcp/McpStdioServer.java` — 调用改为 `ToolSchema.toolsList(Skills.getInstance().all())`
- `src/main/java/edu/szu/agent/cli/MCPCommand.java` — 同上
- `src/test/java/edu/szu/agent/mcp/ToolSchemaTest.java` — 更新调用点
- `src/test/java/edu/szu/agent/cli/SkillCommandTest.java` — 更新注释引用

### 改动 2 — ToolSchema 分发移到 CampusTask
- `src/main/java/edu/szu/agent/task/CampusTask.java` — 新增 `inputSchema()` default 方法
- `src/main/java/edu/szu/agent/task/BookingTask.java` — 实现 `inputSchema()`
- `src/main/java/edu/szu/agent/task/ScheduleListTask.java` — 实现
- `src/main/java/edu/szu/agent/task/HomeworkTask.java` — 实现
- `src/main/java/edu/szu/agent/task/CalendarTask.java` — 实现
- `src/main/java/edu/szu/agent/task/NoticeTask.java` — 实现
- `src/main/java/edu/szu/agent/task/KnowledgeTask.java` — 实现
- `src/main/java/edu/szu/agent/task/HomeworkDownloadTask.java` — 实现
- `src/main/java/edu/szu/agent/task/ExamListTask.java` — 实现
- `src/main/java/edu/szu/agent/mcp/ToolSchema.java` — 删除 switch，改为 `skill.task().inputSchema()`

### 改动 3 — CacheConfig 并入 CacheStore.Builder
- `src/main/java/edu/szu/agent/client/cache/CacheConfig.java` — **删除**
- `src/main/java/edu/szu/agent/client/cache/CacheStore.java` — 新增 Builder，内部持有 TTL 表
- `src/main/java/edu/szu/agent/client/cache/CacheEnvelope.java` — 新增 `envelopeType()` 静态工厂（避开匿名类转换）
- `src/main/java/edu/szu/agent/config/ConfigManager.java` — `cacheStore()` 改为返回 Builder 配置好的 CacheStore
- `src/main/java/edu/szu/agent/client/EhallScheduleClient.java` — 删 `cacheConfig` 参数
- `src/main/java/edu/szu/agent/client/step/CacheLookupStep.java` — 构造函数删 TTL 参数
- `src/test/java/edu/szu/agent/client/step/CacheLookupStepTest.java` — 完全重写

### 改动 4 — CachePipelineBuilder 抽取
- `src/main/java/edu/szu/agent/client/step/CachePipelineBuilder.java` — **新增**（约 165 行）
- `src/main/java/edu/szu/agent/client/EhallScheduleClient.java` — `defaultStepsWithCache` 从 50 行缩短为约 25 行
- `src/test/java/edu/szu/agent/client/step/CachePipelineBuilderTest.java` — **新增**（8 个用例）

### 改动 5 — BookingContext 字段分组（降级路径）
- `src/main/java/edu/szu/agent/client/step/BookingContext.java` — 字段按 pipeline 分组，加分组 javadoc，标记为降级路径
- （计划中提到的 30+ 文件泛型拆分降级为"仅注释澄清 + 字段分组"）

### 副产品
- `src/main/resources/schedule-snapshot.html` — **新增**（缺失资源文件，导致 24 个测试启动失败）
- `src/test/java/edu/szu/agent/task/ScheduleListTaskTest.java` — 修正构造器签名（重写遗留的旧 API 测试）

## 阅读的文件

- `.claude/skills/improve-codebase-architecture/LANGUAGE.md`
- `docs/FEATURE_INTAKE.md`（via CLAUDE.md 引用）
- 当前所有 `src/main/java/edu/szu/agent/client/step/*.java`
- `src/test/java/edu/szu/agent/client/step/*Test.java`

## 验证结果

```
mvn test
Tests run: 578, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

mvn test jacoco:report
Line coverage: 76.4% (2722/3563)
Instruction coverage: 76.7% (12675/16520)
```

测试数从 562 → 578（新增 8 个 CachePipelineBuilder 测试 + 8 个相关测试变化）。

> 覆盖率 76.4% < 80% 目标。此为重构前的基线水平（与改动 1-5 无关）。
> 改进覆盖率是后续任务，非本次范围。

## 设计模式

- **Strategy** — CachePipelineBuilder 组装 CacheLookupStep + CacheWriteStep（沿用）
- **Builder** — CacheStore.Builder、CachePipelineBuilder（新增）
- **Adapter** — CacheStore 适配 NIO.2 + POSIX 权限（沿用）
- **Singleton** — ConfigManager（沿用）

## 编程技术

- Builder 模式（CacheStore / CachePipelineBuilder）
- Lambda（populate/extract 函数式注入）
- 不可变 record-like 中间状态
- Jackson JavaType / TypeReference 泛型捕获 + `envelopeType()` 工厂规避匿名类转换
- NIO.2 原子写（temp + rename）+ POSIX 600 权限
- sealed/switch（ToolSchema 删除 switch，改走 CampusTask 多态）

## 决策

1. **改动 5 降级**：计划中泛型拆分需改 30+ 文件、风险高、且与计划里的"降级路径"明确
   一致。本变更按降级路径交付：BookingContext 字段按 pipeline 分组 + javadoc 明确每个
   pipeline 拥有哪些字段。后续可在不破坏现有 API 的前提下渐进拆分。

2. **schedule-snapshot.html 是 MVP 资源**：parser 实际忽略 HTML 内容（硬编码课程
   列表），但客户端启动时仍校验资源存在。补一个最小 HTML 文件即可。

3. **ScheduleListTaskTest 是遗留**：测试构造器签名 `(EhallScheduleClient, Account)`
   对应当前代码 `(ScheduleListClient)`，是 commit 1703c80 提交时未同步更新的中间态。
   修正而非删除。

## 摩擦

- 改动 3 实施时碰到匿名 TypeReference 不能 cast 的限制 → 用 `CacheEnvelope.envelopeType()` 静态工厂
- 改动 5 在 578 个测试依赖 BookingContext 形状的前提下做"激进"拆分风险过高 → 选降级路径

## 下一步

- 改进 JaCoCo 覆盖率（独立任务）
- 改动 5 后续：渐进迁移到 sealed context（每次只动一个 pipeline 的 step）
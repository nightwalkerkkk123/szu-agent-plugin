# 功能规划

每个要实现的功能先在此写 plan，答辩时可作"先规划再实现"的证据。

## 已完成设计文档

| 功能 | 文档位置 |
|---|---|
| 域模型设计 | `docs/PRD.md` §6 数据模型 |
| 错误码设计 | `docs/system-map.md` §4 错误码枚举 |
| 重试策略设计 | `docs/design-patterns.md` §4 策略模式 |
| 浏览器适配器设计 | `docs/system-map.md` §1 模块拓扑 |
| 静态工厂设计 | `docs/design-patterns.md` §1 静态工厂模式 |

## 待实现功能

| 功能 | 说明 | 状态 |
|---|---|---|
| 域模型 records | Campus / Sport / TimeSlot / Venue / BookingRequest | 待实现 |
| ErrorCode 枚举 | 每个枚举值携带 isRetryable / shouldSwitchAccount / shouldScreenshot | 待实现 |
| RetryPolicy 策略 | 接口 + FixedDelayRetry + ExponentialBackoff | 待实现 |
| BrowserLifecycle 适配器 | 接口 + CloakBrowserAdapter + FakeBrowser | 待实现 |
| ClientFactory 静态工厂 | 按 skillName 创建 CampusTask | 待实现 |
| AccountState 状态机 | AVAILABLE → COOLDOWN → LOCKED | 待实现 |
| Matcher 策略族 | Text / Regex / Contains / Composite | 待实现 |
| TaskExecutor | 统一任务执行 + 重试 | 待实现 |
| CLI picocli 入口 | 子命令 + JSON 输出 | 待实现 |
| NoticeQueryClient | 公文通查询 | 待实现 |
| MCPToolProvider | tools/list + tools/call | 待实现 |

## Plan 格式

文件名: `YYYYMMDD-feature-name.md`

每个 plan 包含:概述 / 类变更表 / 设计模式 / 实现步骤 / 验收标准

---

## 实现原则

1. **先写设计文档，再写代码** — 每个功能先在 `docs/plans/` 下创建 plan
2. **显式标注设计模式** — `// Design Pattern: XXX` 在每个模式类第一行
3. **显式标注编程技术** — `// 编程技术: 泛型/枚举/注解/重载/抽象类/Lambda`
4. **测试驱动** — 核心逻辑先写测试，测试通过后再实现
5. **5 秒可验证** — 每个规则都能在 5 秒内判断代码是否符合
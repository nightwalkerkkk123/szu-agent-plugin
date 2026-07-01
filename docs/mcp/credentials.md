# MCP 凭证注入与会话复用参考

> 本文档是外部 Agent / Skill 作者**接入 szu-agent-plugin 真实路径**时必读。
> 配套阅读:[`docs/mcp/error-codes.md`](error-codes.md)、[`MCP.md`](../../MCP.md)。

## 1. 设计原则(ADR-0005 D1)

- **MCP 层不存储密码**:工具的 `inputSchema` 没有 `password` / `cookie` / `token` 字段。
  所有 `Account` 对象在进程内临时组装,落盘**只有 session storageState**。
- **凭证流单向**:`Agent → SZU_SKILL_PATH/skill.yaml → 启动 jar → AccountResolver 解析`。
  LLM 永远**不**应该把密码放进 `tools/call` 的 `arguments`。
- **三层查找 + 早 fail**:`process env → --env-file → Skill 注入`。三层都没找到立即
  抛 `AccountResolutionException`,绝不重试或用默认值绕过。

> ⚠️ **日志与脱敏**:密码、cookie、token、验证码答案、PII **绝不允许**进入日志。
> [`LogMasker`](../../src/main/java/edu/szu/agent/error/LogMasker.java) 集中脱敏;
> 新增日志时若含上述字段,必须用 `LogMasker.mask()` 处理。

## 2. 三层查找

### 2.1 Layer 1: 进程环境变量(最高优先级)

**命名约定**:`SZU_PASSWORD_<11 位学号>`

```bash
# macOS / Linux
export SZU_PASSWORD_2023150090='real-password-here'
export SZU_USERNAME=2023150090            # 可选,部分 task 拿来做 username 默认值

# Windows PowerShell
$env:SZU_PASSWORD_2023150090 = "real-password-here"
$env:SZU_USERNAME = "2023150090"

# Windows CMD
set SZU_PASSWORD_2023150090=real-password-here
set SZU_USERNAME=2023150090
```

**查找逻辑**: [`AccountResolver.resolve(String)`](../../src/main/java/edu/szu/agent/account/AccountResolver.java#L62)
读 `System.getenv()`,命中即返回。

**适合场景**:
- Skill 包装脚本启动 JVM 前 `export` / `set` 注入
- 容器化部署(Docker `--env-file` / K8s `Secret`)
- 开发环境(直接 shell 启动 jar)

### 2.2 Layer 2: `--env-file`(dotenv)

**文件格式**:标准 dotenv,`KEY=VALUE`,每行一条。

```dotenv
# .env(在仓库根)
SZU_PASSWORD_2023150090=real-password-here
SZU_USERNAME=2023150090
```

**查找逻辑**:`AccountResolver.resolve(String, Map, Path)` 读 dotenv 库(io.github.cdimascio:dotenv)
解析指定文件,命中即返回。**注意:daemon 模式下不读这个文件**(见 §5.2)。

**适合场景**:
- 本地 CLI 调试(`./szu-agent-plugin booking venue --env-file .env …`)
- 不想把密码写进 shell history

**安全约束**:
- `.env` 必须 `.gitignore` 屏蔽(本仓库已配置)。
- 文件权限建议 `chmod 600`,dotenv 库**不会**自动校验权限。

### 2.3 Layer 3: Skill 注入(预留,P1 尚未实现)

保留位。当前版本此层直接 throw,等同不存在。

## 3. 30 天会话复用(ADR-0008)

凭证解析只在**首次登录**需要;登录成功后,Playwright `storageState`(cookie + localStorage)
会被持久化到本地,后续 30 天内调用复用同一会话,**不再触发 CAS 登录**。

### 3.1 文件位置

```
~/.szu-agent/sessions/<username>.json
```

`<username>` 是 `^[A-Za-z0-9_.-]+$` 校验过的学号,**避免路径遍历**。

```bash
ls -la ~/.szu-agent/sessions/
# 2023150090.json
# 2030200100.json
```

文件权限:写入时显式 `PosixFilePermission.OWNER_READ | OWNER_WRITE`(即 `chmod 600`)。

### 3.2 生命周期

| 阶段 | 组件 | 行为 |
|---|---|---|
| 启动 | `RestoreSessionStep` | 读 `~/.szu-agent/sessions/<id>.json`,检查 mtime < 30 天 |
| 启动 | `SessionProbe` | 用 Playwright 导航到 ehall 登录态探针 URL,断言"已登录"选择器存在 |
| 探针通过 | — | 注入 storageState,跳过 CAS,直接走业务流 |
| 探针失败 / mtime 超 30 天 | `SessionStore.deleteIfExists` | 删除旧 session;**走 headed 登录**(见 §3.4) |
| 业务流完成 | `PersistSessionStep` | 重新保存 storageState 到磁盘 |

### 3.3 触发刷新的信号

- `SESSION_READ_FAILED`:文件存在但 JSON 解析失败(损坏)→ 自动删 + 走 headed
- `SESSION_EXPIRED` / `CHAOXING_AUTH_EXPIRED`:探针未通过(CAS 主动登出)→ 走 headed
- 30 天 TTL 到期:`SessionStore.isFresh(Duration.ofDays(30))` 返回 false → 走 headed

### 3.4 Headed 登录流程(用户介入)

```
┌──────────────────────────────────────────────┐
│ 1. Skill 提示用户:需要 headed 登录           │
│ 2. Skill 启动 java ... booking venue \       │
│       --headed-login --username <id>         │
│ 3. Playwright 弹出有头浏览器,用户在浏览器中  │
│    手动完成 CAS 登录 + 图形验证码(如有)      │
│ 4. CAS 跳回 ehall,代码探针通过              │
│ 5. PersistSessionStep 写入 session.json     │
│ 6. Skill 继续执行原 tools/call 请求         │
└──────────────────────────────────────────────┘
```

> **不要绕过 headed 登录**。`PASSWORD_INCORRECT` / `CAPTCHA_REQUIRED` 等错误码
> 出现时,`switchAccount` 标志为 true 才会切备用账号;**不是**自动重试密码。

## 4. SZU_USERNAME 的作用

`SZU_USERNAME` **不是**凭证,是 `username` 参数的**可选默认值**。当 LLM 调用
`homework_list` / `homework_download` / `booking_venue` 时若没传 `username`,task
会回退到 `SZU_USERNAME`。两者都没有时抛 `INVALID_REQUEST("Missing required parameter: username")`。

| 任务 | `username` 默认 |
|---|---|
| `booking_venue` | `input.username` → `SZU_USERNAME` → 抛错 |
| `homework_list` | 同上 |
| `homework_download` | 同上 |
| `schedule_list` | **无默认**,缺即抛错 |
| `notice_list` / `exam_list` | **无默认**(参数 `required`) |

> 注意:`notice_list` 的 `username` 虽然 `required`,但当前静态实现不登录,
> 仍要求传学号是为了**对齐未来真实路径**,避免外部 Agent 形成"不传也行"的错误习惯。

## 5. Daemon 模式 vs CLI 模式

### 5.1 CLI(一次性进程)

```bash
java -jar target/szu-agent-plugin.jar booking venue \
  --username 2023150090 \
  --campus YUEHAI --sport TENNIS \
  --date 2026-06-24 --time-slot 19:00-20:00 \
  --env-file .env          # Layer 2 显式传入
```

- `System.getenv()` 拿到的是 shell 当前环境(Layer 1)
- `--env-file` 显式传给 CLI(经 ConfigManager 加载)

### 5.2 Daemon(常驻 HTTP / stdio)

```bash
scripts/serve.sh --background
# 内部:
#   source .env     ← Layer 1(env vars)
#   exec java -jar target/szu-agent-plugin.jar mcp serve --http --port 8765
```

`scripts/serve.sh` 在 exec Java 前 `set -a; source .env; set +a`,
所以 **Layer 1 实际加载了 .env 的内容**。JVM 看到的 `System.getenv()` 已经包含密码。

> ⚠️ **Windows daemon** (`scripts/serve.bat`) **不会**加载 `.env`。
> Windows 管理员需要在启动 `serve.bat` **之前**用 `setx SZU_PASSWORD_<id> "..." /M`
> 持久化密码到用户/系统环境变量,或者改用 Windows 凭据管理器。

### 5.3 对比表

| 模式 | Layer 1 (env) | Layer 2 (env-file) | Layer 3 (Skill) |
|---|:-:|:-:|:-:|
| CLI `--env-file` | ✓ 来自 shell | ✓ 显式传入 | – |
| CLI 无 `--env-file` | ✓ 来自 shell | – | – |
| Daemon `serve.sh`(macOS/Linux) | ✓ 来自 .env 加载 | – | – |
| Daemon `serve.bat`(Windows) | 仅当用户预先 setx | – | – |
| Claude Desktop 集成 | ✓ 来自 MCP config env | – | – |

## 6. Skill 作者接入 checklist

写一个新 Skill wrapper 调用 `booking_venue` / `homework_*` / `schedule_list` 时,按下面顺序检查:

- [ ] **不要**在 skill manifest 里加 `password` / `cookie` 字段。
- [ ] 在启动 jar 前用 `os.environ` / `setx` / container env 注入 `SZU_PASSWORD_<id>`。
- [ ] 提供一个 "headed 登录" 命令入口,用户首次跑时完成人工登录;之后复用 `~/.szu-agent/sessions/<id>.json`。
- [ ] 调用 `/call` 时不要主动传 `username`(让它走 SZU_USERNAME 默认),除非用户明确指定。
- [ ] 收到 `ACCOUNT_RESOLUTION_FAILED` → 提示用户 headed 登录;**不要**自动重试。
- [ ] 收到 `CHAOXING_ANTI_BOT` → 等 30+ 分钟,考虑切备用账号(switchAccount=true);**不要**高频重试。
- [ ] 在用户文档里**显式**说明本 Skill 不存密码、会话仅本机保留 30 天。

## 7. 安全与合规约束(回归检查)

| 约束 | 检查方式 |
|---|---|
| 不在 MCP 层存储密码 | `git grep -i 'password' src/main/java/edu/szu/agent/mcp/` 应无业务落盘代码 |
| 不打密码 / cookie / token 日志 | `LogMasker` 强制;新日志若含敏感字段必须 `mask()` |
| 不绕过验证码 | 触发 `CAPTCHA_REQUIRED` 必须 headed,**不**自动绕过 |
| 不高频重试 | `RetryPolicies.defaultBooking()` 默认上限;新增 task 沿用 |
| 不发送敏感邮件/消息 | 仓库无邮件客户端;`scripts/serve.sh` 不引入发邮件逻辑 |
| 不硬编码密钥 | `git grep -E 'sk-|api[_-]?key' src/` 应无命中 |

## 8. 故障排查速查

| 现象 | 原因 | 处理 |
|---|---|---|
| `ACCOUNT_RESOLUTION_FAILED` | 三层都查不到 `SZU_PASSWORD_<id>` | 检查 `env | grep SZU_PASSWORD_`;确认 `.env` 已被 `serve.sh` source;Windows 检查 `setx` 是否生效 |
| 重复 headed 登录 | session.json 没写成功 | 检查 `~/.szu-agent/sessions/` 目录权限;看 `errorMessage` 是否有 `SESSION_WRITE_FAILED` |
| 切账号无效 | `switchAccount=false` 的错误码 | 只有 `PASSWORD_INCORRECT` / `ACCOUNT_LOCKED` / `CHAOXING_ANTI_BOT` 会切;其他码切不动 |
| 启程 daemon 后改 .env 不生效 | daemon 启动时已 source,后续修改不会重读 | `kill` daemon + 重新 `serve.sh --background` |
| `dotenv` 库报"file not found" | `--env-file` 路径相对当前工作目录 | 传绝对路径,或 cd 到 .env 所在目录 |

## 9. 相关文档

- [`docs/mcp/error-codes.md`](error-codes.md) — 错误码触发 + 修复表
- [`docs/mcp/workflows.md`](workflows.md) — 跨工具 cookbook
- [`docs/adr/0005-account-resolution.md`](../adr/0005-account-resolution.md) — ADR-0005 凭证架构决策
- [`docs/adr/0008-session-reuse.md`](../adr/0008-session-reuse.md) — ADR-0008 30 天会话复用

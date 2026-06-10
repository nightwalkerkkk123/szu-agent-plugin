# Security Policy — SZU Agent Plugin

> 本文件描述项目的安全策略、敏感信息处理、错误码设计意图与合规声明。

---

## 1. 敏感信息处理

### 1.1 禁止写入日志的内容

以下信息**绝对禁止**写入日志或调试输出：
- 密码（Password）
- Cookie / Session Token
- 学号对应的登录凭证
- 验证码内容
- 个人身份信息

### 1.2 脱敏机制

`LogMasker` 工具类集中处理所有日志输出（详见 `error/LogMasker.java`，12 个 Pattern：9 个字段名 + 2 个值 + 1 个 SZU_PASSWORD 变量名）：

```java
// 编程技术: 枚举 / Lambda / 不可变 Pattern 集合
// 强制约束:见 ADR-0005 D2,archunit 静态规则禁止业务代码绕过 LogMasker
public final class LogMasker {

    // 字段名正则(词边界,避免误伤 "pwdfile" 等)
    private static final List<Pattern> SENSITIVE_KEYS = List.of(
        Pattern.compile("(?i)password"),
        Pattern.compile("(?i)\\bpwd\\b"),
        Pattern.compile("(?i)secret"),
        Pattern.compile("(?i)token"),
        Pattern.compile("(?i)cookie"),
        Pattern.compile("(?i)session"),
        Pattern.compile("(?i)authorization"),
        Pattern.compile("(?i)bearer"),
        Pattern.compile("(?i)szu_password_\\d+")
    );

    // 裸值正则(11 位学号 / 11 位手机号)
    private static final List<Pattern> SENSITIVE_VALUES = List.of(
        Pattern.compile("\\b20\\d{9}\\b"),
        Pattern.compile("\\b1[3-9]\\d{9}\\b")
    );

    public static String scrub(String input) { /* replace 全部命中 → *** */ }

    // 给 SLF4J 用的便捷入口,先 formatted 再 scrub
    public static String fmt(String pattern, Object... args) { /* ... */ }
}
```

### 1.3 环境变量注入

敏感信息通过环境变量注入，不硬编码：

```bash
# 凭证(进程 env 由 Skill wrapper 注入,见 ADR-0005 D1)
SZU_PASSWORD_XXXX    # XXXX = 学号后4位(例如 2023150090 → SZU_PASSWORD_0090)
SZU_USERNAME_XXXX    # 同上

# 非敏感配置(放 application.yml 或环境变量均可)
BROWSER_HEADLESS     # 是否无头模式
TRACE_LEVEL          # 日志级别
```

---

## 2. 错误码设计意图

### 2.1 错误码分类

| 码 | 含义 | 是否可重试 | 是否切账号 | 是否截图 |
|---|---|---|---|---|
| 0 | 成功 | - | - | - |
| 1 | 业务失败（如无可用时段） | ✅ | ❌ | ❌ |
| 2 | 参数错误 | ❌ | ❌ | ❌ |
| 3 | 环境错误（配置缺失） | ❌ | ❌ | ❌ |
| 4 | 浏览器错误（启动失败） | ✅ | ❌ | ✅ |

### 2.2 错误码枚举设计

12 个常量(精简自 Python 参考 17 个,合并 retry 策略相同项;详见 `error/ErrorCode.java`):

```java
// 编程技术: 枚举(每个枚举值携带元数据;元数据即分类依据,
//           无需外部分类器,见 ADR-0001 D9 + ADR-0006 error 子决定)
public enum ErrorCode {
    // 登录阶段
    LOGIN_PAGE_LOAD_FAILED (Severity.HIGH,     true,  false, true,  "登录页加载失败"),
    CAS_REDIRECT_TIMEOUT   (Severity.HIGH,     true,  false, true,  "CAS 重定向超时"),
    PASSWORD_INCORRECT     (Severity.CRITICAL, false, true,  true,  "密码错误"),
    ACCOUNT_LOCKED         (Severity.CRITICAL, false, true,  true,  "账号被锁"),
    CAPTCHA_REQUIRED       (Severity.HIGH,     true,  false, true,  "触发图形验证码"),
    // 选场地阶段
    VENUE_OCCUPIED         (Severity.MEDIUM,   true,  false, false, "目标场地已被预约"),
    NO_AVAILABLE_VENUE     (Severity.MEDIUM,   true,  false, false, "该时段无任何可用场地"),
    ELEMENT_NOT_FOUND      (Severity.MEDIUM,   true,  false, true,  "未找到目标元素"),
    // 网络 / 浏览器
    NETWORK_TIMEOUT        (Severity.MEDIUM,   true,  false, false, "网络超时"),
    BROWSER_CRASH          (Severity.HIGH,     true,  false, true,  "浏览器进程崩溃"),
    // 业务编排
    INVALID_REQUEST        (Severity.LOW,      false, false, false, "请求参数不合法"),
    UNKNOWN                (Severity.HIGH,     true,  false, true,  "未知异常");

    ErrorCode(Severity severity, boolean retryable, boolean switchAccount,
              boolean screenshot, String hint) { /* 字段赋值 */ }

    public Severity severity()            { return severity; }
    public boolean  isRetryable()         { return retryable; }
    public boolean  shouldSwitchAccount() { return switchAccount; }
    public boolean  shouldScreenshot()    { return screenshot; }
    public String   hint()                { return hint; }
}
```

---

## 3. 合规声明

### 3.1 项目边界

本项目**明确不包含**以下功能：

- ❌ 验证码绕过或识别
- ❌ 高频异常访问（正常用户行为频率）
- ❌ 发送敏感邮件或消息（只生成草稿，由用户确认）
- ❌ 攻击性利用或安全测试

### 3.2 干跑模式（dry-run,仅作测试夹具）

按 **ADR-0001 D4**:`--dry-run` 模式使用 `FakeBrowser`,**仅作单元测试夹具**,不出现在课堂演示。
课堂演示默认走 `PlaywrightBrowserAdapter` 真跑(ADR-0001 D2)。
CLI 不再暴露 `--dry-run` 为常规参数;仅在测试代码中通过 `BrowserFactory.create(Kind.FAKE)` 注入。

### 3.3 archunit 静态规则强制(ADR-0005 D2)

`mvn test` 阶段跑 archunit 规则,CI 必过:

| 规则 | 命中即失败 |
|---|---|
| `LogMaskerRuleTest` | 任何业务代码 `log.info/debug/warn/error` 的字符串字面量含 `password` `pwd` `secret` `token` `cookie` `session` `authorization` `bearer` (词边界) |
| `LogMaskerRuleTest` | 任何代码直接 `System.getenv("SZU_PASSWORD_*")`(必须走 `AccountResolver`) |
| `SystemOutRuleTest` | `com.szu` 包下出现 `System.out.println` / `System.err.println` / `printStackTrace`(仅 `Main.main` 豁免) |

补救措施:**所有** `log.info(...)` 入参必须先经 `LogMasker.scrub(msg)` 或 `LogMasker.fmt("...", args...)`,**约定**而非强制,见 README "开发约定"小节。

### 3.4 用户授权

浏览器操作在用户授权下进行，本项目不执行用户未知情的操作。

---

## 4. 安全审查触发条件

**使用 security-reviewer agent 当：**

- 认证或授权代码变更
- 用户输入处理（学号、密码、时间段等）
- Cookie / Token 处理
- 日志输出逻辑
- 外部 API 调用

---

## 5. 报告安全问题

如发现安全漏洞，请联系项目作者（学号 2023150090）。

**不要**在公开场合（如 GitHub Issue）报告安全问题。
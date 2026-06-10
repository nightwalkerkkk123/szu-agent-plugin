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

`LogMasker` 工具类集中处理所有日志输出：

```java
// 编程技术: 注解 / Lambda
public final class LogMasker {
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
        "(password|cookie|token|secret|key)\\s*[:=]\\s*[^,;\\s]+",
        Pattern.CASE_INSENSITIVE
    );

    public static String mask(String message) {
        if (message == null) return null;
        return SENSITIVE_PATTERN.matcher(message).replaceAll("$1=***REDACTED***");
    }
}
```

### 1.3 环境变量注入

敏感信息通过环境变量注入，不硬编码：

```bash
# 环境变量命名规范
SZU_PASSWORD_XXXX    # XXXX = 学号后4位
SZU_USERNAME_XXXX    # 同上
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

```java
// 编程技术: 枚举(每个枚举值携带元数据)
// Design Pattern: Strategy (ErrorClassifier 根据 ErrorCode 选择处理策略)
public enum ErrorCode {
    LOGIN_FAILED(true, false, true),
    PASSWORD_INCORRECT(false, true, true),
    ACCOUNT_LOCKED(false, true, true),
    CAPTCHA_REQUIRED(false, true, false),
    PAGE_LOAD_TIMEOUT(true, false, true),
    ELEMENT_NOT_FOUND(true, false, true),
    NO_AVAILABLE_SLOT(true, false, false),
    SUBMIT_FAILED(true, false, true),
    NETWORK_ERROR(true, false, false),
    BROWSER_CRASHED(true, false, true),
    UNKNOWN_ERROR(false, false, true);

    private final boolean retryable;
    private final boolean switchAccount;
    private final boolean screenshot;

    ErrorCode(boolean retryable, boolean switchAccount, boolean screenshot) {
        this.retryable = retryable;
        this.switchAccount = switchAccount;
        this.screenshot = screenshot;
    }

    public boolean isRetryable() { return retryable; }
    public boolean shouldSwitchAccount() { return switchAccount; }
    public boolean shouldScreenshot() { return screenshot; }
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

### 3.2 干跑模式（dry-run）

默认 `--dry-run` 模式使用 `FakeBrowser`，不访问真实系统。
真实模式仅作技术验证，不在作业演示中触发。

### 3.3 用户授权

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
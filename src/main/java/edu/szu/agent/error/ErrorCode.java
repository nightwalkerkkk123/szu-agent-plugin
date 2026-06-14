package edu.szu.agent.error;

/**
 * Error code enum — 12 values with 5 metadata fields.
 *
 * <p>Per ADR-0006 §二.2: replaces Python's {@code ERROR_MAP} by hanging
 * metadata directly on the enum. Each constant carries:
 * <ul>
 *   <li>{@code severity} — for tracing colors and log filtering</li>
 *   <li>{@code retryable} — whether {@code retry/} should re-attempt</li>
 *   <li>{@code switchAccount} — whether to fall back to a different account</li>
 *   <li>{@code screenshot} — whether to capture a Playwright screenshot</li>
 *   <li>{@code hint} — human-readable next-step advice for the Agent</li>
 * </ul>
 *
 * <p>Per ADR-0006 §二.5: {@code retry/} imports only this enum and uses
 * {@link #isRetryable()} — never references specific constants. This keeps
 * {@code retry} independent of the error taxonomy.
 *
 * // 编程技术: 枚举(每个常量挂 5 字段元数据;替代 Python ERROR_MAP 间接层)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public enum ErrorCode {

    // ----- 登录阶段 -----
    /** 登录页加载失败. */
    LOGIN_PAGE_LOAD_FAILED(Severity.HIGH,     true,  false, true,  "登录页加载失败"),
    /** CAS 重定向超时. */
    CAS_REDIRECT_TIMEOUT  (Severity.HIGH,     true,  false, true,  "CAS 重定向超时"),
    /** 密码错误. */
    PASSWORD_INCORRECT    (Severity.CRITICAL, false, true,  true,  "密码错误"),
    /** 账号被锁. */
    ACCOUNT_LOCKED        (Severity.CRITICAL, false, true,  true,  "账号被锁"),
    /** 触发图形验证码. */
    CAPTCHA_REQUIRED      (Severity.HIGH,     true,  false, true,  "触发图形验证码"),

    // ----- 选场地阶段 -----
    /** 目标场地已被预约. */
    VENUE_OCCUPIED        (Severity.MEDIUM,   true,  false, false, "目标场地已被预约"),
    /** 该时段无任何可用场地. */
    NO_AVAILABLE_VENUE    (Severity.MEDIUM,   true,  false, false, "该时段无任何可用场地"),
    /** 未找到目标元素. */
    ELEMENT_NOT_FOUND     (Severity.MEDIUM,   true,  false, true,  "未找到目标元素"),

    // ----- 网络 / 浏览器 -----
    /** 网络超时. */
    NETWORK_TIMEOUT       (Severity.MEDIUM,   true,  false, false, "网络超时"),
    /** 浏览器进程崩溃. */
    BROWSER_CRASH         (Severity.HIGH,     true,  false, true,  "浏览器进程崩溃"),

    // ----- 作业查询 -----
    /** 作业列表页加载失败. */
    HOMEWORK_PAGE_LOAD_FAILED(Severity.HIGH,  true,  false, true,  "作业列表页加载失败"),
    /** 作业列表为空. */
    HOMEWORK_LIST_EMPTY   (Severity.LOW,      false, false, false, "作业列表为空"),

    // ----- 业务编排 -----
    /** 请求参数不合法. */
    INVALID_REQUEST       (Severity.LOW,      false, false, false, "请求参数不合法"),
    /** 未知异常. */
    UNKNOWN               (Severity.HIGH,     true,  false, true,  "未知异常"),

    // ----- 登录态持久化(US-007) -----
    /** 无持久化登录态. */
    SESSION_NOT_FOUND     (Severity.LOW,      false, false, false, "无持久化登录态"),
    /** 持久化登录态损坏. */
    SESSION_READ_FAILED   (Severity.MEDIUM,   false, false, false, "持久化登录态损坏"),
    /** 持久化登录态写入失败. */
    SESSION_WRITE_FAILED  (Severity.LOW,      false, false, false, "持久化登录态写入失败");

    private final Severity severity;
    private final boolean retryable;
    private final boolean switchAccount;
    private final boolean screenshot;
    private final String hint;

    ErrorCode(Severity severity,
              boolean retryable,
              boolean switchAccount,
              boolean screenshot,
              String hint) {
        this.severity = severity;
        this.retryable = retryable;
        this.switchAccount = switchAccount;
        this.screenshot = screenshot;
        this.hint = hint;
    }

    /** Severity tier — drives log color and trace annotation. */
    public Severity severity() {
        return severity;
    }

    /** Whether the {@code retry/} layer should re-attempt. */
    public boolean isRetryable() {
        return retryable;
    }

    /** Whether the {@code account/} layer should fall back to a different account. */
    public boolean shouldSwitchAccount() {
        return switchAccount;
    }

    /** Whether the {@code browser/} layer should capture a Playwright screenshot. */
    public boolean shouldScreenshot() {
        return screenshot;
    }

    /** Human-readable next-step advice for the Agent. */
    public String hint() {
        return hint;
    }
}

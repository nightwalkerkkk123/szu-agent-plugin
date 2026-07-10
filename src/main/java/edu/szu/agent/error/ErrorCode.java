package edu.szu.agent.error;

/**
 * Error code enum — 36 values with 5 metadata fields.
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
 * @since 0.6.0
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
    SESSION_WRITE_FAILED  (Severity.LOW,      false, false, false, "持久化登录态写入失败"),

    // ----- 作业附件下载(US-008) -----
    /** 作业详情页无附件. */
    ATTACHMENT_NOT_FOUND    (Severity.LOW,    false, false, false, "作业无附件"),
    /** 附件下载失败(HTTP / 写文件). */
    ATTACHMENT_DOWNLOAD_FAILED(Severity.MEDIUM, true, false, true,  "附件下载失败"),
    /** 输出目录非法(不存在 / 不可写 / 不是目录). */
    OUTPUT_DIR_INVALID    (Severity.MEDIUM,   false, false, false, "输出目录非法"),

    // ----- 课表查询(US-009) -----
    /** 课表页加载失败. */
    SCHEDULE_PAGE_LOAD_FAILED(Severity.HIGH,   true,  false, true,  "课表页加载失败"),
    /** 课表解析失败(选择器失效 / 周次非法). */
    SCHEDULE_PARSE_FAILED    (Severity.MEDIUM, true,  false, true,  "课表解析失败"),
    /** 课表为空(可能学期未开始). */
    SCHEDULE_EMPTY           (Severity.LOW,    false, false, false, "课表为空(可能学期未开始)"),
    /** 当前学期课表未发布或不存在. */
    SCHEDULE_NOT_FOUND       (Severity.MEDIUM, false, false, false, "当前学期课表未发布或为空"),
    /** 课表缓存跨学期已失效,已重新抓取. */
    SCHEDULE_CACHE_STALE     (Severity.LOW,    false, false, false, "课表缓存跨学期,已失效并重新抓取"),

    // ----- 畅课/学习通作业(chaoxing_tasks) -----
    /** 畅课 SSO Cookie 过期,需重新登录. */
    CHAOXING_AUTH_EXPIRED    (Severity.HIGH,   true,  false, true,  "畅课 SSO Cookie 过期,需重新登录"),
    /** 课程不存在或已归档. */
    CHAOXING_COURSE_NOT_FOUND(Severity.MEDIUM, true,  false, true,  "课程不存在或已归档"),
    /** 触发畅课反爬,请稍后重试或切换账号. */
    CHAOXING_ANTI_BOT        (Severity.HIGH,   true,  true,  true,  "触发畅课反爬,请稍后重试或切换账号"),

    // ----- 考试安排(exam_list) -----
    /** 考试安排未发布或已结束. */
    EXAM_NOT_FOUND           (Severity.MEDIUM, true,  false, true,  "考试安排未发布或已结束"),
    /** 考试时间地点冲突,请联系教务. */
    EXAM_LOCATION_CONFLICT   (Severity.HIGH,   false, false, false, "考试时间地点冲突,请联系教务"),
    /** 考试安排真实抓取失败(网络/超时/selector 错),已回退到静态快照. */
    EXAM_FETCH_FAILED        (Severity.HIGH,   true,  false, true,  "考试安排真实抓取失败,已回退到静态快照"),
    /** 考试安排真实抓取超时. */
    EXAM_TIMEOUT             (Severity.MEDIUM, true,  false, false, "考试安排真实抓取超时,已回退到静态快照"),

    // ----- 知识库(kb_query) -----
    /** 知识库索引已过期,基于旧版本回答. */
    KNOWLEDGE_STALE          (Severity.LOW,    false, false, false, "知识库索引已过期,基于旧版本回答"),
    /** 知识库未收录该问题. */
    KNOWLEDGE_NOT_FOUND      (Severity.MEDIUM, false, false, false, "知识库未收录该问题"),

    // ----- 校历查询(US-010) -----
    /** 校历 HTML 表格解析失败(降级告警). */
    CALENDAR_PARSE_FAILED    (Severity.LOW,    false, false, false, "校历解析失败，返回已解析部分"),
    /** 校历真实抓取失败(网络/超时/selector 错),已回退到静态快照. */
    CALENDAR_FETCH_FAILED    (Severity.HIGH,   true,  false, true,  "校历真实抓取失败,已回退到静态快照"),
    /** 校历真实抓取超时. */
    CALENDAR_TIMEOUT         (Severity.MEDIUM, true,  false, false, "校历真实抓取超时,已回退到静态快照"),

    // ----- 公文通查询(US-011) -----
    /** 公文通列表为空. */
    NOTICE_LIST_EMPTY        (Severity.LOW,    false, false, false, "公文通列表为空"),
    /** 公文通分类无效. */
    NOTICE_CATEGORY_INVALID  (Severity.LOW,    false, false, false, "公文通分类无效"),
    /** 公文通真实抓取失败(无 HAR / selector 错 / 网络). */
    NOTICE_FETCH_FAILED      (Severity.HIGH,   true,  false, true,  "公文通真实抓取失败,已回退到静态快照"),
    /** 公文通真实抓取超时. */
    NOTICE_TIMEOUT           (Severity.MEDIUM, true,  false, false, "公文通真实抓取超时,已回退到静态快照"),

    // ----- 外部独立 Skill -----
    /** 外部 Skill 入口脚本缺失. */
    EXTERNAL_SKILL_NOT_FOUND  (Severity.LOW,    false, false, false, "外部 Skill 入口脚本缺失"),
    /** 外部 Skill 执行超时. */
    EXTERNAL_SKILL_TIMEOUT    (Severity.LOW,    false, false, false, "外部 Skill 执行超时"),
    /** 外部 Skill 输出非法 JSON. */
    EXTERNAL_SKILL_JSON_ERROR (Severity.LOW,    false, false, false, "外部 Skill 输出非法 JSON"),

    // ----- 支付服务(payment) -----
    /** 订单不存在或无权访问. */
    PAYMENT_ORDER_NOT_FOUND   (Severity.MEDIUM,   false, false, false, "订单不存在或无权访问"),
    /** 该订单已支付. */
    PAYMENT_ALREADY_PAID      (Severity.LOW,      false, false, false, "该订单已支付"),
    /** 所选支付方式不可用. */
    PAYMENT_METHOD_UNAVAILABLE(Severity.MEDIUM,   true,  false, false, "所选支付方式不可用，请尝试其他方式"),
    /** 校园卡自动支付需要配置密码环境变量. */
    PAYMENT_PASSWORD_REQUIRED (Severity.MEDIUM,   false, false, false, "校园卡自动支付需要配置 SZU_CAMPUS_CARD_PASSWORD"),
    /** 校园卡支付密码错误. */
    PAYMENT_PASSWORD_INCORRECT(Severity.HIGH,     false, true,  true,  "校园卡支付密码错误"),
    /** olepay 网关返回异常. */
    PAYMENT_GATEWAY_ERROR     (Severity.HIGH,     true,  false, true,  "olepay 网关返回异常"),
    /** 支付状态轮询超时. */
    PAYMENT_STATUS_TIMEOUT    (Severity.MEDIUM,   false, false, false, "支付状态轮询超时，请使用 direct-pay-status 继续查询"),
    /** 该支付方式需用户在手机上确认. */
    PAYMENT_MANUAL_REQUIRED   (Severity.LOW,      false, false, false, "该支付方式需用户在手机上确认，请使用返回的二维码或链接");

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

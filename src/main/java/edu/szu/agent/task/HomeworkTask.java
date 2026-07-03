package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.ChaoxingHomeworkClient;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.HomeworkListResult;
import edu.szu.agent.retry.RetryPolicies;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@code homework_list} CampusTask — queries the LMS todo list for homework.
 *
 * <p>Parameter contract (string keys, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code username} (optional) — student ID; defaults to SZU_USERNAME env var</li>
 * </ul>
 *
 * <p>Unlike the early placeholder implementation, this task resolves the
 * real account from the supplied username on every execute, so the MCP /
 * Skill path behaves the same as the CLI path.
 *
 * // 编程技术: 泛型 / Lambda / 依赖注入
 *
 * @since 0.6.0
 * @author 王子豪
 */
public class HomeworkTask implements CampusTask<HomeworkListResult> {

    private final Function<String, Account> accountResolver;
    private final Supplier<BrowserLifecycle> browserFactory;
    private final ChaoxingHomeworkClient injectedClient;

    /**
     * Production constructor — resolves credentials from the supplied username
     * and creates a fresh browser per call.
     */
    public HomeworkTask() {
        this(AccountResolver::resolve, () -> ConfigManager.getInstance().browser(), null);
    }

    /**
     * Backward-compatible test constructor — delegates directly to the injected
     * client, ignoring username resolution. Used by existing unit tests.
     */
    public HomeworkTask(ChaoxingHomeworkClient client, Account account) {
        this(u -> account, () -> null, client);
    }

    /**
     * Test constructor — inject custom resolver and browser factory.
     */
    HomeworkTask(Function<String, Account> accountResolver,
                 Supplier<BrowserLifecycle> browserFactory,
                 ChaoxingHomeworkClient injectedClient) {
        this.accountResolver = Objects.requireNonNull(accountResolver, "accountResolver");
        this.browserFactory = Objects.requireNonNull(browserFactory, "browserFactory");
        this.injectedClient = injectedClient;
    }

    @Override
    public String name() {
        return "homework_list";
    }

    @Override
    public String description() {
        return """
            查询深圳大学畅课(LMS/超星)待办作业列表,返回课程名、作业标题、截止时间和提交状态。
            重要约束(必须遵守,否则调用会失败或触发真实账号流程):
            1. username 可选;若不传,默认读取环境变量 SZU_USERNAME。若两者都没有,抛
               IllegalArgumentException("Missing required parameter: username")。
            2. 这是需要账号态的真实路径:会通过 AccountResolver 解析 SZU_PASSWORD_<学号> 或 --env-file 注入凭证,
               然后启动浏览器访问畅课。不要在 MCP 层存储密码,不要把密码放进 arguments。
            3. 当前工具只返回作业列表,不下载附件。下载附件必须先从结果中取 homeworkId,再调用 homework_download。
            4. 返回结果是 sealed HomeworkListResult: Success(homeworks) 或 Failure(code,message)。外部 Agent 应先判断
               success/failure 类型,不要假设一定有 homeworks。
            5. 每条 Homework 含 homeworkId、courseName、title、deadline、status。deadline 是畅课页面原始字符串,
               通常形如 "2026.06.24 23:59",不是 ISO 日期。
            6. 适合回答"我有哪些作业?"、"哪门课作业还没交?"、"作业什么时候截止?"等问题。
            7. 若返回账号解析失败/会话过期,需要用户先配置 env 或完成一次 headed 登录;不要重试高频访问。
            """;
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> username = TaskInputSchema.property("string",
            "深大学号,11 位数字,例如 2023150090。可选;不传则读取 SZU_USERNAME。",
            Map.of("pattern", "^20\\d{9}$", "examples", List.of("2023150090")));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("username", username);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of());
        return schema;
    }

    @Override
    public ToolAnnotations annotations() {
        Map<String, Object> ex1 = new LinkedHashMap<>();
        ex1.put("username", "2023150090");

        Map<String, Object> ex2 = new LinkedHashMap<>();
        // 演示:不传 username,依赖 SZU_USERNAME 环境变量

        Map<String, Object> ex3 = new LinkedHashMap<>();
        ex3.put("username", "2030200100");

        return ToolAnnotations.builder()
            .example(ex1)
            .example(ex2)
            .example(ex3)
            .resultShape("""
                HomeworkListResult (sealed):
                - Success { homeworks: List<Homework> }
                - Failure { code: ErrorCode, message: String }
                Homework 字段:
                - homeworkId: 作业 id,供 homework_download 使用
                - courseName: 课程名
                - title: 作业标题
                - deadline: 原始截止时间文本,例如 "2026.06.24 23:59"
                - status: 状态文本,例如 待提交"""
            )
            .commonError("未传 username 且无 SZU_USERNAME → INVALID_REQUEST(\"Missing required parameter: username\")")
            .commonError("未注入 SZU_PASSWORD_<学号> → ACCOUNT_RESOLUTION_FAILED;需通过 env 或 --env-file 提供")
            .commonError("想下载附件却调用 homework_list → 先取 homeworkId,再调用 homework_download")
            .build();
    }

    @Override
    public HomeworkListResult execute(TaskInput input) {
        if (injectedClient != null) {
            return injectedClient.list();
        }
        String username = resolveUsername(input);
        Account account = accountResolver.apply(username);
        ChaoxingHomeworkClient client = new ChaoxingHomeworkClient(
            account, browserFactory.get(), RetryPolicies.defaultBooking());
        return client.list();
    }

    private String resolveUsername(TaskInput input) {
        String fromInput = input.get("username");
        if (fromInput != null && !fromInput.isBlank()) {
            return fromInput;
        }
        String fromEnv = ConfigManager.getInstance().get("SZU_USERNAME");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        throw new IllegalArgumentException("Missing required parameter: username");
    }
}

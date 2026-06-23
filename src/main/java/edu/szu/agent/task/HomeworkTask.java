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
 * @since 0.1.0
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
        return "查询畅课作业列表";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> username = new LinkedHashMap<>();
        username.put("type", "string");
        username.put("description", "学号;若未提供,默认使用环境变量 SZU_USERNAME 配置的账号");
        properties.put("username", username);

        schema.put("properties", properties);
        schema.put("required", List.of());
        return schema;
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

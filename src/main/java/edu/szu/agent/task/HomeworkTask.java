package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.ChaoxingHomeworkClient;
import edu.szu.agent.domain.HomeworkListResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * {@code homework_list} CampusTask — queries the LMS todo list for homework.
 *
 * <p>Parameter contract (string keys, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code username} (required) — student ID</li>
 * </ul>
 *
 * <p>The actual credentials are held by the injected client; this task is a
 * thin adapter translating {@link TaskInput} into a result, mirroring
 * {@link BookingTask}.
 *
 * // 编程技术: 泛型 / 枚举 / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class HomeworkTask implements CampusTask<HomeworkListResult> {

    private final Function<Account, ChaoxingHomeworkClient> clientFactory;
    private final Function<String, Account> accountResolver;

    /**
     * Production constructor — resolves the account per request via
     * {@link AccountResolver#resolve(String)} and builds a session-aware
     * client for it, mirroring {@link BookingTask}. This lets the daemon /
     * MCP path reuse a persisted login keyed by the real student ID instead
     * of failing under a placeholder account.
     *
     * @param clientFactory builds a {@link ChaoxingHomeworkClient} for a resolved account
     */
    public HomeworkTask(Function<Account, ChaoxingHomeworkClient> clientFactory) {
        this(clientFactory, AccountResolver::resolve);
    }

    /** Test constructor — inject a custom client factory and account resolver. */
    HomeworkTask(Function<Account, ChaoxingHomeworkClient> clientFactory,
                 Function<String, Account> accountResolver) {
        this.clientFactory = clientFactory;
        this.accountResolver = accountResolver;
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
        username.put("description", "学号");
        properties.put("username", username);

        schema.put("properties", properties);
        schema.put("required", List.of("username"));
        return schema;
    }

    @Override
    public HomeworkListResult execute(TaskInput input) {
        String username = input.require("username");
        Account account = accountResolver.apply(username);
        return clientFactory.apply(account).list();
    }
}

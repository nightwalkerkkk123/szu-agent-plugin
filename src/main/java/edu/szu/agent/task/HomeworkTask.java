package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.client.ChaoxingHomeworkClient;
import edu.szu.agent.domain.HomeworkListResult;

import java.util.Objects;

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

    private final ChaoxingHomeworkClient client;
    private final Account account;

    public HomeworkTask(ChaoxingHomeworkClient client, Account account) {
        this.client = Objects.requireNonNull(client, "client");
        this.account = Objects.requireNonNull(account, "account");
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
    public HomeworkListResult execute(TaskInput input) {
        input.require("username");
        return client.list();
    }
}

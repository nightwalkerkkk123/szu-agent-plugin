package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.client.EhallScheduleClient;
import edu.szu.agent.domain.ScheduleListResult;

import java.util.Objects;

/**
 * {@code schedule_list} CampusTask — queries the ehall schedule grid.
 *
 * <p>Parameter contract (string keys, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code username} (required) — student ID</li>
 * </ul>
 *
 * <p>The actual credentials are held by the injected client; this task is a
 * thin adapter translating {@link TaskInput} into a result, mirroring
 * {@link HomeworkTask}.
 *
 * // 编程技术: 泛型 / 枚举 / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class ScheduleListTask implements CampusTask<ScheduleListResult> {

    private final EhallScheduleClient client;
    private final Account account;

    public ScheduleListTask(EhallScheduleClient client, Account account) {
        this.client = Objects.requireNonNull(client, "client");
        this.account = Objects.requireNonNull(account, "account");
    }

    @Override
    public String name() {
        return "schedule_list";
    }

    @Override
    public String description() {
        return "查询学生课表";
    }

    @Override
    public ScheduleListResult execute(TaskInput input) {
        input.require("username");
        return client.list();
    }
}

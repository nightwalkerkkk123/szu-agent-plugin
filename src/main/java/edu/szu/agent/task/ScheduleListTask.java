package edu.szu.agent.task;

import edu.szu.agent.client.schedule.ScheduleListClient;
import edu.szu.agent.domain.ScheduleListResult;

import java.util.List;
import java.util.Map;

/**
 * {@code schedule_list} CampusTask — queries the ehall schedule grid.
 *
 * <p>Parameter contract (string keys, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code username} (required) — student ID</li>
 * </ul>
 *
 * <p>This MVP implementation ships with a static snapshot of the schedule
 * page so the Skill is always available. It can later be replaced by
 * browser automation after CAS login.
 *
 * // 编程技术: 泛型 / 枚举 / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class ScheduleListTask implements CampusTask<ScheduleListResult> {

    private final ScheduleListClient client;

    public ScheduleListTask() {
        this(new ScheduleListClient());
    }

    /**
     * Test constructor — inject a custom client.
     */
    public ScheduleListTask(ScheduleListClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "schedule_list";
    }

    @Override
    public String description() {
        return "查询学生课表(静态 MVP)";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return TaskInputSchema.requiredSingle("username", "学号");
    }

    @Override
    public ScheduleListResult execute(TaskInput input) {
        input.require("username");
        return client.list();
    }
}

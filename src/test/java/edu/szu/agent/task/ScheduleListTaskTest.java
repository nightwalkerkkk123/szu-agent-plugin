package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.client.EhallScheduleClient;
import edu.szu.agent.domain.ScheduleListResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ScheduleListTask")
class ScheduleListTaskTest {

    @Test
    @DisplayName("name = schedule_list")
    void nameIsScheduleList() {
        ScheduleListTask task = new ScheduleListTask(
            mock(EhallScheduleClient.class),
            new Account("u", "p", "x"));
        assertThat(task.name()).isEqualTo("schedule_list");
        assertThat(task.description()).isEqualTo("查询学生课表");
    }

    @Test
    @DisplayName("execute 委托 client.list()")
    void executeDelegatesToClient() {
        EhallScheduleClient client = mock(EhallScheduleClient.class);
        ScheduleListResult expected = new ScheduleListResult.Success(List.of(), java.time.Instant.now());
        when(client.list()).thenReturn(expected);

        ScheduleListTask task = new ScheduleListTask(client, new Account("u", "p", "x"));
        ScheduleListResult result = task.execute(new TaskInput(Map.of("username", "u")));

        assertThat(result).isEqualTo(expected);
        verify(client).list();
    }

    @Test
    @DisplayName("execute 缺少 username 抛 IllegalArgumentException")
    void executeRejectsMissingUsername() {
        ScheduleListTask task = new ScheduleListTask(
            mock(EhallScheduleClient.class),
            new Account("u", "p", "x"));
        assertThatThrownBy(() -> task.execute(new TaskInput(Map.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("username");
    }
}

package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.client.ChaoxingHomeworkClient;
import edu.szu.agent.domain.Homework;
import edu.szu.agent.domain.HomeworkListResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HomeworkTask")
class HomeworkTaskTest {

    @Mock
    private ChaoxingHomeworkClient client;

    private final Account account = new Account("2023150090", "secret", "test");

    @Test
    @DisplayName("name() returns homework_list")
    void nameReturnsHomeworkList() {
        HomeworkTask task = new HomeworkTask(client, account);
        assertThat(task.name()).isEqualTo("homework_list");
    }

    @Test
    @DisplayName("description() returns Chinese description")
    void descriptionReturnsChinese() {
        HomeworkTask task = new HomeworkTask(client, account);
        assertThat(task.description()).isEqualTo("查询畅课作业列表");
    }

    @Test
    @DisplayName("execute() delegates to client.list()")
    void executeDelegatesToClient() {
        Homework expected = new Homework("1", "OS", "lab", "2026.06.24 23:59", "待提交");
        when(client.list()).thenReturn(new HomeworkListResult.Success(List.of(expected)));
        HomeworkTask task = new HomeworkTask(client, account);
        TaskInput input = new TaskInput(Map.of("username", "2023150090"));

        HomeworkListResult result = task.execute(input);

        assertThat(result).isInstanceOf(HomeworkListResult.Success.class);
        assertThat(((HomeworkListResult.Success) result).homeworks()).containsExactly(expected);
    }

    @Test
    @DisplayName("execute() with test constructor ignores missing username and delegates to client")
    void executeWithTestConstructorIgnoresUsername() {
        when(client.list()).thenReturn(new HomeworkListResult.Success(List.of()));
        HomeworkTask task = new HomeworkTask(client, account);
        TaskInput input = new TaskInput(Map.of());

        HomeworkListResult result = task.execute(input);

        assertThat(result).isInstanceOf(HomeworkListResult.Success.class);
    }
}

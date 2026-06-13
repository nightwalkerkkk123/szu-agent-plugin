package edu.szu.agent.skill;

import edu.szu.agent.task.CampusTask;
import edu.szu.agent.task.TaskInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link Skills} registry and the {@link Skill} record
 * validation contract.
 *
 * <p>// 编程技术: Lambda / record
 *
 * @since 0.1.0
 * @author 王子豪
 */
class SkillsTest {

    @BeforeEach
    @AfterEach
    void resetRegistry() {
        Skills.reset();
    }

    private static CampusTask<String> echoTask(String name) {
        return new CampusTask<>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "test task";
            }

            @Override
            public String execute(TaskInput input) {
                return "echo";
            }
        };
    }

    @Test
    @DisplayName("Singleton — getInstance returns the same instance")
    void singletonIdentity() {
        assertThat(Skills.getInstance()).isSameAs(Skills.getInstance());
    }

    @Test
    @DisplayName("Register then all() returns it, sorted by name")
    void registerAndList() {
        Skills registry = Skills.getInstance();
        registry.register(new Skill<>("zeta_task", "Z", echoTask("zeta_task")));
        registry.register(new Skill<>("alpha_task", "A", echoTask("alpha_task")));

        List<Skill<?>> all = registry.all();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).name()).isEqualTo("alpha_task");
        assertThat(all.get(1).name()).isEqualTo("zeta_task");
    }

    @Test
    @DisplayName("Duplicate name → IllegalArgumentException")
    void duplicateNameRejected() {
        Skills registry = Skills.getInstance();
        registry.register(new Skill<>("foo", "F", echoTask("foo")));

        assertThatThrownBy(() -> registry.register(new Skill<>("foo", "F2", echoTask("foo"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("foo");
    }

    @Test
    @DisplayName("Skill name must match task.name()")
    void nameTaskMismatchRejected() {
        CampusTask<String> task = echoTask("real_name");
        assertThatThrownBy(() -> new Skill<>("displayed_name", "desc", task))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("displayed_name")
            .hasMessageContaining("real_name");
    }

    @Test
    @DisplayName("Blank Skill name rejected")
    void blankNameRejected() {
        assertThatThrownBy(() -> new Skill<>("", "d", echoTask("x")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Empty registry — all() returns empty list, size = 0")
    void emptyRegistry() {
        assertThat(Skills.getInstance().all()).isEmpty();
        assertThat(Skills.getInstance().size()).isZero();
    }
}

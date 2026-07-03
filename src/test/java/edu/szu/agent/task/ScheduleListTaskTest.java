package edu.szu.agent.task;

import edu.szu.agent.domain.CourseEntry;
import edu.szu.agent.domain.ScheduleListResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * // 编程技术: JUnit 5 / AssertJ / record
 *
 * @since 0.6.0
 * @author 王子豪
 */
@DisplayName("ScheduleListTask")
class ScheduleListTaskTest {

    @Test
    @DisplayName("name = schedule_list")
    void nameAndDescription() {
        ScheduleListTask task = new ScheduleListTask();
        assertThat(task.name()).isEqualTo("schedule_list");
        assertThat(task.description())
            .startsWith("查询学生本学期课表")
            .contains("SZU_SCHEDULE_REAL=0", "AccountResolver", "username");
    }

    @Test
    @DisplayName("execute returns 8 static courses")
    void executeReturnsCourses() {
        ScheduleListTask task = new ScheduleListTask();
        ScheduleListResult result = task.execute(new TaskInput(Map.of("username", "2023150090")));

        assertThat(result).isInstanceOf(ScheduleListResult.Success.class);
        ScheduleListResult.Success s = (ScheduleListResult.Success) result;
        assertThat(s.courses()).hasSize(8);
        assertThat(s.snapshotAt()).isNotNull();
    }

    @Test
    @DisplayName("execute returns real static course data")
    void executeReturnsRealCourseData() {
        ScheduleListTask task = new ScheduleListTask();
        ScheduleListResult result = task.execute(new TaskInput(Map.of("username", "2023150090")));

        assertThat(result).isInstanceOf(ScheduleListResult.Success.class);
        List<CourseEntry> courses = ((ScheduleListResult.Success) result).courses();
        assertThat(courses).anyMatch(c -> c.courseName().equals("操作系统")
            && c.teacher().equals("杜智华"));
        assertThat(courses).anyMatch(c -> c.courseName().equals("多媒体系统导论")
            && c.teacher().equals("方山城"));
    }

    @Test
    @DisplayName("execute without username throws IllegalArgumentException")
    void executeRejectsMissingUsername() {
        ScheduleListTask task = new ScheduleListTask();
        assertThatThrownBy(() -> task.execute(new TaskInput(Map.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("username");
    }
}

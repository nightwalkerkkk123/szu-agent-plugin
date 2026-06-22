package edu.szu.agent.task;

import edu.szu.agent.client.exam.ExamListClient;
import edu.szu.agent.domain.exam.ExamSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExamListTask")
class ExamListTaskTest {

    private static final String SNAPSHOT = """
        <html><body>
        <table class="exam-table">
        <tbody>
            <tr>
                <td>7月14日</td>
                <td>星期二</td>
                <td>操作系统</td>
                <td>[1500110002]</td>
                <td>2026年07月14日 09:00-11:00</td>
                <td>致理楼L1-601</td>
                <td>杜智华</td>
            </tr>
            <tr>
                <td>7月7日</td>
                <td>星期二</td>
                <td>多媒体系统导论</td>
                <td>[1502860001]</td>
                <td>2026年07月07日 14:30-16:30</td>
                <td>致理楼L3-404</td>
                <td>方山城</td>
            </tr>
        </tbody>
        </table>
        </body></html>
        """;

    @Test
    @DisplayName("name = exam_list")
    void nameAndDescriptionAreCorrect() {
        ExamListTask task = new ExamListTask();
        assertThat(task.name()).isEqualTo("exam_list");
        assertThat(task.description()).isEqualTo("查询深大考试安排列表(静态 MVP)");
    }

    @Test
    @DisplayName("requires username")
    void requiresUsername() {
        ExamListTask task = new ExamListTask(new ExamListClient("<html></html>", 2026));
        assertThatThrownBy(() -> task.execute(new TaskInput(Map.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("username");
    }

    @Test
    @DisplayName("parses exam schedules from snapshot")
    void parsesExamSchedules() {
        ExamListTask task = new ExamListTask(new ExamListClient(SNAPSHOT, 2026));

        List<ExamSchedule> exams = task.execute(new TaskInput(Map.of(
            "username", "2023150090")));

        assertThat(exams).hasSize(2);

        ExamSchedule first = exams.get(0);
        assertThat(first.courseName()).isEqualTo("操作系统");
        assertThat(first.courseCode()).isEqualTo("1500110002");
        assertThat(first.examDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(first.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(first.endTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(first.venue()).isEqualTo("致理楼L1-601");
        assertThat(first.invigilator()).isEqualTo("杜智华");
        assertThat(first.date()).isEqualTo("7月14日");
        assertThat(first.weekday()).isEqualTo("星期二");
    }

    @Test
    @DisplayName("filters 待开始考试")
    void filtersPendingExams() {
        ExamListTask task = new ExamListTask(new ExamListClient(SNAPSHOT, 2026));

        List<ExamSchedule> exams = task.execute(new TaskInput(Map.of(
            "username", "2023150090",
            "status", "待开始考试")));

        // Both exams are in July 2026, current date is June 2026, so both are pending
        assertThat(exams).hasSize(2);
    }

    @Test
    @DisplayName("filters 已结束 exams")
    void filtersCompletedExams() {
        ExamListTask task = new ExamListTask(new ExamListClient(SNAPSHOT, 2026));

        // Using a snapshot with exams in the past
        String pastSnapshot = """
            <html><body>
            <table class="exam-table">
            <tbody>
                <tr>
                    <td>1月10日</td>
                    <td>星期五</td>
                    <td>高等数学</td>
                    <td>[1000110001]</td>
                    <td>2026年01月10日 09:00-11:00</td>
                    <td>致理楼L1-101</td>
                    <td>张老师</td>
                </tr>
            </tbody>
            </table>
            </body></html>
            """;
        ExamListTask pastTask = new ExamListTask(new ExamListClient(pastSnapshot, 2026));

        List<ExamSchedule> exams = pastTask.execute(new TaskInput(Map.of(
            "username", "2023150090",
            "status", "已结束")));

        assertThat(exams).hasSize(1);
        assertThat(exams.get(0).courseName()).isEqualTo("高等数学");
    }
}
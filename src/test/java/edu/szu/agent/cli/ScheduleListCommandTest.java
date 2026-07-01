package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.client.schedule.PeriodMapping;
import edu.szu.agent.domain.CourseEntry;
import edu.szu.agent.domain.ScheduleListResult;
import edu.szu.agent.domain.WeekRange;
import edu.szu.agent.domain.Weekday;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.task.CampusTask;
import edu.szu.agent.task.TaskInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScheduleListCommand.buildSuccessData")
class ScheduleListCommandTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("schedule list delegates to task with username")
    void scheduleListDelegatesToTaskWithUsername() throws Exception {
        CourseEntry c = new CourseEntry(
            "真实路径课程", "01", "某老师", "致理楼L1-101",
            Weekday.MONDAY, PeriodMapping.lookup(1, 2),
            WeekRange.parse("1-4周"), false);
        RecordingTask task = new RecordingTask(new ScheduleListResult.Success(
            List.of(c), Instant.parse("2026-06-25T00:00:00Z")));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exit = new CommandLine(new ScheduleListCommand(task))
            .setOut(new PrintWriter(out, true))
            .execute("--username", "2023150090", "--format", "json");

        assertThat(exit).isEqualTo(0);
        assertThat(task.called).isEqualTo(1);
        assertThat(task.input.get("username")).isEqualTo("2023150090");
        JsonNode root = JSON.readTree(out.toString().trim());
        assertThat(root.get("success").asBoolean()).isTrue();
        assertThat(root.get("data").get("count").asInt()).isEqualTo(1);
        assertThat(root.get("data").get("courses").get(0).get("courseName").asText())
            .isEqualTo("真实路径课程");
    }

    @Test
    @DisplayName("schedule list task failure uses shared exit mapping")
    void scheduleListTaskFailureUsesSharedExitMapping() throws Exception {
        RecordingTask task = new RecordingTask(new ScheduleListResult.Failure(
            ErrorCode.SCHEDULE_PAGE_LOAD_FAILED, "safe failure"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exit = new CommandLine(new ScheduleListCommand(task))
            .setOut(new PrintWriter(out, true))
            .execute("--username", "2023150090", "--format", "json");

        assertThat(exit).isEqualTo(CommandOutput.exitCodeFor(ErrorCode.SCHEDULE_PAGE_LOAD_FAILED));
        JsonNode root = JSON.readTree(out.toString().trim());
        assertThat(root.get("success").asBoolean()).isFalse();
        assertThat(root.get("errorCode").asText()).isEqualTo("SCHEDULE_PAGE_LOAD_FAILED");
        assertThat(root.get("errorMessage").asText()).isEqualTo("safe failure");
    }

    @Test
    @DisplayName("missing username does not execute task")
    void missingUsernameDoesNotExecuteTask() throws Exception {
        RecordingTask task = new RecordingTask(new ScheduleListResult.Failure(
            ErrorCode.UNKNOWN, "should not be called"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exit = new CommandLine(new ScheduleListCommand(task))
            .setOut(new PrintWriter(out, true))
            .execute("--format", "json");

        assertThat(exit).isEqualTo(2);
        assertThat(task.called).isZero();
        JsonNode root = JSON.readTree(out.toString().trim());
        assertThat(root.get("success").asBoolean()).isFalse();
        assertThat(root.get("errorCode").asText()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("buildSuccessData 输出 snapshotAt / count / courses")
    void buildsDataEnvelope() {
        CourseEntry c = new CourseEntry(
            "操作系统", "05", "杜智华", "致理楼L1-601",
            Weekday.WEDNESDAY, PeriodMapping.lookup(1, 2),
            WeekRange.parse("1-17周"), false);
        ScheduleListResult.Success s = new ScheduleListResult.Success(List.of(c), Instant.now());
        var node = ScheduleListCommand.buildSuccessData(s);
        assertThat(node.get("count").asInt()).isEqualTo(1);
        assertThat(node.get("snapshotAt").asText()).isNotBlank();
        assertThat(node.get("courses")).hasSize(1);
    }

    @Test
    @DisplayName("buildCourseNode 输出 13 字段")
    void buildCourseNodeAllFields() {
        CourseEntry c = new CourseEntry(
            "操作系统", "05", "杜智华", "致理楼L1-601",
            Weekday.WEDNESDAY, PeriodMapping.lookup(1, 2),
            WeekRange.parse("1-17周"), false);
        var node = ScheduleListCommand.buildCourseNode(c);
        assertThat(node.get("courseName").asText()).isEqualTo("操作系统");
        assertThat(node.get("section").asText()).isEqualTo("05");
        assertThat(node.get("teacher").asText()).isEqualTo("杜智华");
        assertThat(node.get("room").asText()).isEqualTo("致理楼L1-601");
        assertThat(node.get("weekday").asInt()).isEqualTo(3);
        assertThat(node.get("weekdayName").asText()).isEqualTo("星期三");
        assertThat(node.get("beginUnit").asInt()).isEqualTo(1);
        assertThat(node.get("endUnit").asInt()).isEqualTo(2);
        assertThat(node.get("startTime").asText()).isEqualTo("08:00");
        assertThat(node.get("endTime").asText()).isEqualTo("09:50");
        assertThat(node.get("weekRange").asText()).isEqualTo("1-17");
        assertThat(node.get("weeks")).hasSize(17);
        assertThat(node.get("isAdjusted").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("buildCourseNode 缺 section 写 null")
    void buildCourseNodeNullSection() {
        CourseEntry c = new CourseEntry(
            "公开课", null, "某老师", "主楼",
            Weekday.MONDAY, PeriodMapping.lookup(3, 4),
            WeekRange.parse("1-8周"), true);
        var node = ScheduleListCommand.buildCourseNode(c);
        assertThat(node.get("section").isNull()).isTrue();
        assertThat(node.get("isAdjusted").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("CommandOutput.formatResult 接受课程 Node 数组")
    void commandOutputAcceptsCourses() throws Exception {
        CourseEntry c = new CourseEntry(
            "操作系统", "05", "杜智华", "致理楼L1-601",
            Weekday.WEDNESDAY, PeriodMapping.lookup(1, 2),
            WeekRange.parse("1-17周"), false);
        ScheduleListResult.Success s = new ScheduleListResult.Success(List.of(c), Instant.now());
        var data = ScheduleListCommand.buildSuccessData(s);

        String out = CommandOutput.formatResult(true, data, null, null, "t", 0L, "json");
        JsonNode root = JSON.readTree(out);
        assertThat(root.get("success").asBoolean()).isTrue();
        assertThat(root.get("data").get("count").asInt()).isEqualTo(1);
        assertThat(root.get("data").get("courses")).hasSize(1);
    }

    @Test
    @DisplayName("failure exit code uses shared CommandOutput mapping")
    void failureExitCodeUsesSharedMapping() {
        ScheduleListCommand command = new ScheduleListCommand();
        ScheduleListResult.Failure failure = new ScheduleListResult.Failure(
            ErrorCode.SCHEDULE_EMPTY, "empty");

        int exitCode = command.formatAndOutput(new PrintWriter(new StringWriter()),
            failure, "t", 0L);

        assertThat(exitCode).isEqualTo(CommandOutput.exitCodeFor(ErrorCode.SCHEDULE_EMPTY));
    }

    @Test
    @DisplayName("CommandOutput.formatHuman 输出 count 字段")
    void humanOutput() {
        CourseEntry c = new CourseEntry(
            "操作系统", "05", "杜智华", "致理楼L1-601",
            Weekday.WEDNESDAY, PeriodMapping.lookup(1, 2),
            WeekRange.parse("1-17周"), false);
        ScheduleListResult.Success s = new ScheduleListResult.Success(List.of(c), Instant.now());
        var data = ScheduleListCommand.buildSuccessData(s);

        String out = CommandOutput.formatResult(true, data, null, null, "t", 0L, "human");
        assertThat(out).contains("Success: true");
        assertThat(out).contains("count: 1");
    }


    private static final class RecordingTask implements CampusTask<ScheduleListResult> {

        private final ScheduleListResult result;
        private int called;
        private TaskInput input;

        private RecordingTask(ScheduleListResult result) {
            this.result = result;
        }

        @Override
        public String name() {
            return "schedule_list";
        }

        @Override
        public String description() {
            return "test schedule task";
        }

        @Override
        public ScheduleListResult execute(TaskInput input) {
            this.called++;
            this.input = input;
            return result;
        }
    }
}

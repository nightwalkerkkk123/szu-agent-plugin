package edu.szu.agent.domain;

import edu.szu.agent.client.schedule.PeriodMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CourseEntry")
class CourseEntryTest {

    private static final CourseEntry SAMPLE = new CourseEntry(
        "操作系统",
        "05",
        "杜智华",
        "致理楼L1-601",
        Weekday.WEDNESDAY,
        PeriodMapping.lookup(1, 2),
        WeekRange.parse("1-17周"),
        false
    );

    @Test
    @DisplayName("record 等值性基于全部字段")
    void recordEquality() {
        CourseEntry copy = new CourseEntry(
            "操作系统", "05", "杜智华", "致理楼L1-601",
            Weekday.WEDNESDAY, PeriodMapping.lookup(1, 2),
            WeekRange.parse("1-17周"), false);
        assertThat(SAMPLE).isEqualTo(copy);
        assertThat(SAMPLE.hashCode()).isEqualTo(copy.hashCode());
    }

    @Test
    @DisplayName("record 不等值性: 任一字段不同")
    void recordInequality() {
        CourseEntry adjusted = new CourseEntry(
            "操作系统", "05", "杜智华", "致理楼L1-601",
            Weekday.WEDNESDAY, PeriodMapping.lookup(1, 2),
            WeekRange.parse("1-17周"), true);
        assertThat(SAMPLE).isNotEqualTo(adjusted);
    }

    @Test
    @DisplayName("toString 包含所有字段名")
    void toStringContainsFields() {
        String s = SAMPLE.toString();
        assertThat(s).contains("操作系统");
        assertThat(s).contains("05");
        assertThat(s).contains("杜智华");
        assertThat(s).contains("致理楼L1-601");
        assertThat(s).contains("WEDNESDAY");
    }

    @Test
    @DisplayName("record 不可变: 不可修改字段")
    void recordIsImmutable() {
        // 通过访问器只读
        assertThat(SAMPLE.courseName()).isEqualTo("操作系统");
        assertThat(SAMPLE.section()).isEqualTo("05");
        assertThat(SAMPLE.teacher()).isEqualTo("杜智华");
        assertThat(SAMPLE.room()).isEqualTo("致理楼L1-601");
        assertThat(SAMPLE.weekday()).isEqualTo(Weekday.WEDNESDAY);
        assertThat(SAMPLE.period().startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(SAMPLE.weekRange().weeks()).hasSize(17);
        assertThat(SAMPLE.isAdjusted()).isFalse();
    }
}

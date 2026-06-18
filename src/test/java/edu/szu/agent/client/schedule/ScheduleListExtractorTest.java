package edu.szu.agent.client.schedule;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.CourseEntry;
import edu.szu.agent.domain.ScheduleListResult;
import edu.szu.agent.domain.Weekday;
import edu.szu.agent.error.BookingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ScheduleListExtractor")
class ScheduleListExtractorTest {

    @Test
    @DisplayName("extract 解析 1 条课程为 CourseEntry")
    void extractSingleEntry() {
        String json = """
            [{
              "courseName": "操作系统[05]",
              "teacher": "杜智华",
              "roomText": "1-17周,星期3,1-2节,致理楼L1-601",
              "isAdjusted": false,
              "weekday": 3,
              "beginUnit": 1,
              "endUnit": 2
            }]
            """;
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.evaluate(anyString())).thenReturn(json);

        List<CourseEntry> result = ScheduleListExtractor.extract(browser);
        assertThat(result).hasSize(1);
        CourseEntry e = result.get(0);
        assertThat(e.courseName()).isEqualTo("操作系统");
        assertThat(e.section()).isEqualTo("05");
        assertThat(e.teacher()).isEqualTo("杜智华");
        assertThat(e.room()).isEqualTo("致理楼L1-601");
        assertThat(e.weekday()).isEqualTo(Weekday.WEDNESDAY);
        assertThat(e.period().beginUnit()).isEqualTo(1);
        assertThat(e.period().endUnit()).isEqualTo(2);
        assertThat(e.weekRange().weeks()).hasSize(17);
        assertThat(e.isAdjusted()).isFalse();
    }

    @Test
    @DisplayName("extract 解析多条课程(多天/多节)")
    void extractMultipleEntries() {
        String json = """
            [
              {"courseName":"操作系统[05]","teacher":"杜智华",
               "roomText":"1-17周,星期3,1-2节,致理楼L1-601",
               "isAdjusted":false,"weekday":3,"beginUnit":1,"endUnit":2},
              {"courseName":"多媒体系统导论[02]","teacher":"方山城",
               "roomText":"1-17周,星期2,7-8节,致理楼L1-711",
               "isAdjusted":false,"weekday":2,"beginUnit":7,"endUnit":8},
              {"courseName":"面向对象高级编程专题[01]","teacher":"徐鹏飞",
               "roomText":"1-17周,星期3,11-12节,致理楼L1-201",
               "isAdjusted":false,"weekday":3,"beginUnit":11,"endUnit":12}
            ]
            """;
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.evaluate(anyString())).thenReturn(json);

        List<CourseEntry> result = ScheduleListExtractor.extract(browser);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).weekday()).isEqualTo(Weekday.WEDNESDAY);
        assertThat(result.get(1).weekday()).isEqualTo(Weekday.TUESDAY);
        assertThat(result.get(1).period().beginUnit()).isEqualTo(7);
        assertThat(result.get(2).period().endUnit()).isEqualTo(12);
    }

    @Test
    @DisplayName("extract 跳过空数组(无课)")
    void extractEmptyArray() {
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.evaluate(anyString())).thenReturn("[]");
        assertThat(ScheduleListExtractor.extract(browser)).isEmpty();
    }

    @Test
    @DisplayName("extract 空白/空返回抛 SCHEDULE_PAGE_LOAD_FAILED")
    void extractRejectsBlank() {
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.evaluate(anyString())).thenReturn("");
        assertThatThrownBy(() -> ScheduleListExtractor.extract(browser))
            .isInstanceOf(BookingException.class)
            .extracting("code").isEqualTo(edu.szu.agent.error.ErrorCode.SCHEDULE_PAGE_LOAD_FAILED);
    }

    @Test
    @DisplayName("extract 非法 JSON 抛 SCHEDULE_PARSE_FAILED")
    void extractRejectsInvalidJson() {
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.evaluate(anyString())).thenReturn("not json {");
        assertThatThrownBy(() -> ScheduleListExtractor.extract(browser))
            .isInstanceOf(BookingException.class)
            .extracting("code").isEqualTo(edu.szu.agent.error.ErrorCode.SCHEDULE_PARSE_FAILED);
    }

    @Test
    @DisplayName("extract 调停课标记 isAdjusted=true")
    void extractAdjustedCourse() {
        String json = """
            [{"courseName":"操作系统[05]","teacher":"杜智华",
              "roomText":"1-17周,星期3,1-2节,致理楼L1-601",
              "isAdjusted":true,"weekday":3,"beginUnit":1,"endUnit":2}]
            """;
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.evaluate(anyString())).thenReturn(json);
        CourseEntry e = ScheduleListExtractor.extract(browser).get(0);
        assertThat(e.isAdjusted()).isTrue();
    }

    @Test
    @DisplayName("extract 课程名无 [NN] 后缀时 section=null")
    void extractCourseWithoutSection() {
        String json = """
            [{"courseName":"公开课","teacher":"某老师",
              "roomText":"1-17周,星期3,1-2节,主楼",
              "isAdjusted":false,"weekday":3,"beginUnit":1,"endUnit":2}]
            """;
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.evaluate(anyString())).thenReturn(json);
        CourseEntry e = ScheduleListExtractor.extract(browser).get(0);
        assertThat(e.courseName()).isEqualTo("公开课");
        assertThat(e.section()).isNull();
    }

    @Test
    @DisplayName("extract 非法 weekday 抛 SCHEDULE_PARSE_FAILED")
    void extractRejectsInvalidWeekday() {
        String json = """
            [{"courseName":"X[01]","teacher":"T",
              "roomText":"1-17周,星期3,1-2节,room",
              "isAdjusted":false,"weekday":99,"beginUnit":1,"endUnit":2}]
            """;
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.evaluate(anyString())).thenReturn(json);
        assertThatThrownBy(() -> ScheduleListExtractor.extract(browser))
            .isInstanceOf(BookingException.class)
            .extracting("code").isEqualTo(edu.szu.agent.error.ErrorCode.SCHEDULE_PARSE_FAILED);
    }

    @Test
    @DisplayName("extract 未知节次抛 SCHEDULE_PARSE_FAILED")
    void extractRejectsUnknownPeriod() {
        String json = """
            [{"courseName":"X[01]","teacher":"T",
              "roomText":"1-17周,星期3,1-3节,room",
              "isAdjusted":false,"weekday":3,"beginUnit":1,"endUnit":3}]
            """;
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.evaluate(anyString())).thenReturn(json);
        assertThatThrownBy(() -> ScheduleListExtractor.extract(browser))
            .isInstanceOf(BookingException.class)
            .extracting("code").isEqualTo(edu.szu.agent.error.ErrorCode.SCHEDULE_PARSE_FAILED);
    }

    @Test
    @DisplayName("extract roomText 段数 < 4 抛 SCHEDULE_PARSE_FAILED")
    void extractRejectsShortRoomText() {
        String json = """
            [{"courseName":"X[01]","teacher":"T",
              "roomText":"1-17周,星期3,1-2节",
              "isAdjusted":false,"weekday":3,"beginUnit":1,"endUnit":2}]
            """;
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.evaluate(anyString())).thenReturn(json);
        assertThatThrownBy(() -> ScheduleListExtractor.extract(browser))
            .isInstanceOf(BookingException.class)
            .extracting("code").isEqualTo(edu.szu.agent.error.ErrorCode.SCHEDULE_PARSE_FAILED);
    }

    @Test
    @DisplayName("buildExtractionScript 包含 6 个 CSS 选择器")
    void scriptContainsSelectors() {
        String s = ScheduleListExtractor.buildExtractionScript();
        assertThat(s).contains("td[data-role=\"item\"]");
        assertThat(s).contains(".mtt_arrange_item");
        assertThat(s).contains(".mtt_item_kcmc");
        assertThat(s).contains(".mtt_item_jxbmc");
        assertThat(s).contains(".mtt_item_room");
        assertThat(s).contains(".mtt_item_tzkcicon");
        assertThat(s).contains("JSON.stringify");
    }

    @Test
    @DisplayName("extract 把 evaluate 结果传给 Jackson")
    void extractDelegatesToEvaluate() {
        String json = "[]";
        BrowserLifecycle browser = mock(BrowserLifecycle.class);
        when(browser.evaluate(anyString())).thenReturn(json);
        ScheduleListExtractor.extract(browser);
        verify(browser).evaluate(anyString());
    }

    @Test
    @DisplayName("extract 对 null browser 抛 NPE")
    void extractRejectsNullBrowser() {
        assertThatThrownBy(() -> ScheduleListExtractor.extract(null))
            .isInstanceOf(NullPointerException.class);
    }
}

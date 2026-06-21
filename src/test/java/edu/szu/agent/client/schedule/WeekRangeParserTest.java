package edu.szu.agent.client.schedule;

import edu.szu.agent.domain.WeekRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WeekRangeParser")
class WeekRangeParserTest {

    @Test
    @DisplayName("parse 连续区间 1-17周")
    void parseContiguousRange() {
        WeekRange r = WeekRangeParser.parse("1-17周");
        assertThat(r.weeks()).hasSize(17);
        assertThat(r.weeks().get(0)).isEqualTo(1);
        assertThat(r.weeks().get(16)).isEqualTo(17);
        assertThat(r.raw()).isEqualTo("1-17周");
    }

    @Test
    @DisplayName("parse 跳过区间 1-8,10-17周")
    void parseGappedRange() {
        WeekRange r = WeekRangeParser.parse("1-8,10-17周");
        assertThat(r.weeks()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17);
        assertThat(r.contains(9)).isFalse();
        assertThat(r.contains(1)).isTrue();
        assertThat(r.contains(17)).isTrue();
    }

    @Test
    @DisplayName("parse 单周 5周")
    void parseSingleWeek() {
        WeekRange r = WeekRangeParser.parse("5周");
        assertThat(r.weeks()).containsExactly(5);
    }

    @Test
    @DisplayName("parse 单周(奇) 1-17周(单)")
    void parseOddWeeks() {
        WeekRange r = WeekRangeParser.parse("1-17周(单)");
        assertThat(r.weeks()).containsExactly(1, 3, 5, 7, 9, 11, 13, 15, 17);
        assertThat(r.contains(2)).isFalse();
    }

    @Test
    @DisplayName("parse 双周(偶) 1-17周(双)")
    void parseEvenWeeks() {
        WeekRange r = WeekRangeParser.parse("1-17周(双)");
        assertThat(r.weeks()).containsExactly(2, 4, 6, 8, 10, 12, 14, 16);
        assertThat(r.contains(1)).isFalse();
    }

    @Test
    @DisplayName("parse 接受不含'周'后缀")
    void parseAcceptsNoSuffix() {
        WeekRange r = WeekRangeParser.parse("1-8,10-17");
        assertThat(r.weeks()).hasSize(16);
    }

    @Test
    @DisplayName("parse 自动 trim 前后空白")
    void parseTrimsWhitespace() {
        WeekRange r = WeekRangeParser.parse("  1-5周  ");
        assertThat(r.weeks()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("parse 去重相邻重复段")
    void parseDeduplicates() {
        WeekRange r = WeekRangeParser.parse("1-3周,2-4周");
        assertThat(r.weeks()).containsExactly(1, 2, 3, 4);
    }

    @ParameterizedTest
    @ValueSource(strings = { "abc周", "", "周" })
    @DisplayName("parse 非法输入抛 IllegalArgumentException")
    void parseRejectsInvalid(String input) {
        assertThatThrownBy(() -> WeekRangeParser.parse(input))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parse null 抛 IllegalArgumentException")
    void parseRejectsNull() {
        assertThatThrownBy(() -> WeekRangeParser.parse(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("contains 对连续区间正确")
    void containsWorks() {
        WeekRange r = WeekRangeParser.parse("1-17周");
        assertThat(r.contains(0)).isFalse();
        assertThat(r.contains(1)).isTrue();
        assertThat(r.contains(8)).isTrue();
        assertThat(r.contains(17)).isTrue();
        assertThat(r.contains(18)).isFalse();
    }

    @Test
    @DisplayName("compact 连续区间输出 1-17")
    void compactContiguous() {
        WeekRange r = WeekRangeParser.parse("1-17周");
        assertThat(r.compact()).isEqualTo("1-17");
    }

    @Test
    @DisplayName("compact 跳跃区间输出 1-8,10-17")
    void compactGapped() {
        WeekRange r = WeekRangeParser.parse("1-8,10-17周");
        assertThat(r.compact()).isEqualTo("1-8,10-17");
    }

    @Test
    @DisplayName("compact 单周输出 5")
    void compactSingle() {
        WeekRange r = WeekRangeParser.parse("5周");
        assertThat(r.compact()).isEqualTo("5");
    }

    @Test
    @DisplayName("compact 混合三段 1-3,5,7-9")
    void compactMixed() {
        WeekRange r = WeekRangeParser.parse("1-3周,5周,7-9周");
        assertThat(r.compact()).isEqualTo("1-3,5,7-9");
    }

    @Test
    @DisplayName("compact 空集合返回空串")
    void compactEmpty() {
        // empty list 不可能从合法 parse 获得,直接构造
        WeekRange r = new WeekRange(List.of(), "");
        assertThat(r.compact()).isEqualTo("");
    }
}

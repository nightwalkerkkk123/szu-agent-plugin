package edu.szu.agent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Weekday")
class WeekdayTest {

    @ParameterizedTest
    @CsvSource({
        "1, MONDAY,    星期一",
        "2, TUESDAY,   星期二",
        "3, WEDNESDAY, 星期三",
        "4, THURSDAY,  星期四",
        "5, FRIDAY,    星期五",
        "6, SATURDAY,  星期六",
        "7, SUNDAY,    星期日"
    })
    @DisplayName("of(int) 按 ehall data-week 编号解析")
    void ofMapsCodeToEnum(int code, Weekday expected, String expectedName) {
        assertThat(Weekday.of(code)).isEqualTo(expected);
        assertThat(Weekday.of(code).displayName()).isEqualTo(expectedName);
    }

    @Test
    @DisplayName("of(int) 非法编号抛 IllegalArgumentException")
    void ofRejectsInvalidCode() {
        assertThatThrownBy(() -> Weekday.of(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("0");
        assertThatThrownBy(() -> Weekday.of(8))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("8");
        assertThatThrownBy(() -> Weekday.of(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("code() 与 of() 互逆")
    void codeAndOfAreInverse() {
        for (Weekday w : Weekday.values()) {
            assertThat(Weekday.of(w.code())).isEqualTo(w);
        }
    }
}

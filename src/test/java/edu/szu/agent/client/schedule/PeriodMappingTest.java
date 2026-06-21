package edu.szu.agent.client.schedule;

import edu.szu.agent.domain.Period;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PeriodMapping")
class PeriodMappingTest {

    @ParameterizedTest
    @CsvSource({
        "1,  2,  08:00, 09:50",
        "3,  4,  10:10, 12:00",
        "5,  5,  14:00, 14:50",
        "6,  6,  15:00, 15:50",
        "7,  8,  16:10, 17:50",
        "9,  10, 19:00, 20:50",
        "11, 12, 21:00, 22:50"
    })
    @DisplayName("lookup 命中 7 个标准节次")
    void lookupMapsStandardPeriods(int begin, int end, String start, String endTime) {
        Period p = PeriodMapping.lookup(begin, end);
        assertThat(p.beginUnit()).isEqualTo(begin);
        assertThat(p.endUnit()).isEqualTo(end);
        assertThat(p.startTime().toString()).isEqualTo(start);
        assertThat(p.endTime().toString()).isEqualTo(endTime);
    }

    @Test
    @DisplayName("lookup 13-14 命中(晚间延长段)")
    void lookupMapsExtendedPeriod() {
        Period p = PeriodMapping.lookup(13, 14);
        assertThat(p.beginUnit()).isEqualTo(13);
        assertThat(p.endUnit()).isEqualTo(14);
    }

    @Test
    @DisplayName("lookup 未知节次对抛 IllegalArgumentException")
    void lookupRejectsUnknown() {
        assertThatThrownBy(() -> PeriodMapping.lookup(1, 3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1-3");
        assertThatThrownBy(() -> PeriodMapping.lookup(0, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PeriodMapping.lookup(15, 16))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Period.of 委托 PeriodMapping")
    void periodOfDelegates() {
        Period p = Period.of(1, 2);
        assertThat(p.startTime().toString()).isEqualTo("08:00");
    }

    @Test
    @DisplayName("Period 构造校验 beginUnit >= 1")
    void periodRejectsInvalidBegin() {
        assertThatThrownBy(() -> new Period(0, 1, java.time.LocalTime.of(8, 0), java.time.LocalTime.of(9, 0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Period 构造校验 endUnit >= beginUnit")
    void periodRejectsInvertedRange() {
        assertThatThrownBy(() -> new Period(2, 1, java.time.LocalTime.of(8, 0), java.time.LocalTime.of(9, 0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Period 构造校验 endTime 在 startTime 之后")
    void periodRejectsInvertedTime() {
        assertThatThrownBy(() -> new Period(1, 1, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(8, 0)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

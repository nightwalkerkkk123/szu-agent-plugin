package edu.szu.agent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TimeSlot} enum: parsing + per-constant metadata.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("TimeSlot enum")
class TimeSlotTest {

    @Test
    @DisplayName("Each constant exposes start, end and slotId in HH:mm-HH:mm form")
    void metadataExposed() {
        TimeSlot slot = TimeSlot.T19_20;

        assertThat(slot.start()).isEqualTo("19:00");
        assertThat(slot.end()).isEqualTo("20:00");
        assertThat(slot.slotId()).isEqualTo("19:00-20:00");
    }

    @Test
    @DisplayName("of() parses HH:mm-HH:mm wire format to the matching constant")
    void parsesWireFormat() {
        assertThat(TimeSlot.of("19:00-20:00")).isSameAs(TimeSlot.T19_20);
        assertThat(TimeSlot.of("08:00-09:00")).isSameAs(TimeSlot.T08_09);
        assertThat(TimeSlot.of("21:00-22:00")).isSameAs(TimeSlot.T21_22);
    }

    @Test
    @DisplayName("of() ignores whitespace around start/end")
    void parsesWithWhitespace() {
        assertThat(TimeSlot.of(" 19:00 - 20:00 ")).isSameAs(TimeSlot.T19_20);
    }

    @Test
    @DisplayName("of() rejects null")
    void rejectsNull() {
        assertThatThrownBy(() -> TimeSlot.of(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of() rejects malformed input")
    void rejectsMalformed() {
        assertThatThrownBy(() -> TimeSlot.of("19:0020:00"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of() rejects times not on a valid hour boundary")
    void rejectsNonHourBoundary() {
        assertThatThrownBy(() -> TimeSlot.of("19:30-20:30"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No such time slot");
    }

    @Test
    @DisplayName("parse(start, end) is equivalent to of(start + '-' + end)")
    void parseTwoArgEquivalence() {
        assertThat(TimeSlot.parse("19:00", "20:00")).isSameAs(TimeSlot.T19_20);
    }

    @Test
    @DisplayName("Has all 14 hourly slots from 08:00 to 22:00")
    void hasAllFourteenSlots() {
        assertThat(TimeSlot.values()).hasSize(14);
        assertThat(TimeSlot.values()[0].start()).isEqualTo("08:00");
        assertThat(TimeSlot.values()[13].end()).isEqualTo("22:00");
    }
}

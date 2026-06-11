package edu.szu.agent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TimeSlot} record validation.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("TimeSlot record")
class TimeSlotTest {

    @Test
    @DisplayName("accepts valid HH:mm range where start < end")
    void acceptsValidRange() {
        TimeSlot slot = new TimeSlot("19:00", "20:00");

        assertThat(slot.start()).isEqualTo("19:00");
        assertThat(slot.end()).isEqualTo("20:00");
    }

    @Test
    @DisplayName("rejects null start")
    void rejectsNullStart() {
        assertThatThrownBy(() -> new TimeSlot(null, "20:00"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("start");
    }

    @Test
    @DisplayName("rejects null end")
    void rejectsNullEnd() {
        assertThatThrownBy(() -> new TimeSlot("19:00", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("end");
    }

    @Test
    @DisplayName("rejects start equal to end (zero-length slot)")
    void rejectsEqualStartEnd() {
        assertThatThrownBy(() -> new TimeSlot("19:00", "19:00"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects start after end (chronological order required)")
    void rejectsStartAfterEnd() {
        assertThatThrownBy(() -> new TimeSlot("20:00", "19:00"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

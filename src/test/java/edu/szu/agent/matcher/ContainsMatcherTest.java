package edu.szu.agent.matcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ContainsMatcher}.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("ContainsMatcher")
class ContainsMatcherTest {

    @Test
    @DisplayName("matches when substring is present (case-sensitive)")
    void matchesCaseSensitive() {
        var m = new ContainsMatcher("Tennis");

        assertThat(m.matches("Tennis Court 1")).isTrue();
        assertThat(m.matches("tennis court 1")).isFalse();
    }

    @Test
    @DisplayName("matches case-insensitively when flag is set")
    void matchesIgnoreCase() {
        var m = new ContainsMatcher("Tennis", true);

        assertThat(m.matches("Tennis Court 1")).isTrue();
        assertThat(m.matches("tennis court 1")).isTrue();
        assertThat(m.matches("TENNIS COURT 1")).isTrue();
    }

    @Test
    @DisplayName("returns false for null candidate")
    void nullCandidateReturnsFalse() {
        var m = new ContainsMatcher("x");
        assertThat(m.matches(null)).isFalse();
    }
}

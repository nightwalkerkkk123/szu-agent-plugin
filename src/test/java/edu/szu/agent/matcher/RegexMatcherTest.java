package edu.szu.agent.matcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link RegexMatcher}.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("RegexMatcher")
class RegexMatcherTest {

    @Test
    @DisplayName("matches when regex finds a hit")
    void matchesRegexHit() {
        var m = new RegexMatcher("Tennis.*Court");
        assertThat(m.matches("Tennis Court 1")).isTrue();
        assertThat(m.matches("Volleyball Court 1")).isFalse();
    }

    @Test
    @DisplayName("accepts precompiled Pattern")
    void acceptsPrecompiledPattern() {
        var pattern = java.util.regex.Pattern.compile("\\d+号");
        var m = new RegexMatcher(pattern);

        assertThat(m.matches("场地 12号")).isTrue();
        assertThat(m.matches("no digits")).isFalse();
    }

    @Test
    @DisplayName("invalid regex throws at construction (ADR-0006 §4.8)")
    void invalidRegexThrows() {
        assertThatThrownBy(() -> new RegexMatcher("[invalid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid regex");
    }

    @Test
    @DisplayName("null candidate returns false")
    void nullCandidateReturnsFalse() {
        var m = new RegexMatcher(".*");
        assertThat(m.matches(null)).isFalse();
    }
}

package edu.szu.agent.matcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ExactMatcher}.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("ExactMatcher")
class ExactMatcherTest {

    @Test
    @DisplayName("matches only the exact string")
    void matchesExact() {
        var m = new ExactMatcher("网球1号场");

        assertThat(m.matches("网球1号场")).isTrue();
        assertThat(m.matches(" 网球1号场")).isFalse();
        assertThat(m.matches("网球1号场 ")).isFalse();
        assertThat(m.matches("网球1号")).isFalse();
    }

    @Test
    @DisplayName("description includes target")
    void descriptionIncludesTarget() {
        var m = new ExactMatcher("foo");
        assertThat(m.description()).isEqualTo("exact:foo");
    }
}

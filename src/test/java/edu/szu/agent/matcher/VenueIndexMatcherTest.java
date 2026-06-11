package edu.szu.agent.matcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link VenueIndexMatcher} — ehall's 4 venue numbering styles.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("VenueIndexMatcher")
class VenueIndexMatcherTest {

    @Test
    @DisplayName("matches '1号' style")
    void matchesHaoStyle() {
        var m = new VenueIndexMatcher(1);
        assertThat(m.matches("网球1号场地")).isTrue();
    }

    @Test
    @DisplayName("matches '第1场' style")
    void matchesChangStyle() {
        var m = new VenueIndexMatcher(1);
        assertThat(m.matches("第1场 网球")).isTrue();
    }

    @Test
    @DisplayName("matches '(1)' style")
    void matchesParenStyle() {
        var m = new VenueIndexMatcher(1);
        assertThat(m.matches("场地 (1) 可预约")).isTrue();
    }

    @Test
    @DisplayName("matches bare '1' with word boundaries")
    void matchesBareIndex() {
        var m = new VenueIndexMatcher(1);
        assertThat(m.matches("1")).isTrue();
        assertThat(m.matches("场地 1 可预约")).isTrue();
    }

    @Test
    @DisplayName("does not match venue 1 input when looking for venue 2")
    void doesNotMatchWrongIndex() {
        var m1 = new VenueIndexMatcher(1);
        var m2 = new VenueIndexMatcher(2);

        assertThat(m1.matches("网球2号场地")).isFalse();
        assertThat(m2.matches("网球1号场地")).isFalse();
    }

    @Test
    @DisplayName("does not match a multi-digit prefix that happens to start with target")
    void doesNotMatchLongerNumber() {
        var m1 = new VenueIndexMatcher(1);
        // "10号" should not match "1号" — word boundary excludes it
        assertThat(m1.matches("网球10号场地")).isFalse();
    }

    @Test
    @DisplayName("rejects index < 1")
    void rejectsNonPositiveIndex() {
        assertThatThrownBy(() -> new VenueIndexMatcher(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VenueIndexMatcher(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

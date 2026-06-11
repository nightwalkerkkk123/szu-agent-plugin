package edu.szu.agent.matcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the default combinators on {@link Matcher} and the
 * {@link Matchers} factory methods.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("Matcher combinators + Matchers factory")
class MatcherCombinatorsTest {

    @Test
    @DisplayName("and: both must match")
    void andRequiresBoth() {
        var m = Matchers.exact("网球1号场")
            .and(Matchers.contains("网"));

        assertThat(m.matches("网球1号场")).isTrue();
        assertThat(m.matches("足球1号场")).isFalse();
        assertThat(m.matches("网球场")).isFalse();
    }

    @Test
    @DisplayName("or: either matching is enough")
    void orAcceptsEither() {
        var m = Matchers.exact("网球1号场")
            .or(Matchers.exact("羽毛球1号场"));

        assertThat(m.matches("网球1号场")).isTrue();
        assertThat(m.matches("羽毛球1号场")).isTrue();
        assertThat(m.matches("健身房")).isFalse();
    }

    @Test
    @DisplayName("negate: inverts the matcher")
    void negateInverts() {
        var m = Matchers.exact("网球1号场").negate();

        assertThat(m.matches("网球1号场")).isFalse();
        assertThat(m.matches("羽毛球1号场")).isTrue();
    }

    @Test
    @DisplayName("andNot: this matches but other does not")
    void andNotExcludesOther() {
        var m = Matchers.contains("号场")
            .andNot(Matchers.exact("网球1号场"));

        assertThat(m.matches("羽毛球1号场")).isTrue();
        assertThat(m.matches("网球1号场")).isFalse();
    }

    @Test
    @DisplayName("all() with zero matchers is vacuously true")
    void allEmptyIsTrue() {
        var m = Matchers.all();
        assertThat(m.matches("anything")).isTrue();
        assertThat(m.matches("")).isTrue();
    }

    @Test
    @DisplayName("any() with zero matchers is vacuously false")
    void anyEmptyIsFalse() {
        var m = Matchers.any();
        assertThat(m.matches("anything")).isFalse();
        assertThat(m.matches("")).isFalse();
    }

    @Test
    @DisplayName("Matchers.exact / contains / regex / venueIndex produce correct types")
    void factoryMethods() {
        assertThat(Matchers.exact("x")).isInstanceOf(ExactMatcher.class);
        assertThat(Matchers.contains("x")).isInstanceOf(ContainsMatcher.class);
        assertThat(Matchers.containsIgnoreCase("x")).isInstanceOf(ContainsMatcher.class);
        assertThat(Matchers.regex("\\d+")).isInstanceOf(RegexMatcher.class);
        assertThat(Matchers.venueIndex(1)).isInstanceOf(VenueIndexMatcher.class);
    }
}

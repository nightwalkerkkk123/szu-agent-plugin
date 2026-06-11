package edu.szu.agent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Sport} enum metadata.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("Sport enum")
class SportTest {

    @Test
    @DisplayName("TENNIS exposes displayName and ehallCode")
    void tennisMetadata() {
        Sport sport = Sport.TENNIS;

        assertThat(sport.displayName()).isEqualTo("网球");
        assertThat(sport.ehallCode()).isEqualTo("tennis");
    }

    @Test
    @DisplayName("valueOf resolves constant by English identifier")
    void valueOfByEnglishName() {
        assertThat(Sport.valueOf("TENNIS")).isEqualTo(Sport.TENNIS);
    }

    @Test
    @DisplayName("all sports have non-blank displayName and ehallCode")
    void allConstantsArePopulated() {
        for (Sport sport : Sport.values()) {
            assertThat(sport.displayName())
                .as("displayName for %s", sport.name())
                .isNotBlank();
            assertThat(sport.ehallCode())
                .as("ehallCode for %s", sport.name())
                .isNotBlank();
        }
    }
}

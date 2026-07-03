package edu.szu.agent.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Severity} enum.
 *
 * @since 0.6.0
 * @author 王子豪
 */
@DisplayName("Severity enum")
class SeverityTest {

    @Test
    @DisplayName("has 4 tiers in order LOW < MEDIUM < HIGH < CRITICAL")
    void hasFourTiers() {
        assertThat(Severity.values()).hasSize(4);
        assertThat(Severity.LOW.ordinal()).isLessThan(Severity.MEDIUM.ordinal());
        assertThat(Severity.MEDIUM.ordinal()).isLessThan(Severity.HIGH.ordinal());
        assertThat(Severity.HIGH.ordinal()).isLessThan(Severity.CRITICAL.ordinal());
    }

    @Test
    @DisplayName("valueOf resolves by name")
    void valueOfByName() {
        assertThat(Severity.valueOf("CRITICAL")).isEqualTo(Severity.CRITICAL);
    }
}

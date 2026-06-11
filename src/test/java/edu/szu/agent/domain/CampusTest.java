package edu.szu.agent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Campus} enum metadata.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("Campus enum")
class CampusTest {

    @Test
    @DisplayName("YUEHAI exposes displayName and ehallCode")
    void yuehaiMetadata() {
        Campus campus = Campus.YUEHAI;

        assertThat(campus.displayName()).isEqualTo("粤海校区");
        assertThat(campus.ehallCode()).isEqualTo("yuehai");
    }

    @Test
    @DisplayName("valueOf resolves constant by English identifier")
    void valueOfByEnglishName() {
        assertThat(Campus.valueOf("YUEHAI")).isEqualTo(Campus.YUEHAI);
    }

    @Test
    @DisplayName("all campuses have non-blank displayName and ehallCode")
    void allConstantsArePopulated() {
        for (Campus campus : Campus.values()) {
            assertThat(campus.displayName())
                .as("displayName for %s", campus.name())
                .isNotBlank();
            assertThat(campus.ehallCode())
                .as("ehallCode for %s", campus.name())
                .isNotBlank();
        }
    }
}

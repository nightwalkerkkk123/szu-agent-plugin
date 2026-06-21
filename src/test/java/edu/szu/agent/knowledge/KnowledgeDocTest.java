package edu.szu.agent.knowledge;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeDocTest {

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> new KnowledgeDoc(" ", "content", "path", KnowledgeCategory.FAQ, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("title");
    }

    @Test
    void rejectsBlankPath() {
        assertThatThrownBy(() -> new KnowledgeDoc("title", "content", " ", KnowledgeCategory.FAQ, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("path");
    }

    @Test
    void rejectsNullFields() {
        assertThatThrownBy(() -> new KnowledgeDoc(null, "content", "path", KnowledgeCategory.FAQ, Instant.now()))
            .isInstanceOf(NullPointerException.class);
    }
}

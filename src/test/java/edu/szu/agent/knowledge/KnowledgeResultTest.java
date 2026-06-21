package edu.szu.agent.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeResultTest {

    @Test
    void rejectsBlankSnippet() {
        assertThatThrownBy(() -> new KnowledgeResult(" ", "path", 0.5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNaNScore() {
        assertThatThrownBy(() -> new KnowledgeResult("snippet", "path", Double.NaN))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

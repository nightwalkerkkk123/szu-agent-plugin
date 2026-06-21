package edu.szu.agent.knowledge;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeDocBuilderTest {

    @Test
    void buildsMinimalDocument() {
        Instant now = Instant.now();
        KnowledgeDoc doc = new KnowledgeDocBuilder()
            .title("Test")
            .path("knowledge/05-faq.md")
            .category(KnowledgeCategory.FAQ)
            .lastUpdated(now)
            .build();

        assertThat(doc.title()).isEqualTo("Test");
        assertThat(doc.path()).isEqualTo("knowledge/05-faq.md");
        assertThat(doc.category()).isEqualTo(KnowledgeCategory.FAQ);
        assertThat(doc.lastUpdated()).isEqualTo(now);
        assertThat(doc.content()).isEmpty();
    }

    @Test
    void requiresTitle() {
        assertThatThrownBy(() -> new KnowledgeDocBuilder()
            .path("knowledge/05-faq.md")
            .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> new KnowledgeDocBuilder()
            .title(" ")
            .path("knowledge/05-faq.md")
            .build())
            .isInstanceOf(IllegalStateException.class);
    }
}

package edu.szu.agent.knowledge;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegexMatchingStrategyTest {

    private final RegexMatchingStrategy strategy = new RegexMatchingStrategy();

    @Test
    void regexMatchesContent() {
        KnowledgeDoc doc = doc("标题", "联系电话 0755-12345678", KnowledgeCategory.FAQ);
        List<KnowledgeResult> results = strategy.match("\\d{4}-\\d{8}", List.of(doc));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).relevanceScore()).isEqualTo(0.5);
    }

    @Test
    void invalidRegexReturnsEmpty() {
        KnowledgeDoc doc = doc("标题", "content", KnowledgeCategory.FAQ);
        List<KnowledgeResult> results = strategy.match("[", List.of(doc));

        assertThat(results).isEmpty();
    }

    @Test
    void titleMatchScoresHigher() {
        KnowledgeDoc doc = doc("图书馆电话", "content", KnowledgeCategory.LIBRARY);
        List<KnowledgeResult> results = strategy.match("图书馆.*", List.of(doc));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).relevanceScore()).isEqualTo(0.6);
    }

    private static KnowledgeDoc doc(String title, String content, KnowledgeCategory category) {
        return new KnowledgeDocBuilder()
            .title(title)
            .content(content)
            .path("knowledge/test.md")
            .category(category)
            .lastUpdated(Instant.EPOCH)
            .build();
    }
}

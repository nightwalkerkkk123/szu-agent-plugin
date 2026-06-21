package edu.szu.agent.knowledge;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExactMatchingStrategyTest {

    private final ExactMatchingStrategy strategy = new ExactMatchingStrategy();

    @Test
    void exactTitleMatchScoresHighest() {
        KnowledgeDoc doc = doc("图书馆", "深圳大学图书馆开放时间", KnowledgeCategory.LIBRARY);
        List<KnowledgeResult> results = strategy.match("图书馆", List.of(doc));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).relevanceScore()).isEqualTo(1.0);
    }

    @Test
    void categoryDisplayNameMatchScoresLower() {
        KnowledgeDoc doc = doc(" anything", "content", KnowledgeCategory.LIBRARY);
        List<KnowledgeResult> results = strategy.match("图书馆", List.of(doc));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).relevanceScore()).isEqualTo(0.95);
    }

    @Test
    void noMatchReturnsEmpty() {
        KnowledgeDoc doc = doc("食堂", "content", KnowledgeCategory.DINING);
        List<KnowledgeResult> results = strategy.match("图书馆", List.of(doc));

        assertThat(results).isEmpty();
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

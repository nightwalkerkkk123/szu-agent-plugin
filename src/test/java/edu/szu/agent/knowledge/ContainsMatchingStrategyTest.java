package edu.szu.agent.knowledge;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContainsMatchingStrategyTest {

    private final ContainsMatchingStrategy strategy = new ContainsMatchingStrategy();

    @Test
    void titleContainsScoresHigherThanContent() {
        KnowledgeDoc doc = doc("图书馆指南", "欢迎访问深圳大学图书馆", KnowledgeCategory.LIBRARY);
        List<KnowledgeResult> results = strategy.match("图书馆", List.of(doc));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).relevanceScore()).isEqualTo(0.85);
    }

    @Test
    void contentContainsProducesExcerpt() {
        KnowledgeDoc doc = doc("指南", "深圳大学图书馆开放时间为 08:00-22:30", KnowledgeCategory.LIBRARY);
        List<KnowledgeResult> results = strategy.match("开放时间", List.of(doc));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).relevanceScore()).isEqualTo(0.7);
        assertThat(results.get(0).snippet()).contains("开放时间");
    }

    @Test
    void noMatchReturnsEmpty() {
        KnowledgeDoc doc = doc("食堂", "今天吃什么", KnowledgeCategory.DINING);
        List<KnowledgeResult> results = strategy.match("图书馆", List.of(doc));

        assertThat(results).isEmpty();
    }

    @Test
    void excerptTrimsSurroundingText() {
        String longText = "a".repeat(100) + "图书馆" + "b".repeat(100);
        String excerpt = ContainsMatchingStrategy.excerpt(longText, 100, 3);
        assertThat(excerpt).startsWith("…");
        assertThat(excerpt).endsWith("…");
        assertThat(excerpt).contains("图书馆");
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

package edu.szu.agent.knowledge;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRepositoryTest {

    @Test
    void queryUsesHighestScoreAcrossStrategies() {
        KnowledgeDoc doc = new KnowledgeDocBuilder()
            .title("图书馆")
            .content("深圳大学图书馆")
            .path("knowledge/03-library.md")
            .category(KnowledgeCategory.LIBRARY)
            .lastUpdated(Instant.EPOCH)
            .build();

        KnowledgeRepository repo = new KnowledgeRepository(List.of(doc), List.of(
            new ContainsMatchingStrategy()
        ));

        List<KnowledgeResult> results = repo.query("图书馆", 5, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).relevanceScore()).isEqualTo(0.85);
    }

    @Test
    void categoryFilterExcludesOtherCategories() {
        KnowledgeDoc lib = new KnowledgeDocBuilder()
            .title("图书馆")
            .content("content")
            .path("knowledge/03-library.md")
            .category(KnowledgeCategory.LIBRARY)
            .lastUpdated(Instant.EPOCH)
            .build();
        KnowledgeDoc dining = new KnowledgeDocBuilder()
            .title("食堂")
            .content("content")
            .path("knowledge/02-dining.md")
            .category(KnowledgeCategory.DINING)
            .lastUpdated(Instant.EPOCH)
            .build();

        KnowledgeRepository repo = new KnowledgeRepository(List.of(lib, dining), List.of(
            new ExactMatchingStrategy()
        ));

        List<KnowledgeResult> results = repo.query("图书馆", 5, KnowledgeCategory.LIBRARY);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).sourcePath()).isEqualTo("knowledge/03-library.md");
    }

    @Test
    void emptyQueryReturnsEmpty() {
        KnowledgeDoc doc = new KnowledgeDocBuilder()
            .title("图书馆")
            .content("content")
            .path("knowledge/03-library.md")
            .category(KnowledgeCategory.LIBRARY)
            .lastUpdated(Instant.EPOCH)
            .build();

        KnowledgeRepository repo = new KnowledgeRepository(List.of(doc), List.of(
            new ExactMatchingStrategy()
        ));

        assertThat(repo.query("   ", 5, null)).isEmpty();
        assertThat(repo.query(null, 5, null)).isEmpty();
    }

    @Test
    void parseDocumentExtractsFrontmatter() {
        String text = "---\ntitle: 测试文档\nlast_updated: 2026-06-20T00:00:00Z\n---\n# Body\ncontent";
        KnowledgeDoc doc = KnowledgeRepository.parseDocument("05-faq.md", "knowledge/05-faq.md", text);

        assertThat(doc.title()).isEqualTo("测试文档");
        assertThat(doc.category()).isEqualTo(KnowledgeCategory.FAQ);
        assertThat(doc.content()).contains("# Body");
        assertThat(doc.lastUpdated()).isEqualTo(Instant.parse("2026-06-20T00:00:00Z"));
    }

    @Test
    void parseDocumentFallsBackToFilenameTitle() {
        String text = "no frontmatter";
        KnowledgeDoc doc = KnowledgeRepository.parseDocument("03-library.md", "knowledge/03-library.md", text);

        assertThat(doc.title()).isEqualTo("library");
        assertThat(doc.category()).isEqualTo(KnowledgeCategory.LIBRARY);
    }
}

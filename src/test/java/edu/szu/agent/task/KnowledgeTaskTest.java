package edu.szu.agent.task;

import edu.szu.agent.knowledge.KnowledgeCategory;
import edu.szu.agent.knowledge.KnowledgeDoc;
import edu.szu.agent.knowledge.KnowledgeDocBuilder;
import edu.szu.agent.knowledge.KnowledgeRepository;
import edu.szu.agent.knowledge.KnowledgeResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeTaskTest {

    @Test
    void returnsQueryResults() {
        KnowledgeDoc doc = new KnowledgeDocBuilder()
            .title("图书馆")
            .content("content")
            .path("knowledge/03-library.md")
            .category(KnowledgeCategory.LIBRARY)
            .lastUpdated(Instant.EPOCH)
            .build();
        KnowledgeTask task = new KnowledgeTask(new KnowledgeRepository(List.of(doc), List.of(
            (q, docs) -> List.of(new KnowledgeResult("snippet", "knowledge/03-library.md", 0.9))
        )));

        List<KnowledgeResult> results = task.execute(new TaskInput(Map.of("query", "图书馆")));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).snippet()).isEqualTo("snippet");
    }

    @Test
    void requiresQuery() {
        KnowledgeTask task = new KnowledgeTask(new KnowledgeRepository(List.of(), List.of()));
        assertThatThrownBy(() -> task.execute(new TaskInput(Map.of("limit", "5"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("query");
    }

    @Test
    void rejectsNonPositiveLimit() {
        KnowledgeTask task = new KnowledgeTask(new KnowledgeRepository(List.of(), List.of()));
        assertThatThrownBy(() -> task.execute(new TaskInput(Map.of("query", "x", "limit", "0"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("limit");
    }

    @Test
    void appliesCategoryFilter() {
        KnowledgeDoc doc = new KnowledgeDocBuilder()
            .title("图书馆")
            .content("content")
            .path("knowledge/03-library.md")
            .category(KnowledgeCategory.LIBRARY)
            .lastUpdated(Instant.EPOCH)
            .build();
        KnowledgeTask task = new KnowledgeTask(new KnowledgeRepository(List.of(doc), List.of(
            (q, docs) -> docs.stream()
                .map(d -> new KnowledgeResult(d.title(), d.path(), 0.8))
                .toList()
        )));

        List<KnowledgeResult> results = task.execute(new TaskInput(Map.of(
            "query", "图书馆",
            "category", "LIBRARY",
            "limit", "1")));

        assertThat(results).hasSize(1);
    }

    @Test
    void nameAndDescriptionAreCorrect() {
        KnowledgeTask task = new KnowledgeTask();
        assertThat(task.name()).isEqualTo("kb_query");
        assertThat(task.description())
            .startsWith("查询深大校园知识库")
            .contains("CAMPUS_BASICS", "LIBRARY", "limit");
    }
}

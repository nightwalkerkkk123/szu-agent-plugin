package edu.szu.agent.task;

import edu.szu.agent.knowledge.KnowledgeCategory;
import edu.szu.agent.knowledge.KnowledgeRepository;
import edu.szu.agent.knowledge.KnowledgeResult;

import java.util.List;

/**
 * {@code kb_query} CampusTask — answers SZU knowledge-base queries.
 *
 * <p>Parameters (string contract, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code query} (required) — user question / keywords
 *   <li>{@code limit} (optional, default 5) — max number of results
 *   <li>{@code category} (optional) — CAMPUS_BASICS / DINING / LIBRARY /
 *       ACADEMICS / FAQ
 * </ul>
 *
 * // 编程技术: 泛型 / 枚举 / Lambda
 *
 * @since 0.2.0
 * @author 王子豪
 */
public class KnowledgeTask implements CampusTask<List<KnowledgeResult>> {

    private final KnowledgeRepository repository;

    public KnowledgeTask() {
        this(new KnowledgeRepository());
    }

    /**
     * Test constructor — inject a custom repository.
     */
    public KnowledgeTask(KnowledgeRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "kb_query";
    }

    @Override
    public String description() {
        return "深大知识库查询";
    }

    @Override
    public List<KnowledgeResult> execute(TaskInput input) {
        String query = input.require("query");
        int limit = input.getInt("limit", 5);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        KnowledgeCategory category = null;
        String categoryValue = input.get("category");
        if (categoryValue != null && !categoryValue.isBlank()) {
            category = KnowledgeCategory.valueOf(categoryValue.trim().toUpperCase());
        }

        return repository.query(query, limit, category);
    }
}

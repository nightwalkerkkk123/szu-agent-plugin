package edu.szu.agent.knowledge;

import java.util.List;

/**
 * Pluggable matching strategy for knowledge-base queries.
 *
 * <p>Implementations decide how a natural-language query maps to
 * documents. New strategies can be added without modifying existing
 * callers (Open/Closed Principle).
 *
 * // 设计模式: Strategy
 * // 编程技术: 函数式接口 / Lambda
 *
 * @since 0.2.0
 * @author 王子豪
 */
@FunctionalInterface
public interface MatchingStrategy {

    /**
     * Matches the query against the provided documents.
     *
     * @param query the user's query, never {@code null}
     * @param docs  all loaded documents, never {@code null}
     * @return ordered list of results; empty if nothing matches
     */
    List<KnowledgeResult> match(String query, List<KnowledgeDoc> docs);
}

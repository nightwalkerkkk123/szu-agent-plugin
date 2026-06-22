package edu.szu.agent.knowledge;

import java.util.Objects;

/**
 * One query result: a relevance-scored snippet plus the source document path.
 *
 * // 编程技术: record (Java 16+)
 *
 * @param snippet        matching text excerpt
 * @param sourcePath     classpath path of the source Markdown file
 * @param relevanceScore 1.0 = exact title match, 0.9 = exact content match,
 *                       0.7 = contains, 0.5 = regex; higher is better
 * @since 0.2.0
 * @author 王子豪
 */
public record KnowledgeResult(
    String snippet,
    String sourcePath,
    double relevanceScore
) {
    public KnowledgeResult {
        Objects.requireNonNull(snippet, "snippet");
        Objects.requireNonNull(sourcePath, "sourcePath");
        if (snippet.isBlank()) {
            throw new IllegalArgumentException("snippet must not be blank");
        }
        if (sourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath must not be blank");
        }
        if (Double.isNaN(relevanceScore)) {
            throw new IllegalArgumentException("relevanceScore must not be NaN");
        }
    }
}

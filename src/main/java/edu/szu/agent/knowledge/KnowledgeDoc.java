package edu.szu.agent.knowledge;

import java.time.Instant;
import java.util.Objects;

/**
 * A single knowledge document backed by a Markdown file.
 *
 * <p>Record fields map directly to §3.6 of the final report:
 * title, content, path, category, lastUpdated.
 *
 * // 编程技术: record (Java 16+)
 *
 * @param title       document title, from frontmatter or filename fallback
 * @param content     Markdown body, used for matching
 * @param path        classpath resource path of the source file
 * @param category    business category
 * @param lastUpdated last update timestamp parsed from frontmatter
 * @since 0.2.0
 * @author 王子豪
 */
public record KnowledgeDoc(
    String title,
    String content,
    String path,
    KnowledgeCategory category,
    Instant lastUpdated
) {
    public KnowledgeDoc {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(lastUpdated, "lastUpdated");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
    }
}

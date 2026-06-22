package edu.szu.agent.knowledge;

import java.time.Instant;
import java.util.Objects;

/**
 * Builder for {@link KnowledgeDoc} — handles 5+ fields and cross-field
 * validation without constructor overloading.
 *
 * // 设计模式: Builder
 *
 * @since 0.2.0
 * @author 王子豪
 */
public final class KnowledgeDocBuilder {

    private String title;
    private String content = "";
    private String path;
    private KnowledgeCategory category = KnowledgeCategory.FAQ;
    private Instant lastUpdated = Instant.EPOCH;

    public KnowledgeDocBuilder title(String title) {
        this.title = title;
        return this;
    }

    public KnowledgeDocBuilder content(String content) {
        this.content = content;
        return this;
    }

    public KnowledgeDocBuilder path(String path) {
        this.path = path;
        return this;
    }

    public KnowledgeDocBuilder category(KnowledgeCategory category) {
        this.category = category;
        return this;
    }

    public KnowledgeDocBuilder lastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
        return this;
    }

    /**
     * Builds the document after validating required fields.
     *
     * @throws IllegalStateException if required fields are missing or blank
     */
    public KnowledgeDoc build() {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(lastUpdated, "lastUpdated");
        if (title.isBlank()) {
            throw new IllegalStateException("title must not be blank");
        }
        if (path.isBlank()) {
            throw new IllegalStateException("path must not be blank");
        }
        return new KnowledgeDoc(title, content, path, category, lastUpdated);
    }
}

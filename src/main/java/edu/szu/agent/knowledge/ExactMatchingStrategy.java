package edu.szu.agent.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * Exact matching strategy — highest relevance.
 *
 * <p>Case-insensitive exact match against document title or category
 * display name.
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class ExactMatchingStrategy implements MatchingStrategy {

    @Override
    public List<KnowledgeResult> match(String query, List<KnowledgeDoc> docs) {
        String normalized = query.trim().toLowerCase();
        List<KnowledgeResult> results = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            if (doc.title().trim().equalsIgnoreCase(normalized)) {
                results.add(new KnowledgeResult(
                    doc.title(), doc.path(), 1.0));
            } else if (doc.category().displayName().equals(normalized)) {
                results.add(new KnowledgeResult(
                    doc.title(), doc.path(), 0.95));
            }
        }
        return results;
    }
}

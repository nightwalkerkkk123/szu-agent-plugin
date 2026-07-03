package edu.szu.agent.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains matching strategy — mid-range relevance.
 *
 * <p>Case-insensitive substring match in title, content, or category
 * display name. Produces a short excerpt around the first occurrence.
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class ContainsMatchingStrategy implements MatchingStrategy {

    private static final int SNIPPET_RADIUS = 60;

    @Override
    public List<KnowledgeResult> match(String query, List<KnowledgeDoc> docs) {
        String normalized = query.trim().toLowerCase();
        List<KnowledgeResult> results = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            double score = 0.0;
            String source = null;
            if (doc.title().toLowerCase().contains(normalized)) {
                score = Math.max(score, 0.85);
                source = doc.title();
            }
            if (doc.category().displayName().contains(normalized)) {
                score = Math.max(score, 0.75);
                source = source == null ? doc.title() : source;
            }
            String lowerContent = doc.content().toLowerCase();
            int idx = lowerContent.indexOf(normalized);
            if (idx >= 0) {
                score = Math.max(score, 0.7);
                source = doc.content();
            }
            if (score > 0.0) {
                String snippet = source == null ? doc.title() : excerpt(source, idx, normalized.length());
                results.add(new KnowledgeResult(snippet, doc.path(), score));
            }
        }
        return results;
    }

    static String excerpt(String text, int matchIndex, int matchLength) {
        int start = Math.max(0, matchIndex - SNIPPET_RADIUS);
        int end = Math.min(text.length(), matchIndex + matchLength + SNIPPET_RADIUS);
        String snippet = text.substring(start, end).replaceAll("\\s+", " ").trim();
        return (start > 0 ? "…" : "") + snippet + (end < text.length() ? "…" : "");
    }
}

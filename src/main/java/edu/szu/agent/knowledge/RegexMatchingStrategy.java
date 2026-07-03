package edu.szu.agent.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Regex matching strategy — lowest relevance, fallback for power users.
 *
 * <p>Treats the query as a case-insensitive Java regular expression and
 * matches against title and content.
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class RegexMatchingStrategy implements MatchingStrategy {

    private static final int SNIPPET_RADIUS = 60;

    @Override
    public List<KnowledgeResult> match(String query, List<KnowledgeDoc> docs) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(query.trim(), Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            return List.of();
        }

        List<KnowledgeResult> results = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            var titleMatcher = pattern.matcher(doc.title());
            var contentMatcher = pattern.matcher(doc.content());
            if (titleMatcher.find()) {
                results.add(new KnowledgeResult(doc.title(), doc.path(), 0.6));
            } else if (contentMatcher.find()) {
                int start = Math.max(0, contentMatcher.start() - SNIPPET_RADIUS);
                int end = Math.min(doc.content().length(), contentMatcher.end() + SNIPPET_RADIUS);
                String snippet = doc.content().substring(start, end).replaceAll("\\s+", " ").trim();
                snippet = (start > 0 ? "…" : "") + snippet + (end < doc.content().length() ? "…" : "");
                results.add(new KnowledgeResult(snippet, doc.path(), 0.5));
            }
        }
        return results;
    }
}

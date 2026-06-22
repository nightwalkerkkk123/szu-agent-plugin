package edu.szu.agent.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Loads and queries the local Markdown knowledge base.
 *
 * <p>Documents live under {@code classpath:knowledge/*.md}. Each file
 * may contain a YAML frontmatter block with {@code title} and
 * {@code last_updated}. The file prefix (e.g. {@code 01-campus-basics.md})
 * determines the {@link KnowledgeCategory}.
 *
 * <p>The repository runs matching strategies in descending relevance order
 * and deduplicates by source path, keeping the highest score.
 *
 * // 设计模式: Strategy (delegates to MatchingStrategy implementations)
 * // 编程技术: Stream / Optional / 泛型
 *
 * @since 0.2.0
 * @author 王子豪
 */
public final class KnowledgeRepository {

    private static final String RESOURCE_DIR = "knowledge/";
    private static final String FRONTMATTER_DELIMITER = "---";

    private final List<KnowledgeDoc> docs;
    private final List<MatchingStrategy> strategies;

    /**
     * Default constructor — loads all bundled Markdown files and uses
     * the three standard strategies.
     */
    public KnowledgeRepository() {
        this(loadDocuments(), defaultStrategies());
    }

    /**
     * Test constructor — inject documents and strategies directly.
     */
    public KnowledgeRepository(List<KnowledgeDoc> docs, List<MatchingStrategy> strategies) {
        this.docs = List.copyOf(Objects.requireNonNull(docs, "docs"));
        this.strategies = List.copyOf(Objects.requireNonNull(strategies, "strategies"));
    }

    /**
     * Returns all loaded documents.
     */
    public List<KnowledgeDoc> allDocuments() {
        return docs;
    }

    /**
     * Queries the knowledge base.
     *
     * @param query    the user query
     * @param limit    maximum number of results to return
     * @param category optional category filter; {@code null} means all
     * @return ordered results by descending relevance
     */
    public List<KnowledgeResult> query(String query, int limit, KnowledgeCategory category) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<KnowledgeDoc> candidates = category == null ? docs : docs.stream()
            .filter(d -> d.category() == category)
            .toList();

        return strategies.stream()
            .flatMap(s -> s.match(query, candidates).stream())
            .collect(Collectors.toMap(
                KnowledgeResult::sourcePath,
                r -> r,
                (a, b) -> a.relevanceScore() >= b.relevanceScore() ? a : b))
            .values().stream()
            .sorted(Comparator.comparingDouble(KnowledgeResult::relevanceScore).reversed())
            .limit(limit)
            .toList();
    }

    private static List<MatchingStrategy> defaultStrategies() {
        return List.of(
            new ExactMatchingStrategy(),
            new ContainsMatchingStrategy(),
            new RegexMatchingStrategy()
        );
    }

    private static List<KnowledgeDoc> loadDocuments() {
        List<KnowledgeDoc> loaded = new ArrayList<>();
        for (String filename : bundledFilenames()) {
            String resourcePath = RESOURCE_DIR + filename;
            try (InputStream is = openResource(resourcePath)) {
                if (is == null) {
                    continue;
                }
                String text = readAll(is);
                loaded.add(parseDocument(filename, resourcePath, text));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read " + resourcePath, e);
            }
        }
        return List.copyOf(loaded);
    }

    private static InputStream openResource(String path) {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (is == null) {
            is = KnowledgeRepository.class.getClassLoader().getResourceAsStream(path);
        }
        return is;
    }

    private static List<String> bundledFilenames() {
        // Known bundled files. A runtime directory scanner is intentionally
        // avoided because it behaves differently across build tools and JARs.
        return List.of(
            "01-campus-basics.md",
            "02-dining.md",
            "03-library.md",
            "04-academics.md",
            "05-faq.md"
        );
    }

    static KnowledgeDoc parseDocument(String filename, String resourcePath, String text) {
        String body = text;
        Map<String, Object> frontmatter = Map.of();
        if (text.startsWith(FRONTMATTER_DELIMITER + "\n") || text.startsWith(FRONTMATTER_DELIMITER + "\r\n")) {
            int end = text.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
            if (end > 0) {
                String yaml = text.substring(FRONTMATTER_DELIMITER.length(), end).trim();
                body = text.substring(end + FRONTMATTER_DELIMITER.length()).stripLeading();
                frontmatter = parseYaml(yaml);
            }
        }

        String title = Optional.ofNullable(frontmatter.get("title"))
            .map(Object::toString)
            .filter(t -> !t.isBlank())
            .orElseGet(() -> titleFromFilename(filename));

        Instant lastUpdated = parseLastUpdated(frontmatter.get("last_updated"));
        KnowledgeCategory category = categoryFromFilename(filename);

        return new KnowledgeDocBuilder()
            .title(title)
            .content(body)
            .path(resourcePath)
            .category(category)
            .lastUpdated(lastUpdated)
            .build();
    }

    private static Map<String, Object> parseYaml(String yaml) {
        if (yaml.isBlank()) {
            return Map.of();
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(yaml, Map.class);
            return map == null ? Map.of() : map;
        } catch (IOException e) {
            return Map.of();
        }
    }

    private static Instant parseLastUpdated(Object value) {
        if (value == null) {
            return Instant.EPOCH;
        }
        String s = value.toString().trim();
        if (s.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }

    private static String titleFromFilename(String filename) {
        String base = filename.replaceAll("^\\d+-", "").replaceAll("\\.md$", "");
        return base.replace('-', ' ');
    }

    private static KnowledgeCategory categoryFromFilename(String filename) {
        return switch (filename) {
            case "01-campus-basics.md" -> KnowledgeCategory.CAMPUS_BASICS;
            case "02-dining.md" -> KnowledgeCategory.DINING;
            case "03-library.md" -> KnowledgeCategory.LIBRARY;
            case "04-academics.md" -> KnowledgeCategory.ACADEMICS;
            case "05-faq.md" -> KnowledgeCategory.FAQ;
            default -> KnowledgeCategory.FAQ;
        };
    }

    private static String readAll(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}

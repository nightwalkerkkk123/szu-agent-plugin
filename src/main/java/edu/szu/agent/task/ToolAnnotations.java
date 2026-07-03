package edu.szu.agent.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured metadata carried by each {@link CampusTask} alongside its
 * {@code description()} and {@code inputSchema()}.
 *
 * <p>Three consumers, two distinct flows:
 * <ul>
 *   <li>{@link edu.szu.agent.mcp.ToolSchema} promotes
 *       {@link #examples()} into the top-level {@code examples} key of the
 *       MCP {@code tools/list} envelope — the LLM gets concrete call
 *       payloads at a glance.</li>
 *   <li>{@link ToolDocsGenerator} reads {@link #resultShape()} and
 *       {@link #commonErrors()} when rendering the human-facing
 *       {@code docs/tools/<tool>.md}.</li>
 *   <li>{@link #hints()} is reserved for future use; currently redundant
 *       with {@code task.description()} which already carries the same
 *       Chinese constraints inline. Kept as a forward-compatible slot so
 *       a future refactor can move long prose out of {@code description()}
 *       without breaking the public record shape.</li>
 * </ul>
 *
 * <p>Programming techniques: record (Java 16+ immutable value type),
 * builder (avoids 4-arg canonical-constructor noise at call sites).
 *
 * <p>// Design Pattern: Builder
 * // 编程技术: record / Builder / 不可变 List
 *
 * @param hints        optional supplementary prose; see class javadoc
 * @param examples     0..N example argument payloads (insertion order preserved)
 * @param resultShape  short description of the return-value structure
 * @param commonErrors 0..N common-error → fix strings
 * @since 0.6.0
 * @author 王子豪
 */
public record ToolAnnotations(
    String hints,
    List<Map<String, Object>> examples,
    String resultShape,
    List<String> commonErrors
) {

    public ToolAnnotations {
        examples = examples == null ? List.of() : List.copyOf(examples);
        commonErrors = commonErrors == null ? List.of() : List.copyOf(commonErrors);
        if (resultShape != null && resultShape.isBlank()) {
            resultShape = null;
        }
    }

    /**
     * @return an empty annotations instance — used as the
     *         {@link CampusTask#annotations()} default.
     * @since 0.6.0
     * @author 王子豪
     */
    public static ToolAnnotations empty() {
        return new ToolAnnotations(null, List.of(), null, List.of());
    }

    /**
     * @return a fresh builder with all fields unset
     * @since 0.6.0
     * @author 王子豪
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable accumulator; {@link #build()} returns an immutable
     * {@link ToolAnnotations} via defensive copies.
     */
    public static final class Builder {
        private String hints;
        private final List<Map<String, Object>> examples = new ArrayList<>();
        private String resultShape;
        private final List<String> commonErrors = new ArrayList<>();

        private Builder() {
        }

        public Builder hints(String hints) {
            this.hints = hints;
            return this;
        }

        /**
         * Adds a single example argument payload. Insertion order is
         * preserved in the resulting list.
         *
         * <p>The argument map is copied via {@link LinkedHashMap} so the
         * caller may keep mutating their own map after this call without
         * affecting the built record.
         *
         * @param example argument payload; must not be null
         * @return this builder
         * @since 0.6.0
         * @author 王子豪
         */
        public Builder example(Map<String, Object> example) {
            Objects.requireNonNull(example, "example");
            this.examples.add(new LinkedHashMap<>(example));
            return this;
        }

        public Builder resultShape(String resultShape) {
            this.resultShape = resultShape;
            return this;
        }

        public Builder commonError(String commonError) {
            Objects.requireNonNull(commonError, "commonError");
            this.commonErrors.add(commonError);
            return this;
        }

        public ToolAnnotations build() {
            return new ToolAnnotations(hints, examples, resultShape, commonErrors);
        }
    }
}

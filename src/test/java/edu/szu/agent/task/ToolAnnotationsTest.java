package edu.szu.agent.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ToolAnnotations} — structured metadata carried by
 * each {@link CampusTask} alongside its {@code description()} and
 * {@code inputSchema()}.
 *
 * <p>The metadata flows to two consumers:
 * <ul>
 *   <li>{@link edu.szu.agent.mcp.ToolSchema} — promotes {@code examples}
 *       into the {@code tools/list} envelope.</li>
 *   <li>{@link ToolDocsGenerator} — renders {@code resultShape} and
 *       {@code commonErrors} into the human-facing markdown.</li>
 * </ul>
 *
 * <p>// 编程技术: record / Builder
 *
 * @since 0.5.0
 * @author 王子豪
 */
class ToolAnnotationsTest {

    @Test
    @DisplayName("empty() returns annotations with all four fields null/empty")
    void emptyIsEmpty() {
        ToolAnnotations empty = ToolAnnotations.empty();

        assertThat(empty.hints()).isNull();
        assertThat(empty.examples()).isEmpty();
        assertThat(empty.resultShape()).isNull();
        assertThat(empty.commonErrors()).isEmpty();
    }

    @Test
    @DisplayName("builder builds a fully-populated annotations record")
    void builderPopulatesAllFields() {
        Map<String, Object> ex1 = new LinkedHashMap<>();
        ex1.put("campus", "YUEHAI");
        ex1.put("sport", "TENNIS");

        ToolAnnotations ann = ToolAnnotations.builder()
            .hints("预约说明")
            .example(ex1)
            .resultShape("BookingResult")
            .commonError("缺少 timeSlot")
            .commonError("校区项目不匹配")
            .build();

        assertThat(ann.hints()).isEqualTo("预约说明");
        assertThat(ann.examples()).containsExactly(ex1);
        assertThat(ann.resultShape()).isEqualTo("BookingResult");
        assertThat(ann.commonErrors()).containsExactly("缺少 timeSlot", "校区项目不匹配");
    }

    @Test
    @DisplayName("builder without examples() returns empty list, not null")
    void builderDefaultsExamplesToEmpty() {
        ToolAnnotations ann = ToolAnnotations.builder()
            .hints("only hints")
            .build();

        assertThat(ann.examples()).isNotNull().isEmpty();
        assertThat(ann.commonErrors()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("builder accepts multiple examples in insertion order")
    void multipleExamplesPreserveOrder() {
        Map<String, Object> first = Map.of("query", "图书馆");
        Map<String, Object> second = Map.of("query", "食堂");

        ToolAnnotations ann = ToolAnnotations.builder()
            .example(first)
            .example(second)
            .build();

        assertThat(ann.examples()).containsExactly(first, second);
    }

    @Test
    @DisplayName("record equality is structural (same fields → equal)")
    void recordEquality() {
        ToolAnnotations a = ToolAnnotations.builder()
            .hints("x")
            .resultShape("R")
            .build();
        ToolAnnotations b = ToolAnnotations.builder()
            .hints("x")
            .resultShape("R")
            .build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}

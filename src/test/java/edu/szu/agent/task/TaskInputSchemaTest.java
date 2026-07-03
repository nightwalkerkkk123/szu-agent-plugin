package edu.szu.agent.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TaskInputSchema} — shared factory for the
 * {@code Map<String, Object>} shapes that {@link CampusTask#inputSchema()}
 * returns.
 *
 * <p>// 编程技术: 泛型 / Lambda / 不可变 Map
 *
 * @since 0.6.0
 * @author 王子豪
 */
class TaskInputSchemaTest {

    @Test
    @DisplayName("property(type, desc) yields a {type, description} fragment")
    void propertyBareMinimum() {
        Map<String, Object> p = TaskInputSchema.property("string", "学号");

        assertThat(p).containsEntry("type", "string");
        assertThat(p).containsEntry("description", "学号");
        // The fragment must not leak keys the caller did not request.
        assertThat(p).doesNotContainKeys("enum", "examples", "default", "format", "pattern");
    }

    @Test
    @DisplayName("property merges extras (examples, default, format, pattern)")
    void propertyWithExtras() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("examples", List.of("2023150090"));
        extras.put("default", 5);

        Map<String, Object> p = TaskInputSchema.property("string", "学号", extras);

        assertThat(p).containsEntry("type", "string");
        assertThat(p).containsEntry("description", "学号");
        assertThat(p).containsEntry("examples", List.of("2023150090"));
        assertThat(p).containsEntry("default", 5);
    }

    @Test
    @DisplayName("enumProperty always sets the enum constraint")
    void enumPropertyBasic() {
        Map<String, Object> p = TaskInputSchema.enumProperty(
            "校区", List.of("YUEHAI", "LIHU"));

        assertThat(p).containsEntry("type", "string");
        assertThat(p).containsEntry("description", "校区");
        assertThat(p).containsEntry("enum", List.of("YUEHAI", "LIHU"));
    }

    @Test
    @DisplayName("enumProperty merges extras without overwriting the enum")
    void enumPropertyPreservesEnum() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("examples", List.of("YUEHAI"));
        extras.put("default", "YUEHAI");

        Map<String, Object> p = TaskInputSchema.enumProperty(
            "校区", List.of("YUEHAI", "LIHU"), extras);

        assertThat(p).containsEntry("enum", List.of("YUEHAI", "LIHU"));
        assertThat(p).containsEntry("examples", List.of("YUEHAI"));
        assertThat(p).containsEntry("default", "YUEHAI");
    }

    @Test
    @DisplayName("requiredSingle / optionalOnly / schemaWithOptional keep their old shape")
    void legacyFactoriesUnchanged() {
        Map<String, Object> r = TaskInputSchema.requiredSingle("username", "学号");
        assertThat(r).containsEntry("type", "object");
        @SuppressWarnings("unchecked")
        List<String> rRequired = (List<String>) r.get("required");
        @SuppressWarnings("unchecked")
        Map<String, Object> rProps = (Map<String, Object>) r.get("properties");
        assertThat(rRequired).containsExactly("username");
        assertThat(rProps).containsOnlyKeys("username");

        Map<String, Object> o = TaskInputSchema.optionalOnly(
            Map.of("academicYear", Map.of("type", "string", "description", "学年")));
        assertThat(o).containsEntry("type", "object");
        assertThat(o).doesNotContainKey("required");
        @SuppressWarnings("unchecked")
        Map<String, Object> oProps = (Map<String, Object>) o.get("properties");
        assertThat(oProps).containsOnlyKeys("academicYear");

        Map<String, Object> w = TaskInputSchema.schemaWithOptional(
            "username", "学号",
            Map.of("category", Map.of("type", "string", "description", "分类")));
        assertThat(w).containsEntry("type", "object");
        @SuppressWarnings("unchecked")
        List<String> wRequired = (List<String>) w.get("required");
        @SuppressWarnings("unchecked")
        Map<String, Object> wProps = (Map<String, Object>) w.get("properties");
        assertThat(wRequired).containsExactly("username");
        assertThat(wProps).containsOnlyKeys("category", "username");
    }
}

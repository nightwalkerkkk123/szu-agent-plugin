package edu.szu.agent.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TaskInput} — the parameter bag passed to
 * {@link CampusTask} implementations.
 *
 * <p>// 编程技术: record / 不可变
 *
 * @since 0.1.0
 * @author 王子豪
 */
class TaskInputTest {

    @Test
    @DisplayName("get returns the value when present, null when absent")
    void getBasic() {
        TaskInput input = new TaskInput(Map.of("campus", "YUEHAI"));
        assertThat(input.get("campus")).isEqualTo("YUEHAI");
        assertThat(input.get("missing")).isNull();
    }

    @Test
    @DisplayName("require throws IllegalArgumentException for missing key")
    void requireMissing() {
        TaskInput input = new TaskInput(Map.of());
        assertThatThrownBy(() -> input.require("campus"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("campus");
    }

    @Test
    @DisplayName("require throws for blank value (empty string)")
    void requireBlank() {
        TaskInput input = new TaskInput(Map.of("campus", ""));
        assertThatThrownBy(() -> input.require("campus"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getInt parses valid int, returns default for absent or unparseable")
    void getIntVariants() {
        TaskInput input = new TaskInput(Map.of(
            "good", "5",
            "bad", "not-a-number"));

        assertThat(input.getInt("good", 99)).isEqualTo(5);
        assertThat(input.getInt("bad", 99)).isEqualTo(99);
        assertThat(input.getInt("missing", 99)).isEqualTo(99);
    }

    @Test
    @DisplayName("Defensive copy — mutating the source map does not affect TaskInput")
    void defensiveCopy() {
        Map<String, String> source = new HashMap<>();
        source.put("campus", "YUEHAI");
        TaskInput input = new TaskInput(source);

        source.put("campus", "LIHU");
        assertThat(input.get("campus")).isEqualTo("YUEHAI");
    }
}

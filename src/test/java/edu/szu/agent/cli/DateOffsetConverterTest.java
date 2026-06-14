package edu.szu.agent.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DateOffsetConverter}.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("DateOffsetConverter")
class DateOffsetConverterTest {

    private final DateOffsetConverter converter = new DateOffsetConverter();

    @ParameterizedTest
    @CsvSource({
        "0, 0",
        "today, 0",
        "TODAY, 0",
        "今天, 0",
        "  today  , 0",
        "  今天  , 0",
        "1, 1",
        "tomorrow, 1",
        "TOMORROW, 1",
        "明天, 1",
        "  tomorrow  , 1",
        "  明天  , 1"
    })
    @DisplayName("converts known aliases to day offset")
    void convertsKnownAliases(String input, int expected) {
        assertThat(converter.convert(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "yesterday", "后天", "now"})
    @DisplayName("rejects unsupported values")
    void rejectsUnsupportedValues(String input) {
        assertThatThrownBy(() -> converter.convert(input))
            .isInstanceOf(CommandLine.TypeConversionException.class)
            .hasMessageContaining(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("rejects blank values")
    void rejectsBlankValues(String input) {
        assertThatThrownBy(() -> converter.convert(input))
            .isInstanceOf(CommandLine.TypeConversionException.class)
            .hasMessageContaining("must not be blank");
    }
}

package edu.szu.agent.cli;

import picocli.CommandLine;

import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * Converter for the {@code --date} CLI option.
 *
 * <p>Accepts both numeric offsets and human-readable aliases:
 * <ul>
 *   <li>{@code 0}, {@code today}, {@code 今天} → today</li>
 *   <li>{@code 1}, {@code tomorrow}, {@code 明天} → tomorrow</li>
 * </ul>
 *
 * <p>// 编程技术: 枚举式 switch / 不可变 converter
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class DateOffsetConverter implements CommandLine.ITypeConverter<Integer> {

    @Override
    public Integer convert(String value) {
        requireNonNull(value, "date value must not be null");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new CommandLine.TypeConversionException(
                "Date offset must not be blank. "
                    + "Use 0/today/今天 for today or 1/tomorrow/明天 for tomorrow.");
        }
        return switch (normalized) {
            case "0", "today", "今天" -> 0;
            case "1", "tomorrow", "明天" -> 1;
            default -> throw new CommandLine.TypeConversionException(
                "Invalid date offset '" + value + "'. "
                    + "Use 0/today/今天 for today or 1/tomorrow/明天 for tomorrow.");
        };
    }
}

package edu.szu.agent.error;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Log masker — scrubs sensitive values from log messages.
 *
 * <p>Per ADR-0006 §二.6 and SECURITY.md §1.2: 11 patterns total
 * (8 sensitive field names + 1 env var pattern + 2 bare value regexes).
 * The ADR text mentions "12 patterns" but the canonical list in
 * {@code SECURITY.md} is 11; we follow the canonical list.
 *
 * <p>Usage:
 * <pre>{@code
 *   log.info("login attempt: {}", LogMasker.scrub("user=2023150090 pwd=xxx"));
 *   log.info("login attempt for user {}", LogMasker.fmt("%s", username));
 * }</pre>
 *
 * <p>Per ADR-0005 D2: archunit forbids business code from calling
 * {@code System.getenv("SZU_PASSWORD_*")} or from logging raw sensitive
 * string literals; {@code LogMasker} is the only sanctioned escape hatch.
 *
 * // 编程技术: 不可变 Pattern 集合(类加载时预编译) + Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class LogMasker {

    /** Sensitive field names with word boundaries to avoid false hits. */
    private static final List<Pattern> SENSITIVE_KEYS = List.of(
        Pattern.compile("(?i)password"),
        Pattern.compile("(?i)\\bpwd\\b"),
        Pattern.compile("(?i)secret"),
        Pattern.compile("(?i)token"),
        Pattern.compile("(?i)cookie"),
        Pattern.compile("(?i)session"),
        Pattern.compile("(?i)authorization"),
        Pattern.compile("(?i)bearer"),
        Pattern.compile("(?i)szu_password_\\d+")
    );

    /** Bare value regexes — 10-digit student IDs (start with 2) and 11-digit phone numbers. */
    private static final List<Pattern> SENSITIVE_VALUES = List.of(
        Pattern.compile("\\b2\\d{9}\\b"),
        Pattern.compile("\\b1[3-9]\\d{9}\\b")
    );

    private static final String REPLACEMENT = "***";

    private LogMasker() {
        // utility class, no instances
    }

    /**
     * Replaces every match (in either keys or values) with {@value #REPLACEMENT}.
     *
     * @param input the raw log message
     * @return the scrubbed message; returns the input unchanged if null
     */
    public static String scrub(String input) {
        if (input == null) {
            return null;
        }
        String result = input;
        for (Pattern p : SENSITIVE_KEYS) {
            result = p.matcher(result).replaceAll(REPLACEMENT);
        }
        for (Pattern p : SENSITIVE_VALUES) {
            result = p.matcher(result).replaceAll(REPLACEMENT);
        }
        return result;
    }

    /**
     * Convenience entry point for SLF4J parameter substitution:
     * applies {@link String#formatted} first, then scrubs.
     *
     * @param pattern SLF4J-style pattern with {@code %s} placeholders
     * @param args    substitution values
     * @return formatted and scrubbed message
     */
    public static String fmt(String pattern, Object... args) {
        String formatted = (args == null || args.length == 0)
            ? pattern
            : String.format(pattern, args);
        return scrub(formatted);
    }
}

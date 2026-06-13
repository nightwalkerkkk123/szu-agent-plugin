package edu.szu.agent.packaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static consistency check between {@code pom.xml} shade relocations and
 * {@code logback.xml} class references.
 *
 * <p>Maven Shade Plugin relocates {@code .class} files but does NOT rewrite
 * XML resource files. If {@code logback.xml} references a class under a
 * pattern that shade relocates, the runtime jar throws
 * {@link ClassNotFoundException} when the logger context tries to instantiate
 * the appender.
 *
 * <p>This test enumerates every {@code <pattern>} declared inside
 * {@code maven-shade-plugin}'s {@code <relocations>} block and asserts that
 * NONE of those patterns appear inside {@code src/main/resources/logback.xml}
 * as an attribute value (e.g. {@code class="ch.qos.logback...."}).
 *
 * <p>This catches the bug where {@code logback.xml} keeps a relocated
 * package as an appender class name.
 *
 * @since 0.1.0
 * @author 王子豪
 */
// 编程技术: Lambda + Stream
// Design Pattern: 无(纯静态一致性检查)
class LogbackShadeConsistencyTest {

    private static final Path POM = Path.of("pom.xml");
    private static final Path LOGBACK = Path.of("src/main/resources/logback.xml");

    /** Captures {@code <pattern>foo.bar</pattern>} inside maven-shade-plugin relocations. */
    private static final Pattern SHADE_PATTERN =
            Pattern.compile("<pattern>\\s*([\\w.]+)\\s*</pattern>");

    @Test
    @DisplayName("logback.xml does NOT reference any class under a shaded package")
    void logbackXmlDoesNotReferenceShadedPackages() throws IOException {
        String pomContent = Files.readString(POM);
        String logbackContent = Files.readString(LOGBACK);

        List<String> shadedPatterns = collectShadedPatterns(pomContent);
        assertThat(shadedPatterns)
                .as("pom.xml must declare at least one shade relocation; "
                        + "if you intentionally removed all shading, delete this test")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (String prefix : shadedPatterns) {
            // Search for <something class="prefix.X.Y...."> in logback.xml.
            // The class attribute value uses double quotes in our config.
            String needle = "\"" + prefix + ".";
            if (logbackContent.contains(needle)) {
                violations.add(prefix);
            }
        }

        assertThat(violations)
                .as("logback.xml references classes under shaded packages: %s. "
                        + "Either remove these <relocation> entries from pom.xml, "
                        + "or rewrite logback.xml to use the shaded class names "
                        + "(e.g. edu.szu.agent.shade.logback.core.rolling.RollingFileAppender).",
                        violations)
                .isEmpty();
    }

    /**
     * Extract every {@code <pattern>X</pattern>} inside the maven-shade-plugin
     * relocations block.
     */
    private static List<String> collectShadedPatterns(String pomContent) {
        // Narrow to the <relocations>...</relocations> block to avoid picking up
        // unrelated <pattern> tags elsewhere in the pom.
        int start = pomContent.indexOf("<relocations>");
        int end = pomContent.indexOf("</relocations>");
        if (start < 0 || end < 0 || end < start) {
            return List.of();
        }
        String block = pomContent.substring(start, end);
        Matcher m = SHADE_PATTERN.matcher(block);
        List<String> result = new ArrayList<>();
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }
}

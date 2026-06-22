package edu.szu.agent.architecture;

import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture tests mandated by ADR-0005 D2 and ADR-0006 §2.7.
 *
 * <p>These rules are enforced at test time so security-sensitive patterns
 * cannot re-enter the codebase without CI failing.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@AnalyzeClasses(packages = "edu.szu.agent")
class ArchitectureTest {

    private static final String ACCOUNT_RESOLVER = "edu.szu.agent.account.AccountResolver";
    private static final String MAIN = "edu.szu.agent.cli.Main";

    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
        "password", "pwd", "secret", "token", "cookie", "session",
        "authorization", "bearer", "szu_password"
    );

    /**
     * ADR-0005 D1: credential resolution must go through {@code AccountResolver}.
     * Business code must not read {@code System.getenv} directly.
     */
    @ArchTest
    static final ArchRule noDirectSystemGetenvExceptAccountResolver = noClasses()
        .that().haveNameNotMatching(ACCOUNT_RESOLVER)
        .should(new ArchCondition<>("not call System.getenv directly") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethod method : javaClass.getAllMethods()) {
                    for (Object access : method.getCallsFromSelf()) {
                        if (access instanceof JavaMethodCall call) {
                            AccessTarget.MethodCallTarget target = call.getTarget();
                            boolean isSystemGetenv = target.getOwner().isAssignableTo(System.class)
                                && "getenv".equals(target.getName());
                            if (isSystemGetenv) {
                                events.add(SimpleConditionEvent.violated(
                                    call,
                                    call.getDescription() + " in " + javaClass.getName()
                                        + " must route through " + ACCOUNT_RESOLVER
                                ));
                            }
                        }
                    }
                }
            }
        });

    /**
     * ADR-0005 D2 / ADR-0006 §2.7: no {@code System.out.println},
     * {@code System.err.println} or {@code Throwable.printStackTrace} outside
     * {@code Main.main}. stdout is reserved for the CLI's deliberate skeleton
     * greeting and JSON output, both owned by the entry point.
     */
    @ArchTest
    static final ArchRule noStdoutOrStackTraceExceptMainMain = noClasses()
        .should(new ArchCondition<>("not use System.out.println, System.err.println or printStackTrace outside Main.main") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethod method : javaClass.getAllMethods()) {
                    boolean isMainMain = MAIN.equals(javaClass.getName()) && "main".equals(method.getName());
                    for (Object access : method.getCallsFromSelf()) {
                        if (access instanceof JavaMethodCall call) {
                            AccessTarget.MethodCallTarget target = call.getTarget();
                            if (isForbiddenOutputMethod(target) && !isMainMain) {
                                events.add(SimpleConditionEvent.violated(
                                    call,
                                    call.getDescription() + " in " + javaClass.getName()
                                        + "." + method.getName() + " is not allowed outside Main.main"
                                ));
                            }
                        }
                    }
                }
            }

            private boolean isForbiddenOutputMethod(AccessTarget.MethodCallTarget target) {
                String ownerName = target.getOwner().getName();
                String methodName = target.getName();
                if (("java.io.PrintStream".equals(ownerName) || "java.lang.System".equals(ownerName))
                    && ("println".equals(methodName) || "print".equals(methodName))) {
                    return true;
                }
                return "java.lang.Throwable".equals(ownerName)
                    && "printStackTrace".equals(methodName);
            }
        });

    /**
     * ADR-0005 D2: logger message string literals must not contain sensitive
     * keywords. This scans the Java source (rather than bytecode) because
     * ArchUnit does not expose method-call parameter values.
     *
     * <p>The rule ignores:
     * <ul>
     *   <li>{@code LogMasker.java} itself (it defines the patterns/examples)</li>
     *   <li>comment and Javadoc lines</li>
     *   <li>string literals that are arguments to {@code LogMasker.scrub} or
     *       {@code LogMasker.fmt}</li>
     * </ul>
     */
    @ArchTest
    static void noSensitiveKeywordsInLoggerMessageLiterals(JavaClasses classes) {
        Path srcMain = Paths.get("src/main/java").toAbsolutePath().normalize();
        if (!Files.isDirectory(srcMain)) {
            // When running from a different working directory we cannot scan sources;
            // fail loudly rather than silently skip the rule.
            throw new AssertionError("Cannot scan source files; working directory is not project root: " + Paths.get("").toAbsolutePath());
        }

        Pattern logCallPattern = Pattern.compile(
            "\\.(info|warn|error|debug|trace)\\s*\\(\\s*\"([^\"]*)\"");
        Pattern sensitivePattern = Pattern.compile(
            "(?i)(" + String.join("|", SENSITIVE_KEYWORDS) + ")");
        Pattern commentOrJavadoc = Pattern.compile("^\\s*(//|\\*|/\\*\\*)");

        List<String> violations;
        try (Stream<Path> files = Files.walk(srcMain)) {
            violations = files
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.getFileName().toString().equals("LogMasker.java"))
                .flatMap(p -> {
                    List<String> lines;
                    try {
                        lines = Files.readAllLines(p);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read " + p, e);
                    }
                    String relative = srcMain.relativize(p).toString();
                    return lines.stream()
                        .filter(line -> !commentOrJavadoc.matcher(line).find())
                        .map(line -> {
                            Matcher m = logCallPattern.matcher(line);
                            if (m.find()) {
                                String literal = m.group(2);
                                if (sensitivePattern.matcher(literal).find()) {
                                    return relative + ": " + line.trim();
                                }
                            }
                            return null;
                        })
                        .filter(java.util.Objects::nonNull);
                })
                .toList();
        } catch (IOException e) {
            throw new AssertionError("Failed to walk source tree", e);
        }

        if (!violations.isEmpty()) {
            throw new AssertionError(
                "Logger message literals contain sensitive keywords:\n" + String.join("\n", violations));
        }
    }
}

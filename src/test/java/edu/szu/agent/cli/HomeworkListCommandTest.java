package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HomeworkListCommand}.
 *
 * <p>Mirrors {@link VenueCommandTest}: unit helpers + CLI paths that do not
 * need a real browser.
 *
 * @since 0.1.0
 * @author 王子豪
 */
class HomeworkListCommandTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;

    @BeforeEach
    void resetStreams() {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
    }

    private int runCli(String... args) {
        return new CommandLine(new Main())
            .setOut(new PrintWriter(out, true))
            .setErr(new PrintWriter(err, true))
            .execute(args);
    }

    private JsonNode parseStdout() throws Exception {
        return MAPPER.readTree(out.toString().trim());
    }

    // =========================================================================
    // UNIT LAYER
    // =========================================================================

    @Nested
    @DisplayName("exitCodeFor — ErrorCode → exit code mapping")
    class ExitCodeForUnit {

        @ParameterizedTest(name = "[{index}] {0} → exit {1}")
        @CsvSource({
            "INVALID_REQUEST, 2",
            "HOMEWORK_LIST_EMPTY, 2",
            "HOMEWORK_PAGE_LOAD_FAILED, 1",
            "ELEMENT_NOT_FOUND, 1",
            "BROWSER_CRASH, 4",
            "PASSWORD_INCORRECT, 3"
        })
        void exitCodeMatchesSeverity(String codeName, int expectedExit) {
            assertThat(CommandOutput.exitCodeFor(ErrorCode.valueOf(codeName)))
                .isEqualTo(expectedExit);
        }

        @Test
        @DisplayName("Every ErrorCode maps to a valid exit code 1-4")
        void everyErrorCodeMapsToValidExit() {
            for (ErrorCode code : ErrorCode.values()) {
                int exit = CommandOutput.exitCodeFor(code);
                assertThat(exit)
                    .as("ErrorCode.%s mapped to exit %d", code, exit)
                    .isBetween(1, 4);
            }
        }
    }

    // =========================================================================
    // CLI LAYER
    // =========================================================================

    @Nested
    @DisplayName("--env-file resolution (CLI layer)")
    class EnvFileCli {

        @Test
        @DisplayName("--env-file pointing to nonexistent path → exit 3 + INVALID_REQUEST")
        void envFileNotFound() throws Exception {
            int exit = runCli("homework", "list", "--username", "2023150090",
                "--env-file", "/path/that/does/not/exist/.env");

            assertThat(exit).isEqualTo(3);
            JsonNode root = parseStdout();
            assertThat(root.get("success").asBoolean()).isFalse();
            assertThat(root.get("errorCode").asText()).isEqualTo("INVALID_REQUEST");
            assertThat(root.get("errorMessage").asText()).containsIgnoringCase("env file");
        }

        @Test
        @DisplayName("--env-file exists but lacks SZU_PASSWORD_<id> → CREDENTIAL_NOT_FOUND")
        void envFileWithoutPassword(@TempDir Path tmp) throws Exception {
            Path envFile = tmp.resolve(".env");
            Files.writeString(envFile, "# empty env\n");

            int exit = runCli("homework", "list", "--username", "2099999999",
                "--env-file", envFile.toString());

            assertThat(exit).isEqualTo(3);
            JsonNode root = parseStdout();
            assertThat(root.get("success").asBoolean()).isFalse();
            assertThat(root.get("errorCode").asText()).isEqualTo("CREDENTIAL_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("JSON envelope shape (CLI layer)")
    class JsonShape {

        @Test
        @DisplayName("Failure JSON has all 6 fields per PRD §5.2 schema")
        void failureJsonHasAllFields() throws Exception {
            int exit = runCli("homework", "list", "--username", "2023150090",
                "--env-file", "/nope.env");

            assertThat(exit).isEqualTo(3);
            JsonNode root = parseStdout();
            assertThat(root.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                    "success", "data", "errorCode", "errorMessage",
                    "traceId", "elapsedMs");
            assertThat(root.get("data").isNull()).isTrue();
        }

        @Test
        @DisplayName("Dry-run JSON has data array with homework fields")
        void successJsonShape() throws Exception {
            int exit = runCli("homework", "list", "--dry-run");

            assertThat(exit).isEqualTo(0);
            JsonNode root = parseStdout();
            assertThat(root.get("success").asBoolean()).isTrue();
            assertThat(root.get("data").isArray()).isTrue();
            JsonNode item = root.get("data").get(0);
            assertThat(item.get("homeworkId").asText()).isEqualTo("dry-run-stub");
            assertThat(item.get("courseName").asText()).isEqualTo("dry-run-course");
            assertThat(item.get("title").asText()).isEqualTo("dry-run-homework");
            assertThat(item.get("deadline").asText()).isEqualTo("2099.12.31 23:59");
            assertThat(item.get("status").asText()).isEqualTo("待提交");
        }
    }

    @Nested
    @DisplayName("--format human (CLI layer)")
    class HumanFormat {

        @Test
        @DisplayName("Dry-run human output includes count and item lines")
        void humanFormatSuccessFields() {
            int exit = runCli("homework", "list", "--dry-run", "--format", "human");

            assertThat(exit).isEqualTo(0);
            String text = out.toString();
            assertThat(text).contains("Success: true");
            assertThat(text).contains("Homework count: 1");
            assertThat(text).contains("dry-run-course: dry-run-homework (待提交)");
            assertThat(text).contains("Trace: ");
            assertThat(text).contains("Elapsed: ").contains("ms");
        }

        @Test
        @DisplayName("Failure human output includes Error / Detail lines")
        void humanFormatFailureFields() {
            int exit = runCli("homework", "list", "--username", "2023150090",
                "--env-file", "/nope.env", "--format", "human");

            assertThat(exit).isEqualTo(3);
            String text = out.toString();
            assertThat(text).contains("Success: false");
            assertThat(text).contains("Error: INVALID_REQUEST");
            assertThat(text).contains("Detail: ");
        }
    }
}

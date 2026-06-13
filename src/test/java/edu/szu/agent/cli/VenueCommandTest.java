package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.domain.TimeSlot;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Targeted tests for {@link VenueCommand}, filling the coverage gaps that
 * {@link BookingCommandTest} does not reach.
 *
 * <p>Two test layers:
 * <ul>
 *   <li><b>Unit layer</b> — directly invokes package-private static helpers
 *       ({@code parseTimeSlot}, {@code exitCodeFor}). Covers all branches
 *       without depending on credential resolution or the browser.</li>
 *   <li><b>CLI layer</b> — runs picocli end-to-end and asserts JSON envelope
 *       shape, format flag, and {@code --env-file} resolution. Limited to
 *       paths that don't need a real browser.</li>
 * </ul>
 *
 * <p>设计模式: Command (picocli) — CLI tests run the full picocli dispatch.
 * <br>编程技术: Lambda / @Nested / @ParameterizedTest / @TempDir
 *
 * @since 0.1.0
 * @author 王子豪
 */
class VenueCommandTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;

    @BeforeEach
    void resetStreams() {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
    }

    /** Run picocli with stdout/stderr captured. */
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
    // UNIT LAYER — direct method invocation, no CLI / no AccountResolver
    // =========================================================================

    @Nested
    @DisplayName("parseTimeSlot — pure validation")
    class ParseTimeSlotUnit {

        @Test
        @DisplayName("Valid 19:00-20:00 → TimeSlot(start=19:00, end=20:00)")
        void validSlot() {
            TimeSlot slot = VenueCommand.parseTimeSlot("19:00-20:00");
            assertThat(slot.start()).isEqualTo("19:00");
            assertThat(slot.end()).isEqualTo("20:00");
        }

        @Test
        @DisplayName("Whitespace around delimiter is trimmed")
        void slotTrimmedAroundDash() {
            TimeSlot slot = VenueCommand.parseTimeSlot("19:00 - 20:00");
            assertThat(slot.start()).isEqualTo("19:00");
            assertThat(slot.end()).isEqualTo("20:00");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → IllegalArgumentException")
        @ValueSource(strings = {"19:0020:00", "no-dash-here-but-three-dashes-and-equal", ""})
        @DisplayName("Slots without a recognizable dash split or with equal halves are rejected")
        void rejectsMalformed(String raw) {
            // parseTimeSlot only checks that the input contains "-".
            // The TimeSlot record then enforces non-blank + start < end (lex).
            // These inputs fail at one of those gates.
            assertThatThrownBy(() -> VenueCommand.parseTimeSlot(raw))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Null input → IllegalArgumentException with hint")
        void nullRejected() {
            assertThatThrownBy(() -> VenueCommand.parseTimeSlot(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HH:mm-HH:mm");
        }

        @Test
        @DisplayName("Equal start/end → IllegalArgumentException from TimeSlot record")
        void equalEndpointsRejected() {
            assertThatThrownBy(() -> VenueCommand.parseTimeSlot("19:00-19:00"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Start > end (lexicographic) → IllegalArgumentException")
        void startAfterEndRejected() {
            assertThatThrownBy(() -> VenueCommand.parseTimeSlot("20:00-19:00"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("exitCodeFor — ErrorCode → exit code mapping")
    class ExitCodeForUnit {

        // Per VenueCommand.exitCodeFor switch: severity drives the code,
        // BROWSER_CRASH is the only HIGH→4 special case.

        @ParameterizedTest(name = "[{index}] {0} → exit {1}")
        @CsvSource({
                "INVALID_REQUEST, 2",         // LOW → param error
                "VENUE_OCCUPIED, 1",          // MEDIUM → business
                "NO_AVAILABLE_VENUE, 1",      // MEDIUM → business
                "ELEMENT_NOT_FOUND, 1",       // MEDIUM → business
                "NETWORK_TIMEOUT, 1",         // MEDIUM → business
                "BROWSER_CRASH, 4",           // HIGH special case
                "LOGIN_PAGE_LOAD_FAILED, 1",  // HIGH → business
                "CAS_REDIRECT_TIMEOUT, 1",    // HIGH → business
                "CAPTCHA_REQUIRED, 1",        // HIGH → business
                "UNKNOWN, 1",                 // HIGH default
                "PASSWORD_INCORRECT, 3",      // CRITICAL → env/account
                "ACCOUNT_LOCKED, 3"           // CRITICAL → env/account
        })
        void exitCodeMatchesSeverity(String codeName, int expectedExit) {
            assertThat(VenueCommand.exitCodeFor(ErrorCode.valueOf(codeName)))
                    .isEqualTo(expectedExit);
        }

        @Test
        @DisplayName("Every ErrorCode maps to one of the documented exit codes 0-4")
        void everyErrorCodeMapsToValidExit() {
            for (ErrorCode code : ErrorCode.values()) {
                int exit = VenueCommand.exitCodeFor(code);
                assertThat(exit)
                        .as("ErrorCode.%s mapped to exit %d, expected 1-4 (0=success not reachable here)",
                                code, exit)
                        .isBetween(1, 4);
            }
        }
    }

    // =========================================================================
    // CLI LAYER — picocli end-to-end, paths that don't need a browser
    // =========================================================================

    @Nested
    @DisplayName("--env-file resolution (CLI layer)")
    class EnvFileCli {

        @Test
        @DisplayName("--env-file pointing to nonexistent path → exit 3 + INVALID_REQUEST")
        void envFileNotFound() throws Exception {
            int exit = runCli("booking", "venue",
                    "--campus", "YUEHAI", "--sport", "TENNIS",
                    "--time-slot", "19:00-20:00",
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

            int exit = runCli("booking", "venue",
                    "--username", "2099999999",
                    "--campus", "YUEHAI", "--sport", "TENNIS",
                    "--time-slot", "19:00-20:00",
                    "--env-file", envFile.toString());

            assertThat(exit).isEqualTo(3);
            JsonNode root = parseStdout();
            assertThat(root.get("success").asBoolean()).isFalse();
            assertThat(root.get("errorCode").asText()).isEqualTo("CREDENTIAL_NOT_FOUND");
            assertThat(root.get("errorMessage").asText()).containsIgnoringCase("2099999999");
        }
    }

    @Nested
    @DisplayName("JSON envelope shape (CLI layer)")
    class JsonShape {

        @Test
        @DisplayName("Failure JSON has all 6 fields per PRD §5.2 schema")
        void failureJsonHasAllFields() throws Exception {
            // Trigger failure via missing env-file (deterministic, no browser needed)
            int exit = runCli("booking", "venue",
                    "--campus", "YUEHAI", "--sport", "TENNIS",
                    "--time-slot", "19:00-20:00",
                    "--env-file", "/nope.env");

            assertThat(exit).isEqualTo(3);
            JsonNode root = parseStdout();
            // Per PRD §5.2: success / data / errorCode / errorMessage / traceId / elapsedMs
            assertThat(root.fieldNames())
                    .toIterable()
                    .containsExactlyInAnyOrder(
                            "success", "data", "errorCode", "errorMessage",
                            "traceId", "elapsedMs");
            assertThat(root.get("data").isNull()).isTrue();
            assertThat(root.get("traceId").asText()).matches("\\d{8}-[A-Z0-9]{6}");
            assertThat(root.get("elapsedMs").asLong()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Dry-run JSON has data with venueName and confirmation")
        void successJsonShape() throws Exception {
            int exit = runCli("booking", "venue",
                    "--campus", "YUEHAI", "--sport", "TENNIS",
                    "--time-slot", "19:00-20:00",
                    "--dry-run");

            assertThat(exit).isEqualTo(0);
            JsonNode root = parseStdout();
            assertThat(root.get("success").asBoolean()).isTrue();
            assertThat(root.get("data").isObject()).isTrue();
            assertThat(root.get("data").get("venueName").asText()).isEqualTo("dry-run-stub");
            assertThat(root.get("data").get("confirmation").asText()).isEqualTo("DRY-RUN");
            assertThat(root.get("errorCode").isNull()).isTrue();
            assertThat(root.get("errorMessage").isNull()).isTrue();
        }
    }

    @Nested
    @DisplayName("--format human (CLI layer)")
    class HumanFormat {

        @Test
        @DisplayName("Dry-run human output includes Venue / Confirmation / Trace / Elapsed")
        void humanFormatSuccessFields() {
            int exit = runCli("booking", "venue",
                    "--campus", "YUEHAI", "--sport", "TENNIS",
                    "--time-slot", "19:00-20:00",
                    "--dry-run", "--format", "human");

            assertThat(exit).isEqualTo(0);
            String text = out.toString();
            assertThat(text).contains("Success: true");
            assertThat(text).contains("Venue: dry-run-stub");
            assertThat(text).contains("Confirmation: DRY-RUN");
            assertThat(text).contains("Trace: ");
            assertThat(text).contains("Elapsed: ").contains("ms");
        }

        @Test
        @DisplayName("Failure human output includes Error / Detail lines")
        void humanFormatFailureFields() {
            int exit = runCli("booking", "venue",
                    "--campus", "YUEHAI", "--sport", "TENNIS",
                    "--time-slot", "19:00-20:00",
                    "--env-file", "/nope.env",
                    "--format", "human");

            assertThat(exit).isEqualTo(3);
            String text = out.toString();
            assertThat(text).contains("Success: false");
            assertThat(text).contains("Error: INVALID_REQUEST");
            assertThat(text).contains("Detail: ");
            assertThat(text).contains("Trace: ");
        }

        @Test
        @DisplayName("Format JSON uppercase or mixed case still emits JSON")
        void formatJsonCaseInsensitive() throws Exception {
            int exit = runCli("booking", "venue",
                    "--campus", "YUEHAI", "--sport", "TENNIS",
                    "--time-slot", "19:00-20:00",
                    "--dry-run", "--format", "JSON");

            assertThat(exit).isEqualTo(0);
            // JSON casing isn't validated against an enum — JSON is the default
            // when the value isn't "human" exactly. Verify output is valid JSON.
            assertThat(out.toString().trim()).startsWith("{");
            MAPPER.readTree(out.toString());
        }
    }

    // =========================================================================
    // CLI LAYER — date offset / preferred venue parsing
    // =========================================================================

    @Test
    @DisplayName("--date 1 (tomorrow) is accepted; dry-run short-circuits before use")
    void dateOffsetTomorrowAccepted() {
        int exit = runCli("booking", "venue",
                "--campus", "YUEHAI", "--sport", "TENNIS",
                "--time-slot", "19:00-20:00",
                "--date", "1",
                "--dry-run");

        assertThat(exit).isEqualTo(0);
    }

    @Test
    @DisplayName("--preferred-venue 3 is accepted; dry-run short-circuits")
    void preferredVenueThreeAccepted() {
        int exit = runCli("booking", "venue",
                "--campus", "YUEHAI", "--sport", "TENNIS",
                "--time-slot", "19:00-20:00",
                "--preferred-venue", "3",
                "--dry-run");

        assertThat(exit).isEqualTo(0);
    }
}

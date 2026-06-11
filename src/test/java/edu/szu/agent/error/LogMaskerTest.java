package edu.szu.agent.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LogMasker}.
 *
 * <p>Per ADR-0006 §二.6 and SECURITY.md §1.2: LogMasker scrubs
 * <em>patterns</em> (field names, env var names, bare value regexes).
 * It does NOT parse "key=value" pairs — values that don't match a
 * value pattern are left as-is. Callers should avoid logging
 * raw credential values directly.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("LogMasker")
class LogMaskerTest {

    @Test
    @DisplayName("scrubs 10-digit student IDs starting with 2")
    void scrubsStudentId() {
        String result = LogMasker.scrub("logging in as 2023150090");

        assertThat(result).isEqualTo("logging in as ***");
    }

    @Test
    @DisplayName("scrubs 11-digit phone numbers starting with 1[3-9]")
    void scrubsPhoneNumber() {
        String result = LogMasker.scrub("call 13800138000 now");

        assertThat(result).isEqualTo("call *** now");
    }

    @Test
    @DisplayName("scrubs the 'password' key name in literals (value left as-is by design)")
    void scrubsPasswordKey() {
        String result = LogMasker.scrub("password=hunter2");

        // Key "password" matches the SENSITIVE_KEYS pattern → replaced with ***
        // Value "hunter2" is not a recognized pattern → left as-is (by design)
        assertThat(result).doesNotContain("password");
        assertThat(result).contains("***");
        assertThat(result).contains("hunter2");
    }

    @Test
    @DisplayName("scrubs the 'token' key name in literals (value left as-is by design)")
    void scrubsTokenKey() {
        String result = LogMasker.scrub("auth token=abc.def.ghi");

        // Key "token" → ***; value "abc.def.ghi" not matched → left as-is
        assertThat(result).doesNotContain("token");
        assertThat(result).contains("abc.def.ghi");
    }

    @Test
    @DisplayName("scrubs SZU_PASSWORD_XXXX env var name in literals")
    void scrubsSzPasswordEnvName() {
        String result = LogMasker.scrub("env SZU_PASSWORD_0090=foo");

        assertThat(result).doesNotContain("SZU_PASSWORD_0090");
        assertThat(result).contains("foo"); // value not scrubbed by env-var pattern
    }

    @Test
    @DisplayName("does not match short numbers (e.g. 12345)")
    void leavesShortNumbersAlone() {
        String result = LogMasker.scrub("order 12345 processed");

        assertThat(result).isEqualTo("order 12345 processed");
    }

    @Test
    @DisplayName("does not match a 10-digit number not starting with 2")
    void leavesOtherTenDigitNumbersAlone() {
        String result = LogMasker.scrub("ref 9123456789");

        assertThat(result).isEqualTo("ref 9123456789");
    }

    @Test
    @DisplayName("handles null input")
    void handlesNull() {
        assertThat(LogMasker.scrub(null)).isNull();
    }

    @Test
    @DisplayName("scrubs multiple distinct patterns in one message")
    void scrubsMultiplePatterns() {
        // Mix of: 10-digit student ID, 11-digit phone, sensitive key
        String result = LogMasker.scrub(
            "user 2023150090 phone 13800138000 session=abc");

        assertThat(result).doesNotContain("2023150090");
        assertThat(result).doesNotContain("13800138000");
        assertThat(result).doesNotContain("session");
    }

    @Test
    @DisplayName("fmt() formats then scrubs")
    void fmtFormatsAndScrubs() {
        String result = LogMasker.fmt(
            "user %s phone %s", "2023150090", "13800138000");

        assertThat(result).doesNotContain("2023150090");
        assertThat(result).doesNotContain("13800138000");
    }

    @Test
    @DisplayName("fmt() with no args just scrubs the pattern string")
    void fmtWithNoArgsJustScrubs() {
        String result = LogMasker.fmt("token=abc");

        // Key "token" is scrubbed; value "abc" left as-is
        assertThat(result).doesNotContain("token");
        assertThat(result).contains("***");
        assertThat(result).contains("abc");
    }
}

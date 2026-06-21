package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CommandOutput")
class CommandOutputTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("formatResult JSON 包含 6 字段 envelope")
    void jsonEnvelopeHasSixFields() throws Exception {
        String out = CommandOutput.formatResult(true, JSON.nullNode(), null, null,
            "trace-1", 42L, "json");
        JsonNode root = JSON.readTree(out);
        assertThat(root.get("success").asBoolean()).isTrue();
        assertThat(root.get("data").isNull()).isTrue();
        assertThat(root.get("errorCode").isNull()).isTrue();
        assertThat(root.get("errorMessage").isNull()).isTrue();
        assertThat(root.get("traceId").asText()).isEqualTo("trace-1");
        assertThat(root.get("elapsedMs").asLong()).isEqualTo(42L);
    }

    @Test
    @DisplayName("formatResult 失败 envelope 填 errorCode/errorMessage")
    void failureEnvelope() throws Exception {
        String out = CommandOutput.formatResult(false, null, "NETWORK_TIMEOUT",
            "upstream down", "trace-2", 100L, "json");
        JsonNode root = JSON.readTree(out);
        assertThat(root.get("success").asBoolean()).isFalse();
        assertThat(root.get("errorCode").asText()).isEqualTo("NETWORK_TIMEOUT");
        assertThat(root.get("errorMessage").asText()).isEqualTo("upstream down");
        assertThat(root.get("traceId").asText()).isEqualTo("trace-2");
    }

    @Test
    @DisplayName("formatResult human 模式返回非 JSON 文本")
    void humanFormat() {
        String out = CommandOutput.formatResult(true, JSON.nullNode(), null, null,
            "trace-h", 0L, "human");
        assertThat(out).contains("Success: true");
        assertThat(out).contains("Trace: trace-h");
        assertThat(out).contains("Elapsed: 0ms");
        // Must NOT be JSON
        assertThat(out.trim().charAt(0)).isNotEqualTo('{');
    }

    @Test
    @DisplayName("formatHuman Object 数据 key-value 行")
    void humanObjectData() {
        var data = JSON.createObjectNode();
        data.put("foo", "bar");
        data.put("n", 7);
        String out = CommandOutput.formatHuman(true, data, null, null, "t", 0L);
        assertThat(out).contains("foo: bar");
        assertThat(out).contains("n: 7");
    }

    @Test
    @DisplayName("formatHuman Array 数据 count 行")
    void humanArrayData() {
        var data = JSON.createArrayNode();
        data.add("a");
        data.add("b");
        data.add("c");
        String out = CommandOutput.formatHuman(true, data, null, null, "t", 0L);
        assertThat(out).contains("Count: 3");
    }

    @Test
    @DisplayName("formatHuman 错误 envelope")
    void humanError() {
        String out = CommandOutput.formatHuman(false, null, "X", "msg", "t", 0L);
        assertThat(out).contains("Success: false");
        assertThat(out).contains("Error: X");
        assertThat(out).contains("Detail: msg");
    }

    @Test
    @DisplayName("exitCodeFor LOW severity → 2")
    void exitLow() {
        assertThat(CommandOutput.exitCodeFor(ErrorCode.HOMEWORK_LIST_EMPTY)).isEqualTo(2);
        assertThat(CommandOutput.exitCodeFor(ErrorCode.SCHEDULE_EMPTY)).isEqualTo(2);
    }

    @Test
    @DisplayName("exitCodeFor MEDIUM severity → 1")
    void exitMedium() {
        assertThat(CommandOutput.exitCodeFor(ErrorCode.VENUE_OCCUPIED)).isEqualTo(1);
        assertThat(CommandOutput.exitCodeFor(ErrorCode.SCHEDULE_PARSE_FAILED)).isEqualTo(1);
    }

    @Test
    @DisplayName("exitCodeFor HIGH severity 非 BROWSER_CRASH → 1")
    void exitHighNonBrowser() {
        assertThat(CommandOutput.exitCodeFor(ErrorCode.HOMEWORK_PAGE_LOAD_FAILED)).isEqualTo(1);
        assertThat(CommandOutput.exitCodeFor(ErrorCode.SCHEDULE_PAGE_LOAD_FAILED)).isEqualTo(1);
    }

    @Test
    @DisplayName("exitCodeFor HIGH severity BROWSER_CRASH → 4")
    void exitHighBrowserCrash() {
        assertThat(CommandOutput.exitCodeFor(ErrorCode.BROWSER_CRASH)).isEqualTo(4);
    }

    @Test
    @DisplayName("exitCodeFor CRITICAL severity → 3")
    void exitCritical() {
        assertThat(CommandOutput.exitCodeFor(ErrorCode.PASSWORD_INCORRECT)).isEqualTo(3);
        assertThat(CommandOutput.exitCodeFor(ErrorCode.ACCOUNT_LOCKED)).isEqualTo(3);
    }
}

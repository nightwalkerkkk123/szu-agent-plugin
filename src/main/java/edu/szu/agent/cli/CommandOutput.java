package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.error.ErrorCode;

/**
 * Shared CLI output formatter and exit-code mapper.
 *
 * <p>Per ADR-0009 D7: extracted from {@code HomeworkListCommand} and
 * {@code VenueCommand} so all CLI subcommands emit the same JSON envelope
 * and exit-code mapping. Adding a new subcommand (e.g. {@code schedule list})
 * should reuse {@link #formatResult} and {@link #exitCodeFor} rather than
 * duplicating the boilerplate.
 *
 * <p>Output JSON schema (per PRD §5.2):
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": { ... },
 *   "errorCode": null,
 *   "errorMessage": null,
 *   "traceId": "20260612-ABC123",
 *   "elapsedMs": 4321
 * }
 * }</pre>
 *
 * <p>Exit code mapping (per PRD §5.3):
 * <ul>
 *   <li>{@code 0} — success</li>
 *   <li>{@code 1} — business failure (MEDIUM / HIGH severity, non-browser)</li>
 *   <li>{@code 2} — param error (LOW severity)</li>
 *   <li>{@code 3} — env / account error (CRITICAL severity)</li>
 *   <li>{@code 4} — browser crash (HIGH + {@code BROWSER_CRASH})</li>
 * </ul>
 *
 * // 编程技术: 静态工具类 / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class CommandOutput {

    private static final ObjectMapper JSON = new ObjectMapper();

    private CommandOutput() {
    }

    /**
     * Serializes a result envelope to JSON. The {@code format} string may be
     * {@code "json"} (default) or {@code "human"}; human format is delegated
     * to {@link #formatHuman}.
     *
     * @param success      whether the operation succeeded
     * @param data         payload (may be {@code null} on failure)
     * @param errorCode    error code name, or {@code null} on success
     * @param errorMessage human-readable error detail, or {@code null} on success
     * @param traceId      trace identifier
     * @param elapsedMs    elapsed time in milliseconds
     * @param format       {@code "json"} or {@code "human"}
     * @return the formatted string (no trailing newline)
     * @since 0.1.0
     */
    public static String formatResult(boolean success, JsonNode data,
                                      String errorCode, String errorMessage,
                                      String traceId, long elapsedMs,
                                      String format) {
        if ("human".equalsIgnoreCase(format)) {
            return formatHuman(success, data, errorCode, errorMessage, traceId, elapsedMs);
        }
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("success", success);
            root.set("data", data != null ? data : JSON.nullNode());
            root.put("errorCode", errorCode);
            root.put("errorMessage", errorMessage);
            root.put("traceId", traceId);
            root.put("elapsedMs", elapsedMs);
            return JSON.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON output", e);
        }
    }

    /**
     * Human-friendly format. Falls back to a key/value layout for {@code data}
     * — concrete commands may override by passing a richer payload.
     *
     * @since 0.1.0
     */
    public static String formatHuman(boolean success, JsonNode data,
                                     String errorCode, String errorMessage,
                                     String traceId, long elapsedMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Success: ").append(success).append('\n');
        if (data != null && data.isObject()) {
            ObjectNode obj = (ObjectNode) data;
            obj.fields().forEachRemaining(entry -> {
                JsonNode v = entry.getValue();
                sb.append("  ").append(entry.getKey()).append(": ")
                    .append(v.isTextual() ? v.asText() : v.toString())
                    .append('\n');
            });
        } else if (data != null && data.isArray()) {
            sb.append("Count: ").append(data.size()).append('\n');
        }
        if (errorCode != null) {
            sb.append("Error: ").append(errorCode).append('\n');
            sb.append("Detail: ").append(errorMessage).append('\n');
        }
        sb.append("Trace: ").append(traceId).append('\n');
        sb.append("Elapsed: ").append(elapsedMs).append("ms");
        return sb.toString();
    }

    /**
     * Maps an {@link ErrorCode} to its corresponding process exit code.
     *
     * @param code the error code
     * @return the exit code per PRD §5.3
     * @since 0.1.0
     */
    public static int exitCodeFor(ErrorCode code) {
        return switch (code.severity()) {
            case LOW -> 2;
            case MEDIUM -> 1;
            case HIGH -> switch (code) {
                case BROWSER_CRASH -> 4;
                default -> 1;
            };
            case CRITICAL -> 3;
        };
    }
}

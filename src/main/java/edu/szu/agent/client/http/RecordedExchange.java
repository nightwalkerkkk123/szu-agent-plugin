package edu.szu.agent.client.http;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single HTTP request/response pair recorded during a browser session.
 *
 * <p>Password fields are redacted at capture time by
 * {@link HttpTrafficRecorder}; this record only stores safe metadata.
 *
 * <p>// Design Pattern: Data Transfer Object (immutable record)
 * // 编程技术: Record(Java 16+ 不可变值类型) / 泛型 / Stream
 *
 * @param timestamp      when the response completed
 * @param method         HTTP method (GET, POST, ...)
 * @param url            request URL
 * @param requestHeaders header names and values sent by the browser
 * @param requestBody    request body, with sensitive form fields masked
 * @param status         HTTP response status, or 0 if failed
 * @param responseHeaders response headers received
 * @param responseBodyMime MIME type hint from the response
 * @param responseBodySummary first 4 KB of response body (optional)
 * @since 0.6.0
 * @author 王子豪
 */
public record RecordedExchange(
    Instant timestamp,
    String method,
    URI url,
    Map<String, List<String>> requestHeaders,
    String requestBody,
    int status,
    Map<String, List<String>> responseHeaders,
    String responseBodyMime,
    String responseBodySummary) {

    /**
     * Compact constructor with non-null guards.
     */
    public RecordedExchange {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(requestHeaders, "requestHeaders");
        Objects.requireNonNull(responseHeaders, "responseHeaders");
    }

    /**
     * Convenience predicate: true for POST/PUT/PATCH with a non-empty body.
     */
    public boolean hasBody() {
        return requestBody != null && !requestBody.isBlank();
    }

    /**
     * Convenience predicate: true when the status is in the 2xx range.
     */
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}

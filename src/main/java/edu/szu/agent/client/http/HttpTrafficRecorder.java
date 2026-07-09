package edu.szu.agent.client.http;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.json.JsonMappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Records HTTP request/response metadata from a Playwright {@link Page}
 * without persisting credentials.
 *
 * <p>Used to reverse-engineer SZU intranet login and API contracts so that
 * future implementations can switch from browser automation to direct HTTP
 * requests. Sensitive form fields (password, etc.) are masked at capture time.
 *
 * <p>Attach before navigation, detach after the flow completes:
 * <pre>
 *   Page page = browser.newPage();
 *   HttpTrafficRecorder recorder = HttpTrafficRecorder.attach(page);
 *   page.navigate("https://auth.szu.edu.cn/cas/login");
 *   // fill form, click submit ...
 *   List&lt;RecordedExchange&gt; exchanges = recorder.exchanges();
 * </pre>
 *
 * <p>// Design Pattern: Observer (Playwright request/response events)
 * // 编程技术: Lambda / ConcurrentHashMap / Stream / 正则替换
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class HttpTrafficRecorder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HttpTrafficRecorder.class);

    /** Maximum response body bytes to keep in memory per exchange. */
    public static final int MAX_BODY_SUMMARY_BYTES = 4096;

    /** Form fields whose values must never be stored. */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password", "pwd", "pass", "passwd", "token", "secret",
        "credential", "credentials", "authcode", "verificationcode"
    );

    /** Matches {@code key=value} pairs in {@code application/x-www-form-urlencoded}. */
    private static final Pattern FORM_PAIR = Pattern.compile("(^|&)([^=&]+)=([^&]*)");

    private final Page page;
    private final List<RecordedExchange> exchanges;
    private final Consumer<Request> requestHandler;
    private final Consumer<Response> responseHandler;
    private final Map<String, PendingRequest> pending;

    private HttpTrafficRecorder(Page page) {
        this.page = Objects.requireNonNull(page, "page");
        this.exchanges = Collections.synchronizedList(new ArrayList<>());
        this.pending = new ConcurrentHashMap<>();
        this.requestHandler = this::onRequest;
        this.responseHandler = this::onResponse;
        page.onRequest(requestHandler);
        page.onResponse(responseHandler);
        log.info("HttpTrafficRecorder attached to page");
    }

    /**
     * Attaches a new recorder to the given page.
     *
     * @param page the Playwright page to observe
     * @return a recorder; must be closed to detach listeners
     */
    public static HttpTrafficRecorder attach(Page page) {
        return new HttpTrafficRecorder(page);
    }

    /**
     * Returns all completed exchanges captured so far.
     *
     * @return an unmodifiable view of the captured exchanges
     */
    public List<RecordedExchange> exchanges() {
        return List.copyOf(exchanges);
    }

    /**
     * Writes the captured exchanges to a JSON file for offline analysis.
     *
     * @param target absolute path to write
     * @throws BookingException with {@link ErrorCode#SESSION_WRITE_FAILED} on IO error
     */
    public void writeToJson(java.nio.file.Path target) {
        Objects.requireNonNull(target, "target");
        try {
            JsonMappers.standard().writerWithDefaultPrettyPrinter()
                .writeValue(target.toFile(), exchanges());
            log.info("Wrote {} recorded exchanges to {}", exchanges.size(), target);
        } catch (Exception e) {
            throw new BookingException(ErrorCode.SESSION_WRITE_FAILED,
                "Failed to write traffic recording to " + target, e);
        }
    }

    /**
     * Detaches Playwright listeners. Safe to call multiple times.
     */
    @Override
    public void close() {
        try {
            page.offRequest(requestHandler);
            page.offResponse(responseHandler);
        } catch (RuntimeException e) {
            log.warn("Failed to detach traffic recorder listeners: {}", e.getMessage());
        }
        log.info("HttpTrafficRecorder detached; captured {} exchanges", exchanges.size());
    }

    private void onRequest(Request request) {
        String url = request.url();
        // Ignore data URLs and browser-extension noise.
        if (url.startsWith("data:") || url.startsWith("chrome-extension:")) {
            return;
        }
        pending.put(request.url() + "@" + request.method(),
            new PendingRequest(request.method(), URI.create(url), request.headers(),
                maskSensitiveBody(request.postData())));
    }

    private void onResponse(Response response) {
        Request request = response.request();
        String key = request.url() + "@" + request.method();
        PendingRequest pendingRequest = pending.remove(key);
        if (pendingRequest == null) {
            return;
        }

        String mime = response.headerValue("content-type");
        if (mime == null) {
            mime = "";
        }

        String summary = null;
        if (isTextResponse(mime)) {
            try {
                String text = response.text();
                if (text != null) {
                    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                    int limit = Math.min(bytes.length, MAX_BODY_SUMMARY_BYTES);
                    summary = new String(bytes, 0, limit, StandardCharsets.UTF_8);
                }
            } catch (RuntimeException e) {
                log.debug("Could not read response body for {}: {}", response.url(), e.getMessage());
            }
        }

        RecordedExchange exchange = new RecordedExchange(
            Instant.now(),
            pendingRequest.method(),
            pendingRequest.url(),
            copyHeaders(pendingRequest.headers()),
            pendingRequest.body(),
            response.status(),
            copyHeaders(response.headers()),
            mime,
            summary
        );
        exchanges.add(exchange);
        log.debug("Recorded {} {} -> {}", exchange.method(), exchange.url(), exchange.status());
    }

    public static boolean isTextResponse(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.contains("text/")
            || lower.contains("application/json")
            || lower.contains("application/xml")
            || lower.contains("application/javascript")
            || lower.contains("application/x-www-form-urlencoded");
    }

    private static Map<String, List<String>> copyHeaders(Map<String, String> src) {
        return src.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                e -> List.of(e.getValue()),
                (a, b) -> {
                    List<String> merged = new ArrayList<>(a);
                    merged.addAll(b);
                    return List.copyOf(merged);
                }
            ));
    }

    /**
     * Masks values of sensitive form fields in {@code application/x-www-form-urlencoded}
     * bodies. JSON bodies are left as-is because field names are application-specific.
     */
    public static String maskSensitiveBody(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        StringBuilder sb = new StringBuilder(body.length());
        var matcher = FORM_PAIR.matcher(body);
        int last = 0;
        while (matcher.find()) {
            sb.append(body, last, matcher.end(2) + 1); // including '='
            String key = matcher.group(2).toLowerCase(Locale.ROOT);
            if (SENSITIVE_FIELDS.contains(key)) {
                sb.append("***");
            } else {
                sb.append(matcher.group(3));
            }
            last = matcher.end();
        }
        sb.append(body.substring(last));
        return sb.toString();
    }

    private record PendingRequest(
        String method,
        URI url,
        Map<String, String> headers,
        String body) {
    }
}

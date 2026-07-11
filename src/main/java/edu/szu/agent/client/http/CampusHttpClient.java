package edu.szu.agent.client.http;

import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Direct HTTP client for SZU intranet endpoints, with manual cookie management.
 *
 * <p>This client is intended to replace {@link edu.szu.agent.browser.BrowserLifecycle}
 * for endpoints whose request/response contracts have been reverse-engineered.
 * It follows redirects manually so that CAS {@code Set-Cookie} headers are
 * honored on every hop.
 *
 * <p>The implementation uses {@link HttpURLConnection} instead of
 * {@code java.net.http.HttpClient} because SZU authserver closes connections
 * from the JDK 11+ client during the login POST. {@code HttpURLConnection}
 * gives explicit control over keep-alive and header ordering.
 *
 * <p>// Design Pattern: Adapter (HTTP transport replacing browser automation)
 * // 编程技术: Builder / try-with-resources / Lambda / 泛型
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class CampusHttpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CampusHttpClient.class);

    private static final int MAX_REDIRECTS = 10;
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final HostnameVerifier ALLOW_ALL_HOSTS = (hostname, session) -> true;
    private static final SSLContext TRUST_ALL_CONTEXT;

    static {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // Intentionally no-op for dev/internal trust-all mode.
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // Intentionally no-op for dev/internal trust-all mode.
                    }
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            TRUST_ALL_CONTEXT = sc;
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final CookieJar cookieJar;
    private final Duration timeout;
    private final ExchangeRecorder exchangeRecorder;
    private final boolean trustAll;

    private CampusHttpClient(Builder builder) {
        this.cookieJar = Objects.requireNonNull(builder.cookieJar, "cookieJar");
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofSeconds(30);
        this.exchangeRecorder = builder.exchangeRecorder;
        this.trustAll = builder.trustAll;
    }

    /**
     * Creates a builder for {@code CampusHttpClient}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience factory: fresh client with a new cookie jar.
     */
    public static CampusHttpClient create() {
        return builder().build();
    }

    /**
     * Performs a GET request and returns the response body as a String.
     *
     * @param url target URL
     * @return response body
     * @throws BookingException on network or HTTP failure
     */
    public String get(String url) {
        return get(url, null, Map.of());
    }

    /**
     * Performs a GET request with an optional Referer and extra headers.
     *
     * @param url          target URL
     * @param referer      referer URL, or {@code null}
     * @param extraHeaders additional request headers
     * @return response body
     * @throws BookingException on network or HTTP failure
     * @since 0.6.0
     * @author 王子豪
     */
    public String get(String url, String referer, Map<String, String> extraHeaders) {
        return executeWithRedirects(url, "GET", null, referer, null,
            BodyHandlers.ofString(StandardCharsets.UTF_8), extraHeaders).body();
    }

    /**
     * Performs a POST request with form-encoded data.
     *
     * @param url  target URL
     * @param form form fields
     * @return response body
     * @throws BookingException on network or HTTP failure
     */
    public String postForm(String url, Map<String, String> form) {
        return postForm(url, null, Map.of(), form);
    }

    /**
     * Performs a POST request with form-encoded data and an optional Referer.
     *
     * @param url     target URL
     * @param referer referer URL, or {@code null}
     * @param form    form fields
     * @return response body
     * @throws BookingException on network or HTTP failure
     */
    public String postForm(String url, String referer, Map<String, String> form) {
        return postForm(url, referer, Map.of(), form);
    }

    /**
     * Performs a POST request with form-encoded data, an optional Referer,
     * and extra headers.
     *
     * @param url          target URL
     * @param referer      referer URL, or {@code null}
     * @param extraHeaders additional request headers
     * @param form         form fields
     * @return response body
     * @throws BookingException on network or HTTP failure
     * @since 0.6.0
     * @author 王子豪
     */
    public String postForm(String url, String referer, Map<String, String> extraHeaders,
                           Map<String, String> form) {
        String body = form.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(Collectors.joining("&"));
        return post(url, "application/x-www-form-urlencoded", referer, extraHeaders, body);
    }

    /**
     * Performs a POST request with an arbitrary body and content type.
     *
     * @param url         target URL
     * @param contentType request content type
     * @param body        request body
     * @return response body
     * @throws BookingException on network or HTTP failure
     */
    public String post(String url, String contentType, String body) {
        return post(url, contentType, null, Map.of(), body);
    }

    private String post(String url, String contentType, String referer,
                        Map<String, String> extraHeaders, String body) {
        return executeWithRedirects(url, "POST", contentType, referer, body,
            BodyHandlers.ofString(StandardCharsets.UTF_8), extraHeaders).body();
    }

    /**
     * Downloads a binary resource to the given path.
     *
     * @param url    resource URL
     * @param target file to write
     * @return number of bytes written
     * @throws BookingException on network or HTTP failure
     */
    public long download(String url, Path target) {
        Objects.requireNonNull(target, "target");
        Response<Path> response = executeWithRedirects(url, "GET", null, null, null,
            BodyHandlers.ofFile(target), Map.of());
        try {
            return Files.size(response.body());
        } catch (IOException e) {
            throw new BookingException(ErrorCode.ATTACHMENT_DOWNLOAD_FAILED,
                "Downloaded file not readable: " + target, e);
        }
    }

    /**
     * Returns the cookie jar used by this client.
     */
    public CookieJar cookieJar() {
        return cookieJar;
    }

    /**
     * Returns the current timeout.
     */
    public Duration timeout() {
        return timeout;
    }

    private <T> Response<T> executeWithRedirects(String url, String method, String contentType,
                                                  String referer, String body,
                                                  BodyHandler<T> handler,
                                                  Map<String, String> extraHeaders) {
        return executeWithRedirects(URI.create(url), method, contentType, referer, body,
            handler, extraHeaders, 0);
    }

    private <T> Response<T> executeWithRedirects(URI uri, String method, String contentType,
                                                  String referer, String body,
                                                  BodyHandler<T> handler,
                                                  Map<String, String> extraHeaders, int redirects) {
        Response<T> response = send(uri, method, contentType, referer, extraHeaders, body, handler);
        log.debug("{} {} -> {}", method, uri, response.statusCode());

        if (isRedirect(response.statusCode()) && redirects < MAX_REDIRECTS) {
            String location = response.headers().get("Location").stream()
                .findFirst()
                .orElseThrow(() -> new BookingException(ErrorCode.NETWORK_TIMEOUT,
                    "Redirect without Location header"));
            URI next = uri.resolve(location);
            storeCookies(response);
            log.debug("Following redirect {} -> {}", response.statusCode(), next);
            return executeWithRedirects(next, "GET", null, null, null, handler,
                Map.of(), redirects + 1);
        }

        storeCookies(response);
        return response;
    }

    private <T> Response<T> send(URI uri, String method, String contentType,
                                  String referer, Map<String, String> extraHeaders,
                                  String body, BodyHandler<T> handler) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) uri.toURL().openConnection();
            if (trustAll && conn instanceof HttpsURLConnection https) {
                https.setSSLSocketFactory(TRUST_ALL_CONTEXT.getSocketFactory());
                https.setHostnameVerifier(ALLOW_ALL_HOSTS);
            }
            conn.setRequestMethod(method);
            conn.setConnectTimeout((int) timeout.toMillis());
            conn.setReadTimeout((int) timeout.toMillis());
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);

            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

            if (referer != null && !referer.isBlank()) {
                conn.setRequestProperty("Referer", referer);
            }
            if (contentType != null && !contentType.isBlank()) {
                conn.setRequestProperty("Content-Type", contentType);
            }

            String cookies = cookieJar.cookieHeaderFor(uri);
            if (cookies != null) {
                conn.setRequestProperty("Cookie", cookies);
            }

            if (extraHeaders != null) {
                for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
                    if (header.getKey() != null && header.getValue() != null) {
                        conn.setRequestProperty(header.getKey(), header.getValue());
                    }
                }
            }

            Map<String, List<String>> requestHeaders = new HashMap<>(conn.getRequestProperties());

            if (body != null && !body.isBlank()) {
                conn.setDoOutput(true);
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(payload);
                }
            }

            int statusCode = conn.getResponseCode();
            Map<String, List<String>> headers = conn.getHeaderFields();

            InputStream raw = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            T parsedBody;
            if (raw != null) {
                try (InputStream in = raw) {
                    parsedBody = handler.apply(in);
                }
            } else {
                parsedBody = handler.apply(InputStream.nullInputStream());
            }

            Response<T> response = new Response<>(uri, statusCode, headers, parsedBody);
            recordExchange(uri, method, body, requestHeaders, statusCode, headers, parsedBody);
            return response;

        } catch (IOException e) {
            throw new BookingException(ErrorCode.NETWORK_TIMEOUT,
                "HTTP request failed: " + uri + " — " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private <T> void recordExchange(URI uri, String method, String body,
                                      Map<String, List<String>> requestHeaders,
                                      int statusCode,
                                      Map<String, List<String>> responseHeaders,
                                      T parsedBody) {
        if (exchangeRecorder == null) {
            return;
        }
        try {
            String maskedBody = HttpTrafficRecorder.maskSensitiveBody(body);
            String mime = responseHeaders.getOrDefault("Content-Type", List.of()).stream()
                .findFirst()
                .orElse("");
            String summary = null;
            if (HttpTrafficRecorder.isTextResponse(mime) && parsedBody != null) {
                String text = parsedBody.toString();
                summary = text.length() > HttpTrafficRecorder.MAX_BODY_SUMMARY_BYTES
                    ? text.substring(0, HttpTrafficRecorder.MAX_BODY_SUMMARY_BYTES)
                    : text;
            }
            RecordedExchange exchange = new RecordedExchange(
                Instant.now(),
                method,
                uri,
                sanitizeHeaders(requestHeaders),
                maskedBody,
                statusCode,
                sanitizeHeaders(responseHeaders),
                mime,
                summary
            );
            exchangeRecorder.record(exchange);
        } catch (RuntimeException e) {
            log.warn("Failed to record HTTP exchange for {}: {}", uri, e.getMessage());
        }
    }

    private <T> void storeCookies(Response<T> response) {
        List<String> setCookies = response.headers().get("Set-Cookie");
        if (setCookies != null) {
            cookieJar.storeFromResponse(response.uri(), setCookies);
        }
    }

    private static Map<String, List<String>> sanitizeHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> sanitized = new HashMap<>();
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            sanitized.put(e.getKey(), e.getValue());
        }
        return sanitized;
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        // Nothing to close explicitly.
    }

    /**
     * Simple HTTP response wrapper.
     *
     * <p>// 编程技术: record(不可变值类型)
     */
    public record Response<T>(URI uri, int statusCode, Map<String, List<String>> headers, T body) {
    }

    /**
     * Body handler for {@link Response}.
     */
    @FunctionalInterface
    public interface BodyHandler<T> {
        T apply(InputStream inputStream) throws IOException;
    }

    /**
     * Common body handlers.
     */
    public static final class BodyHandlers {
        private BodyHandlers() {
        }

        public static BodyHandler<String> ofString(java.nio.charset.Charset charset) {
            return in -> new String(in.readAllBytes(), charset);
        }

        public static BodyHandler<Path> ofFile(Path target) {
            return in -> {
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return target;
            };
        }
    }

    /**
     * Optional callback for recording request/response exchanges.
     */
    @FunctionalInterface
    public interface ExchangeRecorder {
        void record(RecordedExchange exchange);
    }

    /**
     * Builder for {@link CampusHttpClient}.
     */
    public static final class Builder {
        private CookieJar cookieJar = new CookieJar();
        private Duration timeout;
        private ExchangeRecorder exchangeRecorder;
        private boolean trustAll;

        public Builder cookieJar(CookieJar cookieJar) {
            this.cookieJar = Objects.requireNonNull(cookieJar, "cookieJar");
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        public Builder exchangeRecorder(ExchangeRecorder exchangeRecorder) {
            this.exchangeRecorder = exchangeRecorder;
            return this;
        }

        public Builder trustAll(boolean trustAll) {
            this.trustAll = trustAll;
            return this;
        }

        public CampusHttpClient build() {
            return new CampusHttpClient(this);
        }
    }
}

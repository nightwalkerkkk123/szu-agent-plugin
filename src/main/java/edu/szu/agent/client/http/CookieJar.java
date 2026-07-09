package edu.szu.agent.client.http;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Simple in-memory cookie jar for direct HTTP clients.
 *
 * <p>Parses {@code Set-Cookie} / {@code Set-Cookie2} headers and selects
 * applicable cookies for outgoing requests based on domain and path.
 *
 * <p>// Design Pattern: Repository (in-memory cookie store)
 * // 编程技术: ConcurrentHashMap / Stream / record / 正则解析
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class CookieJar {

    private final Map<String, Cookie> cookies;

    public CookieJar() {
        this.cookies = new ConcurrentHashMap<>();
    }

    /**
     * Creates a jar pre-populated with the given cookies.
     *
     * @param initial initial cookie snapshot
     * @since 0.6.0
     * @author 王子豪
     */
    public CookieJar(List<Cookie> initial) {
        this();
        loadFromSnapshot(initial);
    }

    /**
     * Replaces the current cookie store with the given snapshot.
     *
     * @param snapshot cookies to load
     * @since 0.6.0
     * @author 王子豪
     */
    public void loadFromSnapshot(List<Cookie> snapshot) {
        cookies.clear();
        if (snapshot == null) {
            return;
        }
        for (Cookie cookie : snapshot) {
            if (cookie != null) {
                cookies.put(cookie.key(), cookie);
            }
        }
    }

    /**
     * Parses one or more {@code Set-Cookie} header values and stores them.
     *
     * @param source the URI that issued the cookies
     * @param values header values (may be multiple values from one or more headers)
     */
    public void storeFromResponse(URI source, List<String> values) {
        if (values == null) {
            return;
        }
        for (String raw : values) {
            Cookie parsed = Cookie.parse(source, raw);
            if (parsed != null) {
                if (parsed.maxAge() == 0 || isExpired(parsed)) {
                    cookies.remove(parsed.key());
                } else {
                    cookies.put(parsed.key(), parsed);
                }
            }
        }
    }

    /**
     * Returns the {@code Cookie} header value for a request to {@code target},
     * or {@code null} if no cookies apply.
     *
     * @param target target request URI
     * @return cookie header value, or null
     */
    public String cookieHeaderFor(URI target) {
        String host = target.getHost().toLowerCase(Locale.ROOT);
        String rawPath = target.getRawPath();
        String path = (rawPath == null || rawPath.isEmpty()) ? "/" : rawPath;

        List<Cookie> matching = cookies.values().stream()
            .filter(c -> host.equals(c.domain()) || host.endsWith("." + c.domain()))
            .filter(c -> path.startsWith(c.path()))
            .filter(c -> !isExpired(c))
            .toList();

        if (matching.isEmpty()) {
            return null;
        }
        return matching.stream()
            .map(c -> c.name() + "=" + c.value())
            .collect(Collectors.joining("; "));
    }

    /**
     * Returns all stored cookies for inspection.
     */
    public List<Cookie> snapshot() {
        return List.copyOf(cookies.values());
    }

    /**
     * Clears all cookies.
     */
    public void clear() {
        cookies.clear();
    }

    private boolean isExpired(Cookie cookie) {
        return cookie.expiresAt() != null && Instant.now().isAfter(cookie.expiresAt());
    }

    /**
     * A single stored cookie.
     *
     * <p>// 编程技术: record(不可变值类型)
     */
    public record Cookie(
        String name,
        String value,
        String domain,
        String path,
        Instant expiresAt,
        boolean secure,
        boolean httpOnly,
        long maxAge) {

        @JsonIgnore
        String key() {
            return domain + "/" + name;
        }

        static Cookie parse(URI source, String header) {
            if (header == null || header.isBlank()) {
                return null;
            }
            String[] parts = header.split(";");
            if (parts.length == 0) {
                return null;
            }
            String[] nv = parts[0].trim().split("=", 2);
            if (nv.length != 2) {
                return null;
            }
            String name = nv[0].trim();
            String value = nv[1].trim();

            String domain = source.getHost().toLowerCase(Locale.ROOT);
            String path = "/";
            Instant expiresAt = null;
            boolean secure = false;
            boolean httpOnly = false;
            long maxAge = -1;

            for (int i = 1; i < parts.length; i++) {
                String[] av = parts[i].trim().split("=", 2);
                String attr = av[0].trim().toLowerCase(Locale.ROOT);
                String attrValue = av.length == 2 ? av[1].trim() : "";
                switch (attr) {
                    case "domain" -> {
                        if (!attrValue.isEmpty()) {
                            domain = attrValue.toLowerCase(Locale.ROOT);
                        }
                    }
                    case "path" -> {
                        if (!attrValue.isEmpty()) {
                            path = attrValue;
                        }
                    }
                    case "expires" -> {
                        try {
                            expiresAt = Instant.parse(attrValue);
                        } catch (Exception ignored) {
                            // Fall back to HttpDate parsing if needed; for now ignore.
                        }
                    }
                    case "max-age" -> {
                        try {
                            maxAge = Long.parseLong(attrValue);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    case "secure" -> secure = true;
                    case "httponly" -> httpOnly = true;
                    default -> {
                        // Ignore unknown attributes.
                    }
                }
            }
            return new Cookie(name, value, domain, path, expiresAt, secure, httpOnly, maxAge);
        }
    }
}

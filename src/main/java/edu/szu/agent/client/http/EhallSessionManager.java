package edu.szu.agent.client.http;

import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.json.JsonMappers;
import edu.szu.agent.retry.RetryPolicies;
import edu.szu.agent.retry.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages an ehall sports-venue session, transparently re-authenticating via
 * CAS when the persisted cookies no longer work.
 *
 * <p>This hides the two-hop CAS dance from callers: a persisted snapshot may
 * only contain authserver cookies (e.g. from a {@code www1} CAS login), so the
 * manager first visits the ehall CAS entry point to exchange the CASTGC for an
 * ehall session; if that fails, it falls back to a fresh CAS login using the
 * supplied credentials.
 *
 * <p>Design notes:
 * <ul>
 *   <li>The manager is stateless and immutable — all inputs come through the
 *       constructor and {@link #ensureSession(CookieJar)}.</li>
 *   <li>Retry policy is injectable so callers can tune resilience vs. latency.</li>
 *   <li>{@link CasLoginClientFactory} is injectable for unit tests.</li>
 * </ul>
 *
 * // Design Pattern: Facade (single entry point for session validation + login)
 * // 编程技术: Jackson JsonNode / HttpURLConnection cookie jar / 构造器注入
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class EhallSessionManager {

    private static final Logger log = LoggerFactory.getLogger(EhallSessionManager.class);

    private static final String EHALL_BASE = EhallAjaxHeaders.BASE;
    private static final String EHALL_REFERER = EhallAjaxHeaders.REFERER;
    private static final String EHALL_CAS_ENTRY =
        "https://ehall.szu.edu.cn/login?service=https%3A%2F%2Fehall.szu.edu.cn%2Fqljfwapp%2Fsys%2FlwSzuCgyy%2Findex.do";
    private static final String CAS_BASE = "https://authserver.szu.edu.cn";
    private static final String DATE_PROBE_URL = EHALL_BASE + "/sportVenue/getRqList.do";
    private static final Map<String, String> AJAX_HEADERS = EhallAjaxHeaders.standard();

    private final String username;
    private final String password;
    private final boolean trustAll;
    private final RetryPolicy retryPolicy;
    private final CasLoginClientFactory casLoginClientFactory;
    private final CampusHttpClientFactory httpClientFactory;

    /**
     * Creates a manager for the given credentials with sensible defaults.
     *
     * @param username student ID
     * @param password CAS password
     * @param trustAll disable TLS certificate validation (dev/internal only)
     * @since 0.6.0
     * @author 王子豪
     */
    public EhallSessionManager(String username, String password, boolean trustAll) {
        this(username, password, trustAll, RetryPolicies.login(),
            CasLoginClientFactory.DEFAULT, CampusHttpClientFactory.DEFAULT);
    }

    /**
     * Creates a fully configured manager.
     *
     * @param username             student ID
     * @param password             CAS password
     * @param trustAll             disable TLS certificate validation (dev/internal only)
     * @param retryPolicy          retry policy for session probes and CAS login
     * @param casLoginClientFactory factory for CAS login clients
     * @since 0.6.0
     * @author 王子豪
     */
    public EhallSessionManager(String username, String password, boolean trustAll,
                               RetryPolicy retryPolicy, CasLoginClientFactory casLoginClientFactory,
                               CampusHttpClientFactory httpClientFactory) {
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.trustAll = trustAll;
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.casLoginClientFactory = Objects.requireNonNull(casLoginClientFactory, "casLoginClientFactory");
        this.httpClientFactory = Objects.requireNonNull(httpClientFactory, "httpClientFactory");
    }

    /**
     * Returns a {@link CampusHttpClient} that has a working ehall session.
     *
     * <p>If the supplied cookie jar already works for ehall AJAX APIs, it is
     * reused. Otherwise the manager tries to prime it via the ehall CAS entry
     * point; if that still does not yield a valid session, it performs a full
     * CAS re-login.
     *
     * @param initial jar loaded from persisted session, may be empty or stale
     * @return a client with a validated ehall session
     * @throws BookingException if no working session can be established
     * @since 0.6.0
     * @author 王子豪
     */
    public CampusHttpClient ensureSession(CookieJar initial) {
        Objects.requireNonNull(initial, "initial cookie jar");

        // 1. Try the persisted jar as-is.
        CampusHttpClient http = createClient(initial);
        if (isSessionValid(http)) {
            log.info("Persisted ehall state is still valid for {}", username);
            return http;
        }

        // 2. Try to exchange the authserver CASTGC for an ehall session.
        if (!initial.snapshot().isEmpty()) {
            log.info("Persisted ehall state is stale; priming via ehall CAS entry for {}", username);
            http = primeSession(http);
            if (isSessionValid(http)) {
                log.info("Ehall state primed successfully for {}", username);
                return http;
            }
        }

        // 3. Fall back to a fresh CAS login.
        log.info("Ehall state could not be primed; re-authenticating {} via CAS", username);
        http = createClient(new CookieJar());
        casLogin(http);

        if (!isSessionValid(http)) {
            throw new BookingException(ErrorCode.SESSION_READ_FAILED,
                "CAS login succeeded but ehall state is still not valid for " + username);
        }
        log.info("CAS re-login succeeded for {} and ehall state is valid", username);
        return http;
    }

    private CampusHttpClient createClient(CookieJar jar) {
        return httpClientFactory.create(jar, trustAll);
    }

    private CampusHttpClient primeSession(CampusHttpClient http) {
        return retryPolicy.execute(() -> {
            http.get(EHALL_CAS_ENTRY);
            return http;
        });
    }

    private void casLogin(CampusHttpClient http) {
        retryPolicy.execute(() -> {
            CasLoginClient cas = casLoginClientFactory.create(http, CAS_BASE);
            cas.login(username, password);
            return null;
        });
    }

    private boolean isSessionValid(CampusHttpClient http) {
        try {
            String body = http.postForm(DATE_PROBE_URL, EHALL_REFERER, AJAX_HEADERS, Map.of());
            if (body == null || body.isBlank()) {
                return false;
            }
            String trimmed = stripBom(body).trim();
            return trimmed.startsWith("[") && trimmed.endsWith("]") && isJsonArray(trimmed);
        } catch (BookingException e) {
            log.debug("Ehall state probe failed for {}: {}", username, e.getMessage());
            return false;
        }
    }

    private boolean isJsonArray(String text) {
        try {
            JsonMappers.standard().readTree(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String stripBom(String body) {
        if (body != null && !body.isEmpty() && body.charAt(0) == '\uFEFF') {
            return body.substring(1);
        }
        return body;
    }
}

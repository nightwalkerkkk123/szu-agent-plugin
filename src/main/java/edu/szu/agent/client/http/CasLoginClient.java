package edu.szu.agent.client.http;

import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Direct HTTP client for SZU CAS (Central Authentication Service) login.
 *
 * <p>After recording a browser login session with {@link HttpTrafficRecorder},
 * this client can replay the CAS flow using direct HTTP requests, producing
 * a cookie jar that subsequent API calls can reuse.
 *
 * <p>// Design Pattern: Strategy (alternative to CasLoginStep browser automation)
 * // 编程技术: 正则 / Map / Builder / 不可变返回
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class CasLoginClient {

    private static final Logger log = LoggerFactory.getLogger(CasLoginClient.class);

    /** Default CAS login path; override via {@link Builder#loginPath(String)}. */
    public static final String DEFAULT_LOGIN_PATH = "/authserver/login";

    private static final Pattern PWD_FORM_PATTERN = Pattern.compile(
        "<div[^>]+id=[\"']pwdLoginDiv[\"'][^>]*>(.*?)(?=<div[^>]+id=[\"']qrLoginDiv[\"'])",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HIDDEN_INPUT_PATTERN = Pattern.compile(
        "<input[^>]*type=[\"']hidden[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_NAME_PATTERN = Pattern.compile(
        "name=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_VALUE_PATTERN = Pattern.compile(
        "value=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORM_ACTION_PATTERN = Pattern.compile(
        "<form[^>]+action=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern PWD_SALT_PATTERN = Pattern.compile(
        "<input[^>]+id=[\"']pwdEncryptSalt[\"'][^>]+value=[\"']([^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE);

    private final CampusHttpClient http;
    private final String casBaseUrl;
    private final String loginPath;
    private final String service;
    private final String usernameField;
    private final String passwordField;
    private final PasswordEncryptor passwordEncryptor;

    private CasLoginClient(Builder builder) {
        this.http = Objects.requireNonNull(builder.http, "http");
        this.casBaseUrl = Objects.requireNonNull(builder.casBaseUrl, "casBaseUrl");
        this.loginPath = builder.loginPath != null ? builder.loginPath : DEFAULT_LOGIN_PATH;
        this.service = builder.service;
        this.usernameField = builder.usernameField != null ? builder.usernameField : "username";
        this.passwordField = builder.passwordField != null ? builder.passwordField : "password";
        this.passwordEncryptor = builder.passwordEncryptor != null
            ? builder.passwordEncryptor
            : PasswordEncryptor.plaintext();
    }

    /**
     * Creates a builder for {@code CasLoginClient}.
     */
    public static Builder builder(CampusHttpClient http, String casBaseUrl) {
        return new Builder(http, casBaseUrl);
    }

    /**
     * Performs CAS login and returns the post-login landing page body.
     *
     * @param username CAS username
     * @param password CAS password
     * @return login result containing the final URL and landing body
     * @throws BookingException on network failure or recognizable login error
     */
    /**
     * Fetches the CAS login page HTML for inspection/debugging.
     *
     * @return raw login page body
     */
    public String fetchLoginPage() {
        return http.get(buildLoginUrl());
    }

    public CasLoginResult login(String username, String password) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");

        String loginUrl = buildLoginUrl();
        log.info("Fetching CAS login page: {}", loginUrl);
        String loginPage = http.get(loginUrl);

        Map<String, String> hidden = parseHiddenFields(loginPage);
        Map<String, String> pageParams = new HashMap<>(hidden);
        pageParams.putAll(extractEncryptionParams(loginPage));

        Map<String, String> form = new HashMap<>(hidden);
        form.put(usernameField, username);
        form.put(passwordField, passwordEncryptor.encrypt(password, pageParams));
        // Authserver / CAS conventions; harmless if the server ignores them.
        form.putIfAbsent("_eventId", "submit");
        form.putIfAbsent("cllt", "userNameLogin");
        form.putIfAbsent("dllt", "generalLogin");

        log.info("Parsed CAS hidden fields: {}", hidden.keySet());
        log.info("CAS encryption salt present: {}", pageParams.containsKey("pwdEncryptSalt"));
        log.info("Submitting CAS credentials to {} for user: {}", loginUrl, username);
        String responseBody = http.postForm(loginUrl, loginUrl, form);

        // Detect common failure markers in the response body.
        String lower = responseBody.toLowerCase();
        if (lower.contains("密码错误") || lower.contains("incorrect password")
            || lower.contains("invalid credentials")) {
            throw new BookingException(ErrorCode.PASSWORD_INCORRECT,
                "CAS rejected credentials for " + username);
        }
        if (lower.contains("验证码") || lower.contains("captcha")) {
            throw new BookingException(ErrorCode.CAPTCHA_REQUIRED,
                "CAS requires CAPTCHA for " + username);
        }
        if (lower.contains("账号被锁") || lower.contains("locked")) {
            throw new BookingException(ErrorCode.ACCOUNT_LOCKED,
                "CAS account locked: " + username);
        }

        return new CasLoginResult(http.cookieJar(), responseBody);
    }

    /**
     * Extracts hidden input fields from a CAS login form.
     *
     * <p>Package-private test seam.
     */
    Map<String, String> parseHiddenFields(String html) {
        Map<String, String> fields = new HashMap<>();
        Matcher formMatcher = PWD_FORM_PATTERN.matcher(html);
        String formHtml = formMatcher.find() ? formMatcher.group(1) : html;

        Matcher inputMatcher = HIDDEN_INPUT_PATTERN.matcher(formHtml);
        while (inputMatcher.find()) {
            String tag = inputMatcher.group(0);
            Matcher nameMatcher = INPUT_NAME_PATTERN.matcher(tag);
            if (!nameMatcher.find()) {
                continue;
            }
            String name = nameMatcher.group(1);
            if (name.equalsIgnoreCase(usernameField) || name.equalsIgnoreCase(passwordField)) {
                continue;
            }
            Matcher valueMatcher = INPUT_VALUE_PATTERN.matcher(tag);
            String value = valueMatcher.find() ? valueMatcher.group(1) : "";
            fields.put(name, value);
        }
        log.debug("Parsed {} hidden CAS form fields from account login form", fields.size());
        return fields;
    }

    /**
     * Extracts password-encryption parameters from the login page HTML.
     *
     * <p>For authserver deployments this is typically
     * {@code pwdDefaultEncryptSalt}; the actual RSA public key may come from
     * a separate endpoint such as {@code /authserver/getKey}.
     */
    private Map<String, String> extractEncryptionParams(String html) {
        Map<String, String> params = new HashMap<>();
        Matcher m = PWD_SALT_PATTERN.matcher(html);
        if (m.find()) {
            params.put("pwdEncryptSalt", m.group(1));
        }
        return params;
    }

    /**
     * Result of a successful CAS login.
     *
     * <p>// 编程技术: record(不可变值类型)
     */
    public record CasLoginResult(CookieJar cookieJar, String landingBody) {

        /**
         * Returns true if the cookie jar contains a session identifier.
         */
        public boolean hasSession() {
            return cookieJar.snapshot().stream()
                .anyMatch(c -> c.name().toLowerCase().contains("session")
                    || c.name().toLowerCase().contains("castgc")
                    || c.name().toLowerCase().contains("tgc"));
        }
    }

    /**
     * Resolves the login form's action attribute, falling back to the original
     * login URL if the page does not contain an explicit action.
     */
    private String resolveFormAction(String html, String loginUrl) {
        Matcher m = FORM_ACTION_PATTERN.matcher(html);
        if (!m.find()) {
            return loginUrl;
        }
        String action = m.group(1);
        if (action.startsWith("http://") || action.startsWith("https://")) {
            return action;
        }
        return URI.create(casBaseUrl).resolve(action).toString();
    }

    private String buildLoginUrl() {
        String loginUrl = casBaseUrl + loginPath;
        if (service != null && !service.isBlank()) {
            String sep = loginUrl.contains("?") ? "&" : "?";
            loginUrl = loginUrl + sep + "service=" + encodeService(service);
        }
        return loginUrl;
    }

    private static String encodeService(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Builder for {@link CasLoginClient}.
     */
    public static final class Builder {
        private final CampusHttpClient http;
        private final String casBaseUrl;
        private String loginPath;
        private String service;
        private String usernameField;
        private String passwordField;
        private PasswordEncryptor passwordEncryptor;

        private Builder(CampusHttpClient http, String casBaseUrl) {
            this.http = Objects.requireNonNull(http, "http");
            this.casBaseUrl = Objects.requireNonNull(casBaseUrl, "casBaseUrl");
        }

        public Builder loginPath(String loginPath) {
            this.loginPath = Objects.requireNonNull(loginPath, "loginPath");
            return this;
        }

        public Builder service(String service) {
            this.service = service;
            return this;
        }

        public Builder usernameField(String usernameField) {
            this.usernameField = Objects.requireNonNull(usernameField, "usernameField");
            return this;
        }

        public Builder passwordField(String passwordField) {
            this.passwordField = Objects.requireNonNull(passwordField, "passwordField");
            return this;
        }

        public Builder passwordEncryptor(PasswordEncryptor encryptor) {
            this.passwordEncryptor = Objects.requireNonNull(encryptor, "encryptor");
            return this;
        }

        public CasLoginClient build() {
            return new CasLoginClient(this);
        }
    }
}

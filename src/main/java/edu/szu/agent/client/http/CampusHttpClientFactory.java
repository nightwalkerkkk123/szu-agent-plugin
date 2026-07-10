package edu.szu.agent.client.http;

/**
 * Factory for creating {@link CampusHttpClient} instances tied to a given
 * {@link CookieJar}.
 *
 * <p>Extracted as an interface so {@link EhallSessionManager} can create
 * clients without hard-coding builder construction, enabling unit tests to
 * supply mock HTTP transports.
 *
 * // 编程技术: 工厂接口(依赖注入,可测试)
 *
 * @since 0.6.0
 * @author 王子豪
 */
@FunctionalInterface
public interface CampusHttpClientFactory {

    /**
     * Default factory using {@link CampusHttpClient#builder()}.
     */
    CampusHttpClientFactory DEFAULT = (jar, trustAll) -> CampusHttpClient.builder()
        .cookieJar(jar)
        .trustAll(trustAll)
        .build();

    /**
     * Creates an HTTP client bound to the given cookie jar.
     *
     * @param jar      cookie jar to associate with the client
     * @param trustAll whether to disable TLS certificate validation
     * @return a new HTTP client
     * @since 0.6.0
     * @author 王子豪
     */
    CampusHttpClient create(CookieJar jar, boolean trustAll);
}

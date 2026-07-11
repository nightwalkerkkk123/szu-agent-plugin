package edu.szu.agent.client.http;

/**
 * Factory for creating {@link CasLoginClient} instances backed by a shared
 * {@link CampusHttpClient}.
 *
 * <p>Extracted as an interface so {@link EhallSessionManager} can receive a
 * testable, configurable CAS client strategy instead of hard-coding encryptor
 * and service URL choices.
 *
 * // 编程技术: Strategy / 工厂接口(构造器注入,可测试)
 *
 * @since 0.6.0
 * @author 王子豪
 */
@FunctionalInterface
public interface CasLoginClientFactory {

    /**
     * Default factory: authserver encryptor + ehall sports-venue service URL.
     *
     * <p>The service parameter is the final ehall application URL, not the
     * {@code /login} CAS entry point. CAS will redirect back to this URL with a
     * service ticket; ehall validates it and issues the session cookies required
     * by the sports-venue module.
     */
    CasLoginClientFactory DEFAULT = (http, casBase) -> CasLoginClient.builder(http, casBase)
        .service("https://ehall.szu.edu.cn/qljfwapp/sys/lwSzuCgyy/index.do")
        .passwordEncryptor(new AuthserverPasswordEncryptor())
        .build();

    /**
     * Creates a CAS login client bound to the given HTTP transport.
     *
     * @param http    the HTTP client that will carry cookies across CAS hops
     * @param casBase the CAS server base URL, e.g. {@code https://authserver.szu.edu.cn}
     * @return a configured CAS login client
     * @since 0.6.0
     * @author 王子豪
     */
    CasLoginClient create(CampusHttpClient http, String casBase);
}

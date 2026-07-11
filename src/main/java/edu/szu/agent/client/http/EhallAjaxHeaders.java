package edu.szu.agent.client.http;

import java.util.Map;

/**
 * Standard browser-fingerprint headers for ehall AJAX endpoints.
 *
 * <p>All ehall sports-venue and payment APIs are called from the same SPA,
 * so they share the same referer, origin, and fetch metadata headers. Using
 * a single source of truth reduces drift between clients and makes the
 * requests look consistent to the server.
 *
 * // 编程技术: 常量集中 / Map.of
 *
 * @since 0.7.0
 * @author 王子豪
 */
public final class EhallAjaxHeaders {

    private EhallAjaxHeaders() {
    }

    /** Base URL of the ehall sports-venue module. */
    public static final String BASE = "https://ehall.szu.edu.cn/qljfwapp/sys/lwSzuCgyy";

    /** Referer used by the Vue SPA. */
    public static final String REFERER = BASE + "/index.do";

    /** Origin for all ehall requests. */
    public static final String ORIGIN = "https://ehall.szu.edu.cn";

    /**
     * Returns the standard AJAX header map used by every ehall API call.
     *
     * @return immutable header map
     * @since 0.7.0
     * @author 王子豪
     */
    public static Map<String, String> standard() {
        return Map.of(
            "X-Requested-With", "XMLHttpRequest",
            "Accept", "application/json, text/javascript, */*; q=0.01",
            "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8",
            "Origin", ORIGIN,
            "Sec-Fetch-Dest", "empty",
            "Sec-Fetch-Mode", "cors",
            "Sec-Fetch-Site", "same-origin"
        );
    }
}

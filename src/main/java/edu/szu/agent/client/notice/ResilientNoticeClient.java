package edu.szu.agent.client.notice;

import edu.szu.agent.domain.notice.NoticeListResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Wraps a real-fetch {@link NoticeListClient} (with injected
 * {@link NoticeFetchProvider}) with a static {@link NoticeListClient} fallback
 * so the {@code notice_list} Skill is always available — when the real path
 * fails (no HAR calibrated, network down, page changed) the wrapper
 * transparently falls back to the embedded snapshot.
 *
 * <p>Per PLAN-p1-real-fetch.md §5 阶段 2: 动态判断路由,默认优先真实抓取,
 * 任何阶段失败(无 HAR / selector 错 / 网络)回退到静态,Skill 永远可用。
 *
 * <p>// Design Pattern: Decorator + Strategy(动态选择实现)
 * // 编程技术: 不可变组合 / Lambda / 密封类型模式匹配
 *
 * @since 0.4.0
 * @author 王子豪
 */
public class ResilientNoticeClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientNoticeClient.class);

    private final NoticeListClient real;
    private final NoticeListClient fallback;

    /**
     * @param real     the real-fetch client; if {@code null}, the wrapper
     *                 behaves as if the real path always failed
     * @param fallback the static fallback client; if {@code null}, a real
     *                 failure surfaces as a {@link NoticeListResult.Failure}
     */
    public ResilientNoticeClient(NoticeListClient real, NoticeListClient fallback) {
        this.real = real;
        this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
    }

    /**
     * Tries the real-fetch path first; on {@link NoticeListResult.Failure}
     * or any thrown exception, falls back to the static client and logs a
     * warn. Never returns {@code null}.
     *
     * @return the notice list result (success or failure)
     */
    public NoticeListResult list() {
        if (real == null) {
            log.info("No real-fetch client wired; using static fallback directly");
            return fallback.list();
        }

        try {
            NoticeListResult result = real.list();
            if (result instanceof NoticeListResult.Success s) {
                log.info("Real fetch succeeded ({} notices); using it", s.notices().size());
                return s;
            }
            if (result instanceof NoticeListResult.Failure f) {
                log.warn("Real fetch returned failure [{}:{}]; falling back to static",
                    f.code(), f.message());
                return fallback.list();
            }
            log.warn("Real fetch returned unknown result type {}; falling back to static",
                result == null ? "null" : result.getClass().getSimpleName());
            return fallback.list();
        } catch (RuntimeException e) {
            log.warn("Real fetch threw {}; falling back to static: {}",
                e.getClass().getSimpleName(), e.getMessage());
            return fallback.list();
        }
    }
}

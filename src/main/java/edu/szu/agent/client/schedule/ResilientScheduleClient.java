package edu.szu.agent.client.schedule;

import edu.szu.agent.client.EhallScheduleClient;
import edu.szu.agent.domain.ScheduleListResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Wraps a real-fetch {@link EhallScheduleClient} with a static
 * {@link ScheduleListClient} fallback so the {@code schedule_list} Skill is
 * always available — when the real path fails (no session, CAS expired,
 * page changed, network down) the wrapper transparently falls back to the
 * embedded static course list.
 *
 * <p>Per PLAN-p1-real-fetch.md §4 阶段 1: 动态判断路由,默认优先真实抓取,
 * 任何阶段失败(登录态丢失 / 抓取失败 / 解析失败)回退到静态,Skill 永远可用。
 *
 * @since 0.6.0
 * @author 王子豪
 */
// Design Pattern: Decorator + Strategy(动态选择实现)
// 编程技术: 不可变组合 / Lambda / 密封类型模式匹配
public class ResilientScheduleClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientScheduleClient.class);

    private final EhallScheduleClient real;
    private final ScheduleListClient fallback;

    /**
     * @param real     the real-fetch client; if {@code null}, the wrapper
     *                 behaves as if the real path always failed
     * @param fallback the static fallback client; if {@code null}, a real
     *                 failure surfaces as a {@link ScheduleListResult.Failure}
     */
    public ResilientScheduleClient(EhallScheduleClient real, ScheduleListClient fallback) {
        this.real = real;
        this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
    }

    /**
     * Tries the real-fetch path first; on {@link ScheduleListResult.Failure}
     * or any thrown exception, falls back to the static client and logs a
     * warn. Never returns {@code null}.
     *
     * @return the schedule list result (success or failure)
     */
    public ScheduleListResult list() {
        if (real == null) {
            log.info("No real-fetch client wired; using static fallback directly");
            return fallback.list();
        }

        try {
            ScheduleListResult result = real.list();
            if (result instanceof ScheduleListResult.Success s) {
                log.info("Real fetch succeeded ({} courses); using it", s.courses().size());
                return s;
            }
            if (result instanceof ScheduleListResult.Failure f) {
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

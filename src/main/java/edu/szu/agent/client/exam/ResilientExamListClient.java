package edu.szu.agent.client.exam;

import edu.szu.agent.domain.exam.ExamSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Decorator + Strategy wrapper that routes exam list fetches through the
 * real (live) provider first and falls back to the static snapshot when the
 * real fetch fails or returns an empty list.
 *
 * <p>Rationale (per P1 real-fetch plan): the live exam page requires CAS login
 * and session authentication. When the session is valid, we use the live data;
 * when the fetch fails (session expired, network error, selector mismatch) we
 * automatically fall back to the embedded static snapshot that ships with
 * the build. This provides a degraded-but-working experience even when
 * authentication fails.
 *
 * <p>// Design Pattern: Decorator + Strategy(动态选择实现)
 * <p>// 编程技术: 函数式接口 / Supplier 注入 / 不可变性 / 异常处理
 *
 * @since 0.6.0
 * @author 王子豪
 */
public class ResilientExamListClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientExamListClient.class);

    private final Supplier<List<ExamSchedule>> realSupplier;
    private final Supplier<List<ExamSchedule>> fallbackSupplier;

    /**
     * @param realSupplier    returns the real-fetched list from the live page
     *                       (may throw RuntimeException on failure)
     * @param fallbackSupplier returns the static snapshot list (must not throw)
     */
    public ResilientExamListClient(Supplier<List<ExamSchedule>> realSupplier,
                                   Supplier<List<ExamSchedule>> fallbackSupplier) {
        this.realSupplier = Objects.requireNonNull(realSupplier, "realSupplier must not be null");
        this.fallbackSupplier = Objects.requireNonNull(fallbackSupplier, "fallbackSupplier must not be null");
    }

    /**
     * Fetch the exam list using the real client first; fall back to the static
     * snapshot on any failure or empty result.
     *
     * @return never null; always returns at least the static snapshot list
     */
    public List<ExamSchedule> list() {
        try {
            List<ExamSchedule> realEvents = realSupplier.get();
            if (realEvents == null || realEvents.isEmpty()) {
                log.info("Real exam fetch returned no events; falling back to static snapshot");
                return fallbackSupplier.get();
            }
            log.info("Real exam fetch succeeded ({} events); using it", realEvents.size());
            return realEvents;
        } catch (RuntimeException e) {
            log.warn("Real exam fetch threw {}; falling back to static snapshot: {}",
                e.getClass().getSimpleName(), e.getMessage());
            return fallbackSupplier.get();
        }
    }
}

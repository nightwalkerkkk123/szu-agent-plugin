package edu.szu.agent.client.calendar;

import edu.szu.agent.domain.calendar.AcademicEvent;
import edu.szu.agent.domain.calendar.CalendarListResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Decorator + Strategy wrapper that routes calendar fetches through the
 * real client first and falls back to the static snapshot when the real
 * fetch fails or returns an empty list.
 *
 * <p>Rationale (per PLAN-p1-real-fetch.md §5 阶段 4): the SZU official
 * calendar page is publicly accessible but currently renders the calendar
 * as PNG images rather than parseable text. The real fetch's primary value
 * is therefore **liveness probing**: if the page becomes unreachable or
 * returns 404, we know immediately and fall back. If the page later gains
 * HTML text content, parsing will yield events and we'll use them; until
 * then we always return the static 2025-2026 spring data.
 *
 * <p>// Design Pattern: Decorator + Strategy(动态选择实现)
 * <p>// 编程技术: 函数式接口 / Supplier 注入 / 密封类型模式匹配
 *
 * @since 0.4.0
 * @author 王子豪
 */
public class ResilientCalendarClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientCalendarClient.class);

    private final Supplier<List<AcademicEvent>> realSupplier;
    private final Supplier<List<AcademicEvent>> fallbackSupplier;

    /**
     * @param realSupplier returns the real-fetched list (may throw RuntimeException);
     *                      must not be {@code null}. The {@code staticOnly} contract
     *                      is owned by {@link edu.szu.agent.task.CalendarTask},
     *                      which short-circuits before constructing this decorator.
     * @param fallbackSupplier returns the static snapshot list (must not throw)
     */
    public ResilientCalendarClient(Supplier<List<AcademicEvent>> realSupplier,
                                    Supplier<List<AcademicEvent>> fallbackSupplier) {
        this.realSupplier = Objects.requireNonNull(realSupplier, "realSupplier");
        this.fallbackSupplier = Objects.requireNonNull(fallbackSupplier, "fallbackSupplier");
    }

    /**
     * Fetch the calendar using the real client first; fall back to the
     * static snapshot on any failure or empty result.
     */
    public CalendarListResult list() {
        try {
            List<AcademicEvent> realEvents = realSupplier.get();
            if (realEvents == null || realEvents.isEmpty()) {
                log.info("Real fetch returned no events; falling back to static");
                return success(fallbackSupplier.get());
            }
            log.info("Real fetch succeeded ({} events); using it", realEvents.size());
            return success(realEvents);
        } catch (RuntimeException e) {
            log.warn("Real fetch threw {}; falling back to static: {}",
                e.getClass().getSimpleName(), e.getMessage());
            return success(fallbackSupplier.get());
        }
    }

    private static CalendarListResult.Success success(List<AcademicEvent> events) {
        return new CalendarListResult.Success(events, Instant.now());
    }
}
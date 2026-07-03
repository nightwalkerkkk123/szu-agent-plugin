package edu.szu.agent.domain;

import edu.szu.agent.client.step.VenueSelector;

/**
 * Sport — booking domain. Each campus has its own sport offering, and
 * the offerings overlap only by name (e.g. "网球" exists at both campuses
 * but maps to different physical courts and different ehall back-ends),
 * so we model {@code Sport} as a sealed type with one enum per campus.
 *
 * <p>Compile-time consequence: a {@code (Campus, Sport)} pair is
 * impossible to misalign — every {@code Sport} carries its own campus
 * via {@link #campus()}, and the {@code BookingRequest} builder rejects
 * any mismatch between its campus parameter and {@code sport.campus()}.
 *
 * <p>To add a new campus, declare a new enum permitted by this sealed
 * interface and update {@link Campus} accordingly.
 *
 * // Design Pattern: Strategy (each campus = a different concrete strategy
 * // for the sport list); also Type Object (each enum constant carries
 * // displayName + ehallCode metadata).
 * // 编程技术: sealed interface (Java 17+) / 枚举 / 接口默认方法 / 模式匹配 (Java 21)
 *
 * @since 0.6.0
 * @author 王子豪
 */
public sealed interface Sport permits YuehaiSport, LihuSport {

    /** Human-readable Chinese name, matches the page tile text. */
    String displayName();

    /** Wire-format code; lowercase ASCII, used for logging and APIs. */
    String ehallCode();

    /** The campus this sport belongs to. Never null. */
    Campus campus();

    /**
     * Returns the venue-selection strategy for this sport's ehall page.
     *
     * <p>Court-style sports use a list selector; capacity-style sports
     * (e.g. gym) use a single-item selector. Binding the strategy to the
     * sport enum keeps the booking step free of sport-specific conditionals.
     *
     * @return the {@link VenueSelector} for this sport
     */
    VenueSelector venueSelector();

    /**
     * Resolves a sport by its campus and English identifier (enum name),
     * routing to the correct campus-specific enum. Used by CLI / Skill
     * input parsing.
     *
     * @param campus the booking campus
     * @param name   the enum constant name (e.g. {@code "TENNIS"})
     * @return the resolved {@code Sport}
     * @throws IllegalArgumentException if {@code name} is not a constant
     *                                  of the campus's sport enum
     */
    static Sport of(Campus campus, String name) {
        return switch (campus) {
            case YUEHAI -> YuehaiSport.valueOf(name);
            case LIHU -> LihuSport.valueOf(name);
        };
    }
}

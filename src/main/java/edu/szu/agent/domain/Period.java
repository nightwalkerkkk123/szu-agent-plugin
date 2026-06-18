package edu.szu.agent.domain;

import edu.szu.agent.client.schedule.PeriodMapping;

import java.time.LocalTime;
import java.util.Objects;

/**
 * A class period on the SZU schedule grid.
 *
 * <p>Periods are defined by their start/end "unit" (节次) and the actual
 * start/end clock times. The mapping from unit to time is held by
 * {@link PeriodMapping}.
 *
 * <p>Immutable value object.
 *
 * // 编程技术: record(不可变值对象)
 *
 * @param beginUnit  1-based start unit (e.g. 1 for 1-2节)
 * @param endUnit    1-based end unit (e.g. 2 for 1-2节)
 * @param startTime  clock time when the period begins
 * @param endTime    clock time when the period ends
 * @since 0.1.0
 * @author 王子豪
 */
public record Period(int beginUnit, int endUnit,
                     LocalTime startTime, LocalTime endTime) {

    /**
     * Compact constructor — validates invariants so {@link Period#of(int, int)}
     * can return safely-typed values.
     */
    public Period {
        if (beginUnit < 1 || endUnit < beginUnit) {
            throw new IllegalArgumentException(
                "Invalid period range: begin=" + beginUnit + ", end=" + endUnit);
        }
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                "endTime must be after startTime: " + startTime + " -> " + endTime);
        }
    }

    /**
     * Looks up a period by its start/end unit pair via {@link PeriodMapping}.
     *
     * @param beginUnit 1-based start unit
     * @param endUnit   1-based end unit
     * @return the mapped period
     * @throws IllegalArgumentException if the unit pair is not in the mapping
     * @since 0.1.0
     */
    public static Period of(int beginUnit, int endUnit) {
        return PeriodMapping.lookup(beginUnit, endUnit);
    }
}

package edu.szu.agent.client.schedule;

import edu.szu.agent.domain.Period;

import java.time.LocalTime;
import java.util.Map;

/**
 * Static mapping from SZU ehall period (节次) identifiers to {@link Period}
 * values carrying actual clock times.
 *
 * <p>The ehall grid encodes periods by their start/end "unit" (1-14). This
 * table maps each (beginUnit, endUnit) pair to its associated time range.
 *
 * <p><b>Time values are placeholders</b> based on common SZU class schedules;
 * per ADR-0009 D8 they are scheduled for calibration in P1. Use the
 * {@code beginUnit}/{@code endUnit} fields for reliable cross-version logic.
 *
 * // 编程技术: 不可变 Map + 静态工厂
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class PeriodMapping {

    /** Key format: {@code "begin-end"}, value: {@link Period}. */
    private static final Map<String, Period> TABLE = Map.ofEntries(
        Map.entry("1-2",  new Period(1,  2,  LocalTime.of(8,  0),  LocalTime.of(9,  50))),
        Map.entry("3-4",  new Period(3,  4,  LocalTime.of(10, 10), LocalTime.of(12, 0))),
        Map.entry("5-5",  new Period(5,  5,  LocalTime.of(14, 0),  LocalTime.of(14, 50))),
        Map.entry("6-6",  new Period(6,  6,  LocalTime.of(15, 0),  LocalTime.of(15, 50))),
        Map.entry("7-8",  new Period(7,  8,  LocalTime.of(16, 10), LocalTime.of(17, 50))),
        Map.entry("9-10", new Period(9,  10, LocalTime.of(19, 0),  LocalTime.of(20, 50))),
        Map.entry("11-12",new Period(11, 12, LocalTime.of(21, 0),  LocalTime.of(22, 50))),
        Map.entry("13-14",new Period(13, 14, LocalTime.of(22, 0),  LocalTime.of(22, 50)))
    );

    private PeriodMapping() {
    }

    /**
     * Looks up a period by its start/end unit pair.
     *
     * @param beginUnit 1-based start unit
     * @param endUnit   1-based end unit
     * @return the mapped period
     * @throws IllegalArgumentException if the unit pair is not in the table
     * @since 0.1.0
     */
    public static Period lookup(int beginUnit, int endUnit) {
        Period p = TABLE.get(beginUnit + "-" + endUnit);
        if (p == null) {
            throw new IllegalArgumentException(
                "Unknown period unit pair: " + beginUnit + "-" + endUnit);
        }
        return p;
    }
}

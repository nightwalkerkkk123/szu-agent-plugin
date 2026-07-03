package edu.szu.agent.domain;

/**
 * Weekday of the SZU ehall schedule grid.
 *
 * <p>The encoding follows the ehall DOM convention ({@code data-week} attribute):
 * <ul>
 *   <li>{@code 1} = Monday</li>
 *   <li>{@code 2} = Tuesday</li>
 *   <li>{@code 3} = Wednesday</li>
 *   <li>{@code 4} = Thursday</li>
 *   <li>{@code 5} = Friday</li>
 *   <li>{@code 6} = Saturday</li>
 *   <li>{@code 7} = Sunday</li>
 * </ul>
 *
 * <p>Matches ISO 8601 / {@link java.time.DayOfWeek} numeric values for Monday-Saturday
 * but uses 7 (not 0) for Sunday to keep the ehall numbering.
 *
 * // 编程技术: 枚举
 *
 * @since 0.6.0
 * @author 王子豪
 */
public enum Weekday {
    MONDAY(1, "星期一"),
    TUESDAY(2, "星期二"),
    WEDNESDAY(3, "星期三"),
    THURSDAY(4, "星期四"),
    FRIDAY(5, "星期五"),
    SATURDAY(6, "星期六"),
    SUNDAY(7, "星期日");

    private final int code;
    private final String displayName;

    Weekday(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * Returns the ehall DOM {@code data-week} numeric code.
     *
     * @return 1-7, where 7 is Sunday
     * @since 0.6.0
     */
    public int code() {
        return code;
    }

    /**
     * Returns the Chinese display name used in the ehall DOM.
     *
     * @return e.g. {@code "星期三"}
     * @since 0.6.0
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Maps an ehall {@code data-week} code to its {@code Weekday}.
     *
     * @param code 1-7 (7 = Sunday)
     * @return the matching weekday
     * @throws IllegalArgumentException if {@code code} is not in [1, 7]
     * @since 0.6.0
     */
    public static Weekday of(int code) {
        for (Weekday w : values()) {
            if (w.code == code) {
                return w;
            }
        }
        throw new IllegalArgumentException("Invalid weekday code: " + code);
    }
}

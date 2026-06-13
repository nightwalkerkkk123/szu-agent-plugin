package edu.szu.agent.domain;

/**
 * Time slot — a fixed 1-hour window on the booking page.
 *
 * <p>The ehall page renders exactly 14 slots per day, every hour from
 * 08:00 to 22:00, with {@code id="HH:mm-HH:mm"} on each radio. Modeling
 * the slot as an enum eliminates an entire class of mistake (typed-but-
 * non-existent slots like 19:30-20:30) at the input boundary.
 *
 * <p>{@link #of(String)} parses the standard {@code "HH:mm-HH:mm"} CLI /
 * MCP wire format. It returns the same constant for both ends of the
 * page-supplied id.
 *
 * <p>Old code paths used {@code new TimeSlot(start, end)} as a record;
 * that constructor is gone. {@link #parse(String, String)} provides a
 * temporary equivalent for tests that still pass two strings.
 *
 * // Design Pattern: Type Object (per-slot metadata)
 * // 编程技术: 枚举 / 静态工厂方法 / Stream
 *
 * @since 0.1.0
 * @author 王子豪
 */
public enum TimeSlot {

    T08_09("08:00", "09:00"),
    T09_10("09:00", "10:00"),
    T10_11("10:00", "11:00"),
    T11_12("11:00", "12:00"),
    T12_13("12:00", "13:00"),
    T13_14("13:00", "14:00"),
    T14_15("14:00", "15:00"),
    T15_16("15:00", "16:00"),
    T16_17("16:00", "17:00"),
    T17_18("17:00", "18:00"),
    T18_19("18:00", "19:00"),
    T19_20("19:00", "20:00"),
    T20_21("20:00", "21:00"),
    T21_22("21:00", "22:00");

    private final String start;
    private final String end;

    TimeSlot(String start, String end) {
        this.start = start;
        this.end = end;
    }

    /** Start time, {@code HH:mm}. */
    public String start() {
        return start;
    }

    /** End time, {@code HH:mm}. */
    public String end() {
        return end;
    }

    /**
     * The page-rendered id / label, e.g. {@code "19:00-20:00"}. Used as
     * the {@code <label for="...">} target by {@code SelectTimeSlotStep}.
     */
    public String slotId() {
        return start + "-" + end;
    }

    /**
     * Parses the standard {@code "HH:mm-HH:mm"} wire format.
     *
     * @param raw e.g. {@code "19:00-20:00"}
     * @return the matching constant
     * @throws IllegalArgumentException if the format is wrong, the times
     *                                  aren't in chronological order, or
     *                                  no constant matches the slot ids
     */
    public static TimeSlot of(String raw) {
        if (raw == null || !raw.contains("-")) {
            throw new IllegalArgumentException(
                "Invalid time-slot format (expected HH:mm-HH:mm): " + raw);
        }
        String[] parts = raw.split("-", 2);
        return parse(parts[0].trim(), parts[1].trim());
    }

    /**
     * Looks up the constant whose {@link #start()} and {@link #end()}
     * match the supplied strings. Useful for tests still using the old
     * two-arg construction style.
     *
     * @throws IllegalArgumentException if no constant matches
     */
    public static TimeSlot parse(String start, String end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException(
                "TimeSlot start/end must not be null: start=" + start + ", end=" + end);
        }
        for (TimeSlot s : values()) {
            if (s.start.equals(start) && s.end.equals(end)) {
                return s;
            }
        }
        throw new IllegalArgumentException(
            "No such time slot (must be a 1-hour boundary 08:00-22:00): "
                + start + "-" + end);
    }
}

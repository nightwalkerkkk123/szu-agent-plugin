package edu.szu.agent.domain;

/**
 * Sport enum — sports supported by the SZU ehall booking system.
 *
 * <p>Per ADR-0006 §一.1: English constant name; {@code displayName} for humans;
 * {@code ehallCode} for the ehall wire format.
 *
 * <p>Per ADR-0001 D3: multiple sports enable demo rotation when one slot is
 * already booked.
 *
 * // 编程技术: 枚举(携带元数据字段)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public enum Sport {

    /** 网球. */
    TENNIS("网球", "tennis");

    private final String displayName;
    private final String ehallCode;

    Sport(String displayName, String ehallCode) {
        this.displayName = displayName;
        this.ehallCode = ehallCode;
    }

    /** Human-readable sport name (Chinese). */
    public String displayName() {
        return displayName;
    }

    /** Wire-format code sent to ehall. */
    public String ehallCode() {
        return ehallCode;
    }
}

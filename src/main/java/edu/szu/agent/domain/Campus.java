package edu.szu.agent.domain;

/**
 * Campus enum — SZU ehall booking system supported campuses.
 *
 * <p>Per ADR-0006 §一.1: English constant name; {@code displayName} for humans;
 * {@code ehallCode} for the ehall wire format. Wire format is the English
 * constant name (Jackson default).
 *
 * // 编程技术: 枚举(携带元数据字段)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public enum Campus {

    /** 粤海校区 — primary campus supported in Phase 1. */
    YUEHAI("粤海校区", "yuehai");

    private final String displayName;
    private final String ehallCode;

    Campus(String displayName, String ehallCode) {
        this.displayName = displayName;
        this.ehallCode = ehallCode;
    }

    /** Human-readable campus name (Chinese). */
    public String displayName() {
        return displayName;
    }

    /** Wire-format code sent to ehall. */
    public String ehallCode() {
        return ehallCode;
    }
}

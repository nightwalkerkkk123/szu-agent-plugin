package edu.szu.agent.domain;

/**
 * Sports offered at the 丽湖 campus. As of 2026-06: 15 entries (9 from
 * the first row + 6 from the second, per the {@code .frame-4} tile list).
 *
 * <p>Several names overlap with {@link YuehaiSport} (e.g. TENNIS,
 * BASKETBALL) but the underlying ehall venues are independent — that's
 * why each campus gets its own enum rather than a shared one.
 *
 * // Design Pattern: Type Object
 * // 编程技术: 枚举(携带元数据字段) / sealed-interface 实现
 *
 * @since 0.1.0
 * @author 王子豪
 */
public enum LihuSport implements Sport {

    BADMINTON("羽毛球", "badminton"),
    VOLLEYBALL("排球", "volleyball"),
    TENNIS("网球", "tennis"),
    BASKETBALL("篮球", "basketball"),
    SWIMMING("游泳", "swimming"),
    TABLE_TENNIS("乒乓球", "table_tennis"),
    DANCE("舞蹈", "dance"),
    POOL("桌球", "pool"),
    CYCLING("骑行", "cycling"),
    MAGIC_MIRROR("魔镜", "magic_mirror"),
    BOARD_GAME("桌游", "board_game"),
    GYM("健身房", "gym"),
    YOGA("瑜伽", "yoga"),
    PICKLEBALL("匹克球", "pickleball"),
    SHUTTLECOCK("毽球", "shuttlecock");

    private final String displayName;
    private final String ehallCode;

    LihuSport(String displayName, String ehallCode) {
        this.displayName = displayName;
        this.ehallCode = ehallCode;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String ehallCode() {
        return ehallCode;
    }

    @Override
    public Campus campus() {
        return Campus.LIHU;
    }
}

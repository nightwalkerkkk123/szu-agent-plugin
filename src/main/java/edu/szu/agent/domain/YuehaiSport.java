package edu.szu.agent.domain;

/**
 * Sports offered at the 粤海 campus. As of 2026-06: 9 entries (matches
 * the {@code .frame-4} tile list rendered by ehall after selecting 粤海).
 *
 * // Design Pattern: Type Object
 * // 编程技术: 枚举(携带元数据字段) / sealed-interface 实现
 *
 * @since 0.1.0
 * @author 王子豪
 */
public enum YuehaiSport implements Sport {

    BADMINTON("羽毛球", "badminton"),
    FOOTBALL("足球", "football"),
    VOLLEYBALL("排球", "volleyball"),
    TENNIS("网球", "tennis"),
    BASKETBALL("篮球", "basketball"),
    SQUASH("壁球", "squash"),
    GYM_HEAVY("一楼重量型健身", "gym_heavy"),
    GYM_AEROBIC("二楼有氧健身", "gym_aerobic"),
    SWIMMING("游泳", "swimming");

    private final String displayName;
    private final String ehallCode;

    YuehaiSport(String displayName, String ehallCode) {
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
        return Campus.YUEHAI;
    }
}

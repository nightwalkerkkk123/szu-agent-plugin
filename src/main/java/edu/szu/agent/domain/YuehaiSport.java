package edu.szu.agent.domain;

import edu.szu.agent.client.step.CapacityVenueSelector;
import edu.szu.agent.client.step.CourtListSelector;
import edu.szu.agent.client.step.VenueSelector;

/**
 * Sports offered at the 粤海 campus. As of 2026-06: 9 entries (matches
 * the {@code .frame-4} tile list rendered by ehall after selecting 粤海).
 *
 * <p>// Design Pattern: Type Object
 * <p>// 编程技术: 枚举(携带元数据字段) / sealed-interface 实现
 *
 * @since 0.1.0
 * @author 王子豪
 */
public enum YuehaiSport implements Sport {

    BADMINTON("羽毛球", "badminton", new CourtListSelector()),
    FOOTBALL("足球", "football", new CourtListSelector()),
    VOLLEYBALL("排球", "volleyball", new CourtListSelector()),
    TENNIS("网球", "tennis", new CourtListSelector()),
    BASKETBALL("篮球", "basketball", new CourtListSelector()),
    SQUASH("壁球", "squash", new CourtListSelector()),
    GYM_HEAVY("一楼重量型健身", "gym_heavy", new CapacityVenueSelector()),
    GYM_AEROBIC("二楼有氧健身", "gym_aerobic", new CapacityVenueSelector()),
    SWIMMING("游泳", "swimming", new CourtListSelector());

    private final String displayName;
    private final String ehallCode;
    private final VenueSelector venueSelector;

    YuehaiSport(String displayName, String ehallCode, VenueSelector venueSelector) {
        this.displayName = displayName;
        this.ehallCode = ehallCode;
        this.venueSelector = venueSelector;
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
    public VenueSelector venueSelector() {
        return venueSelector;
    }

    @Override
    public Campus campus() {
        return Campus.YUEHAI;
    }
}

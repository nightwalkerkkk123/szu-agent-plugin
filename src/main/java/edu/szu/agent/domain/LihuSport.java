package edu.szu.agent.domain;

import edu.szu.agent.client.step.CapacityVenueSelector;
import edu.szu.agent.client.step.CourtListSelector;
import edu.szu.agent.client.step.VenueSelector;

/**
 * Sports offered at the 丽湖 campus. As of 2026-06: 15 entries (9 from
 * the first row + 6 from the second, per the {@code .frame-4} tile list).
 *
 * <p>Several names overlap with {@link YuehaiSport} (e.g. TENNIS,
 * BASKETBALL) but the underlying ehall venues are independent — that's
 * why each campus gets its own enum rather than a shared one.
 *
 * <p>// 编程技术: 枚举(携带元数据字段) / sealed-interface 实现
 *
 * @since 0.1.0
 * @author 王子豪
 */
public enum LihuSport implements Sport {

    BADMINTON("羽毛球", "badminton", new CourtListSelector()),
    VOLLEYBALL("排球", "volleyball", new CourtListSelector()),
    TENNIS("网球", "tennis", new CourtListSelector()),
    BASKETBALL("篮球", "basketball", new CourtListSelector()),
    SWIMMING("游泳", "swimming", new CourtListSelector()),
    TABLE_TENNIS("乒乓球", "table_tennis", new CourtListSelector()),
    DANCE("舞蹈", "dance", new CourtListSelector()),
    POOL("桌球", "pool", new CourtListSelector()),
    CYCLING("骑行", "cycling", new CourtListSelector()),
    MAGIC_MIRROR("魔镜", "magic_mirror", new CourtListSelector()),
    BOARD_GAME("桌游", "board_game", new CourtListSelector()),
    GYM("健身房", "gym", new CapacityVenueSelector()),
    YOGA("瑜伽", "yoga", new CourtListSelector()),
    PICKLEBALL("匹克球", "pickleball", new CourtListSelector()),
    SHUTTLECOCK("毽球", "shuttlecock", new CourtListSelector());

    private final String displayName;
    private final String ehallCode;
    private final VenueSelector venueSelector;

    LihuSport(String displayName, String ehallCode, VenueSelector venueSelector) {
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
        return Campus.LIHU;
    }
}

package edu.szu.agent.cli;

import edu.szu.agent.client.http.EhallSportVenueClient;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.Sport;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;

import java.util.Objects;

/**
 * Normalizes CLI campus/sport input into the wire codes ({@code XQDM},
 * {@code XMDM}) and display names expected by ehall.
 *
 * <p>Resolution rules:
 * <ul>
 *   <li>Explicit {@code --campus-code}/{@code --sport-code} always win.</li>
 *   <li>Chinese campus names (粤海/丽湖) and sport names are mapped directly.</li>
 *   <li>Otherwise the input is treated as an enum constant name.</li>
 * </ul>
 *
 * // Design Pattern: Strategy (encapsulates a family of normalization rules)
 * // 编程技术: record / 卫语句(early return) / switch表达式
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class CampusSportCodeResolver {

    /**
     * Result of resolving campus and sport inputs.
     *
     * @param campusCode        ehall campus wire code
     * @param campusDisplayName human-readable campus name
     * @param sportCode         ehall sport wire code
     * @param sportDisplayName  human-readable sport name
     */
    public record Resolution(
        String campusCode,
        String campusDisplayName,
        String sportCode,
        String sportDisplayName
    ) {
    }

    private CampusSportCodeResolver() {
    }

    /**
     * Resolves the given inputs into wire codes and display names.
     *
     * @param campusName raw {@code --campus} value, or null
     * @param campusCode raw {@code --campus-code} value, or null
     * @param sportName  raw {@code --sport} value, or null
     * @param sportCode  raw {@code --sport-code} value, or null
     * @return resolved codes and display names
     * @throws BookingException if inputs are missing or invalid
     * @since 0.6.0
     * @author 王子豪
     */
    public static Resolution resolve(String campusName, String campusCode,
                                      String sportName, String sportCode) {
        ResolvedCampus campus = resolveCampus(campusName, campusCode);
        ResolvedSport sport = resolveSport(sportName, sportCode, campus.nameOrCode());
        return new Resolution(
            campus.code(), campus.displayName(),
            sport.code(), sport.displayName()
        );
    }

    private static ResolvedCampus resolveCampus(String campusName, String campusCode) {
        if (campusCode != null && !campusCode.isBlank()) {
            return new ResolvedCampus(campusCode, campusCode);
        }
        if (campusName != null && !campusName.isBlank()) {
            String normalized = campusName.trim().toUpperCase();
            if (normalized.contains("粤") || normalized.contains("海")) {
                return new ResolvedCampus("1", "粤海校区");
            }
            if (normalized.contains("丽") || normalized.contains("湖")) {
                return new ResolvedCampus("2", "丽湖校区");
            }
            Campus campus = Campus.valueOf(normalized);
            return new ResolvedCampus(campusCodeOf(campus), campus.displayName());
        }
        throw new BookingException(ErrorCode.INVALID_REQUEST,
            "Either --campus or --campus-code must be provided");
    }

    private static ResolvedSport resolveSport(String sportName, String sportCode,
                                               String campusNameOrCode) {
        if (sportCode != null && !sportCode.isBlank()) {
            return new ResolvedSport(sportCode, sportCode);
        }
        if (sportName != null && !sportName.isBlank()) {
            String raw = sportName.trim();
            String chineseCode = resolveChineseSport(raw);
            if (chineseCode != null) {
                return new ResolvedSport(chineseCode, resolveChineseSportDisplay(raw));
            }
            Campus campus = resolveCampusForEnum(campusNameOrCode);
            Sport sport = Sport.of(campus, raw.toUpperCase());
            return new ResolvedSport(sportCodeOf(sport), sport.displayName());
        }
        throw new BookingException(ErrorCode.INVALID_REQUEST,
            "Either --sport or --sport-code must be provided");
    }

    private static Campus resolveCampusForEnum(String campusNameOrCode) {
        Objects.requireNonNull(campusNameOrCode, "campusNameOrCode");
        String normalized = campusNameOrCode.trim().toUpperCase();
        if ("1".equals(normalized) || normalized.contains("粤") || normalized.contains("YUEHAI")) {
            return Campus.YUEHAI;
        }
        if ("2".equals(normalized) || normalized.contains("丽") || normalized.contains("LIHU")) {
            return Campus.LIHU;
        }
        return Campus.valueOf(normalized);
    }

    private static String campusCodeOf(Campus campus) {
        return EhallSportVenueClient.campusCode(campus);
    }

    private static String sportCodeOf(Sport sport) {
        return EhallSportVenueClient.sportCode(sport);
    }

    private static String resolveChineseSport(String chinese) {
        if (chinese.contains("羽毛球")) return "001";
        if (chinese.contains("足球")) return "002";
        if (chinese.contains("排球")) return "003";
        if (chinese.contains("网球")) return "004";
        if (chinese.contains("篮球")) return "005";
        if (chinese.contains("壁球")) return "006";
        if (chinese.contains("健身")) return "007";
        if (chinese.contains("游泳")) return "009";
        if (chinese.contains("乒乓球")) return "013";
        if (chinese.contains("舞蹈")) return "015";
        if (chinese.contains("桌球")) return "016";
        if (chinese.contains("瑜伽")) return "021";
        return null;
    }

    private static String resolveChineseSportDisplay(String chinese) {
        if (chinese.contains("羽毛球")) return "羽毛球";
        if (chinese.contains("足球")) return "足球";
        if (chinese.contains("排球")) return "排球";
        if (chinese.contains("网球")) return "网球";
        if (chinese.contains("篮球")) return "篮球";
        if (chinese.contains("壁球")) return "壁球";
        if (chinese.contains("健身")) return "健身";
        if (chinese.contains("游泳")) return "游泳";
        if (chinese.contains("乒乓球")) return "乒乓球";
        if (chinese.contains("舞蹈")) return "舞蹈";
        if (chinese.contains("桌球")) return "桌球";
        if (chinese.contains("瑜伽")) return "瑜伽";
        return chinese;
    }

    private record ResolvedCampus(String code, String displayName) {
        String nameOrCode() {
            return displayName.equals(code) ? code : displayName;
        }
    }

    private record ResolvedSport(String code, String displayName) {
    }
}

package edu.szu.agent.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.Sport;
import edu.szu.agent.domain.TimeSlot;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.json.JsonMappers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Direct HTTP client for the SZU ehall sports-venue booking module.
 *
 * <p>This is a minimal, HAR-driven implementation covering the core flow:
 * list available dates → list time slots → list open venues → submit booking.
 *
 * <p>As of this version the code mapping is intentionally limited to the
 * tennis / 粤海 campus combination captured in the reference HAR. Other
 * campus/sport combinations can be added by extending the mapping tables.
 *
 * <p>// Design Pattern: Adapter (HTTP transport replacing browser automation)
 * // 编程技术: Jackson JsonNode / record / Builder
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class EhallSportVenueClient {

    private static final String BASE = EhallAjaxHeaders.BASE;
    private static final String REFERER = EhallAjaxHeaders.REFERER;
    private static final Map<String, String> AJAX_HEADERS = EhallAjaxHeaders.standard();
    private static final String YYLX_DEFAULT = "1.0";
    private static final String YYLX_DISMISSAL = "2.0";

    private static final ObjectMapper MAPPER = JsonMappers.standard();

    private final CampusHttpClient http;

    /**
     * Parses a JSON string that may start with a UTF-8 BOM.
     *
     * <p>The ehall sports-venue endpoints occasionally return responses with a
     * leading {@code U+FEFF} byte order mark; Jackson rejects those unless the
     * marker is stripped first.
     *
     * @param body raw response body, possibly with a leading BOM
     * @return parsed JSON tree
     * @throws Exception if the body is not valid JSON
     * @since 0.6.0
     * @author 王子豪
     */
    private static JsonNode readTree(String body) throws Exception {
        return MAPPER.readTree(stripBom(body));
    }

    private static String stripBom(String body) {
        if (body != null && !body.isEmpty() && body.charAt(0) == '\uFEFF') {
            return body.substring(1);
        }
        return body;
    }

    /**
     * Creates a client backed by the given HTTP transport.
     *
     * @param http the HTTP client (must already carry a logged-in session)
     * @since 0.6.0
     * @author 王子豪
     */
    public EhallSportVenueClient(CampusHttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    /**
     * Returns the list of dates currently open for booking.
     *
     * @return list of ISO dates (e.g. {@code 2026-07-10})
     * @throws BookingException on network or parse failure
     * @since 0.6.0
     * @author 王子豪
     */
    public List<String> getAvailableDates() {
        String body = http.postForm(BASE + "/sportVenue/getRqList.do", REFERER, AJAX_HEADERS, Map.of());
        return parseDateList(body);
    }

    /**
     * Returns the master discovery payload: campuses, sports, and venue groups.
     *
     * <p>This is the same payload the ehall Vue app uses to render the booking
     * tiles. Calling it once gives enough metadata to drive any campus/sport
     * combination without hard-coding {@code XQDM}/{@code XMDM} mappings.
     *
     * @return the sport-venue discovery data
     * @throws BookingException on network or parse failure
     * @since 0.6.0
     * @author 王子豪
     */
    public SportVenueData getSportVenueData() {
        String body = http.postForm(BASE + "/sportVenue/getSportVenueData.do",
            REFERER, AJAX_HEADERS, Map.of());
        return parseSportVenueData(body);
    }

    /**
     * Returns the time-slot options for a given campus, sport, and date.
     *
     * @param campusCode campus code ({@code XQDM})
     * @param sportCode  sport code ({@code XMDM})
     * @param date       booking date
     * @return list of time-slot options
     * @throws BookingException on network or parse failure
     * @since 0.6.0
     * @author 王子豪
     */
    public List<TimeSlotOption> getTimeSlots(String campusCode, String sportCode, LocalDate date) {
        return getTimeSlots(campusCode, sportCode, date, YYLX_DEFAULT);
    }

    /**
     * Returns the time-slot options for a given campus, sport, date, and
     * booking type ({@code YYLX}).
     *
     * <p>{@code YYLX} controls whether the request is for package venues
     * ({@code 1.0}) or dismissal/scattered venues ({@code 2.0}). Some sports
     * such as gym and swimming only support {@code 2.0}.
     *
     * @param campusCode campus code ({@code XQDM})
     * @param sportCode  sport code ({@code XMDM})
     * @param date       booking date
     * @param yylx       booking type: {@code 1.0} or {@code 2.0}
     * @return list of time-slot options
     * @throws BookingException on network or parse failure
     * @since 0.6.0
     * @author 王子豪
     */
    public List<TimeSlotOption> getTimeSlots(String campusCode, String sportCode,
                                              LocalDate date, String yylx) {
        Map<String, String> form = Map.of(
            "XQ", campusCode,
            "YYRQ", date.toString(),
            "YYLX", normalizeYylx(yylx),
            "XMDM", sportCode
        );
        String body = http.postForm(BASE + "/sportVenue/getTimeList.do", REFERER, AJAX_HEADERS, form);
        return parseTimeSlots(body);
    }

    /**
     * Returns the open venues for a given campus, sport, date, and time slot.
     *
     * @param campusCode campus code ({@code XQDM})
     * @param sportCode  sport code ({@code XMDM})
     * @param date       booking date
     * @param timeSlot   1-hour time slot
     * @return list of venue options
     * @throws BookingException on network or parse failure
     * @since 0.6.0
     * @author 王子豪
     */
    public List<VenueOption> getOpeningRooms(String campusCode, String sportCode,
                                              LocalDate date, TimeSlot timeSlot) {
        return getOpeningRooms(campusCode, sportCode, date, timeSlot, null, YYLX_DEFAULT);
    }

    /**
     * Returns the open venues with an optional venue-group filter.
     *
     * @param campusCode      campus code ({@code XQDM})
     * @param sportCode       sport code ({@code XMDM})
     * @param date            booking date
     * @param timeSlot        1-hour time slot
     * @param venueGroupCode  optional venue group code ({@code CGBM}) to narrow results
     * @return list of venue options
     * @throws BookingException on network or parse failure
     * @since 0.6.0
     * @author 王子豪
     */
    public List<VenueOption> getOpeningRooms(String campusCode, String sportCode,
                                              LocalDate date, TimeSlot timeSlot,
                                              String venueGroupCode) {
        return getOpeningRooms(campusCode, sportCode, date, timeSlot, venueGroupCode, YYLX_DEFAULT);
    }

    /**
     * Returns the open venues with optional venue-group filter and booking type.
     *
     * @param campusCode      campus code ({@code XQDM})
     * @param sportCode       sport code ({@code XMDM})
     * @param date            booking date
     * @param timeSlot        1-hour time slot
     * @param venueGroupCode  optional venue group code ({@code CGBM}) to narrow results
     * @param yylx            booking type: {@code 1.0} or {@code 2.0}
     * @return list of venue options
     * @throws BookingException on network or parse failure
     * @since 0.6.0
     * @author 王子豪
     */
    public List<VenueOption> getOpeningRooms(String campusCode, String sportCode,
                                              LocalDate date, TimeSlot timeSlot,
                                              String venueGroupCode, String yylx) {
        Map<String, String> form = new java.util.HashMap<>(Map.of(
            "XMDM", sportCode,
            "YYRQ", date.toString(),
            "YYLX", normalizeYylx(yylx),
            "KSSJ", timeSlot.start(),
            "JSSJ", timeSlot.end(),
            "XQDM", campusCode
        ));
        if (venueGroupCode != null && !venueGroupCode.isBlank()) {
            form.put("CGBM", venueGroupCode);
        }
        String body = http.postForm(BASE + "/modules/sportVenue/getOpeningRoom.do",
            REFERER, AJAX_HEADERS, form);
        return parseVenues(body);
    }

    /**
     * Returns a page of the current user's booking history.
     *
     * @param pageNumber 1-based page index
     * @param pageSize   rows per page
     * @return page of booking records
     * @throws BookingException on network or parse failure
     * @since 0.6.0
     * @author 王子豪
     */
    public MyBookingsPage getMyBookings(int pageNumber, int pageSize) {
        Map<String, String> form = Map.of(
            "pageSize", String.valueOf(pageSize),
            "pageNumber", String.valueOf(pageNumber)
        );
        String body = http.postForm(BASE + "/modules/myBooking/myBookingInfo.do",
            REFERER, AJAX_HEADERS, form);
        return parseMyBookings(body);
    }

    /**
     * Submits a booking request.
     *
     * @param form booking parameters
     * @return the generated order id ({@code DHID})
     * @throws BookingException on network, parse, or business failure
     * @since 0.6.0
     * @author 王子豪
     */
    public String book(BookingForm form) {
        Map<String, String> post = Map.ofEntries(
            Map.entry("DHID", ""),
            Map.entry("YYRGH", form.studentId()),
            Map.entry("CYRS", form.companionCount()),
            Map.entry("YYRXM", form.displayName()),
            Map.entry("CGDM", form.venueGroupCode()),
            Map.entry("CDWID", form.venueWid()),
            Map.entry("XMDM", form.sportCode()),
            Map.entry("XQWID", form.campusCode()),
            Map.entry("KYYSJD", form.timeSlot().slotId()),
            Map.entry("YYRQ", form.date().toString()),
            Map.entry("YYLX", form.yylx()),
            Map.entry("YYKS", form.date() + " " + form.timeSlot().start()),
            Map.entry("YYJS", form.date() + " " + form.timeSlot().end()),
            Map.entry("PC_OR_PHONE", "pc")
        );
        String body = http.postForm(BASE + "/sportVenue/insertVenueBookingInfo.do",
            REFERER, AJAX_HEADERS, post);
        return parseBookingResult(body);
    }

    /**
     * Resolves a {@link Campus} to its ehall wire code ({@code XQDM}).
     *
     * @throws BookingException if the campus is not yet mapped
     * @since 0.6.0
     * @author 王子豪
     */
    public static String campusCode(Campus campus) {
        return switch (campus) {
            case YUEHAI -> "1";
            case LIHU -> "2";
        };
    }

    /**
     * Resolves a {@link Sport} to its ehall wire code ({@code XMDM}).
     *
     * <p>Mappings are taken from the live {@code getSportVenueData.do} discovery
     * endpoint. For sports not yet enumerated here, use
     * {@code --sport-code <XMDM>} in the CLI instead of the enum name.
     *
     * @throws BookingException if the sport is not yet mapped
     * @since 0.6.0
     * @author 王子豪
     */
    public static String sportCode(Sport sport) {
        return switch (sport) {
            case edu.szu.agent.domain.YuehaiSport s -> switch (s) {
                case BADMINTON -> "001";
                case FOOTBALL -> "002";
                case VOLLEYBALL -> "003";
                case TENNIS -> "004";
                case BASKETBALL -> "005";
                case SQUASH -> "006";
                case GYM_HEAVY -> "007";
                case GYM_AEROBIC -> "008";
                case SWIMMING -> "009";
            };
            case edu.szu.agent.domain.LihuSport s -> switch (s) {
                case BADMINTON -> "001";
                case VOLLEYBALL -> "003";
                case TENNIS -> "004";
                case BASKETBALL -> "005";
                case SWIMMING -> "009";
                case TABLE_TENNIS -> "013";
                case DANCE -> "015";
                case POOL -> "016";
                case CYCLING -> "017";
                case MAGIC_MIRROR -> "018";
                case BOARD_GAME -> "019";
                case GYM -> "020";
                case YOGA -> "021";
                case PICKLEBALL -> "030";
                case SHUTTLECOCK -> "034";
            };
        };
    }

    private List<String> parseDateList(String body) {
        try {
            JsonNode root = readTree(body);
            List<String> dates = new ArrayList<>(root.size());
            for (JsonNode node : root) {
                dates.add(node.asText());
            }
            return dates;
        } catch (Exception e) {
            throw new BookingException(ErrorCode.NETWORK_TIMEOUT,
                "Failed to parse available dates: " + e.getMessage(), e);
        }
    }

    private SportVenueData parseSportVenueData(String body) {
        try {
            JsonNode root = readTree(body);
            // The endpoint returns the payload at the top level, not wrapped in "datas".
            List<SportInfo> sports = parseSportInfoList(root.path("xmList"));
            List<CampusInfo> campuses = parseCampusList(root.path("campusList"));
            List<VenueGroupInfo> packageVenues = parseVenueGroupList(root.path("packageVenueList"));
            List<VenueGroupInfo> dismissalVenues = parseVenueGroupList(root.path("dismissalVenueList"));
            return new SportVenueData(campuses, sports, packageVenues, dismissalVenues);
        } catch (BookingException e) {
            throw e;
        } catch (Exception e) {
            throw new BookingException(ErrorCode.NETWORK_TIMEOUT,
                "Failed to parse sport-venue data: " + e.getMessage(), e);
        }
    }

    private List<SportInfo> parseSportInfoList(JsonNode array) {
        List<SportInfo> list = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            list.add(new SportInfo(
                text(node, "WID"),
                text(node, "XMDM"),
                text(node, "XMMC"),
                text(node, "XQDM"),
                text(node, "DCFS"),
                text(node, "STORE_NAME"),
                text(node, "SORT")
            ));
        }
        return list;
    }

    private List<CampusInfo> parseCampusList(JsonNode array) {
        List<CampusInfo> list = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            list.add(new CampusInfo(
                text(node, "WID"),
                text(node, "XQDM"),
                text(node, "XQDM_DISPLAY"),
                text(node, "SORT")
            ));
        }
        return list;
    }

    private List<VenueGroupInfo> parseVenueGroupList(JsonNode array) {
        List<VenueGroupInfo> list = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            list.add(new VenueGroupInfo(
                text(node, "WID"),
                text(node, "CGBM"),
                text(node, "CGMC"),
                text(node, "SSXQ"),
                text(node, "XM")
            ));
        }
        return list;
    }

    private List<TimeSlotOption> parseTimeSlots(String body) {
        try {
            JsonNode root = readTree(body);
            List<TimeSlotOption> list = new ArrayList<>(root.size());
            for (JsonNode node : root) {
                list.add(new TimeSlotOption(
                    text(node, "WID"),
                    text(node, "CODE"),
                    text(node, "text"),
                    node.path("disabled").asBoolean(true),
                    text(node, "STATE_EXPLAIN")
                ));
            }
            return list;
        } catch (Exception e) {
            throw new BookingException(ErrorCode.NETWORK_TIMEOUT,
                "Failed to parse time slots: " + e.getMessage(), e);
        }
    }

    private List<VenueOption> parseVenues(String body) {
        try {
            JsonNode rows = readTree(body)
                .path("datas")
                .path("getOpeningRoom")
                .path("rows");
            List<VenueOption> list = new ArrayList<>(rows.size());
            for (JsonNode node : rows) {
                list.add(new VenueOption(
                    text(node, "WID"),
                    text(node, "CDMC"),
                    text(node, "CGBM"),
                    text(node, "XQDM"),
                    text(node, "XMDM"),
                    text(node, "text"),
                    node.path("disabled").asBoolean(true),
                    text(node, "STATE_EXPLAIN")
                ));
            }
            return list;
        } catch (Exception e) {
            throw new BookingException(ErrorCode.NETWORK_TIMEOUT,
                "Failed to parse venues: " + e.getMessage(), e);
        }
    }

    private String parseBookingResult(String body) {
        try {
            JsonNode root = readTree(body);
            String code = root.path("code").asText("");
            String msg = root.path("msg").asText("");
            if (!"0".equals(code)) {
                throw bookingExceptionForCode(code, msg);
            }
            return root.path("data").path("DHID").asText();
        } catch (BookingException e) {
            throw e;
        } catch (Exception e) {
            throw new BookingException(ErrorCode.NETWORK_TIMEOUT,
                "Failed to parse booking response: " + e.getMessage(), e);
        }
    }

    private static BookingException bookingExceptionForCode(String code, String msg) {
        String normalized = msg == null ? "" : msg;
        if ("E111080000000".equals(code) || normalized.contains("操作过于频繁")) {
            return new BookingException(ErrorCode.RATE_LIMITED,
                "Booking rate limited: [" + code + "] " + normalized);
        }
        return new BookingException(ErrorCode.VENUE_OCCUPIED,
            "Booking rejected: [" + code + "] " + normalized);
    }

    private MyBookingsPage parseMyBookings(String body) {
        try {
            JsonNode info = readTree(body)
                .path("datas")
                .path("myBookingInfo");
            int totalSize = info.path("totalSize").asInt(0);
            int pageNumber = info.path("pageNumber").asInt(1);
            int pageSize = info.path("pageSize").asInt(10);
            JsonNode rows = info.path("rows");
            List<BookingRecord> list = new ArrayList<>(rows.size());
            for (JsonNode node : rows) {
                list.add(new BookingRecord(
                    text(node, "DHID"),
                    text(node, "WID"),
                    text(node, "XQWID"),
                    text(node, "XQWID_DISPLAY"),
                    text(node, "XMDM"),
                    text(node, "XMDM_DISPLAY"),
                    text(node, "CGDM"),
                    text(node, "CGDM_DISPLAY"),
                    text(node, "CDWID"),
                    text(node, "CDWID_DISPLAY"),
                    text(node, "YYLX"),
                    text(node, "YYZT"),
                    text(node, "YYZT_DISPLAY"),
                    text(node, "YYSJD"),
                    text(node, "CJSJ"),
                    text(node, "ACTULAMT"),
                    text(node, "VERIFY_TYPE"),
                    text(node, "SFZF")
                ));
            }
            return new MyBookingsPage(totalSize, pageNumber, pageSize, list);
        } catch (Exception e) {
            throw new BookingException(ErrorCode.NETWORK_TIMEOUT,
                "Failed to parse my-bookings response: " + e.getMessage(), e);
        }
    }

    private static String normalizeYylx(String yylx) {
        if (yylx == null || yylx.isBlank()) {
            return YYLX_DEFAULT;
        }
        String trimmed = yylx.trim();
        if ("2".equals(trimmed) || "2.0".equals(trimmed) || "散场".equals(trimmed)) {
            return YYLX_DISMISSAL;
        }
        if ("1".equals(trimmed) || "1.0".equals(trimmed) || "包场".equals(trimmed)) {
            return YYLX_DEFAULT;
        }
        return trimmed;
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    /**
     * A bookable time-slot option.
     */
    public record TimeSlotOption(String wid, String code, String text,
                                  boolean disabled, String stateExplain) {
    }

    /**
     * A bookable venue option.
     */
    public record VenueOption(String wid, String name, String venueGroupCode,
                               String campusCode, String sportCode, String text,
                               boolean disabled, String stateExplain) {
    }

    /**
     * A campus entry returned by {@link #getSportVenueData()}.
     */
    public record CampusInfo(String wid, String code, String name, String sort) {
    }

    /**
     * A sport entry returned by {@link #getSportVenueData()}.
     */
    public record SportInfo(String wid, String sportCode, String sportName,
                             String campusCodes, String dcfs, String icon, String sort) {
    }

    /**
     * A venue group entry returned by {@link #getSportVenueData()}.
     */
    public record VenueGroupInfo(String wid, String venueGroupCode, String venueGroupName,
                                  String campusCode, String sportCodes) {
    }

    /**
     * Master discovery payload returned by {@link #getSportVenueData()}.
     */
    public record SportVenueData(List<CampusInfo> campuses,
                                  List<SportInfo> sports,
                                  List<VenueGroupInfo> packageVenues,
                                  List<VenueGroupInfo> dismissalVenues) {
    }

    /**
     * A booking record returned by {@link #getMyBookings(int, int)}.
     */
    public record BookingRecord(
        String dhid,
        String wid,
        String campusCode,
        String campusName,
        String sportCode,
        String sportName,
        String venueGroupCode,
        String venueGroupName,
        String venueWid,
        String venueName,
        String yylx,
        String yyzt,
        String statusText,
        String timeSlot,
        String createTime,
        String amount,
        String verifyType,
        String paidFlag
    ) {
    }

    /**
     * Page of booking records.
     */
    public record MyBookingsPage(
        int totalSize,
        int pageNumber,
        int pageSize,
        List<BookingRecord> rows
    ) {
    }

    /**
     * Parameters required to submit a booking.
     */
    public record BookingForm(String studentId, String displayName, String campusCode,
                               String sportCode, String venueGroupCode, String venueWid,
                               LocalDate date, TimeSlot timeSlot, String yylx,
                               String companionCount) {

        /**
         * Canonical constructor with defaults.
         */
        public BookingForm {
            if (companionCount == null || companionCount.isBlank()) {
                companionCount = "";
            }
            if (yylx == null || yylx.isBlank()) {
                yylx = YYLX_DEFAULT;
            }
        }
    }
}

package edu.szu.agent.client.http;

import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.LihuSport;
import edu.szu.agent.domain.Sport;
import edu.szu.agent.domain.TimeSlot;
import edu.szu.agent.domain.YuehaiSport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EhallSportVenueClient")
class EhallSportVenueClientTest {

    @Test
    @DisplayName("粤海校区映射为 XQDM 1")
    void mapsYuehaiCampus() {
        assertThat(EhallSportVenueClient.campusCode(Campus.YUEHAI)).isEqualTo("1");
    }

    @Test
    @DisplayName("网球映射为 XMDM 004")
    void mapsTennisSport() {
        Sport tennis = YuehaiSport.TENNIS;
        assertThat(EhallSportVenueClient.sportCode(tennis)).isEqualTo("004");
    }

    @Test
    @DisplayName("健身项目映射为对应的 XMDM")
    void mapsGymSports() {
        assertThat(EhallSportVenueClient.sportCode(YuehaiSport.GYM_HEAVY)).isEqualTo("007");
        assertThat(EhallSportVenueClient.sportCode(YuehaiSport.GYM_AEROBIC)).isEqualTo("008");
    }

    @Test
    @DisplayName("所有粤海/丽湖项目均已映射到 XMDM")
    void mapsAllKnownSports() {
        assertThat(EhallSportVenueClient.sportCode(YuehaiSport.BADMINTON)).isEqualTo("001");
        assertThat(EhallSportVenueClient.sportCode(YuehaiSport.FOOTBALL)).isEqualTo("002");
        assertThat(EhallSportVenueClient.sportCode(YuehaiSport.VOLLEYBALL)).isEqualTo("003");
        assertThat(EhallSportVenueClient.sportCode(YuehaiSport.TENNIS)).isEqualTo("004");
        assertThat(EhallSportVenueClient.sportCode(YuehaiSport.BASKETBALL)).isEqualTo("005");
        assertThat(EhallSportVenueClient.sportCode(YuehaiSport.SQUASH)).isEqualTo("006");
        assertThat(EhallSportVenueClient.sportCode(YuehaiSport.GYM_HEAVY)).isEqualTo("007");
        assertThat(EhallSportVenueClient.sportCode(YuehaiSport.GYM_AEROBIC)).isEqualTo("008");
        assertThat(EhallSportVenueClient.sportCode(YuehaiSport.SWIMMING)).isEqualTo("009");

        assertThat(EhallSportVenueClient.sportCode(LihuSport.TABLE_TENNIS)).isEqualTo("013");
        assertThat(EhallSportVenueClient.sportCode(LihuSport.DANCE)).isEqualTo("015");
        assertThat(EhallSportVenueClient.sportCode(LihuSport.GYM)).isEqualTo("020");
        assertThat(EhallSportVenueClient.sportCode(LihuSport.PICKLEBALL)).isEqualTo("030");
    }

    @Test
    @DisplayName("丽湖校区映射为 XQDM 2")
    void mapsLihuCampus() {
        assertThat(EhallSportVenueClient.campusCode(Campus.LIHU)).isEqualTo("2");
    }

    @Test
    @DisplayName("BookingForm 提供默认值")
    void bookingFormDefaults() {
        EhallSportVenueClient.BookingForm form = new EhallSportVenueClient.BookingForm(
            "2023150090", "王子豪", "1", "004", "015", "wid",
            LocalDate.of(2026, 7, 10), TimeSlot.T17_18, null, null);

        assertThat(form.yylx()).isEqualTo("1.0");
        assertThat(form.companionCount()).isEmpty();
    }

    @Test
    @DisplayName("解析我的预约列表")
    void parsesMyBookings() {
        CampusHttpClient http = mock(CampusHttpClient.class);
        when(http.postForm(any(), any(), any(), any())).thenReturn("""
            {"datas":{"myBookingInfo":{
              "totalSize":336,
              "pageNumber":1,
              "pageSize":10,
              "rows":[
                {"DHID":"202607092034012630","XQWID":"1","XQWID_DISPLAY":"粤海校区",
                 "XMDM":"004","XMDM_DISPLAY":"网球","CGDM":"015","CGDM_DISPLAY":"北区网球场",
                 "CDWID":"wid","CDWID_DISPLAY":"北区网球1号场","YYLX":"1.0","YYZT":"CG_YY",
                 "YYZT_DISPLAY":"已预约","YYSJD":"17:00-18:00","CJSJ":"2026-07-09 20:34:01","ZHJE":"0.0"}
              ]
            }},"code":"0"}
            """);

        EhallSportVenueClient client = new EhallSportVenueClient(http);
        EhallSportVenueClient.MyBookingsPage page = client.getMyBookings(1, 10);

        assertThat(page.totalSize()).isEqualTo(336);
        assertThat(page.rows()).hasSize(1);
        EhallSportVenueClient.BookingRecord r = page.rows().get(0);
        assertThat(r.dhid()).isEqualTo("202607092034012630");
        assertThat(r.campusName()).isEqualTo("粤海校区");
        assertThat(r.sportName()).isEqualTo("网球");
        assertThat(r.statusText()).isEqualTo("已预约");
        assertThat(r.timeSlot()).isEqualTo("17:00-18:00");
    }

    @Test
    @DisplayName("解析运动/校区/场馆发现数据")
    void parsesSportVenueData() {
        CampusHttpClient http = mock(CampusHttpClient.class);
        when(http.postForm(any(), any(), any(), any())).thenReturn("""
            {
              "campusList":[{"XQDM":"1","XQDM_DISPLAY":"粤海校区"}],
              "xmList":[{"XMDM":"001","XMMC":"羽毛球","STORE_NAME":"icon.png","DCFS":"1.0","XQDM":"1"}],
              "packageVenueList":[{"WID":"v1","CGBM":"001","CGMC":"运动广场东馆羽毛球场","SSXQ":"1","XM":"001"}],
              "dismissalVenueList":[]
            }
            """);

        EhallSportVenueClient client = new EhallSportVenueClient(http);
        EhallSportVenueClient.SportVenueData data = client.getSportVenueData();

        assertThat(data.campuses()).hasSize(1);
        assertThat(data.campuses().get(0).code()).isEqualTo("1");
        assertThat(data.campuses().get(0).name()).isEqualTo("粤海校区");

        assertThat(data.sports()).hasSize(1);
        EhallSportVenueClient.SportInfo sport = data.sports().get(0);
        assertThat(sport.sportCode()).isEqualTo("001");
        assertThat(sport.sportName()).isEqualTo("羽毛球");
        assertThat(sport.dcfs()).isEqualTo("1.0");

        assertThat(data.packageVenues()).hasSize(1);
        EhallSportVenueClient.VenueGroupInfo venue = data.packageVenues().get(0);
        assertThat(venue.venueGroupCode()).isEqualTo("001");
        assertThat(venue.venueGroupName()).isEqualTo("运动广场东馆羽毛球场");
    }

    @Test
    @DisplayName("getOpeningRooms 支持 CGBM 过滤")
    void openingRoomsSupportsVenueGroupFilter() {
        CampusHttpClient http = mock(CampusHttpClient.class);
        when(http.postForm(any(), any(), any(), any())).thenReturn("""
            {"datas":{"getOpeningRoom":{"rows":[
              {"WID":"wid","CDMC":"北区网球1号场","CGBM":"015","XQDM":"1","XMDM":"004",
               "text":"1/1","disabled":false,"STATE_EXPLAIN":"SYS_OPEN"}
            ],"pageNumber":0,"pageSize":0,"totalSize":1}},"code":"0"}
            """);

        EhallSportVenueClient client = new EhallSportVenueClient(http);
        List<EhallSportVenueClient.VenueOption> venues = client.getOpeningRooms(
            "1", "004", LocalDate.of(2026, 7, 10), TimeSlot.T08_09, "015");

        assertThat(venues).hasSize(1);
        assertThat(venues.get(0).name()).isEqualTo("北区网球1号场");
    }

    @Test
    @DisplayName("getTimeSlots 按 YYLX 传入正确的预约类型")
    void timeSlotsPassesYylx() {
        CampusHttpClient http = mock(CampusHttpClient.class);
        when(http.postForm(any(), any(), any(), any())).thenReturn("[]");

        EhallSportVenueClient client = new EhallSportVenueClient(http);
        client.getTimeSlots("1", "007", LocalDate.of(2026, 7, 10), "2.0");

        verify(http).postForm(eq("https://ehall.szu.edu.cn/qljfwapp/sys/lwSzuCgyy/sportVenue/getTimeList.do"),
            any(), any(), eq(Map.of(
                "XQ", "1",
                "YYRQ", "2026-07-10",
                "YYLX", "2.0",
                "XMDM", "007")));
    }

    @Test
    @DisplayName("getOpeningRooms 按 YYLX 传入正确的预约类型")
    void openingRoomsPassesYylx() {
        CampusHttpClient http = mock(CampusHttpClient.class);
        when(http.postForm(any(), any(), any(), any())).thenReturn("""
            {"datas":{"getOpeningRoom":{"rows":[
              {"WID":"wid","CDMC":"健身房01","CGBM":"007","XQDM":"1","XMDM":"007",
               "text":"1/1","disabled":false,"STATE_EXPLAIN":"SYS_OPEN"}
            ],"pageNumber":0,"pageSize":0,"totalSize":1}},"code":"0"}
            """);

        EhallSportVenueClient client = new EhallSportVenueClient(http);
        client.getOpeningRooms("1", "007", LocalDate.of(2026, 7, 10), TimeSlot.T08_09, null, "2.0");

        verify(http).postForm(eq("https://ehall.szu.edu.cn/qljfwapp/sys/lwSzuCgyy/modules/sportVenue/getOpeningRoom.do"),
            any(), any(), eq(Map.of(
                "XMDM", "007",
                "YYRQ", "2026-07-10",
                "YYLX", "2.0",
                "KSSJ", "08:00",
                "JSSJ", "09:00",
                "XQDM", "1")));
    }

    @Test
    @DisplayName("BookingForm 接受自定义 YYLX")
    void bookingFormAcceptsCustomYylx() {
        EhallSportVenueClient.BookingForm form = new EhallSportVenueClient.BookingForm(
            "2023150090", "王子豪", "1", "007", "007", "wid",
            LocalDate.of(2026, 7, 10), TimeSlot.T08_09, "2.0", null);

        assertThat(form.yylx()).isEqualTo("2.0");
    }
}

package edu.szu.agent.client.http;

import edu.szu.agent.account.Account;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.TimeSlot;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("VenueBookingService")
class VenueBookingServiceTest {

    @Test
    @DisplayName("books a venue when session is already valid")
    void booksWhenSessionValid(@TempDir Path temp) {
        Account account = new Account("2023150090", "secret", "Test User");
        SessionStore store = new SessionStore(temp, "2023150090");
        EhallSessionManager sessionManager = mock(EhallSessionManager.class);
        CampusHttpClient http = mock(CampusHttpClient.class);
        CookieJar jar = new CookieJar();

        when(sessionManager.ensureSession(any())).thenReturn(http);
        when(http.cookieJar()).thenReturn(jar);
        when(http.postForm(any(), any(), any(), any())).thenReturn(
            // getAvailableDates
            "[\"2026-07-11\"]",
            // getTimeSlots
            "[{\"WID\":\"twid\",\"CODE\":\"14:00-15:00\",\"text\":\"14:00-15:00\",\"disabled\":false}]",
            // getOpeningRooms
            "{\"datas\":{\"getOpeningRoom\":{\"rows\":[{\"WID\":\"vwid\",\"CDMC\":\"健身房1\",\"CGBM\":\"cg\",\"XQDM\":\"1\",\"XMDM\":\"007\",\"disabled\":false}]}}}",
            // book
            "{\"code\":\"0\",\"data\":{\"DHID\":\"202607102256205748\"}}"
        );

        VenueBookingService service = new VenueBookingService(account, store, sessionManager);
        RawBookingRequest request = new RawBookingRequest(
            "1", "007", LocalDate.of(2026, 7, 11),
            TimeSlot.of("14:00-15:00"), 1, "2.0");

        String dhid = service.book(request);

        assertThat(dhid).isEqualTo("202607102256205748");
        assertThat(store.exists()).isTrue();
    }

    @Test
    @DisplayName("throws when date is not open for booking")
    void rejectsUnavailableDate(@TempDir Path temp) {
        Account account = new Account("2023150090", "secret", "Test User");
        SessionStore store = new SessionStore(temp, "2023150090");
        EhallSessionManager sessionManager = mock(EhallSessionManager.class);
        CampusHttpClient http = mock(CampusHttpClient.class);

        when(sessionManager.ensureSession(any())).thenReturn(http);
        when(http.cookieJar()).thenReturn(new CookieJar());
        when(http.postForm(any(), any(), any(), any())).thenReturn(
            "[\"2026-07-12\"]"
        );

        VenueBookingService service = new VenueBookingService(account, store, sessionManager);
        RawBookingRequest request = new RawBookingRequest(
            "1", "007", LocalDate.of(2026, 7, 11),
            TimeSlot.of("14:00-15:00"), 1, "2.0");

        assertThatThrownBy(() -> service.book(request))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code()).isEqualTo(ErrorCode.NO_AVAILABLE_VENUE));
    }

    @Test
    @DisplayName("persists refreshed session")
    void persistsRefreshedSession(@TempDir Path temp) {
        Account account = new Account("2023150090", "secret", "Test User");
        SessionStore store = new SessionStore(temp, "2023150090");
        EhallSessionManager sessionManager = mock(EhallSessionManager.class);
        CampusHttpClient http = mock(CampusHttpClient.class);
        CookieJar refreshedJar = new CookieJar();

        when(sessionManager.ensureSession(any())).thenReturn(http);
        when(http.cookieJar()).thenReturn(refreshedJar);
        when(http.postForm(any(), any(), any(), any())).thenReturn(
            "[\"2026-07-11\"]",
            "[{\"WID\":\"twid\",\"CODE\":\"14:00-15:00\",\"text\":\"14:00-15:00\",\"disabled\":false}]",
            "{\"datas\":{\"getOpeningRoom\":{\"rows\":[{\"WID\":\"vwid\",\"CDMC\":\"健身房1\",\"CGBM\":\"cg\",\"XQDM\":\"1\",\"XMDM\":\"007\",\"disabled\":false}]}}}",
            "{\"code\":\"0\",\"data\":{\"DHID\":\"DHID\"}}"
        );

        VenueBookingService service = new VenueBookingService(account, store, sessionManager);
        RawBookingRequest request = new RawBookingRequest(
            "1", "007", LocalDate.of(2026, 7, 11),
            TimeSlot.of("14:00-15:00"), 1, "2.0");

        service.book(request);

        verify(sessionManager).ensureSession(any());
        assertThat(store.exists()).isTrue();
    }
}

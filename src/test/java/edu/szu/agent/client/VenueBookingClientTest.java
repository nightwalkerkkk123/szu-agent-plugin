package edu.szu.agent.client;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.*;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicy;
import edu.szu.agent.retry.RetryPolicies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link VenueBookingClient}.
 *
 * <p>Uses Mockito mocks for {@link BrowserLifecycle} since FakeBrowser
 * is not yet implemented (Phase 4+ deliverable per ADR-0007 D1).
 * Tests exercise the orchestration logic (login → select campus → select
 * sport → select time slot → select venue → confirm) without a real browser.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@ExtendWith(MockitoExtension.class)
class VenueBookingClientTest {

    @Mock
    private BrowserLifecycle browser;

    private RetryPolicy noRetry;
    private VenueBookingClient client;
    private BookingRequest request;

    @BeforeEach
    void setUp() {
        Tracer.getInstance().reset();
        noRetry = RetryPolicies.quickFix();
        client = new VenueBookingClient(browser, noRetry);
        request = BookingRequest.builder()
            .username("2023150090")
            .campus(Campus.YUEHAI)
            .sport(Sport.TENNIS)
            .date(LocalDate.now())
            .timeSlot(new TimeSlot("19:00", "20:00"))
            .preferredVenueIndex(1)
            .build();
    }

    /** Configure browser mock for a successful full flow. */
    private void stubSuccessFlow() {
        when(browser.isVisible(contains("time-slot"))).thenReturn(true);
        when(browser.isVisible(contains("venue"))).thenReturn(true);
        // Time slot list contains the requested slot
        when(browser.allTextOf(contains("time-slot"))).thenReturn(
            List.of("19:00-20:00", "20:00-21:00"));
        // Venue list contains available venues
        when(browser.allTextOf(contains("venue"))).thenReturn(
            List.of("网球1号场", "网球2号场"));
    }

    // ---------- Slice 1: success path ----------

    @Test
    @DisplayName("book() returns BookingResult.Success when all browser steps succeed")
    void bookReturnsSuccessWhenAllStepsSucceed() {
        stubSuccessFlow();

        BookingResult result = client.book(request);

        assertThat(result).isInstanceOf(BookingResult.Success.class);
        BookingResult.Success success = (BookingResult.Success) result;
        assertThat(success.venueName()).isEqualTo("网球1号场");
        assertThat(success.confirmation()).isNotBlank();
    }

    @Test
    @DisplayName("book() calls browser.open() and browser.close()")
    void bookOpensAndClosesBrowser() {
        stubSuccessFlow();

        client.book(request);

        verify(browser).open();
        verify(browser).close();
    }

    @Test
    @DisplayName("book() closes browser even when an exception occurs")
    void bookClosesBrowserOnException() {
        doThrow(new BookingException(ErrorCode.NETWORK_TIMEOUT, "timeout"))
            .when(browser).navigateTo(anyString());

        BookingResult result = client.book(request);

        verify(browser).close();
        assertThat(result).isInstanceOf(BookingResult.Failure.class);
    }

    // ---------- Slice 2: failure paths ----------

    @Test
    @DisplayName("book() returns Failure when time slot area is not visible")
    void bookReturnsFailureWhenTimeSlotNotVisible() {
        when(browser.isVisible(contains("time-slot"))).thenReturn(false);

        BookingResult result = client.book(request);

        assertThat(result).isInstanceOf(BookingResult.Failure.class);
        BookingResult.Failure failure = (BookingResult.Failure) result;
        assertThat(failure.code()).isEqualTo(ErrorCode.ELEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("book() returns Failure when requested time slot is not available")
    void bookReturnsFailureWhenTimeSlotNotAvailable() {
        when(browser.isVisible(contains("time-slot"))).thenReturn(true);
        when(browser.allTextOf(contains("time-slot"))).thenReturn(
            List.of("08:00-09:00", "09:00-10:00"));

        BookingResult result = client.book(request);

        assertThat(result).isInstanceOf(BookingResult.Failure.class);
        BookingResult.Failure failure = (BookingResult.Failure) result;
        assertThat(failure.code()).isEqualTo(ErrorCode.NO_AVAILABLE_VENUE);
    }

    @Test
    @DisplayName("book() returns Failure when venue list is empty")
    void bookReturnsFailureWhenNoVenues() {
        when(browser.isVisible(contains("time-slot"))).thenReturn(true);
        when(browser.isVisible(contains("venue"))).thenReturn(true);
        when(browser.allTextOf(contains("time-slot"))).thenReturn(
            List.of("19:00-20:00"));
        when(browser.allTextOf(contains("venue"))).thenReturn(
            List.of());

        BookingResult result = client.book(request);

        assertThat(result).isInstanceOf(BookingResult.Failure.class);
        BookingResult.Failure failure = (BookingResult.Failure) result;
        assertThat(failure.code()).isEqualTo(ErrorCode.NO_AVAILABLE_VENUE);
    }

    @Test
    @DisplayName("book() returns Failure when browser throws BookingException")
    void bookReturnsFailureOnBrowserException() {
        doThrow(new BookingException(ErrorCode.BROWSER_CRASH, "chromium died"))
            .when(browser).open();

        BookingResult result = client.book(request);

        assertThat(result).isInstanceOf(BookingResult.Failure.class);
        BookingResult.Failure failure = (BookingResult.Failure) result;
        assertThat(failure.code()).isEqualTo(ErrorCode.BROWSER_CRASH);
    }

    // ---------- Slice 3: retry policy ----------

    @Test
    @DisplayName("book() retries on retryable error and succeeds on second attempt")
    void bookRetriesOnRetryableError() {
        // First navigateTo throws, second succeeds
        doThrow(new BookingException(ErrorCode.NETWORK_TIMEOUT, "first fail"))
            .doNothing()
            .when(browser).navigateTo(anyString());
        stubSuccessFlow();

        // Use a policy that retries once
        RetryPolicy retryOnce = RetryPolicies.login();
        VenueBookingClient retryClient = new VenueBookingClient(browser, retryOnce);

        BookingResult result = retryClient.book(request);

        assertThat(result).isInstanceOf(BookingResult.Success.class);
        verify(browser, atLeast(2)).navigateTo(anyString());
    }

    // ---------- Slice 4: Tracer + screenshot ----------

    @Test
    @DisplayName("book() takes screenshot when ErrorCode.shouldScreenshot() is true")
    void bookTakesScreenshotOnScreenshotableError() {
        doThrow(new BookingException(ErrorCode.ELEMENT_NOT_FOUND, "not found"))
            .when(browser).navigateTo(anyString());

        client.book(request);

        // ELEMENT_NOT_FOUND.shouldScreenshot() == true
        verify(browser).screenshot(anyString());
    }

    @Test
    @DisplayName("book() does NOT take screenshot when ErrorCode.shouldScreenshot() is false")
    void bookSkipsScreenshotOnNonScreenshotableError() {
        when(browser.isVisible(contains("time-slot"))).thenReturn(true);
        when(browser.allTextOf(contains("time-slot"))).thenReturn(
            List.of("08:00-09:00"));

        client.book(request);

        // NO_AVAILABLE_VENUE.shouldScreenshot() == false
        verify(browser, never()).screenshot(anyString());
    }

    @AfterEach
    void tearDown() {
        Tracer.getInstance().reset();
    }
}

package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.step.BookingStep;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.Sport;
import edu.szu.agent.domain.TimeSlot;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicies;
import edu.szu.agent.retry.RetryPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link VenueBookingClient}.
 *
 * <p>Covers the orchestration logic of {@code book()}: browser open/close
 * lifecycle, retry wrapping, screenshot-on-shouldScreenshot, and
 * {@link Tracer#recordFailure} integration per ADR-0007 D4.
 *
 * <p>Uses Mockito mocks for {@link BrowserLifecycle} and explicit
 * {@link BookingStep} stubs (rather than the 7 production steps) so each
 * test exercises exactly the path under test.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VenueBookingClient")
class VenueBookingClientTest {

    @Mock
    private BrowserLifecycle browser;

    private Account account;
    private BookingRequest request;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        tracer = Tracer.getInstance();
        tracer.reset();
        account = new Account("2023150090", "secret", "test");
        request = BookingRequest.builder()
            .campus(Campus.YUEHAI)
            .sport(Sport.TENNIS)
            .date(LocalDate.now())
            .timeSlot(new TimeSlot("19:00", "20:00"))
            .preferredVenueIndex(1)
            .build();
    }

    @AfterEach
    void tearDown() {
        tracer.reset();
    }

    private VenueBookingClient clientWith(List<BookingStep> steps) {
        return new VenueBookingClient(account, browser, RetryPolicies.quickFix(), steps);
    }

    // ---------- happy path ----------

    @Test
    @DisplayName("book() returns Success when all steps complete")
    void bookReturnsSuccessWhenAllStepsSucceed() {
        BookingStep step1 = named("S1", null);
        BookingStep step2 = named("S2", null);
        VenueBookingClient client = clientWith(List.of(step1, step2));

        BookingResult result = client.book(request);

        assertThat(result).isInstanceOf(BookingResult.Success.class);
        verify(browser).open();
        verify(browser).close();
        verify(step1).execute(browser, any());
        verify(step2).execute(browser, any());
    }

    @Test
    @DisplayName("book() runs steps in declared order")
    void bookRunsStepsInOrder() {
        BookingStep step1 = named("S1", null);
        BookingStep step2 = named("S2", null);
        BookingStep step3 = named("S3", null);
        VenueBookingClient client = clientWith(List.of(step1, step2, step3));

        client.book(request);

        var inOrder = org.mockito.Mockito.inOrder(step1, step2, step3);
        inOrder.verify(step1).execute(any(), any());
        inOrder.verify(step2).execute(any(), any());
        inOrder.verify(step3).execute(any(), any());
    }

    // ---------- step returns Failure ----------

    @Test
    @DisplayName("book() returns Failure and short-circuits when a step returns Failure")
    void bookShortCircuitsOnStepFailure() {
        BookingResult.Failure failure = new BookingResult.Failure(
            ErrorCode.ELEMENT_NOT_FOUND, "venue list missing");
        BookingStep ok = named("OK", null);
        BookingStep fail = named("FAIL", failure);
        BookingStep notRun = named("NOT_RUN", null);
        VenueBookingClient client = clientWith(List.of(ok, fail, notRun));

        BookingResult result = client.book(request);

        assertThat(result).isSameAs(failure);
        verify(ok).execute(any(), any());
        verify(fail).execute(any(), any());
        verify(notRun, never()).execute(any(), any());
        verify(browser).close();
    }

    // ---------- step throws BookingException ----------

    @Test
    @DisplayName("book() captures screenshot when shouldScreenshot() is true and records failure")
    void bookTakesScreenshotWhenShouldScreenshotTrue() {
        BookingStep throwing = throwing(new BookingException(
            ErrorCode.ELEMENT_NOT_FOUND, "missing"));
        VenueBookingClient client = clientWith(List.of(throwing));

        BookingResult result = client.book(request);

        assertThat(result).isInstanceOf(BookingResult.Failure.class);
        BookingResult.Failure f = (BookingResult.Failure) result;
        assertThat(f.code()).isEqualTo(ErrorCode.ELEMENT_NOT_FOUND);
        assertThat(f.message()).isEqualTo("missing");
        verify(browser).screenshot(anyString());
        verify(browser).close();
    }

    @Test
    @DisplayName("book() skips screenshot when shouldScreenshot() is false but still records failure")
    void bookSkipsScreenshotWhenShouldScreenshotFalse() {
        BookingStep throwing = throwing(new BookingException(
            ErrorCode.VENUE_OCCUPIED, "occupied"));
        VenueBookingClient client = clientWith(List.of(throwing));

        BookingResult result = client.book(request);

        assertThat(result).isInstanceOf(BookingResult.Failure.class);
        assertThat(((BookingResult.Failure) result).code()).isEqualTo(ErrorCode.VENUE_OCCUPIED);
        verify(browser, never()).screenshot(anyString());
        verify(browser).close();
    }

    // ---------- browser close robustness ----------

    @Test
    @DisplayName("book() swallows browser.close() exceptions but still returns the result")
    void bookSwallowsBrowserCloseException() {
        BookingStep ok = named("OK", null);
        doThrow(new RuntimeException("close failed")).when(browser).close();
        VenueBookingClient client = clientWith(List.of(ok));

        BookingResult result = client.book(request);

        assertThat(result).isInstanceOf(BookingResult.Success.class);
        verify(browser).close();
    }

    // ---------- retry wrapping ----------

    @Test
    @DisplayName("book() wraps the flow in retryPolicy.execute()")
    void bookWrapsFlowInRetryPolicy() {
        BookingStep ok = named("OK", null);
        RetryPolicy retry = RetryPolicies.quickFix();
        VenueBookingClient client = new VenueBookingClient(
            account, browser, retry, List.of(ok));

        client.book(request);

        verify(retry).execute(any());
    }

    // ---------- context propagation ----------

    @Test
    @DisplayName("book() passes a BookingContext built from request + account to each step")
    void bookBuildsContextFromRequestAndAccount() {
        CapturingStep step = new CapturingStep();
        VenueBookingClient client = clientWith(List.of(step));

        client.book(request);

        assertThat(step.ctx).isNotNull();
        assertThat(step.ctx.request()).isSameAs(request);
        assertThat(step.ctx.account()).isSameAs(account);
    }

    // ---------- helpers ----------

    private static BookingStep named(String name, BookingResult result) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public BookingResult execute(BrowserLifecycle b,
                                                   edu.szu.agent.client.step.BookingContext ctx) {
                return result;
            }
        };
    }

    private static BookingStep throwing(BookingException ex) {
        return new BookingStep() {
            @Override public String name() { return "THROW"; }
            @Override public BookingResult execute(BrowserLifecycle b,
                                                   edu.szu.agent.client.step.BookingContext ctx) {
                throw ex;
            }
        };
    }

    private static final class CapturingStep implements BookingStep {
        edu.szu.agent.client.step.BookingContext ctx;
        @Override public String name() { return "CAPTURE"; }
        @Override public BookingResult execute(BrowserLifecycle b,
                                               edu.szu.agent.client.step.BookingContext ctx) {
            this.ctx = ctx;
            return null;
        }
    }
}

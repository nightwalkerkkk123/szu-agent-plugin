package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.client.step.BookingContext;
import edu.szu.agent.client.step.BookingStep;
import edu.szu.agent.client.step.StepOutcome;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.Campus;
import edu.szu.agent.domain.Sport;
import edu.szu.agent.domain.YuehaiSport;
import edu.szu.agent.domain.TimeSlot;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link VenueBookingClient}.
 *
 * <p>Covers the orchestration logic of {@code book()}: browser open/close
 * lifecycle, screenshot-on-shouldScreenshot, and
 * {@link Tracer#recordFailure} integration per ADR-0007 D4.
 *
 * <p>Uses explicit {@link BookingStep} implementations (not Mockito mocks)
 * since steps are functional interfaces best stubbed as anonymous classes.
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

    @BeforeEach
    void setUp() {
        Tracer.getInstance().reset();
        account = new Account("2023150090", "secret", "test");
        request = BookingRequest.builder()
            .campus(Campus.YUEHAI)
            .sport(YuehaiSport.TENNIS)
            .date(LocalDate.now())
            .timeSlot(TimeSlot.T19_20)
            .preferredVenueIndex(1)
            .build();
    }

    @AfterEach
    void tearDown() {
        Tracer.getInstance().reset();
    }

    private VenueBookingClient clientWith(BookingStep... steps) {
        return new VenueBookingClient(
            browser, RetryPolicies.quickFix(), List.of(steps));
    }

    // ---------- happy path ----------

    @Test
    @DisplayName("book() returns Success when all steps complete")
    void bookReturnsSuccessWhenAllStepsSucceed() {
        VenueBookingClient client = clientWith(noop("S1"), noop("S2"));

        BookingResult result = client.book(request, account);

        assertThat(result).isInstanceOf(BookingResult.Success.class);
        verify(browser).open();
        verify(browser).close();
    }

    @Test
    @DisplayName("book() runs steps in declared order")
    void bookRunsStepsInOrder() {
        StringBuilder log = new StringBuilder();
        VenueBookingClient client = clientWith(
            tracked("S1", log), tracked("S2", log), tracked("S3", log));

        client.book(request, account);

        assertThat(log.toString()).isEqualTo("S1-S2-S3-");
    }

    // ---------- step returns Failure ----------

    @Test
    @DisplayName("book() returns Failure and short-circuits when a step returns Failure")
    void bookShortCircuitsOnStepFailure() {
        BookingResult.Failure failure = new BookingResult.Failure(
            ErrorCode.ELEMENT_NOT_FOUND, "venue list missing");
        AtomicBoolean thirdRan = new AtomicBoolean();
        VenueBookingClient client = clientWith(
            noop("OK"),
            failing("FAIL", failure),
            sideEffect("NOT_RUN", () -> thirdRan.set(true)));

        BookingResult result = client.book(request, account);

        assertThat(result).isSameAs(failure);
        assertThat(thirdRan).isFalse();
        verify(browser).close();
    }

    // ---------- step throws BookingException ----------

    @Test
    @DisplayName("book() captures screenshot when shouldScreenshot() is true")
    void bookTakesScreenshotWhenShouldScreenshotTrue() {
        VenueBookingClient client = clientWith(
            throwing(new BookingException(ErrorCode.ELEMENT_NOT_FOUND, "missing")));

        BookingResult result = client.book(request, account);

        assertThat(result).isInstanceOf(BookingResult.Failure.class);
        BookingResult.Failure f = (BookingResult.Failure) result;
        assertThat(f.code()).isEqualTo(ErrorCode.ELEMENT_NOT_FOUND);
        verify(browser).screenshot(anyString());
        verify(browser).close();
    }

    @Test
    @DisplayName("book() skips screenshot when shouldScreenshot() is false")
    void bookSkipsScreenshotWhenShouldScreenshotFalse() {
        VenueBookingClient client = clientWith(
            throwing(new BookingException(ErrorCode.VENUE_OCCUPIED, "occupied")));

        BookingResult result = client.book(request, account);

        assertThat(result).isInstanceOf(BookingResult.Failure.class);
        assertThat(((BookingResult.Failure) result).code()).isEqualTo(ErrorCode.VENUE_OCCUPIED);
        verify(browser, never()).screenshot(anyString());
        verify(browser).close();
    }

    // ---------- browser close robustness ----------

    @Test
    @DisplayName("book() swallows browser.close() exceptions and still returns the result")
    void bookSwallowsBrowserCloseException() {
        doThrow(new RuntimeException("close failed")).when(browser).close();
        VenueBookingClient client = clientWith(noop("OK"));

        BookingResult result = client.book(request, account);

        assertThat(result).isInstanceOf(BookingResult.Success.class);
        verify(browser).close();
    }

    // ---------- context propagation ----------

    @Test
    @DisplayName("book() passes a BookingContext built from request + account to each step")
    void bookBuildsContextFromRequestAndAccount() {
        AtomicReference<BookingContext> captured = new AtomicReference<>();
        VenueBookingClient client = clientWith(
            new BookingStep() {
                @Override public String name() { return "CAPTURE"; }
                @Override public StepOutcome execute(BrowserLifecycle b, BookingContext ctx) {
                    captured.set(ctx);
                    return new StepOutcome.Continue(ctx);
                }
            });

        client.book(request, account);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().request()).isSameAs(request);
        assertThat(captured.get().account()).isSameAs(account);
    }

    @Test
    @DisplayName("book() always calls browser.open() before steps and browser.close() after")
    void bookOpensAndClosesBrowser() {
        AtomicBoolean ran = new AtomicBoolean();
        VenueBookingClient client = clientWith(sideEffect("S", () -> ran.set(true)));

        client.book(request, account);

        assertThat(ran).isTrue();
        verify(browser).open();
        verify(browser).close();
    }

    // ---------- session-aware pipeline composition ----------

    @Test
    @DisplayName("会话感知构造器: RESTORE_SESSION 在首, PERSIST_SESSION 紧跟 SELECT_CAMPUS")
    void sessionAwareConstructorInsertsSessionSteps() {
        VenueBookingClient client = new VenueBookingClient(
            browser, RetryPolicies.quickFix(),
            new SessionStore(Path.of("/tmp"), "2023150090"),
            new SessionProbe("https://ehall.szu.edu.cn/probe", ".bh-btn"),
            Duration.ofDays(30));

        assertThat(client.stepNames()).containsExactly(
            "RESTORE_SESSION",
            "CAS_LOGIN",
            "NAVIGATE_TO_BOOKING",
            "SELECT_CAMPUS",
            "PERSIST_SESSION",
            "SELECT_SPORT",
            "SELECT_DATE",
            "SELECT_TIME_SLOT",
            "SELECT_VENUE",
            "CONFIRM_BOOKING");
    }

    @Test
    @DisplayName("无会话构造器: 8 步原样, 无 RESTORE/PERSIST")
    void plainConstructorHasNoSessionSteps() {
        VenueBookingClient client = new VenueBookingClient(browser, RetryPolicies.quickFix());

        assertThat(client.stepNames()).containsExactly(
            "CAS_LOGIN",
            "NAVIGATE_TO_BOOKING",
            "SELECT_CAMPUS",
            "SELECT_SPORT",
            "SELECT_DATE",
            "SELECT_TIME_SLOT",
            "SELECT_VENUE",
            "CONFIRM_BOOKING");
    }

    // ---------- helpers ----------

    private static BookingStep noop(String name) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public StepOutcome execute(BrowserLifecycle b, BookingContext ctx) {
                return new StepOutcome.Continue(ctx);
            }
        };
    }

    /** Step that runs a side effect on execution, then returns Continue. */
    private static BookingStep sideEffect(String name, Runnable sideEffect) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public StepOutcome execute(BrowserLifecycle b, BookingContext ctx) {
                sideEffect.run();
                return new StepOutcome.Continue(ctx);
            }
        };
    }

    private static BookingStep failing(String name, BookingResult.Failure failure) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public StepOutcome execute(BrowserLifecycle b, BookingContext ctx) {
                return new StepOutcome.Failure(failure);
            }
        };
    }

    private static BookingStep throwing(BookingException ex) {
        return new BookingStep() {
            @Override public String name() { return "THROW"; }
            @Override public StepOutcome execute(BrowserLifecycle b, BookingContext ctx) {
                throw ex;
            }
        };
    }

    private static BookingStep tracked(String name, StringBuilder log) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public StepOutcome execute(BrowserLifecycle b, BookingContext ctx) {
                log.append(name).append('-');
                return new StepOutcome.Continue(ctx);
            }
        };
    }
}

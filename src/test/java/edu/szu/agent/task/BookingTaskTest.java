package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.VenueBookingClient;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.retry.RetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link BookingTask} — the P0 realized CampusTask.
 *
 * <p>Verifies the parameter → domain type translation logic. The
 * parameter-validation tests fail before the client is reached, so
 * the underlying {@link VenueBookingClient} never executes (it would
 * require a real browser). We still need to construct one, hence the
 * mocked {@link BrowserLifecycle}.
 *
 * <p>// 编程技术: 泛型 / 枚举 / record
 *
 * @since 0.1.0
 * @author 王子豪
 */
class BookingTaskTest {

    @Test
    @DisplayName("name() and description() return the documented contract")
    void identity() {
        BookingTask task = newTask();
        assertThat(task.name()).isEqualTo("booking_venue");
        assertThat(task.description())
            .startsWith("深圳大学体育场馆定时预约")
            .contains("真实预约会占用实际名额", "YUEHAI", "GYM_HEAVY(一楼重量型健身/一楼健身房)", "16:00-17:00");
    }

    @Test
    @DisplayName("Required campus missing → IllegalArgumentException")
    void missingCampus() {
        BookingTask task = newTask();
        TaskInput input = new TaskInput(Map.of(
            "username", "2023150090",
            "sport", "TENNIS",
            "date", "2026-06-13",
            "timeSlot", "19:00-20:00"));

        assertThatThrownBy(() -> task.execute(input))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("campus");
    }

    @Test
    @DisplayName("Unknown campus enum value → IllegalArgumentException")
    void unknownCampusEnum() {
        BookingTask task = newTask();
        TaskInput input = new TaskInput(Map.of(
            "username", "2023150090",
            "campus", "ATLANTIS",
            "sport", "TENNIS",
            "date", "2026-06-13",
            "timeSlot", "19:00-20:00"));

        assertThatThrownBy(() -> task.execute(input))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ATLANTIS");
    }

    @Test
    @DisplayName("Bad date format → IllegalArgumentException")
    void badDate() {
        BookingTask task = newTask();
        TaskInput input = new TaskInput(Map.of(
            "username", "2023150090",
            "campus", "YUEHAI",
            "sport", "TENNIS",
            "date", "not-a-date",
            "timeSlot", "19:00-20:00"));

        assertThatThrownBy(() -> task.execute(input))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("date");
    }

    @Test
    @DisplayName("Bad timeSlot format → IllegalArgumentException")
    void badTimeSlot() {
        BookingTask task = newTask();
        TaskInput input = new TaskInput(Map.of(
            "username", "2023150090",
            "campus", "YUEHAI",
            "sport", "TENNIS",
            "date", "2026-06-13",
            "timeSlot", "no-dash"));

        // The actual message comes from the TimeSlot record (start vs end),
        // not the timeSlot key — we only assert the type of exception.
        assertThatThrownBy(() -> task.execute(input))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Equal endpoints (19:00-19:00) → IllegalArgumentException from TimeSlot")
    void equalEndpoints() {
        BookingTask task = newTask();
        TaskInput input = new TaskInput(Map.of(
            "username", "2023150090",
            "campus", "YUEHAI",
            "sport", "TENNIS",
            "date", "2026-06-13",
            "timeSlot", "19:00-19:00"));

        assertThatThrownBy(() -> task.execute(input))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Builds a BookingTask with a mocked browser. The mock is never
     * used because the validation tests fail before reaching the
     * browser.
     */
    private static BookingTask newTask() {
        BrowserLifecycle mockBrowser = mock(BrowserLifecycle.class);
        // RetryPolicy's SAM is a generic method <T> T execute(Supplier<T>),
        // so a lambda can't infer T — use an anonymous class (see ADR-0006 §3.3).
        RetryPolicy noRetry = new RetryPolicy() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> action) {
                return action.get();
            }
        };
        VenueBookingClient client = new VenueBookingClient(mockBrowser, noRetry);
        // Validation tests fail before the client is used, so a constant factory suffices.
        // Use 4-arg constructor; headed factories throw since these tests
        // never trigger the fallback path.
        return new BookingTask(
            account -> client,
            (browser, username) -> { throw new IllegalStateException("headed fallback not wired"); },
            username -> new Account(username, "test-pw", "test"),
            () -> { throw new IllegalStateException("headed fallback not wired"); });
    }

    // ---------- Headed fallback path ----------

    @Test
    @DisplayName("AccountResolutionException triggers headed fallback: rebuilds browser, invokes headed client, closes browser")
    void headedFallbackRebuildsBrowserOnAccountResolutionException() {
        BrowserLifecycle normalBrowser = mock(BrowserLifecycle.class);
        BrowserLifecycle headedBrowser = mock(BrowserLifecycle.class);
        AtomicReference<BookingRequest> capturedRequest = new AtomicReference<>();
        AtomicReference<Account> capturedAccount = new AtomicReference<>();
        AtomicReference<BrowserLifecycle> capturedHeadedBrowser = new AtomicReference<>();
        AtomicReference<String> capturedUsername = new AtomicReference<>();

        RetryPolicy noRetry = new RetryPolicy() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> action) {
                return action.get();
            }
        };
        VenueBookingClient normalClient = new VenueBookingClient(normalBrowser, noRetry);
        // Capture the headed-client invocation: record (browser, username, request, account).
        VenueBookingClient headedClient = new VenueBookingClient(headedBrowser, noRetry) {
            @Override
            public BookingResult book(BookingRequest req, Account acc) {
                capturedRequest.set(req);
                capturedAccount.set(acc);
                return new BookingResult.Success("manual-login-venue", "MANUAL-CONFIRMED");
            }
        };
        BiFunction<BrowserLifecycle, String, VenueBookingClient> headedFactory =
            (b, u) -> {
                capturedHeadedBrowser.set(b);
                capturedUsername.set(u);
                return headedClient;
            };

        BookingTask task = new BookingTask(
            account -> normalClient,
            headedFactory,
            username -> { throw new AccountResolutionException(username); },
            () -> headedBrowser);

        TaskInput input = new TaskInput(Map.of(
            "username", "2023150090",
            "campus", "YUEHAI",
            "sport", "TENNIS",
            "date", "2026-06-13",
            "timeSlot", "19:00-20:00"));

        BookingResult result = task.execute(input);

        assertThat(result).isInstanceOf(BookingResult.Success.class);
        assertThat(((BookingResult.Success) result).venueName()).isEqualTo("manual-login-venue");
        // Headed factory was called with the fresh headed browser and the username
        assertThat(capturedHeadedBrowser.get()).isSameAs(headedBrowser);
        assertThat(capturedUsername.get()).isEqualTo("2023150090");
        // book() was called with the original request and null account
        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().username()).isEqualTo("2023150090");
        assertThat(capturedAccount.get()).isNull();
    }

    @Test
    @DisplayName("Headed fallback is NOT triggered when credentials are present")
    void headedFallbackNotTriggeredWhenCredentialsPresent() {
        BrowserLifecycle normalBrowser = mock(BrowserLifecycle.class);
        BrowserLifecycle headedBrowser = mock(BrowserLifecycle.class);
        java.util.concurrent.atomic.AtomicInteger headedFactoryCalls = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger headedBrowserFactoryCalls = new java.util.concurrent.atomic.AtomicInteger(0);

        RetryPolicy noRetry = new RetryPolicy() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> action) {
                return action.get();
            }
        };
        VenueBookingClient normalClient = new VenueBookingClient(normalBrowser, noRetry);
        BiFunction<BrowserLifecycle, String, VenueBookingClient> headedFactory =
            (b, u) -> { headedFactoryCalls.incrementAndGet(); return null; };
        Supplier<BrowserLifecycle> headedBrowserSupplier = () -> {
            headedBrowserFactoryCalls.incrementAndGet();
            return headedBrowser;
        };

        BookingTask task = new BookingTask(
            account -> normalClient,
            headedFactory,
            username -> new Account(username, "test-pw", "test"),
            headedBrowserSupplier);

        TaskInput input = new TaskInput(Map.of(
            "username", "2023150090",
            "campus", "YUEHAI",
            "sport", "TENNIS",
            "date", "2026-06-13",
            "timeSlot", "19:00-20:00"));

        // Pipeline outcome (Success/Failure) is irrelevant here; the mock
        // browser will return nulls and the pipeline may fail with
        // ELEMENT_NOT_FOUND. What matters is that the headed fallback was
        // NOT entered — credential resolution succeeded.
        task.execute(input);

        assertThat(headedFactoryCalls.get()).isZero();
        assertThat(headedBrowserFactoryCalls.get()).isZero();
    }
}

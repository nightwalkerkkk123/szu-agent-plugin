package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.FakeBrowser;
import edu.szu.agent.client.VenueBookingClient;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.retry.RetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BookingTask} with a {@link FakeBrowser}.
 *
 * <p>Covers the full path: parameter validation → client.book() →
 * 7-step pipeline → BookingResult. With FakeBrowser returning
 * non-empty venue options, the entire happy path is exercised.
 *
 * <p>// 编程技术: 泛型 / record / Lambda
 *
 * @since 0.6.0
 * @author 王子豪
 */
class BookingTaskIntegrationTest {

    @Test
    @DisplayName("Happy path: valid input + fake browser → BookingResult.Success")
    void happyPath() {
        FakeBrowser browser = new FakeBrowser();
        Account account = new Account("2023150090", "fake-pw", "test");
        RetryPolicy noRetry = noRetry();
        VenueBookingClient client = new VenueBookingClient(browser, noRetry);
        BookingTask task = new BookingTask(
            acct -> client,
            (b, u) -> { throw new IllegalStateException("headed fallback not wired"); },
            uname -> account,
            () -> { throw new IllegalStateException("headed fallback not wired"); });

        TaskInput input = new TaskInput(Map.of(
            "username", "2023150090",
            "campus", "YUEHAI",
            "sport", "TENNIS",
            "date", "2026-06-13",
            "timeSlot", "19:00-20:00"));

        BookingResult result = task.execute(input);

        assertThat(result).isInstanceOf(BookingResult.Success.class);
        BookingResult.Success success = (BookingResult.Success) result;
        assertThat(success.venueName()).isEqualTo("网球场1号");
        assertThat(success.confirmation()).startsWith("CONFIRMED-");
        assertThat(browser.isOpened()).isTrue();
    }

    @Test
    @DisplayName("preferredVenue = 2 picks the second venue")
    void preferredVenueIndex() {
        FakeBrowser browser = new FakeBrowser();
        Account account = new Account("2023150090", "fake-pw", "test");
        RetryPolicy noRetry = noRetry();
        VenueBookingClient client = new VenueBookingClient(browser, noRetry);
        BookingTask task = new BookingTask(
            acct -> client,
            (b, u) -> { throw new IllegalStateException("headed fallback not wired"); },
            uname -> account,
            () -> { throw new IllegalStateException("headed fallback not wired"); });

        TaskInput input = new TaskInput(Map.of(
            "username", "2023150090",
            "campus", "YUEHAI",
            "sport", "TENNIS",
            "date", "2026-06-13",
            "timeSlot", "19:00-20:00",
            "preferredVenue", "2"));

        BookingResult result = task.execute(input);

        assertThat(result).isInstanceOf(BookingResult.Success.class);
        BookingResult.Success success = (BookingResult.Success) result;
        assertThat(success.venueName()).isEqualTo("网球场2号");
    }

    @Test
    @DisplayName("Bad sport enum → IllegalArgumentException (param validation)")
    void badSportEnum() {
        FakeBrowser browser = new FakeBrowser();
        Account account = new Account("2023150090", "fake-pw", "test");
        RetryPolicy noRetry = noRetry();
        VenueBookingClient client = new VenueBookingClient(browser, noRetry);
        BookingTask task = new BookingTask(
            acct -> client,
            (b, u) -> { throw new IllegalStateException("headed fallback not wired"); },
            uname -> account,
            () -> { throw new IllegalStateException("headed fallback not wired"); });

        TaskInput input = new TaskInput(Map.of(
            "username", "2023150090",
            "campus", "YUEHAI",
            "sport", "POLO",  // not in Sport enum
            "date", "2026-06-13",
            "timeSlot", "19:00-20:00"));

        try {
            task.execute(input);
            org.junit.jupiter.api.Assertions.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("POLO");
        }
    }

    @Test
    @DisplayName("Missing username → IllegalArgumentException")
    void missingUsername() {
        FakeBrowser browser = new FakeBrowser();
        Account account = new Account("2023150090", "fake-pw", "test");
        RetryPolicy noRetry = noRetry();
        VenueBookingClient client = new VenueBookingClient(browser, noRetry);
        BookingTask task = new BookingTask(
            acct -> client,
            (b, u) -> { throw new IllegalStateException("headed fallback not wired"); },
            uname -> account,
            () -> { throw new IllegalStateException("headed fallback not wired"); });

        TaskInput input = new TaskInput(Map.of(
            "campus", "YUEHAI",
            "sport", "TENNIS",
            "date", "2026-06-13",
            "timeSlot", "19:00-20:00"));

        try {
            task.execute(input);
            org.junit.jupiter.api.Assertions.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("username");
        }
    }

    @Test
    @DisplayName("Empty venue list in fake → BookingResult.Failure (ELEMENT_NOT_FOUND)")
    void emptyVenueList() {
        FakeBrowser browser = new FakeBrowser(List.of());  // empty!
        Account account = new Account("2023150090", "fake-pw", "test");
        RetryPolicy noRetry = noRetry();
        VenueBookingClient client = new VenueBookingClient(browser, noRetry);
        BookingTask task = new BookingTask(
            acct -> client,
            (b, u) -> { throw new IllegalStateException("headed fallback not wired"); },
            uname -> account,
            () -> { throw new IllegalStateException("headed fallback not wired"); });

        TaskInput input = new TaskInput(Map.of(
            "username", "2023150090",
            "campus", "YUEHAI",
            "sport", "TENNIS",
            "date", "2026-06-13",
            "timeSlot", "19:00-20:00"));

        BookingResult result = task.execute(input);

        // The pipeline should return a Failure (no venues to pick).
        // We don't pin the exact error code (that's a pipeline detail);
        // we just verify the result is a Failure.
        assertThat(result).isInstanceOf(BookingResult.Failure.class);
    }

    /**
     * RetryPolicy's SAM is a generic method <T> T execute(Supplier<T>),
     * so a lambda can't infer T — use an anonymous class (per ADR-0006 §3.3).
     */
    private static RetryPolicy noRetry() {
        return new RetryPolicy() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> action) {
                return action.get();
            }
        };
    }
}

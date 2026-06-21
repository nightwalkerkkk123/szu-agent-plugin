package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.VenueBookingClient;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.retry.RetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        assertThat(task.description()).isEqualTo("体育场馆定时预约");
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
        return new BookingTask(client, username -> new Account(username, "test-pw", "test"));
    }
}

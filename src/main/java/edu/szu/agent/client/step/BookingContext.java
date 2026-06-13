package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;

/**
 * Mutable context passed through the booking pipeline.
 *
 * <p>Each step reads from {@link #request} and writes intermediate results
 * here (e.g. {@link #selectedVenue}). The {@link #account} field holds
 * resolved credentials injected by the caller before the pipeline runs.
 * This keeps {@link BookingRequest} free of credential types.
 *
 * // 编程技术: record(不可变外壳) / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class BookingContext {

    private final BookingRequest request;
    private final Account account;
    private String selectedVenue;
    private BookingResult lastFailure;

    public BookingContext(BookingRequest request, Account account) {
        this.request = request;
        this.account = account;
    }

    /**
     * Convenience constructor for steps that don't need credentials
     * (e.g. {@link SelectCampusStep}, {@link SelectSportStep},
     * {@link SelectTimeSlotStep}, {@link SelectVenueStep},
     * {@link ConfirmBookingStep}). Equivalent to passing {@code null}
     * for {@code account}.
     */
    public BookingContext(BookingRequest request) {
        this(request, null);
    }

    public BookingRequest request() {
        return request;
    }

    public Account account() {
        return account;
    }

    public String selectedVenue() {
        return selectedVenue;
    }

    public void selectedVenue(String selectedVenue) {
        this.selectedVenue = selectedVenue;
    }

    public BookingResult lastFailure() {
        return lastFailure;
    }

    public void lastFailure(BookingResult.Failure lastFailure) {
        this.lastFailure = lastFailure;
    }

    public BookingResult.Success success(String venueName, String confirmation) {
        return new BookingResult.Success(venueName, confirmation);
    }
}
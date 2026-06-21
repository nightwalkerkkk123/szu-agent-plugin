package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;

/**
 * Immutable context passed through the booking pipeline.
 *
 * <p>Each step reads from {@link #request} and returns a new context with
 * intermediate results (e.g. {@link #selectedVenue}) via
 * {@link #withSelectedVenue(String)}. The {@link #account} field holds
 * resolved credentials injected by the caller before the pipeline runs.
 * This keeps {@link BookingRequest} free of credential types.
 *
 * <p>// 编程技术: record(不可变) / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
public record BookingContext(
    BookingRequest request,
    Account account,
    String selectedVenue,
    BookingResult.Failure lastFailure
) {

    public BookingContext(BookingRequest request, Account account) {
        this(request, account, null, null);
    }

    public BookingContext withSelectedVenue(String selectedVenue) {
        return new BookingContext(request, account, selectedVenue, lastFailure);
    }

    public BookingContext withLastFailure(BookingResult.Failure lastFailure) {
        return new BookingContext(request, account, selectedVenue, lastFailure);
    }
}

package edu.szu.agent.client.step;

import edu.szu.agent.domain.BookingResult;

/**
 * Result of executing a {@link BookingStep}.
 *
 * <p>Steps are pure functions over {@link BookingContext}: they receive an
 * immutable context and return an outcome that either carries the next
 * context (continue pipeline) or a terminal failure. The pipeline owner
 * ({@link edu.szu.agent.client.VenueBookingClient}) constructs the final
 * {@link BookingResult.Success} after the last step.
 *
 * <p>// Design Pattern: Strategy
 * <p>// 编程技术: sealed interface / record
 *
 * @since 0.1.0
 * @author 王子豪
 */
public sealed interface StepOutcome {

    /**
     * Step succeeded; pipeline should continue with the (possibly updated)
     * context.
     */
    record Continue(BookingContext nextContext) implements StepOutcome {}

    /**
     * Step succeeded and produced a complete result on its own (e.g. a cache
     * hit): the pipeline should STOP and skip all remaining steps. Unlike
     * {@link Failure} this is a success — the context already holds the data
     * the pipeline owner needs to build a success result.
     */
    record ShortCircuit(BookingContext nextContext) implements StepOutcome {}

    /**
     * Step failed terminally; pipeline should stop and return this failure.
     */
    record Failure(BookingResult.Failure result) implements StepOutcome {}
}

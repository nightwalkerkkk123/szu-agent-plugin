package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Step that persists the current browser login state to disk after a
 * successful flow. Writes a Playwright storageState JSON.
 *
 * <p>Whether to persist is decided by an injected
 * {@link Predicate}{@code <BookingContext>} so the same step serves two flows:
 * the homework flow persists only when items were captured (proxy for a valid
 * login), while the booking flow persists unconditionally because it is placed
 * after an auth-proving step (e.g. SELECT_CAMPUS) and must save the session
 * even if a later step fails (e.g. slot full).
 *
 * <p>// Design Pattern: Strategy (concrete step in pipeline)
 * // 编程技术: 泛型 / 函数式接口 Predicate / Lambda
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class PersistSessionStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(PersistSessionStep.class);

    private final SessionStore store;
    private final Predicate<BookingContext> shouldPersist;

    /**
     * Creates a step that persists only when homeworks were captured — the
     * default condition for the homework flow.
     *
     * @param store session storage abstraction
     * @since 0.6.0
     * @author 王子豪
     */
    public PersistSessionStep(SessionStore store) {
        this(store, ctx -> ctx.homeworks() != null && !ctx.homeworks().isEmpty());
    }

    /**
     * Creates a step backed by the given store with a custom persist condition.
     *
     * @param store         session storage abstraction
     * @param shouldPersist predicate deciding whether the current context
     *                      warrants persisting the login state
     * @since 0.6.0
     * @author 王子豪
     */
    public PersistSessionStep(SessionStore store, Predicate<BookingContext> shouldPersist) {
        this.store = Objects.requireNonNull(store, "store");
        this.shouldPersist = Objects.requireNonNull(shouldPersist, "shouldPersist");
    }

    @Override
    public String name() {
        return "PERSIST_SESSION";
    }

    @Override
    public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
        if (!shouldPersist.test(ctx)) {
            log.info("Skip persist: condition not met for user {}", ctx.username());
            return new StepOutcome.Continue(ctx);
        }
        try {
            browser.exportStorageState(store.defaultPath());
            log.info("Persisted login state for user {}", ctx.username());
        } catch (RuntimeException e) {
            log.warn("Failed to persist state: {}", e.getMessage());
        }
        return new StepOutcome.Continue(ctx);
    }
}

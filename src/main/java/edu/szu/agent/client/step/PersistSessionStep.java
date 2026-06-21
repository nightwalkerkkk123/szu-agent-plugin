package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.domain.BookingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Step that persists the current browser login state to disk after a
 * successful flow. Writes a Playwright storageState JSON.
 *
 * <p>// Design Pattern: Strategy (concrete step in pipeline)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class PersistSessionStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(PersistSessionStep.class);

    private final SessionStore store;

    /**
     * Creates a step backed by the given store.
     *
     * @param store session storage abstraction
     * @since 0.1.0
     * @author 王子豪
     */
    public PersistSessionStep(SessionStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "PERSIST_SESSION";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        if (ctx.homeworks() == null || ctx.homeworks().isEmpty()) {
            log.info("Skip persist: no homeworks captured for user {}", ctx.username());
            return null;
        }
        try {
            browser.exportStorageState(store.defaultPath());
            log.info("Persisted login state for user {}", ctx.username());
        } catch (RuntimeException e) {
            log.warn("Failed to persist state: {}", e.getMessage());
        }
        return null;
    }
}

package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionResult;
import edu.szu.agent.client.session.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

/**
 * Step that tries to restore a previously persisted login state and
 * validates it via {@link SessionProbe}. On success, sets
 * {@link BookingContext#sessionOk(boolean)} true so {@link CasLoginStep}
 * can skip its work.
 *
 * <p>// Design Pattern: Strategy (concrete step in pipeline)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class RestoreSessionStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(RestoreSessionStep.class);

    private final SessionStore store;
    private final SessionProbe probe;
    private final Duration ttl;

    /**
     * Creates a step backed by the given store and probe.
     *
     * @param store session storage abstraction
     * @param probe alive-check probe
     * @param ttl   maximum age allowed for the persisted file
     * @since 0.1.0
     * @author 王子豪
     */
    public RestoreSessionStep(SessionStore store, SessionProbe probe, Duration ttl) {
        this.store = Objects.requireNonNull(store, "store");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
    }

    @Override
    public String name() {
        return "RESTORE_SESSION";
    }

    @Override
    public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
        if (!store.exists() || !store.isFresh(ttl)) {
            log.info("No fresh persisted state for user {}", ctx.username());
            ctx.sessionOk(false);
            return new StepOutcome.Continue(ctx);
        }

        boolean loaded = browser.importStorageState(store.defaultPath());
        if (!loaded) {
            log.info("import returned false for user {}", ctx.username());
            ctx.sessionOk(false);
            return new StepOutcome.Continue(ctx);
        }

        SessionResult result = probe.isAlive(browser);
        if (result instanceof SessionResult.Fresh) {
            log.info("Reusing persisted state for user {}", ctx.username());
            ctx.sessionOk(true);
        } else {
            String reason = result instanceof SessionResult.Stale s ? s.reason() : "unknown";
            log.info("Persisted state stale, will re-login: {}", reason);
            try {
                store.deleteIfExists();
            } catch (IOException e) {
                log.warn("Failed to delete stale storage state: {}", e.getMessage());
            }
            ctx.sessionOk(false);
        }
        return new StepOutcome.Continue(ctx);
    }
}

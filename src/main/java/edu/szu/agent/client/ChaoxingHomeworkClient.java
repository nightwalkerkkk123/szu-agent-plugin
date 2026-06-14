package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.client.step.BookingContext;
import edu.szu.agent.client.step.BookingStep;
import edu.szu.agent.client.step.CasLoginStep;
import edu.szu.agent.client.step.NavigateToHomeworkStep;
import edu.szu.agent.client.step.ParseHomeworkListStep;
import edu.szu.agent.client.step.PersistSessionStep;
import edu.szu.agent.client.step.RestoreSessionStep;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.HomeworkListResult;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Chaoxing / LMS homework list orchestrator.
 *
 * <p>Drives the browser through: CAS login → LMS user index → parse todo list.
 * Mirrors {@link VenueBookingClient} in lifecycle handling (open, retry, close,
 * screenshot-on-failure, trace recording).
 *
 * // Design Pattern: Strategy (pipeline of BookingSteps) + Adapter
 * // 编程技术: 枚举 / Lambda / 不可变构造器注入
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class ChaoxingHomeworkClient {

    private static final Logger log = LoggerFactory.getLogger(ChaoxingHomeworkClient.class);

    private final Account account;
    private final BrowserLifecycle browser;
    private final RetryPolicy retryPolicy;
    private final List<BookingStep> steps;
    private final SessionStore sessionStore;
    private final SessionProbe sessionProbe;
    private final Duration sessionTtl;

    /**
     * Production constructor — uses the default homework pipeline without
     * session persistence. Kept for backward compatibility.
     *
     * @param account     resolved credentials
     * @param browser     browser adapter injected by ConfigManager
     * @param retryPolicy retry policy for the flow
     */
    public ChaoxingHomeworkClient(Account account,
                                   BrowserLifecycle browser,
                                   RetryPolicy retryPolicy) {
        this(account, browser, retryPolicy, defaultSteps(null, null, null),
            null, null, null);
    }

    /**
     * Production constructor — wires session persistence into the homework
     * pipeline. Pipeline becomes: RestoreSession → CasLogin (skipped if
     * sessionOk) → NavigateToHomework → ParseHomeworkList → PersistSession.
     *
     * @param account      resolved credentials
     * @param browser      browser adapter
     * @param retryPolicy  retry policy
     * @param sessionStore on-disk store rooted at user home (must not be null)
     * @param sessionProbe alive probe for the LMS user index (must not be null)
     * @param sessionTtl   freshness window for persisted state (must not be null)
     * @since 0.1.0
     */
    public ChaoxingHomeworkClient(Account account,
                                   BrowserLifecycle browser,
                                   RetryPolicy retryPolicy,
                                   SessionStore sessionStore,
                                   SessionProbe sessionProbe,
                                   Duration sessionTtl) {
        this(account, browser, retryPolicy,
            defaultSteps(
                Objects.requireNonNull(sessionStore, "sessionStore"),
                Objects.requireNonNull(sessionProbe, "sessionProbe"),
                Objects.requireNonNull(sessionTtl, "sessionTtl")),
            sessionStore, sessionProbe, sessionTtl);
    }

    /**
     * Full DI constructor — for testing with custom step lists.
     */
    ChaoxingHomeworkClient(Account account,
                            BrowserLifecycle browser,
                            RetryPolicy retryPolicy,
                            List<BookingStep> steps) {
        this(account, browser, retryPolicy, steps, null, null, null);
    }

    /**
     * Full DI constructor with session dependencies — for testing.
     */
    ChaoxingHomeworkClient(Account account,
                            BrowserLifecycle browser,
                            RetryPolicy retryPolicy,
                            List<BookingStep> steps,
                            SessionStore sessionStore,
                            SessionProbe sessionProbe,
                            Duration sessionTtl) {
        this.account = Objects.requireNonNull(account, "account");
        this.browser = Objects.requireNonNull(browser, "browser");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.steps = List.copyOf(steps);
        this.sessionStore = sessionStore;
        this.sessionProbe = sessionProbe;
        this.sessionTtl = sessionTtl;
    }

    private static List<BookingStep> defaultSteps(SessionStore store,
                                                   SessionProbe probe,
                                                   Duration ttl) {
        List<BookingStep> built = new ArrayList<>();
        if (store != null && probe != null && ttl != null) {
            built.add(new RestoreSessionStep(store, probe, ttl));
        }
        built.add(new CasLoginStep(NavigateToHomeworkStep.LMS_USER_INDEX_URL));
        built.add(new NavigateToHomeworkStep());
        built.add(new ParseHomeworkListStep());
        if (store != null) {
            built.add(new PersistSessionStep(store));
        }
        return List.copyOf(built);
    }

    /**
     * Executes the full homework-list flow.
     *
     * @return {@link HomeworkListResult.Success} with the list of homework items,
     *         or {@link HomeworkListResult.Failure} on error
     */
    public HomeworkListResult list() {
        BookingContext ctx = new BookingContext(null, account);
        ctx.username(account.studentId());
        try {
            browser.open();
            return retryPolicy.execute(() -> executePipeline(ctx));
        } catch (BookingException e) {
            Optional<Path> screenshotPath = Optional.empty();
            if (e.code().shouldScreenshot()) {
                try {
                    Path path = Path.of(System.getProperty("java.io.tmpdir"),
                        "lms-error-" + System.currentTimeMillis() + ".png");
                    browser.screenshot(path.toString());
                    screenshotPath = Optional.of(path);
                } catch (Exception screenshotEx) {
                    log.warn("Failed to take screenshot: {}", screenshotEx.getMessage());
                }
            }
            Tracer.getInstance().recordFailure(e.code(), e.getMessage(), screenshotPath);
            return new HomeworkListResult.Failure(e.code(), e.getMessage());
        } finally {
            try {
                browser.close();
            } catch (Exception e) {
                log.warn("Failed to close browser: {}", e.getMessage());
            }
        }
    }

    private HomeworkListResult executePipeline(BookingContext ctx) {
        log.info("Starting homework list flow for {}", account.studentId());

        for (BookingStep step : steps) {
            log.info("Executing step: {}", step.name());
            BookingResult r = step.execute(browser, ctx);
            if (r instanceof BookingResult.Failure f) {
                log.warn("Step {} failed: {}", step.name(), f.message());
                return new HomeworkListResult.Failure(f.code(), f.message());
            }
        }

        if (ctx.homeworks() == null) {
            return new HomeworkListResult.Failure(ErrorCode.HOMEWORK_PAGE_LOAD_FAILED,
                "pipeline finished but no homework list was parsed");
        }
        log.info("Homework list retrieved: {} item(s)", ctx.homeworks().size());
        return new HomeworkListResult.Success(ctx.homeworks());
    }
}

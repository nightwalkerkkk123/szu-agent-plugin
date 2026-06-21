package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.client.step.BookingContext;
import edu.szu.agent.client.step.BookingStep;
import edu.szu.agent.client.step.CasLoginStep;
import edu.szu.agent.client.step.NavigateToScheduleStep;
import edu.szu.agent.client.step.ParseScheduleStep;
import edu.szu.agent.client.step.PersistSessionStep;
import edu.szu.agent.client.step.RestoreSessionStep;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.ScheduleListResult;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * ehall schedule list orchestrator.
 *
 * <p>Drives the browser through: CAS login → ehall schedule page → parse grid.
 * Mirrors {@link ChaoxingHomeworkClient} in lifecycle handling (open, retry,
 * close, screenshot-on-failure, trace recording) and pipeline composition.
 *
 * // Design Pattern: Strategy (pipeline of BookingSteps) + Adapter
 * // 编程技术: 枚举 / Lambda / 不可变构造器注入
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class EhallScheduleClient {

    private static final Logger log = LoggerFactory.getLogger(EhallScheduleClient.class);

    private final Account account;
    private final BrowserLifecycle browser;
    private final RetryPolicy retryPolicy;
    private final List<BookingStep> steps;

    /**
     * Production constructor — uses the default schedule pipeline without
     * session persistence.
     *
     * @param account     resolved credentials
     * @param browser     browser adapter injected by ConfigManager
     * @param retryPolicy retry policy for the flow
     * @since 0.1.0
     */
    public EhallScheduleClient(Account account,
                                BrowserLifecycle browser,
                                RetryPolicy retryPolicy) {
        this(account, browser, retryPolicy, defaultSteps(null, null, null));
    }

    /**
     * Production constructor — wires session persistence into the schedule
     * pipeline. Pipeline becomes:
     * {@code RestoreSession → CasLogin (skipped if sessionOk) → NavigateToSchedule
     * → ParseSchedule → PersistSession}.
     *
     * @since 0.1.0
     */
    public EhallScheduleClient(Account account,
                                BrowserLifecycle browser,
                                RetryPolicy retryPolicy,
                                SessionStore sessionStore,
                                SessionProbe sessionProbe,
                                Duration sessionTtl) {
        this(account, browser, retryPolicy,
            defaultSteps(
                Objects.requireNonNull(sessionStore, "sessionStore"),
                Objects.requireNonNull(sessionProbe, "sessionProbe"),
                Objects.requireNonNull(sessionTtl, "sessionTtl")));
    }

    /**
     * Full DI constructor — for testing with custom step lists.
     */
    EhallScheduleClient(Account account,
                        BrowserLifecycle browser,
                        RetryPolicy retryPolicy,
                        List<BookingStep> steps) {
        this.account = Objects.requireNonNull(account, "account");
        this.browser = Objects.requireNonNull(browser, "browser");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.steps = List.copyOf(steps);
    }

    private static List<BookingStep> defaultSteps(SessionStore store,
                                                  SessionProbe probe,
                                                  Duration ttl) {
        List<BookingStep> built = new ArrayList<>();
        if (store != null && probe != null && ttl != null) {
            built.add(new RestoreSessionStep(store, probe, ttl));
        }
        built.add(new CasLoginStep(NavigateToScheduleStep.EHALL_SCHEDULE_URL));
        built.add(new NavigateToScheduleStep());
        built.add(new ParseScheduleStep());
        if (store != null) {
            built.add(new PersistSessionStep(store));
        }
        return List.copyOf(built);
    }

    /**
     * Executes the full schedule-list flow.
     *
     * @return {@link ScheduleListResult.Success} with the list of course entries,
     *         or {@link ScheduleListResult.Failure} on error
     * @since 0.1.0
     */
    public ScheduleListResult list() {
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
                        "schedule-error-" + System.currentTimeMillis() + ".png");
                    browser.screenshot(path.toString());
                    screenshotPath = Optional.of(path);
                } catch (Exception screenshotEx) {
                    log.warn("Failed to take screenshot: {}", screenshotEx.getMessage());
                }
            }
            Tracer.getInstance().recordFailure(e.code(), e.getMessage(), screenshotPath);
            return new ScheduleListResult.Failure(e.code(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in schedule list flow", e);
            Tracer.getInstance().recordFailure(ErrorCode.UNKNOWN, e.getMessage(), Optional.empty());
            return new ScheduleListResult.Failure(ErrorCode.UNKNOWN, e.getMessage());
        } finally {
            try {
                browser.close();
            } catch (Exception e) {
                log.warn("Failed to close browser: {}", e.getMessage());
            }
        }
    }

    private ScheduleListResult executePipeline(BookingContext ctx) {
        log.info("Starting schedule list flow for {}", account.studentId());

        for (BookingStep step : steps) {
            log.info("Executing step: {}", step.name());
            BookingResult r = step.execute(browser, ctx);
            if (r instanceof BookingResult.Failure f) {
                log.warn("Step {} failed: {}", step.name(), f.message());
                return new ScheduleListResult.Failure(f.code(), f.message());
            }
        }

        if (ctx.scheduleCourses() == null) {
            return new ScheduleListResult.Failure(ErrorCode.SCHEDULE_PARSE_FAILED,
                "pipeline finished but no schedule was parsed");
        }
        if (ctx.scheduleCourses().isEmpty()) {
            return new ScheduleListResult.Failure(ErrorCode.SCHEDULE_EMPTY,
                "schedule grid is empty");
        }
        log.info("Schedule retrieved: {} course entry(s)", ctx.scheduleCourses().size());
        return new ScheduleListResult.Success(ctx.scheduleCourses(), Instant.now());
    }
}

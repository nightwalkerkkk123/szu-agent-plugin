package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.cache.CacheStore;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.client.step.BookingContext;
import edu.szu.agent.client.step.BookingStep;
import edu.szu.agent.client.step.CachePipelineBuilder;
import edu.szu.agent.client.step.CasLoginStep;
import edu.szu.agent.client.step.NavigateToScheduleStep;
import edu.szu.agent.client.step.ParseScheduleStep;
import edu.szu.agent.client.step.StepOutcome;
import edu.szu.agent.config.ConfigManager;
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
            defaultStepsWithCache(
                Objects.requireNonNull(sessionStore, "sessionStore"),
                Objects.requireNonNull(sessionProbe, "sessionProbe"),
                Objects.requireNonNull(sessionTtl, "sessionTtl"),
                null, null));
    }

    /**
     * Production constructor — wires caching into the schedule pipeline.
     * Pipeline becomes:
     * {@code RestoreSession → CasLogin → CacheLookup (short-circuits on hit)
     * → NavigateToSchedule → ParseSchedule → CacheWrite → PersistSession}.
     *
     * @since 0.3.0
     */
    public EhallScheduleClient(Account account,
                                BrowserLifecycle browser,
                                RetryPolicy retryPolicy,
                                SessionStore sessionStore,
                                SessionProbe sessionProbe,
                                Duration sessionTtl,
                                CacheStore cacheStore) {
        this(account, browser, retryPolicy,
            defaultStepsWithCache(
                sessionStore, sessionProbe, sessionTtl,
                Objects.requireNonNull(cacheStore, "cacheStore"),
                Objects.requireNonNull(account, "account")));
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
        return defaultStepsWithCache(store, probe, ttl, null, null);
    }

    // 编程技术: 泛型 / Lambda / Jackson TypeReference
    private static List<BookingStep> defaultStepsWithCache(SessionStore store,
                                                            SessionProbe probe,
                                                            Duration ttl,
                                                            CacheStore cacheStore,
                                                            Account account) {
        List<BookingStep> built = new ArrayList<>();
        built.addAll(CachePipelineBuilder.sessionRestore(store, probe, ttl));
        built.add(new CasLoginStep(NavigateToScheduleStep.EHALL_SCHEDULE_URL));

        if (cacheStore != null && account != null) {
            String key = "schedule-" + account.studentId();
            built.addAll(CachePipelineBuilder.scheduleLookupAndWrite(
                cacheStore, key,
                (ctx, courses) -> ctx.scheduleCourses(courses),
                BookingContext::scheduleCourses));
        }

        built.add(new NavigateToScheduleStep());
        built.add(new ParseScheduleStep());
        built.addAll(CachePipelineBuilder.sessionPersist(store));
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
            StepOutcome outcome = step.execute(browser, ctx);
            if (outcome instanceof StepOutcome.Failure f) {
                BookingResult.Failure bf = f.result();
                log.warn("Step {} failed: {}", step.name(), bf.message());
                return new ScheduleListResult.Failure(bf.code(), bf.message());
            }
            if (outcome instanceof StepOutcome.ShortCircuit sc) {
                // Cache hit (or other self-sufficient step): stop the pipeline
                // and skip remaining browser-automation steps.
                ctx = sc.nextContext();
                log.info("Step {} short-circuited the pipeline", step.name());
                break;
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

package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.step.BookingContext;
import edu.szu.agent.client.step.BookingStep;
import edu.szu.agent.client.step.CasLoginStep;
import edu.szu.agent.client.step.NavigateToHomeworkStep;
import edu.szu.agent.client.step.ParseHomeworkListStep;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.HomeworkListResult;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
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

    /**
     * Production constructor — uses the default homework pipeline.
     *
     * @param account     resolved credentials
     * @param browser     browser adapter injected by ConfigManager
     * @param retryPolicy retry policy for the flow
     */
    public ChaoxingHomeworkClient(Account account,
                                   BrowserLifecycle browser,
                                   RetryPolicy retryPolicy) {
        this(account, browser, retryPolicy, List.of(
            new CasLoginStep(NavigateToHomeworkStep.LMS_USER_INDEX_URL),
            new NavigateToHomeworkStep(),
            new ParseHomeworkListStep()
        ));
    }

    /**
     * Full DI constructor — for testing with custom step lists.
     */
    ChaoxingHomeworkClient(Account account,
                            BrowserLifecycle browser,
                            RetryPolicy retryPolicy,
                            List<BookingStep> steps) {
        this.account = Objects.requireNonNull(account, "account");
        this.browser = Objects.requireNonNull(browser, "browser");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.steps = List.copyOf(steps);
    }

    /**
     * Executes the full homework-list flow.
     *
     * @return {@link HomeworkListResult.Success} with the list of homework items,
     *         or {@link HomeworkListResult.Failure} on error
     */
    public HomeworkListResult list() {
        BookingContext ctx = new BookingContext(null, account);
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

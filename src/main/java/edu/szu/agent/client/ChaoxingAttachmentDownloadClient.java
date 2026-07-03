package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.client.step.BookingContext;
import edu.szu.agent.client.step.BookingStep;
import edu.szu.agent.client.step.CasLoginStep;
import edu.szu.agent.client.step.DownloadFilesStep;
import edu.szu.agent.client.step.NavigateToHomeworkDetailStep;
import edu.szu.agent.client.step.NavigateToHomeworkStep;
import edu.szu.agent.client.step.ParseAttachmentsStep;
import edu.szu.agent.client.step.PersistSessionStep;
import edu.szu.agent.client.step.RestoreSessionStep;
import edu.szu.agent.client.step.StepOutcome;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.HomeworkAttachment;
import edu.szu.agent.domain.HomeworkDownloadRequest;
import edu.szu.agent.domain.HomeworkDownloadResult;
import edu.szu.agent.error.BookingException;
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
 * Chaoxing / LMS homework attachment download orchestrator.
 *
 * <p>Drives the browser through: CAS login → LMS homework detail page →
 * parse attachment list → download each file → persist LMS session.
 * Mirrors {@link ChaoxingHomeworkClient} in lifecycle handling (open,
 * retry, close, screenshot-on-failure, trace recording).
 *
 * <p>Returns a {@link HomeworkDownloadResult}: Success (one or more
 * files), Empty (homework has no attachments — not an error), or
 * Failure (with {@link edu.szu.agent.error.ErrorCode} for retry
 * decisions).
 *
 * // Design Pattern: Strategy (pipeline of BookingSteps) + Adapter
 * // 编程技术: 不可变构造器注入 / sealed 接口返回
 *
 * @since 0.6.0
 * @author 王子豪
 */
public class ChaoxingAttachmentDownloadClient {

    private static final Logger log = LoggerFactory.getLogger(ChaoxingAttachmentDownloadClient.class);

    private final Account account;
    private final BrowserLifecycle browser;
    private final RetryPolicy retryPolicy;
    private final List<BookingStep> steps;

    /**
     * Production constructor — wires session persistence. Pipeline:
     * RestoreSession → CasLogin (skipped if sessionOk) →
     * NavigateToHomeworkDetail → ParseAttachments → DownloadFiles →
     * PersistSession.
     *
     * @param account      resolved credentials
     * @param browser      browser adapter
     * @param retryPolicy  retry policy
     * @param sessionStore on-disk store rooted at user home (must not be null)
     * @param sessionProbe alive probe for the LMS user index (must not be null)
     * @param sessionTtl   freshness window for persisted state (must not be null)
     * @since 0.6.0
     * @author 王子豪
     */
    public ChaoxingAttachmentDownloadClient(Account account,
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
     * DI constructor — for testing with custom step lists.
     */
    ChaoxingAttachmentDownloadClient(Account account,
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
        built.add(new RestoreSessionStep(store, probe, ttl));
        built.add(new CasLoginStep(NavigateToHomeworkStep.LMS_USER_INDEX_URL));
        built.add(new NavigateToHomeworkDetailStep());
        built.add(new ParseAttachmentsStep());
        built.add(new DownloadFilesStep());
        if (store != null) {
            built.add(new PersistSessionStep(store));
        }
        return List.copyOf(built);
    }

    /**
     * Executes the full download flow for the given request.
     *
     * @param request homework id + output dir + throttle params
     * @return {@link HomeworkDownloadResult.Success} with downloaded files,
     *         {@link HomeworkDownloadResult.Empty} if homework has no
     *         attachments, or {@link HomeworkDownloadResult.Failure} on error
     */
    public HomeworkDownloadResult download(HomeworkDownloadRequest request) {
        Objects.requireNonNull(request, "request");
        BookingContext ctx = new BookingContext(null, account);
        ctx.username(account.studentId());
        ctx.homeworkId(request.homeworkId());
        ctx.outputDir(request.outputDir());
        try {
            browser.open();
            return retryPolicy.execute(() -> executePipeline(ctx, request));
        } catch (BookingException e) {
            return recordFailure(e);
        } finally {
            try {
                browser.close();
            } catch (Exception e) {
                log.warn("Failed to close browser: {}", e.getMessage());
            }
        }
    }

    private HomeworkDownloadResult executePipeline(BookingContext ctx,
                                                    HomeworkDownloadRequest request) {
        log.info("Starting homework download flow for homeworkId={}",
            request.homeworkId());

        for (BookingStep step : steps) {
            log.info("Executing step: {}", step.name());
            StepOutcome outcome = step.execute(browser, ctx);
            if (outcome instanceof StepOutcome.Failure f) {
                BookingResult.Failure bf = f.result();
                log.warn("Step {} failed: {}", step.name(), bf.message());
                return new HomeworkDownloadResult.Failure(bf.code(), bf.message());
            }
        }

        List<HomeworkAttachment> downloaded = ctx.attachments();
        if (downloaded == null || downloaded.isEmpty()) {
            log.info("Homework {} has no attachments", request.homeworkId());
            return new HomeworkDownloadResult.Empty(request.homeworkId());
        }
        log.info("Downloaded {} attachment(s) for homework {}",
            downloaded.size(), request.homeworkId());
        return new HomeworkDownloadResult.Success(downloaded);
    }

    private HomeworkDownloadResult recordFailure(BookingException e) {
        Optional<Path> screenshotPath = Optional.empty();
        if (e.code().shouldScreenshot()) {
            try {
                Path path = Path.of(System.getProperty("java.io.tmpdir"),
                    "lms-download-error-" + System.currentTimeMillis() + ".png");
                browser.screenshot(path.toString());
                screenshotPath = Optional.of(path);
            } catch (Exception screenshotEx) {
                log.warn("Failed to take screenshot: {}", screenshotEx.getMessage());
            }
        }
        Tracer.getInstance().recordFailure(e.code(), e.getMessage(), screenshotPath);
        return new HomeworkDownloadResult.Failure(e.code(), e.getMessage());
    }
}

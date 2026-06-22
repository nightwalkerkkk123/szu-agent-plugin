package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.step.BookingContext;
import edu.szu.agent.client.step.BookingStep;
import edu.szu.agent.client.step.StepOutcome;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.HomeworkAttachment;
import edu.szu.agent.domain.HomeworkDownloadRequest;
import edu.szu.agent.domain.HomeworkDownloadResult;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChaoxingAttachmentDownloadClient")
class ChaoxingAttachmentDownloadClientTest {

    @Mock
    private BrowserLifecycle browser;

    private Account account;

    @BeforeEach
    void setUp() {
        Tracer.getInstance().reset();
        account = new Account("2023150090", "secret", "test");
    }

    @AfterEach
    void tearDown() {
        Tracer.getInstance().reset();
    }

    @Test
    @DisplayName("download() returns Success when all steps complete with attachments")
    void downloadReturnsSuccessWhenAttachmentsPresent(@TempDir Path tmp) {
        List<HomeworkAttachment> downloaded = List.of(
            new HomeworkAttachment("169193", "a.pdf", "https://x/a",
                tmp.resolve("a.pdf"), 10L, Instant.now()));
        ChaoxingAttachmentDownloadClient client = clientWith(
            noop("RESTORE"),
            noop("LOGIN"),
            noop("NAV"),
            noop("PARSE"),
            captureAttachments("DL", downloaded));

        HomeworkDownloadResult result = client.download(
            HomeworkDownloadRequest.builder()
                .homeworkId("169193")
                .outputDir(tmp)
                .build());

        assertThat(result).isInstanceOf(HomeworkDownloadResult.Success.class);
        assertThat(((HomeworkDownloadResult.Success) result).attachments())
            .isEqualTo(downloaded);
        verify(browser).open();
        verify(browser).close();
    }

    @Test
    @DisplayName("download() returns Empty when no attachments were parsed")
    void downloadReturnsEmptyWhenNoAttachments(@TempDir Path tmp) {
        ChaoxingAttachmentDownloadClient client = clientWith(
            noop("RESTORE"),
            noop("LOGIN"),
            noop("NAV"),
            captureAttachments("PARSE_EMPTY", List.of()));

        HomeworkDownloadResult result = client.download(
            HomeworkDownloadRequest.builder()
                .homeworkId("177533")
                .outputDir(tmp)
                .build());

        assertThat(result).isInstanceOf(HomeworkDownloadResult.Empty.class);
        assertThat(((HomeworkDownloadResult.Empty) result).homeworkId())
            .isEqualTo("177533");
    }

    @Test
    @DisplayName("download() returns Failure when a step returns Failure")
    void downloadReturnsFailureOnStepFailure(@TempDir Path tmp) {
        BookingResult.Failure failure = new BookingResult.Failure(
            ErrorCode.ATTACHMENT_NOT_FOUND, "no rows");
        AtomicBoolean secondRan = new AtomicBoolean();
        ChaoxingAttachmentDownloadClient client = clientWith(
            noop("OK"),
            returning("FAIL", failure),
            sideEffect("NOT_RUN", () -> secondRan.set(true)));

        HomeworkDownloadResult result = client.download(
            HomeworkDownloadRequest.builder()
                .homeworkId("169193")
                .outputDir(tmp)
                .build());

        assertThat(result).isInstanceOf(HomeworkDownloadResult.Failure.class);
        assertThat(((HomeworkDownloadResult.Failure) result).code())
            .isEqualTo(ErrorCode.ATTACHMENT_NOT_FOUND);
        assertThat(secondRan).isFalse();
        verify(browser).close();
    }

    @Test
    @DisplayName("download() takes screenshot when shouldScreenshot() is true")
    void downloadTakesScreenshotOnScreenshotableError(@TempDir Path tmp) {
        ChaoxingAttachmentDownloadClient client = clientWith(
            throwing(new BookingException(ErrorCode.ATTACHMENT_DOWNLOAD_FAILED, "boom")));

        HomeworkDownloadResult result = client.download(
            HomeworkDownloadRequest.builder()
                .homeworkId("169193")
                .outputDir(tmp)
                .build());

        assertThat(result).isInstanceOf(HomeworkDownloadResult.Failure.class);
        verify(browser).screenshot(anyString());
        verify(browser).close();
    }

    @Test
    @DisplayName("download() skips screenshot when shouldScreenshot() is false")
    void downloadSkipsScreenshotOnNonScreenshotableError(@TempDir Path tmp) {
        ChaoxingAttachmentDownloadClient client = clientWith(
            throwing(new BookingException(ErrorCode.ATTACHMENT_NOT_FOUND, "no rows")));

        HomeworkDownloadResult result = client.download(
            HomeworkDownloadRequest.builder()
                .homeworkId("169193")
                .outputDir(tmp)
                .build());

        assertThat(result).isInstanceOf(HomeworkDownloadResult.Failure.class);
        verify(browser, never()).screenshot(anyString());
    }

    @Test
    @DisplayName("download() swallows browser.close() exceptions")
    void downloadSwallowsBrowserCloseException(@TempDir Path tmp) {
        doThrow(new RuntimeException("close failed")).when(browser).close();
        List<HomeworkAttachment> downloaded = List.of(
            new HomeworkAttachment("169193", "a.pdf", "https://x/a",
                tmp.resolve("a.pdf"), 10L, Instant.now()));
        ChaoxingAttachmentDownloadClient client = clientWith(
            captureAttachments("DL", downloaded));

        HomeworkDownloadResult result = client.download(
            HomeworkDownloadRequest.builder()
                .homeworkId("169193")
                .outputDir(tmp)
                .build());

        assertThat(result).isInstanceOf(HomeworkDownloadResult.Success.class);
        verify(browser).close();
    }

    @Test
    @DisplayName("download() sets homeworkId and outputDir on context before pipeline runs")
    void downloadPopulatesContext(@TempDir Path tmp) {
        AtomicBoolean captured = new AtomicBoolean();
        ChaoxingAttachmentDownloadClient client = clientWith(
            inspectingContext("INSPECT", ctx -> {
                assertThat(ctx.homeworkId()).isEqualTo("169193");
                assertThat(ctx.outputDir()).isEqualTo(tmp);
                assertThat(ctx.username()).isEqualTo("2023150090");
                captured.set(true);
            }));

        client.download(
            HomeworkDownloadRequest.builder()
                .homeworkId("169193")
                .outputDir(tmp)
                .build());

        assertThat(captured).isTrue();
    }

    // ---------- helpers ----------

    private ChaoxingAttachmentDownloadClient clientWith(BookingStep... steps) {
        return new ChaoxingAttachmentDownloadClient(
            account, browser, RetryPolicies.quickFix(), List.of(steps));
    }

    private static BookingStep noop(String name) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public StepOutcome execute(BrowserLifecycle b, BookingContext ctx) {
                return new StepOutcome.Continue(ctx);
            }
        };
    }

    private static BookingStep captureAttachments(String name,
                                                   List<HomeworkAttachment> list) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public StepOutcome execute(BrowserLifecycle b, BookingContext ctx) {
                ctx.attachments(list);
                return new StepOutcome.Continue(ctx);
            }
        };
    }

    private static BookingStep returning(String name, BookingResult result) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public StepOutcome execute(BrowserLifecycle b, BookingContext ctx) {
                if (result instanceof BookingResult.Failure f) {
                    return new StepOutcome.Failure(f);
                }
                return new StepOutcome.Continue(ctx);
            }
        };
    }

    private static BookingStep sideEffect(String name, Runnable sideEffect) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public StepOutcome execute(BrowserLifecycle b, BookingContext ctx) {
                sideEffect.run();
                return new StepOutcome.Continue(ctx);
            }
        };
    }

    private static BookingStep inspectingContext(String name,
                                                  java.util.function.Consumer<BookingContext> check) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public StepOutcome execute(BrowserLifecycle b, BookingContext ctx) {
                check.accept(ctx);
                return new StepOutcome.Continue(ctx);
            }
        };
    }

    private static BookingStep throwing(BookingException ex) {
        return new BookingStep() {
            @Override public String name() { return "THROW"; }
            @Override public StepOutcome execute(BrowserLifecycle b, BookingContext ctx) {
                throw ex;
            }
        };
    }
}

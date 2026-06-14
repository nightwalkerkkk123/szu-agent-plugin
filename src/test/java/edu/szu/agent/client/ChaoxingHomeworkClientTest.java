package edu.szu.agent.client;

import edu.szu.agent.account.Account;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.step.BookingContext;
import edu.szu.agent.client.step.BookingStep;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.Homework;
import edu.szu.agent.domain.HomeworkListResult;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChaoxingHomeworkClient")
class ChaoxingHomeworkClientTest {

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

    private ChaoxingHomeworkClient clientWith(BookingStep... steps) {
        return new ChaoxingHomeworkClient(
            account, browser, RetryPolicies.quickFix(), List.of(steps));
    }

    @Test
    @DisplayName("list() returns Success when all steps complete and homeworks are parsed")
    void listReturnsSuccessWhenParsed() {
        Homework expected = new Homework("1", "OS", "lab", "2026.06.24 23:59", "待提交");
        ChaoxingHomeworkClient client = clientWith(
            noop("S1"),
            captureHomeworks("S2", List.of(expected)));

        HomeworkListResult result = client.list();

        assertThat(result).isInstanceOf(HomeworkListResult.Success.class);
        assertThat(((HomeworkListResult.Success) result).homeworks()).containsExactly(expected);
        verify(browser).open();
        verify(browser).close();
    }

    @Test
    @DisplayName("list() returns Failure when a step returns Failure")
    void listReturnsFailureOnStepFailure() {
        BookingResult.Failure failure = new BookingResult.Failure(
            ErrorCode.ELEMENT_NOT_FOUND, "list missing");
        AtomicBoolean secondRan = new AtomicBoolean();
        ChaoxingHomeworkClient client = clientWith(
            noop("OK"),
            returning("FAIL", failure),
            sideEffect("NOT_RUN", () -> secondRan.set(true)));

        HomeworkListResult result = client.list();

        assertThat(result).isInstanceOf(HomeworkListResult.Failure.class);
        HomeworkListResult.Failure f = (HomeworkListResult.Failure) result;
        assertThat(f.code()).isEqualTo(ErrorCode.ELEMENT_NOT_FOUND);
        assertThat(secondRan).isFalse();
        verify(browser).close();
    }

    @Test
    @DisplayName("list() captures screenshot when shouldScreenshot() is true")
    void listTakesScreenshotWhenShouldScreenshotTrue() {
        ChaoxingHomeworkClient client = clientWith(
            throwing(new BookingException(ErrorCode.ELEMENT_NOT_FOUND, "missing")));

        HomeworkListResult result = client.list();

        assertThat(result).isInstanceOf(HomeworkListResult.Failure.class);
        assertThat(((HomeworkListResult.Failure) result).code())
            .isEqualTo(ErrorCode.ELEMENT_NOT_FOUND);
        verify(browser).screenshot(anyString());
        verify(browser).close();
    }

    @Test
    @DisplayName("list() skips screenshot when shouldScreenshot() is false")
    void listSkipsScreenshotWhenShouldScreenshotFalse() {
        ChaoxingHomeworkClient client = clientWith(
            throwing(new BookingException(ErrorCode.HOMEWORK_LIST_EMPTY, "empty")));

        HomeworkListResult result = client.list();

        assertThat(result).isInstanceOf(HomeworkListResult.Failure.class);
        verify(browser, never()).screenshot(anyString());
        verify(browser).close();
    }

    @Test
    @DisplayName("list() swallows browser.close() exceptions")
    void listSwallowsBrowserCloseException() {
        doThrow(new RuntimeException("close failed")).when(browser).close();
        Homework expected = new Homework("1", "OS", "lab", "2026.06.24 23:59", "待提交");
        ChaoxingHomeworkClient client = clientWith(captureHomeworks("OK", List.of(expected)));

        HomeworkListResult result = client.list();

        assertThat(result).isInstanceOf(HomeworkListResult.Success.class);
        verify(browser).close();
    }

    // ---------- helpers ----------

    private static BookingStep noop(String name) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public BookingResult execute(BrowserLifecycle b, BookingContext ctx) {
                return null;
            }
        };
    }

    private static BookingStep captureHomeworks(String name, List<Homework> homeworks) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public BookingResult execute(BrowserLifecycle b, BookingContext ctx) {
                ctx.homeworks(homeworks);
                return null;
            }
        };
    }

    private static BookingStep returning(String name, BookingResult result) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public BookingResult execute(BrowserLifecycle b, BookingContext ctx) {
                return result;
            }
        };
    }

    private static BookingStep sideEffect(String name, Runnable sideEffect) {
        return new BookingStep() {
            @Override public String name() { return name; }
            @Override public BookingResult execute(BrowserLifecycle b, BookingContext ctx) {
                sideEffect.run();
                return null;
            }
        };
    }

    private static BookingStep throwing(BookingException ex) {
        return new BookingStep() {
            @Override public String name() { return "THROW"; }
            @Override public BookingResult execute(BrowserLifecycle b, BookingContext ctx) {
                throw ex;
            }
        };
    }
}

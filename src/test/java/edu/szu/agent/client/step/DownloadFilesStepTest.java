package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.HomeworkAttachment;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DownloadFilesStep")
class DownloadFilesStepTest {

    @Mock
    private BrowserLifecycle browser;

    @Test
    @DisplayName("execute() attachments 为空时是 no-op")
    void executeWithEmptyAttachments(@TempDir Path tmp) {
        BookingContext ctx = new BookingContext(null);
        ctx.outputDir(tmp);

        new DownloadFilesStep().execute(browser, ctx);

        // no download calls, attachments stays null (we never assigned it)
        assertThat(ctx.attachments()).isNull();
    }

    @Test
    @DisplayName("execute() outputDir 缺失抛 OUTPUT_DIR_INVALID")
    void executeThrowsWhenOutputDirMissing() {
        BookingContext ctx = new BookingContext(null);
        ctx.attachments(List.of(stub("a.pdf", "https://x/a")));

        assertThatThrownBy(() -> new DownloadFilesStep().execute(browser, ctx))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code())
                .isEqualTo(ErrorCode.OUTPUT_DIR_INVALID));
    }

    @Test
    @DisplayName("execute() outputDir 指向文件而非目录时抛错")
    void executeThrowsWhenOutputDirIsFile(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("not-a-dir");
        Files.writeString(file, "x");
        BookingContext ctx = new BookingContext(null);
        ctx.outputDir(file);
        ctx.attachments(List.of(stub("a.pdf", "https://x/a")));

        assertThatThrownBy(() -> new DownloadFilesStep().execute(browser, ctx))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code())
                .isEqualTo(ErrorCode.OUTPUT_DIR_INVALID));
    }

    @Test
    @DisplayName("execute() 单项下载,sizeBytes/localPath/downloadedAt 写回")
    void executeSingleAttachment(@TempDir Path tmp) throws IOException {
        // mock downloadAttachment 模拟真实写盘
        when(browser.downloadAttachment(anyString(), any(Path.class)))
            .thenAnswer(inv -> {
                Path target = inv.getArgument(1);
                Files.writeString(target, "stub-content");
                return 12L; // 字节数
            });

        BookingContext ctx = new BookingContext(null);
        ctx.outputDir(tmp);
        ctx.attachments(List.of(stub("期末大作业.docx",
            "https://lms.szu.edu.cn/api/uploads/reference/741182/blob")));

        new DownloadFilesStep(0L).execute(browser, ctx);

        assertThat(ctx.attachments()).hasSize(1);
        HomeworkAttachment got = ctx.attachments().get(0);
        assertThat(got.fileName()).isEqualTo("期末大作业.docx");
        assertThat(got.sizeBytes()).isEqualTo(12L);
        assertThat(got.localPath()).isEqualTo(tmp.resolve("期末大作业.docx"));
        assertThat(got.downloadedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(Files.exists(got.localPath())).isTrue();
    }

    @Test
    @DisplayName("execute() 已有同名文件时按 (1) 递增重命名")
    void executeCollisionRename(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("lab.pdf"), "old");
        when(browser.downloadAttachment(anyString(), any(Path.class))).thenReturn(10L);

        BookingContext ctx = new BookingContext(null);
        ctx.outputDir(tmp);
        ctx.attachments(List.of(stub("lab.pdf", "https://x/lab")));

        new DownloadFilesStep(0L).execute(browser, ctx);

        HomeworkAttachment got = ctx.attachments().get(0);
        assertThat(got.fileName()).isEqualTo("lab (1).pdf");
        assertThat(got.localPath()).isEqualTo(tmp.resolve("lab (1).pdf"));
        // 原文件未被覆盖
        assertThat(Files.readString(tmp.resolve("lab.pdf"))).isEqualTo("old");
    }

    @Test
    @DisplayName("execute() 多项下载 + 500ms throttle 串行执行")
    void executeMultipleWithThrottle(@TempDir Path tmp) throws IOException {
        when(browser.downloadAttachment(anyString(), any(Path.class)))
            .thenAnswer(inv -> {
                Path target = inv.getArgument(1);
                Files.writeString(target, "x");
                return 1L;
            });

        BookingContext ctx = new BookingContext(null);
        ctx.outputDir(tmp);
        ctx.attachments(List.of(
            stub("a.pdf", "https://x/a"),
            stub("b.pdf", "https://x/b"),
            stub("c.pdf", "https://x/c")));

        long start = System.nanoTime();
        new DownloadFilesStep(50L).execute(browser, ctx);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        // 3 个文件,2 个间隔,总耗时 >= 100ms(50*2)
        assertThat(elapsedMs).isGreaterThanOrEqualTo(100L);
        assertThat(ctx.attachments()).hasSize(3);
        assertThat(ctx.attachments())
            .extracting(HomeworkAttachment::fileName)
            .containsExactly("a.pdf", "b.pdf", "c.pdf");
    }

    @Test
    @DisplayName("execute() browser 抛错时原样上抛(ATTACHMENT_DOWNLOAD_FAILED)")
    void executePropagatesDownloadError(@TempDir Path tmp) {
        when(browser.downloadAttachment(anyString(), any(Path.class)))
            .thenThrow(new BookingException(ErrorCode.ATTACHMENT_DOWNLOAD_FAILED, "boom"));

        BookingContext ctx = new BookingContext(null);
        ctx.outputDir(tmp);
        ctx.attachments(List.of(stub("a.pdf", "https://x/a")));

        assertThatThrownBy(() -> new DownloadFilesStep(0L).execute(browser, ctx))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code())
                .isEqualTo(ErrorCode.ATTACHMENT_DOWNLOAD_FAILED));
    }

    @Test
    @DisplayName("execute() throttle=0 时多项之间无 sleep,保持串行顺序")
    void executeZeroThrottleStaysSequential(@TempDir Path tmp) throws IOException {
        lenient().when(browser.downloadAttachment(anyString(), any(Path.class)))
            .thenAnswer(inv -> {
                Path target = inv.getArgument(1);
                Files.writeString(target, "x");
                return 1L;
            });

        BookingContext ctx = new BookingContext(null);
        ctx.outputDir(tmp);
        ctx.attachments(List.of(
            stub("a.pdf", "https://x/a"),
            stub("b.pdf", "https://x/b")));

        new DownloadFilesStep(0L).execute(browser, ctx);

        // 串行调用,顺序与输入一致
        verify(browser).downloadAttachment("https://x/a", tmp.resolve("a.pdf"));
        verify(browser).downloadAttachment("https://x/b", tmp.resolve("b.pdf"));
    }

    @Test
    @DisplayName("name() 返回 DOWNLOAD_FILES")
    void nameIsDownloadFiles() {
        assertThat(new DownloadFilesStep().name()).isEqualTo("DOWNLOAD_FILES");
    }

    private static HomeworkAttachment stub(String name, String url) {
        return new HomeworkAttachment("169193", name, url, null, 0L, null);
    }
}

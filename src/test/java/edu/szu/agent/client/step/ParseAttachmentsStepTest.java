package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.homework.AttachmentListExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParseAttachmentsStep")
class ParseAttachmentsStepTest {

    @Mock
    private BrowserLifecycle browser;

    @Test
    @DisplayName("execute() 抽取空列表是合法状态,attachments 为空")
    void executeWithEmptyList() {
        when(browser.evaluate(anyString())).thenReturn("[]");

        BookingContext ctx = new BookingContext(null);
        ctx.homeworkId("169193");
        new ParseAttachmentsStep().execute(browser, ctx);

        assertThat(ctx.attachments()).isEmpty();
    }

    @Test
    @DisplayName("execute() 抽取多项,清洗后入库,homeworkId 正确")
    void executeWithMultipleAttachments() {
        String json = """
            [
              {"fileName":"期末大作业.docx",
               "sourceUrl":"/api/uploads/reference/741182/blob",
               "fileSizeText":"26.31 KB"},
              {"fileName":"实验一<bad>.pdf",
               "sourceUrl":"/api/uploads/reference/741183/blob",
               "fileSizeText":"1.2 MB"}
            ]
            """;
        when(browser.evaluate(anyString())).thenReturn(json);

        BookingContext ctx = new BookingContext(null);
        ctx.homeworkId("177533");
        new ParseAttachmentsStep().execute(browser, ctx);

        assertThat(ctx.attachments()).hasSize(2);
        assertThat(ctx.attachments().get(0).homeworkId()).isEqualTo("177533");
        assertThat(ctx.attachments().get(0).fileName()).isEqualTo("期末大作业.docx");
        assertThat(ctx.attachments().get(0).sourceUrl())
            .isEqualTo("/api/uploads/reference/741182/blob");
        // < > 已清洗
        assertThat(ctx.attachments().get(1).fileName())
            .isEqualTo("实验一_bad_.pdf");
        // 没下载时 localPath/sizeBytes/downloadedAt 为空
        assertThat(ctx.attachments().get(0).localPath()).isNull();
        assertThat(ctx.attachments().get(0).sizeBytes()).isZero();
        assertThat(ctx.attachments().get(0).downloadedAt()).isNull();
    }

    @Test
    @DisplayName("execute() JSON 解析失败抛 BOOKING 异常")
    void executeThrowsOnInvalidJson() {
        when(browser.evaluate(anyString())).thenReturn("not json at all");

        BookingContext ctx = new BookingContext(null);
        ctx.homeworkId("169193");
        assertThatThrownBy(() -> new ParseAttachmentsStep().execute(browser, ctx))
            .isInstanceOf(edu.szu.agent.error.BookingException.class);
    }

    @Test
    @DisplayName("execute() homeworkId 缺失抛 IllegalStateException")
    void executeThrowsWhenHomeworkIdMissing() {
        BookingContext ctx = new BookingContext(null);
        // ctx.homeworkId() == null
        assertThatThrownBy(() -> new ParseAttachmentsStep().execute(browser, ctx))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("execute() 走的是真实 LMS 选择器常量")
    void executeUsesRealLmsSelectors() {
        // 验证脚本包含真实的选择器(避免 spec 与 code 漂移)
        String script = AttachmentListExtractor.buildExtractionScript();
        assertThat(script).contains(".attachment-row.preview-able");
        assertThat(script).contains(".file-name");
        assertThat(script).contains(".file-extension");
        assertThat(script).contains("a[ng-href*=\"/api/uploads/reference/\"]");

        when(browser.evaluate(anyString())).thenReturn("[]");
        BookingContext ctx = new BookingContext(null);
        ctx.homeworkId("169193");
        new ParseAttachmentsStep().execute(browser, ctx);

        verify(browser).evaluate(anyString());
    }

    @Test
    @DisplayName("name() 返回 PARSE_ATTACHMENTS")
    void nameIsParseAttachments() {
        assertThat(new ParseAttachmentsStep().name()).isEqualTo("PARSE_ATTACHMENTS");
    }
}

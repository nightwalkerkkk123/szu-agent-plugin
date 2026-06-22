package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.homework.AttachmentListExtractor;
import edu.szu.agent.domain.HomeworkAttachment;

import java.util.List;
import java.util.Objects;

/**
 * Step that parses the LMS homework detail page DOM into
 * {@link HomeworkAttachment} records.
 *
 * <p>Writes the parsed list (possibly empty) into
 * {@link BookingContext#attachments(List)} so the downstream
 * {@code DownloadFilesStep} can fetch and persist them. An empty
 * result is a valid state (homework with no attachments) — it does
 * not raise an error; {@code ChaoxingAttachmentDownloadClient}
 * surfaces that as {@code HomeworkDownloadResult.Empty} instead.
 *
 * <p>Reads {@link BookingContext#homeworkId()} to stamp each record.
 *
 * // Design Pattern: Strategy
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class ParseAttachmentsStep implements BookingStep {

    @Override
    public String name() {
        return "PARSE_ATTACHMENTS";
    }

    @Override
    public StepOutcome execute(BrowserLifecycle browser, BookingContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        String homeworkId = ctx.homeworkId();
        if (homeworkId == null || homeworkId.isBlank()) {
            throw new IllegalStateException(
                "homeworkId must be set on BookingContext before ParseAttachmentsStep");
        }
        List<HomeworkAttachment> attachments =
            AttachmentListExtractor.extract(browser, homeworkId);
        ctx.attachments(attachments);
        return new StepOutcome.Continue(ctx);
    }
}

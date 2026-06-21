package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.client.ChaoxingAttachmentDownloadClient;
import edu.szu.agent.domain.HomeworkDownloadRequest;
import edu.szu.agent.domain.HomeworkDownloadResult;

import java.nio.file.Path;
import java.util.Objects;

/**
 * {@code homework_download} CampusTask — downloads all attachments of a
 * single LMS homework to a local directory.
 *
 * <p>Parameter contract (string keys, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code username} (required) — student ID</li>
 *   <li>{@code homeworkId} (required) — numeric homework id (e.g. "169193")</li>
 *   <li>{@code outputDir} (required) — absolute local directory to write to</li>
 *   <li>{@code throttleMs} (optional) — inter-download delay, default 500</li>
 *   <li>{@code maxRetries} (optional) — retry count, default 2</li>
 * </ul>
 *
 * <p>The actual credentials are held by the injected client; this task is a
 * thin adapter translating {@link TaskInput} into a result.
 *
 * // 编程技术: 泛型 / Builder / 枚举
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class HomeworkDownloadTask implements CampusTask<HomeworkDownloadResult> {

    private final ChaoxingAttachmentDownloadClient client;
    private final Account account;

    public HomeworkDownloadTask(ChaoxingAttachmentDownloadClient client,
                                Account account) {
        this.client = Objects.requireNonNull(client, "client");
        this.account = Objects.requireNonNull(account, "account");
    }

    @Override
    public String name() {
        return "homework_download";
    }

    @Override
    public String description() {
        return "下载畅课作业的全部附件到本地目录";
    }

    @Override
    public HomeworkDownloadResult execute(TaskInput input) {
        input.require("username");
        input.require("homeworkId");
        input.require("outputDir");
        HomeworkDownloadRequest req = HomeworkDownloadRequest.builder()
            .homeworkId(input.require("homeworkId"))
            .outputDir(Path.of(input.require("outputDir")))
            .maxRetries(input.getInt("maxRetries",
                HomeworkDownloadRequest.DEFAULT_MAX_RETRIES))
            .build();
        return client.download(req);
    }
}

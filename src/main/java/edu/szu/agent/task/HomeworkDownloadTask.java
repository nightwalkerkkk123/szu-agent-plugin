package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.client.ChaoxingAttachmentDownloadClient;
import edu.szu.agent.domain.HomeworkDownloadRequest;
import edu.szu.agent.domain.HomeworkDownloadResult;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> username = new LinkedHashMap<>();
        username.put("type", "string");
        username.put("description", "学号");
        properties.put("username", username);

        Map<String, Object> homeworkId = new LinkedHashMap<>();
        homeworkId.put("type", "string");
        homeworkId.put("description", "作业编号");
        properties.put("homeworkId", homeworkId);

        Map<String, Object> outputDir = new LinkedHashMap<>();
        outputDir.put("type", "string");
        outputDir.put("description", "本地输出目录");
        properties.put("outputDir", outputDir);

        Map<String, Object> throttleMs = new LinkedHashMap<>();
        throttleMs.put("type", "integer");
        throttleMs.put("description", "下载间隔毫秒,默认 500");
        throttleMs.put("default", 500);
        properties.put("throttleMs", throttleMs);

        Map<String, Object> maxRetries = new LinkedHashMap<>();
        maxRetries.put("type", "integer");
        maxRetries.put("description", "最大重试次数,默认 2");
        maxRetries.put("default", 2);
        properties.put("maxRetries", maxRetries);

        schema.put("properties", properties);
        schema.put("required", List.of("username", "homeworkId", "outputDir"));
        return schema;
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

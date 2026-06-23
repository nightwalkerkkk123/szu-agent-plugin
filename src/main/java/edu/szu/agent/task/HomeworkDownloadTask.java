package edu.szu.agent.task;

import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.client.ChaoxingAttachmentDownloadClient;
import edu.szu.agent.client.session.SessionProbe;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.HomeworkDownloadRequest;
import edu.szu.agent.domain.HomeworkDownloadResult;
import edu.szu.agent.retry.RetryPolicies;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

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
 * <p>This task resolves the real account from the supplied username on every
 * execute, matching the CLI behaviour.
 *
 * // 编程技术: 泛型 / Builder / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
public class HomeworkDownloadTask implements CampusTask<HomeworkDownloadResult> {

    private static final Duration SESSION_TTL = Duration.ofDays(30);

    private final Function<String, Account> accountResolver;
    private final Supplier<BrowserLifecycle> browserFactory;

    public HomeworkDownloadTask() {
        this(AccountResolver::resolve, () -> ConfigManager.getInstance().browser());
    }

    HomeworkDownloadTask(Function<String, Account> accountResolver,
                         Supplier<BrowserLifecycle> browserFactory) {
        this.accountResolver = Objects.requireNonNull(accountResolver, "accountResolver");
        this.browserFactory = Objects.requireNonNull(browserFactory, "browserFactory");
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
        username.put("description", "学号;若未提供,默认使用环境变量 SZU_USERNAME 配置的账号");
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
        schema.put("required", List.of("homeworkId", "outputDir"));
        return schema;
    }

    @Override
    public HomeworkDownloadResult execute(TaskInput input) {
        String username = resolveUsername(input);
        Account account = accountResolver.apply(username);

        HomeworkDownloadRequest.Builder reqBuilder = HomeworkDownloadRequest.builder()
            .homeworkId(input.require("homeworkId"))
            .outputDir(Path.of(input.require("outputDir")))
            .maxRetries(input.getInt("maxRetries", HomeworkDownloadRequest.DEFAULT_MAX_RETRIES));

        String throttleMs = input.get("throttleMs");
        if (throttleMs != null && !throttleMs.isBlank()) {
            try {
                reqBuilder.throttle(Duration.ofMillis(Integer.parseInt(throttleMs.trim())));
            } catch (NumberFormatException ignored) {
                // fall back to builder default
            }
        }
        HomeworkDownloadRequest req = reqBuilder.build();

        SessionStore store = new SessionStore(
            Path.of(System.getProperty("user.home")), account.studentId());
        SessionProbe probe = new SessionProbe(
            "https://lms.szu.edu.cn/user/index", ".todo-list-container");

        ChaoxingAttachmentDownloadClient client = new ChaoxingAttachmentDownloadClient(
            account,
            browserFactory.get(),
            RetryPolicies.defaultBooking(),
            store,
            probe,
            SESSION_TTL);
        return client.download(req);
    }

    private String resolveUsername(TaskInput input) {
        String fromInput = input.get("username");
        if (fromInput != null && !fromInput.isBlank()) {
            return fromInput;
        }
        String fromEnv = ConfigManager.getInstance().get("SZU_USERNAME");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        throw new IllegalArgumentException("Missing required parameter: username");
    }
}

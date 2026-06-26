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
        return """
            下载深圳大学畅课(LMS/超星)单个作业的全部附件到本地目录。
            重要约束(必须遵守,否则调用会失败或写错位置):
            1. homeworkId 是必填,必须来自 homework_list 返回的 Homework.homeworkId。不要传课程名、作业标题或 URL。
            2. outputDir 是必填,表示本机输出目录。MCP 宿主/daemon 运行在哪台机器,文件就写到那台机器的该目录;
               不要把云盘 URL、HTTP URL 或用户看不见的相对路径当成本地目录。
            3. username 可选;若不传,读取环境变量 SZU_USERNAME。若两者都没有,抛 Missing required parameter: username。
            4. 这是需要账号态的真实下载路径:会解析 SZU_PASSWORD_<学号> 或 --env-file 凭证,并复用 30 天会话。
               不要在 MCP 参数中传密码、cookie 或 token。
            5. throttleMs 可选,默认 500ms,建议保持默认以避免对 LMS 高频访问。maxRetries 可选,默认 2,必须 >= 0。
            6. 返回结果是 sealed HomeworkDownloadResult: Success(attachments)、Empty(homeworkId)、Failure(code,message)。
               Empty 表示该作业没有附件,不是错误,不要自动重试。
            7. 适合用户明确要求"下载这个作业附件"且已知道 homeworkId 时调用;不知道 homeworkId 时先调用 homework_list。
            """;
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> username = TaskInputSchema.property("string",
            "深大学号,11 位数字,例如 2023150090。可选;不传则读取 SZU_USERNAME。",
            Map.of("pattern", "^20\\d{9}$", "examples", List.of("2023150090")));
        Map<String, Object> homeworkId = TaskInputSchema.property("string",
            "作业编号,必须来自 homework_list 返回的 homeworkId。必填。",
            Map.of("pattern", "^\\d+$", "examples", List.of("169193")));
        Map<String, Object> outputDir = TaskInputSchema.property("string",
            "本机输出目录路径。必填。建议传绝对路径。",
            Map.of("format", "uri-reference", "examples", List.of("/Users/wangzihao/Downloads/szu-homework")));
        Map<String, Object> throttleMs = TaskInputSchema.property("integer",
            "附件之间下载间隔毫秒,默认 500。必须 >= 0。",
            Map.of("default", 500, "minimum", 0, "examples", List.of(500, 1000)));
        Map<String, Object> maxRetries = TaskInputSchema.property("integer",
            "单个附件最大重试次数,默认 2。必须 >= 0。",
            Map.of("default", 2, "minimum", 0, "examples", List.of(2, 3)));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("username", username);
        properties.put("homeworkId", homeworkId);
        properties.put("outputDir", outputDir);
        properties.put("throttleMs", throttleMs);
        properties.put("maxRetries", maxRetries);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("homeworkId", "outputDir"));
        return schema;
    }

    @Override
    public ToolAnnotations annotations() {
        Map<String, Object> ex1 = new LinkedHashMap<>();
        ex1.put("username", "2023150090");
        ex1.put("homeworkId", "169193");
        ex1.put("outputDir", "/Users/wangzihao/Downloads/szu-homework");

        Map<String, Object> ex2 = new LinkedHashMap<>();
        ex2.put("homeworkId", "169193");
        ex2.put("outputDir", "./downloads/homework-169193");
        ex2.put("throttleMs", 1000);
        ex2.put("maxRetries", 3);

        Map<String, Object> ex3 = new LinkedHashMap<>();
        ex3.put("username", "2023150090");
        ex3.put("homeworkId", "177533");
        ex3.put("outputDir", "/tmp/szu-attachments");
        ex3.put("throttleMs", 0);
        ex3.put("maxRetries", 0);

        Map<String, Object> ex4 = new LinkedHashMap<>();
        ex4.put("username", "2030200100");
        ex4.put("homeworkId", "200001");
        ex4.put("outputDir", "/Users/other/Downloads/szu");

        Map<String, Object> ex5 = new LinkedHashMap<>();
        ex5.put("homeworkId", "169193");
        ex5.put("outputDir", ".");
        ex5.put("throttleMs", 1500);

        return ToolAnnotations.builder()
            .example(ex1)
            .example(ex2)
            .example(ex3)
            .example(ex4)
            .example(ex5)
            .resultShape("""
                HomeworkDownloadResult (sealed):
                - Success { attachments: List<HomeworkAttachment> }
                - Empty { homeworkId: String } // 无附件,不是错误
                - Failure { code: ErrorCode, message: String }
                HomeworkAttachment 字段:
                - homeworkId, fileName, sourceUrl
                - localPath: 下载后的本地绝对路径
                - sizeBytes: 文件大小
                - downloadedAt: 完成时间 Instant""")
            .commonError("缺 homeworkId/outputDir → INVALID_REQUEST;两者都是必填")
            .commonError("传作业标题而非数字 homeworkId → homeworkId must not be blank/下载失败;先用 homework_list 查 id")
            .commonError("未注入凭证或会话过期 → ACCOUNT_RESOLUTION_FAILED/SESSION_EXPIRED;需 env 或 headed 登录刷新")
            .build();
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

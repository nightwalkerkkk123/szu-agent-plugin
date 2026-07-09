package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.http.AuthserverPasswordEncryptor;
import edu.szu.agent.client.http.CampusHttpClient;
import edu.szu.agent.client.http.CasLoginClient;
import edu.szu.agent.client.http.CasLoginClient.CasLoginResult;
import edu.szu.agent.client.session.HttpSession;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code direct-login} subcommand — development tool that logs in to SZU
 * authserver using direct HTTP requests and then probes a protected URL.
 *
 * <p>Demonstrates the migration path from Playwright browser automation to
 * direct HTTP for endpoints whose contracts are known.
 *
 * <p>// Design Pattern: Adapter (CLI wrapper around HTTP CAS client)
 * // 编程技术: 注解 / Builder / try-with-resources
 *
 * @since 0.6.0
 * @author 王子豪
 */
@Command(
    name = "direct-login",
    description = "Direct HTTP authserver login + probe (dev only)",
    mixinStandardHelpOptions = true
)
public class DirectLoginCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--cas-url"}, description = "Authserver base URL",
        defaultValue = "https://authserver.szu.edu.cn")
    private String casUrl;

    @Option(names = {"--service"}, description = "CAS service parameter",
        defaultValue = "http://www1.szu.edu.cn/manage/caslogin.asp?rurl=/")
    private String service;

    @Option(names = {"--probe-url"}, description = "URL to fetch after login",
        defaultValue = "https://www1.szu.edu.cn/board/boardlist.asp")
    private String probeUrl;

    @Option(names = {"-e", "--env-file"}, description = "Path to .env file for credentials")
    private String envFile;

    @Option(names = {"--dump-page"}, description = "Dump the CAS login page HTML to this path")
    private String dumpPagePath;

    @Option(names = {"--trust-all"}, description = "Disable TLS certificate validation (dev/internal only)")
    private boolean trustAll;

    @Option(names = {"--persist"}, description = "Save session to disk after login", defaultValue = "true")
    private boolean persist;

    @Option(names = {"--no-persist"}, description = "Do not save session to disk")
    private boolean noPersist;

    @Option(names = {"--session-home"}, description = "Directory under which .szu-agent/sessions is created",
        defaultValue = "${sys:user.home}")
    private String sessionHome;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        long startMs = System.currentTimeMillis();
        String traceId = Tracer.getInstance().generateTraceId();

        Account account;
        try {
            account = resolveAccount();
        } catch (AccountResolutionException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(),
                "Could not resolve credential for " + username
                    + " (set SZU_PASSWORD_" + username + ")", traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }

        try (CampusHttpClient http = CampusHttpClient.builder().trustAll(trustAll).build()) {
            CasLoginClient cas = CasLoginClient.builder(http, casUrl)
                .service(service)
                .passwordEncryptor(new AuthserverPasswordEncryptor())
                .build();

            if (dumpPagePath != null) {
                String loginPage = cas.fetchLoginPage();
                Path dumpPath = Path.of(dumpPagePath);
                try {
                    Files.createDirectories(dumpPath.getParent());
                    Files.writeString(dumpPath, loginPage);
                    ObjectNode data = JSON.createObjectNode();
                    data.put("dumpPath", dumpPath.toAbsolutePath().toString());
                    data.put("pageLength", loginPage.length());
                    out.println(CommandOutput.formatResult(true, data, null, null,
                        traceId, System.currentTimeMillis() - startMs, "json"));
                    return 0;
                } catch (IOException e) {
                    throw new BookingException(ErrorCode.UNKNOWN,
                        "Failed to dump login page: " + e.getMessage(), e);
                }
            }

            CasLoginResult result = cas.login(account.studentId(), account.password());

            String probeBody = null;
            int probeStatus = -1;
            try {
                probeBody = http.get(probeUrl);
                probeStatus = 200;
            } catch (BookingException probeEx) {
                probeStatus = 0;
            }

            String probePreview = null;
            if (probeBody != null && !probeBody.isBlank()) {
                int limit = Math.min(probeBody.length(), 500);
                probePreview = probeBody.substring(0, limit)
                    .replace("\r", "")
                    .replace("\n", " ")
                    .trim();
            }

            boolean shouldPersist = persist && !noPersist;
            String sessionPath = null;
            if (shouldPersist) {
                try {
                    SessionStore store = new SessionStore(Path.of(sessionHome), username);
                    HttpSession.write(store, http.cookieJar());
                    sessionPath = store.defaultPath().toAbsolutePath().toString();
                } catch (IOException e) {
                    throw new BookingException(ErrorCode.SESSION_WRITE_FAILED,
                        "Login succeeded but failed to persist state: " + e.getMessage(), e);
                }
            }

            ObjectNode data = JSON.createObjectNode();
            data.put("traceId", traceId);
            data.put("hasSession", result.hasSession());
            data.put("cookieCount", http.cookieJar().snapshot().size());
            data.put("probeStatus", probeStatus);
            data.put("probeBodyLength", probeBody != null ? probeBody.length() : -1);
            data.put("probeBodyPreview", probePreview);
            data.put("persisted", shouldPersist);
            data.put("sessionPath", sessionPath);
            data.put("durationMs", System.currentTimeMillis() - startMs);

            out.println(CommandOutput.formatResult(true, data, null, null,
                traceId, data.get("durationMs").asLong(), "json"));
            return 0;

        } catch (BookingException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                e.code().name(), e.getMessage(), traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(e.code());
        } catch (RuntimeException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.UNKNOWN.name(), "Unexpected error: " + e.getMessage(),
                traceId, elapsed, "json"));
            return 1;
        }
    }

    private Account resolveAccount() {
        if (envFile != null) {
            return AccountResolver.resolve(username, System.getenv(), Path.of(envFile));
        }
        return AccountResolver.resolve(username, System.getenv(), null);
    }
}

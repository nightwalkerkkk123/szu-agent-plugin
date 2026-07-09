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
import edu.szu.agent.client.http.RecordedExchange;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.json.JsonMappers;
import edu.szu.agent.observability.Tracer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code capture-login} subcommand — development tool for recording a CAS
 * login session so the request/response contract can be reverse-engineered.
 *
 * <p>This command is intentionally <strong>not</strong> registered as an MCP
 * tool or Skill: it requires real credentials and writes traffic metadata to
 * disk, so it is only exposed through the CLI for authorized debugging.
 *
 * <p>Credentials are resolved via {@link AccountResolver} (env / env-file /
 * Skill injection) and are never written to the recording.
 *
 * <p>// Design Pattern: Adapter (CLI wrapper around HTTP traffic recorder)
 * // 编程技术: 注解 / try-with-resources / Lambda
 *
 * @since 0.6.0
 * @author 王子豪
 */
@Command(
    name = "capture-login",
    description = "Record CAS login HTTP traffic for reverse engineering (dev only)",
    mixinStandardHelpOptions = true
)
public class CaptureLoginCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--cas-url"}, description = "CAS base URL",
        defaultValue = "https://authserver.szu.edu.cn")
    private String casUrl;

    @Option(names = {"--service"}, description = "CAS service parameter",
        defaultValue = "http://www1.szu.edu.cn/manage/caslogin.asp?rurl=/")
    private String service;

    @Option(names = {"-o", "--output"}, description = "Output JSON file path",
        defaultValue = "${java.io.tmpdir}/szu-cas-login-traffic.json")
    private String outputPath;

    @Option(names = {"-e", "--env-file"}, description = "Path to .env file for credentials")
    private String envFile;

    @Option(names = {"--trust-all"}, description = "Disable TLS certificate validation (dev/internal only)")
    private boolean trustAll;

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

        Path output = Path.of(outputPath);
        List<RecordedExchange> exchanges = Collections.synchronizedList(new ArrayList<>());

        try (CampusHttpClient http = CampusHttpClient.builder()
                .trustAll(trustAll)
                .exchangeRecorder(exchanges::add)
                .build()) {
            CasLoginClient cas = CasLoginClient.builder(http, casUrl)
                .service(service)
                .passwordEncryptor(new AuthserverPasswordEncryptor())
                .build();

            CasLoginResult result = cas.login(account.studentId(), account.password());

            try {
                Files.createDirectories(output.getParent());
                JsonMappers.standard().writerWithDefaultPrettyPrinter()
                    .writeValue(output.toFile(), exchanges);
            } catch (IOException e) {
                throw new BookingException(ErrorCode.SESSION_WRITE_FAILED,
                    "Failed to write traffic recording to " + output, e);
            }

            String finalUrl = exchanges.isEmpty() ? null : exchanges.get(exchanges.size() - 1).url().toString();

            ObjectNode resultNode = JSON.createObjectNode();
            resultNode.put("traceId", traceId);
            resultNode.put("recordedExchanges", exchanges.size());
            resultNode.put("outputPath", output.toAbsolutePath().toString());
            resultNode.put("finalUrl", finalUrl);
            resultNode.put("hasSession", result.hasSession());
            resultNode.put("durationMs", System.currentTimeMillis() - startMs);
            out.println(CommandOutput.formatResult(true, resultNode, null, null,
                traceId, resultNode.get("durationMs").asLong(), "json"));
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

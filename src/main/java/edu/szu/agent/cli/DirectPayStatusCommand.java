package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.http.CookieJar;
import edu.szu.agent.client.http.EhallSessionManager;
import edu.szu.agent.client.payment.OlepayStatusPoller;
import edu.szu.agent.client.payment.PaymentStatus;
import edu.szu.agent.client.session.HttpSession;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * {@code direct-pay-status} subcommand — poll olepay for payment status.
 *
 * @since 0.7.0
 * @author 王子豪
 */
@Command(
    name = "direct-pay-status",
    description = "Poll olepay payment status for an order",
    mixinStandardHelpOptions = true
)
public class DirectPayStatusCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--orderid"}, description = "Olepay order id", required = true)
    private String orderId;

    @Option(names = {"--timeout-seconds"}, description = "Maximum polling time in seconds",
        defaultValue = "60")
    private int timeoutSeconds;

    @Option(names = {"--poll-interval-seconds"}, description = "Polling interval in seconds",
        defaultValue = "2")
    private int pollIntervalSeconds;

    @Option(names = {"--session-home"}, description = "Directory under which .szu-agent/sessions is created",
        defaultValue = "${sys:user.home}")
    private String sessionHome;

    @Option(names = {"--trust-all"}, description = "Disable TLS certificate validation (dev/internal only)")
    private boolean trustAll;

    @Option(names = {"-e", "--env-file"}, description = "Path to .env file for account resolution")
    private String envFile;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        long startMs = System.currentTimeMillis();
        String traceId = Tracer.getInstance().generateTraceId();

        if (timeoutSeconds <= 0 || pollIntervalSeconds <= 0) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(), "Timeout and interval must be positive",
                traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }

        Account account = resolveAccount();
        if (account == null) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(),
                "Could not resolve credential for " + username,
                traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }

        try {
            SessionStore store = new SessionStore(Path.of(sessionHome), username);
            EhallSessionManager sessionManager = new EhallSessionManager(
                account.studentId(), account.password(), trustAll);
            CookieJar initialJar = loadJar(store);
            var http = sessionManager.ensureSession(initialJar);
            OlepayStatusPoller poller = new OlepayStatusPoller(http);

            long deadline = System.currentTimeMillis() + Duration.ofSeconds(timeoutSeconds).toMillis();
            PaymentStatus status = PaymentStatus.UNKNOWN;
            while (System.currentTimeMillis() < deadline) {
                status = poller.query(orderId);
                if (status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED) {
                    break;
                }
                Thread.sleep(Duration.ofSeconds(pollIntervalSeconds).toMillis());
            }

            boolean success = status == PaymentStatus.SUCCESS;
            ObjectNode data = JSON.createObjectNode();
            data.put("olepayOrderId", orderId);
            data.put("status", status.name());
            data.put("success", success);
            data.put("traceId", traceId);
            data.put("durationMs", System.currentTimeMillis() - startMs);

            String errorCode = (status == PaymentStatus.TIMEOUT || status == PaymentStatus.UNKNOWN)
                ? ErrorCode.PAYMENT_STATUS_TIMEOUT.name()
                : null;
            String errorMessage = errorCode != null
                ? "Payment status polling did not reach a terminal state"
                : null;

            out.println(CommandOutput.formatResult(success, data, errorCode, errorMessage,
                traceId, data.get("durationMs").asLong(), "json"));
            return success ? 0 : (errorCode != null ? CommandOutput.exitCodeFor(ErrorCode.PAYMENT_STATUS_TIMEOUT) : 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.UNKNOWN.name(), "Polling interrupted",
                traceId, elapsed, "json"));
            return 1;
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

    private static CookieJar loadJar(SessionStore store) {
        if (!store.exists()) {
            return new CookieJar();
        }
        try {
            return new CookieJar(HttpSession.read(store).cookies());
        } catch (Exception e) {
            return new CookieJar();
        }
    }

    private Account resolveAccount() {
        try {
            return (envFile != null)
                ? AccountResolver.resolve(username, System.getenv(), Path.of(envFile))
                : AccountResolver.resolve(username, System.getenv(), null);
        } catch (AccountResolutionException e) {
            return null;
        }
    }
}

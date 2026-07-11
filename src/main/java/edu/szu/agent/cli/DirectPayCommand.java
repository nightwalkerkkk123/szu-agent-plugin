package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.account.AccountResolver;
import com.microsoft.playwright.Playwright;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.browser.PlaywrightBrowserAdapter;
import edu.szu.agent.client.http.CookieJar;
import edu.szu.agent.client.http.EhallSessionManager;
import edu.szu.agent.client.http.EhallSportVenueClient;
import edu.szu.agent.client.session.HttpSession;
import edu.szu.agent.client.payment.DefaultPaymentMethodResolver;
import edu.szu.agent.client.payment.EhallPaymentOrderClient;
import edu.szu.agent.client.payment.ManualLinkPaymentDriver;
import edu.szu.agent.client.payment.PaymentCredentials;
import edu.szu.agent.client.payment.PaymentMethod;
import edu.szu.agent.client.payment.PaymentResult;
import edu.szu.agent.client.payment.PaymentService;
import edu.szu.agent.client.payment.PaymentStatus;
import edu.szu.agent.client.payment.PaymentStatusPoller;
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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code direct-pay} subcommand — resolve an unpaid booking and pay or return a payment link.
 *
 * @since 0.7.0
 * @author 王子豪
 */
@Command(
    name = "direct-pay",
    description = "Resolve an unpaid booking and pay / return a payment link",
    mixinStandardHelpOptions = true
)
public class DirectPayCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_PASSWORD_ENV = "SZU_CAMPUS_CARD_PASSWORD";

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--dhid"}, description = "Ehall booking DHID", required = true)
    private String dhid;

    @Option(names = {"--method"}, description = "Payment method: auto, campus_card, wechat, alipay, manual_link",
        defaultValue = "manual_link")
    private String methodName;

    @Option(names = {"--password-env"}, description = "Environment variable name for campus-card password",
        defaultValue = DEFAULT_PASSWORD_ENV)
    private String passwordEnv;

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

        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(methodName.toUpperCase());
        } catch (IllegalArgumentException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(), "Unknown payment method: " + methodName,
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

        try (Playwright playwright = Playwright.create()) {
            SessionStore store = new SessionStore(Path.of(sessionHome), username);
            EhallSessionManager sessionManager = new EhallSessionManager(
                account.studentId(), account.password(), trustAll);
            CookieJar initialJar = loadJar(store);
            var http = sessionManager.ensureSession(initialJar);
            try {
                persistSession(store, http.cookieJar());
            } catch (Exception ignored) {
                // Persistence failure is non-fatal; the operation already succeeded.
            }

            EhallSportVenueClient venueClient = new EhallSportVenueClient(http);
            EhallPaymentOrderClient orderClient = new EhallPaymentOrderClient(
                d -> venueClient.getMyBookings(1, 50).rows().stream()
                    .filter(r -> r.dhid().equals(d))
                    .findFirst()
                    .orElse(null),
                d -> {
                    String url = "https://olepay.szu.edu.cn/Order/CreateOrder?merr=1100058"
                        + "&registerid=paychangguan_2023&orderid=P" + d + "&account=" + account.studentId();
                    return http.get(url);
                }
            );

            PaymentStatusPoller poller = olepayOrderId -> PaymentStatus.UNKNOWN;

            BrowserLifecycle browser = new PlaywrightBrowserAdapter(playwright);
            PaymentService service = new PaymentService(
                orderClient,
                new DefaultPaymentMethodResolver(),
                List.of(new ManualLinkPaymentDriver()),
                poller,
                browser
            );

            PaymentCredentials credentials = new PaymentCredentials(System.getenv(passwordEnv));
            PaymentResult result = service.pay(dhid, method, credentials);

            ObjectNode data = toJson(result);
            data.put("traceId", traceId);
            data.put("username", username);
            data.put("durationMs", System.currentTimeMillis() - startMs);

            out.println(CommandOutput.formatResult(result.success(), data, null, null,
                traceId, data.get("durationMs").asLong(), "json"));
            return result.success() ? 0 : 1;
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

    private static void persistSession(SessionStore store, CookieJar jar) {
        try {
            HttpSession.write(store, jar);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist session: " + e.getMessage(), e);
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

    private ObjectNode toJson(PaymentResult result) {
        ObjectNode data = JSON.createObjectNode();
        data.put("olepayOrderId", result.olepayOrderId());
        data.put("dhid", result.dhid());
        data.put("amountFen", result.amountFen());
        data.put("amountDisplay", String.format("%.2f", result.amountFen() / 100.0));
        data.put("method", result.method().name());
        data.put("status", result.status().name());
        if (result.paidAt() != null && !result.paidAt().isBlank()) {
            data.put("paidAt", result.paidAt());
        }
        if (result.qrCodeUrl() != null && !result.qrCodeUrl().isBlank()) {
            data.put("qrCodeUrl", result.qrCodeUrl());
        }
        if (result.manualPaymentUrl() != null && !result.manualPaymentUrl().isBlank()) {
            data.put("manualPaymentUrl", result.manualPaymentUrl());
        }
        if (!result.message().isBlank()) {
            data.put("message", result.message());
        }
        return data;
    }
}

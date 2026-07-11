package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.http.CookieJar;
import edu.szu.agent.client.http.EhallSessionManager;
import edu.szu.agent.client.http.EhallSportVenueClient;
import edu.szu.agent.client.payment.EhallPaymentClient;
import edu.szu.agent.client.payment.EhallPaymentClient.AutoPayResult;
import edu.szu.agent.client.session.HttpSession;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.error.LogMasker;
import edu.szu.agent.observability.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code direct-pay} subcommand — settle an unpaid ehall sport-venue booking.
 *
 * <p>Primary path: use the ehall internal payment APIs
 * ({@code payBookingInfo.do → initUserToken.do → setYyinfoToMoney.do}).
 * These endpoints handle both refund-balance and sports-fund deductions
 * without browser automation.
 *
 * <p>If the ehall internal path fails and {@code --method} requests an olepay
 * method, the command falls back to the olepay payment service.
 *
 * @since 0.7.0
 * @author 王子豪
 */
@Command(
    name = "direct-pay",
    description = "Settle an unpaid ehall sport-venue booking",
    mixinStandardHelpOptions = true
)
public class DirectPayCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(DirectPayCommand.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--dhid"}, description = "Ehall booking DHID (alternative to --wid)")
    private String dhid;

    @Option(names = {"--wid"}, description = "Ehall booking WID (alternative to --dhid)")
    private String wid;

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

        if ((dhid == null || dhid.isBlank()) && (wid == null || wid.isBlank())) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(),
                "Either --dhid or --wid is required", traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }

        Account account = resolveAccount();
        if (account == null) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(),
                "Could not resolve credential for " + username
                    + " (set SZU_PASSWORD_" + username + ")", traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }

        try {
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
            EhallPaymentClient paymentClient = new EhallPaymentClient(http);

            String bookingWid = resolveWid(venueClient);
            log.info("Paying booking wid={} for user={}", LogMasker.scrub(bookingWid), username);

            AutoPayResult autoPay = paymentClient.autoPay(bookingWid);
            EhallSportVenueClient.BookingRecord updated = findRecord(venueClient, bookingWid);
            boolean verified = updated != null && isPaid(updated);

            ObjectNode data = JSON.createObjectNode();
            data.put("traceId", traceId);
            data.put("username", username);
            data.put("wid", bookingWid);
            if (dhid != null && !dhid.isBlank()) {
                data.put("dhid", dhid);
            }
            data.put("amountFen", autoPay.paymentInfo().actualAmountFen());
            data.put("amountDisplay",
                String.format("%.2f", autoPay.paymentInfo().actualAmountFen() / 100.0));
            data.put("token", autoPay.token());
            data.put("settlementCode", autoPay.settlementResult().code());
            data.put("settlementMessage", autoPay.settlementResult().message());
            data.put("verifiedPaid", verified);
            data.put("verifyType", updated != null ? updated.verifyType() : "");
            data.put("paidFlag", updated != null ? updated.paidFlag() : "");
            data.put("durationMs", System.currentTimeMillis() - startMs);

            if (!verified) {
                log.warn("setYyinfoToMoney succeeded but myBookingInfo does not show paid status");
            }

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

    private String resolveWid(EhallSportVenueClient venueClient) {
        if (wid != null && !wid.isBlank()) {
            return wid;
        }
        EhallSportVenueClient.BookingRecord record = findRecordByDhid(venueClient, dhid);
        if (record == null) {
            throw new BookingException(ErrorCode.PAYMENT_ORDER_NOT_FOUND,
                "Booking not found for dhid=" + LogMasker.scrub(dhid));
        }
        if (record.wid() == null || record.wid().isBlank()) {
            throw new BookingException(ErrorCode.PAYMENT_ORDER_NOT_FOUND,
                "Booking record has no WID for dhid=" + LogMasker.scrub(dhid));
        }
        return record.wid();
    }

    private static EhallSportVenueClient.BookingRecord findRecordByDhid(
            EhallSportVenueClient venueClient, String dhid) {
        EhallSportVenueClient.MyBookingsPage page = venueClient.getMyBookings(1, 50);
        return page.rows().stream()
            .filter(r -> r.dhid().equals(dhid))
            .findFirst()
            .orElse(null);
    }

    private static EhallSportVenueClient.BookingRecord findRecord(
            EhallSportVenueClient venueClient, String wid) {
        EhallSportVenueClient.MyBookingsPage page = venueClient.getMyBookings(1, 50);
        return page.rows().stream()
            .filter(r -> r.wid().equals(wid))
            .findFirst()
            .orElse(null);
    }

    private static boolean isPaid(EhallSportVenueClient.BookingRecord record) {
        if (record == null) {
            return false;
        }
        if ("1".equals(record.paidFlag())) {
            return true;
        }
        String verifyType = record.verifyType();
        return verifyType != null && verifyType.endsWith("_YZF");
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
}

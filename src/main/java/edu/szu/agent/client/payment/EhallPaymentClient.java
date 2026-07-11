package edu.szu.agent.client.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.client.http.CampusHttpClient;
import edu.szu.agent.client.http.EhallAjaxHeaders;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.error.LogMasker;
import edu.szu.agent.json.JsonMappers;
import edu.szu.agent.util.SimpleRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;

/**
 * Direct HTTP client for the SZU ehall sports-venue payment APIs.
 *
 * <p>This client drives the three-step internal payment flow discovered from
 * the live HAR:
 * <ol>
 *   <li>{@code payBookingInfo.do} returns the amount breakdown and a hidden
 *       olepay form.</li>
 *   <li>{@code initUserToken.do} registers a 32-character random token.</li>
 *   <li>{@code setYyinfoToMoney.do} settles the booking using the token and
 *       the values returned by {@code payBookingInfo.do}.</li>
 * </ol>
 *
 * <p>For bookings where the ehall internal settlement succeeds, this avoids
 * the heavier olepay browser-automation path entirely.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public final class EhallPaymentClient {

    private static final Logger log = LoggerFactory.getLogger(EhallPaymentClient.class);

    private static final String BASE = EhallAjaxHeaders.BASE;
    private static final String REFERER = EhallAjaxHeaders.REFERER;
    private static final String PAY_BOOKING_INFO_URL = BASE + "/sportVenue/payBookingInfo.do";
    private static final String INIT_USER_TOKEN_URL = BASE + "/sportVenue/initUserToken.do";
    private static final String SET_YYINFO_TO_MONEY_URL = BASE + "/sportVenue/setYyinfoToMoney.do";

    private static final double PAY_PERMITS_PER_SECOND = 1.0;

    private static final ObjectMapper MAPPER = JsonMappers.standard();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN_ALPHABET = "0123456789abcdef";
    private static final int TOKEN_LENGTH = 32;

    private static final Map<String, String> AJAX_HEADERS = EhallAjaxHeaders.standard();

    private final CampusHttpClient http;
    private final SimpleRateLimiter limiter;

    /**
     * Creates a client backed by the given HTTP transport.
     *
     * @param http the HTTP client (must already carry a logged-in ehall session)
     * @since 0.7.0
     * @author 王子豪
     */
    public EhallPaymentClient(CampusHttpClient http) {
        this(http, new SimpleRateLimiter(PAY_PERMITS_PER_SECOND));
    }

    /**
     * Creates a client with an injectable rate limiter for tests.
     *
     * @param http    the HTTP client (must already carry a logged-in ehall session)
     * @param limiter rate limiter for payment API calls
     * @since 0.7.0
     * @author 王子豪
     */
    public EhallPaymentClient(CampusHttpClient http, SimpleRateLimiter limiter) {
        this.http = Objects.requireNonNull(http, "http");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
    }

    /**
     * Fetches the payment breakdown for a booking.
     *
     * @param wid the booking WID (not DHID)
     * @return parsed payment information
     * @throws BookingException on network or parse failure
     * @since 0.7.0
     * @author 王子豪
     */
    public PaymentInfo payBookingInfo(String wid) {
        Objects.requireNonNull(wid, "wid");
        limiter.acquire();
        String body = postForm(PAY_BOOKING_INFO_URL, Map.of("WID", wid));
        log.info("payBookingInfo response for wid={}: {}", LogMasker.scrub(wid),
            LogMasker.scrub(body));
        return parsePaymentInfo(body, wid);
    }

    /**
     * Registers a user token required by {@link #setYyinfoToMoney}.
     *
     * @param token the token to register
     * @return true if the server reported success
     * @throws BookingException on network failure
     * @since 0.7.0
     * @author 王子豪
     */
    public boolean initUserToken(String token) {
        Objects.requireNonNull(token, "token");
        limiter.acquire();
        String body = postForm(INIT_USER_TOKEN_URL, Map.of("token", token));
        log.info("User credential registration response: {}", LogMasker.scrub(body));
        try {
            JsonNode root = MAPPER.readTree(stripBom(body));
            return root.path("success").asBoolean(false);
        } catch (Exception e) {
            throw new BookingException(ErrorCode.PAYMENT_GATEWAY_ERROR,
                "Failed to parse initUserToken response: " + e.getMessage(), e);
        }
    }

    /**
     * Settles a booking using the ehall internal payment endpoint.
     *
     * @param request settlement parameters
     * @return settlement result
     * @throws BookingException on network, parse, or business failure
     * @since 0.7.0
     * @author 王子豪
     */
    public SettlementResult setYyinfoToMoney(SettlementRequest request) {
        Objects.requireNonNull(request, "request");
        limiter.acquire();
        Map<String, String> form = Map.of(
            "ZFJE", String.valueOf(request.amountFen()),
            "TPIAO", String.valueOf(request.refundFen()),
            "WID", request.wid(),
            "TOKEN", request.token(),
            "ZFLX", request.payType(),
            "tksyje", String.valueOf(request.refundFen())
        );
        String body = postForm(SET_YYINFO_TO_MONEY_URL, form);
        log.info("setYyinfoToMoney response for wid={}: {}",
            LogMasker.scrub(request.wid()), LogMasker.scrub(body));
        return parseSettlementResult(body);
    }

    /**
     * High-level auto-pay: generate token, register it, and settle the booking.
     *
     * @param wid the booking WID
     * @return the settlement result, including the generated token
     * @throws BookingException if any step fails
     * @since 0.7.0
     * @author 王子豪
     */
    public AutoPayResult autoPay(String wid) {
        PaymentInfo info = payBookingInfo(wid);
        String token = generateToken();
        boolean tokenRegistered = initUserToken(token);
        if (!tokenRegistered) {
            throw new BookingException(ErrorCode.PAYMENT_GATEWAY_ERROR,
                "initUserToken refused token for wid=" + LogMasker.scrub(wid));
        }
        SettlementRequest request = new SettlementRequest(
            wid, info.actualAmountFen(), info.refundBalanceFen(), token, "jingfei");
        SettlementResult result = setYyinfoToMoney(request);
        if (!result.success()) {
            throw new BookingException(ErrorCode.PAYMENT_GATEWAY_ERROR,
                "setYyinfoToMoney failed: [" + result.code() + "] " + result.message());
        }
        return new AutoPayResult(wid, token, info, result);
    }

    private String postForm(String url, Map<String, String> form) {
        return http.postForm(url, REFERER, AJAX_HEADERS, form);
    }

    private PaymentInfo parsePaymentInfo(String body, String wid) {
        try {
            JsonNode root = MAPPER.readTree(stripBom(body));
            if (!root.path("success").asBoolean(true)) {
                String code = root.path("code").asText("");
                String msg = root.path("msg").asText("");
                throw gatewayExceptionForCode(code, msg,
                    "payBookingInfo returned failure for wid=" + LogMasker.scrub(wid));
            }
            int totalFen = parseFen(root.path("zuizong").asText("0"));
            int refundBalanceFen = parseFen(root.path("tuipiao").asText("0"));
            int refundRemainingFen = parseFen(root.path("tksyje").asText("0"));
            int actualAmountFen = parseFen(root.path("gxje").asText("0"));
            String html = root.path("content").asText("");
            return new PaymentInfo(wid, totalFen, refundBalanceFen, refundRemainingFen,
                actualAmountFen, html);
        } catch (BookingException e) {
            throw e;
        } catch (Exception e) {
            throw new BookingException(ErrorCode.PAYMENT_GATEWAY_ERROR,
                "Failed to parse payBookingInfo response: " + e.getMessage(), e);
        }
    }

    private SettlementResult parseSettlementResult(String body) {
        try {
            JsonNode root = MAPPER.readTree(stripBom(body));
            String code = root.path("code").asText("");
            String msg = root.path("msg").asText("");
            if ("E111080000000".equals(code) || isRateLimited(msg)) {
                throw new BookingException(ErrorCode.RATE_LIMITED,
                    "Payment rate limited: [" + code + "] " + msg);
            }
            return new SettlementResult("0".equals(code), code, msg);
        } catch (BookingException e) {
            throw e;
        } catch (Exception e) {
            throw new BookingException(ErrorCode.PAYMENT_GATEWAY_ERROR,
                "Failed to parse setYyinfoToMoney response: " + e.getMessage(), e);
        }
    }

    private static BookingException gatewayExceptionForCode(String code, String msg,
                                                             String fallback) {
        if ("E111080000000".equals(code) || isRateLimited(msg)) {
            return new BookingException(ErrorCode.RATE_LIMITED,
                "Payment rate limited: [" + code + "] " + msg);
        }
        return new BookingException(ErrorCode.PAYMENT_GATEWAY_ERROR,
            fallback + " [" + code + "] " + msg);
    }

    private static boolean isRateLimited(String msg) {
        return msg != null && msg.contains("操作过于频繁");
    }

    private static int parseFen(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            double yuan = Double.parseDouble(value.trim());
            return (int) Math.round(yuan * 100);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String generateToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(TOKEN_ALPHABET.charAt(RANDOM.nextInt(TOKEN_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String stripBom(String body) {
        if (body != null && !body.isEmpty() && body.charAt(0) == '\uFEFF') {
            return body.substring(1);
        }
        return body;
    }

    /**
     * Payment breakdown returned by {@link #payBookingInfo(String)}.
     *
     * @param wid                 booking WID
     * @param totalFen            total amount due, in fen
     * @param refundBalanceFen    available refund balance, in fen
     * @param refundRemainingFen  refund balance remaining after this payment, in fen
     * @param actualAmountFen     amount actually deducted now, in fen
     * @param html                raw payment dialog HTML (contains olepay form fallback)
     * @since 0.7.0
     * @author 王子豪
     */
    public record PaymentInfo(
        String wid,
        int totalFen,
        int refundBalanceFen,
        int refundRemainingFen,
        int actualAmountFen,
        String html
    ) {
    }

    /**
     * Parameters for {@link #setYyinfoToMoney(SettlementRequest)}.
     *
     * @param wid        booking WID
     * @param amountFen  amount to settle, in fen
     * @param refundFen  refund balance portion, in fen
     * @param token      registered token
     * @param payType    payment type, e.g. {@code jingfei}
     * @since 0.7.0
     * @author 王子豪
     */
    public record SettlementRequest(
        String wid,
        int amountFen,
        int refundFen,
        String token,
        String payType
    ) {
    }

    /**
     * Outcome of {@link #setYyinfoToMoney(SettlementRequest)}.
     *
     * @param success whether the server returned {@code code="0"}
     * @param code    server response code
     * @param message server response message
     * @since 0.7.0
     * @author 王子豪
     */
    public record SettlementResult(
        boolean success,
        String code,
        String message
    ) {
    }

    /**
     * Result of the high-level {@link #autoPay(String)} flow.
     *
     * @param wid            booking WID
     * @param token          registered token
     * @param paymentInfo    payment breakdown
     * @param settlementResult server settlement result
     * @since 0.7.0
     * @author 王子豪
     */
    public record AutoPayResult(
        String wid,
        String token,
        PaymentInfo paymentInfo,
        SettlementResult settlementResult
    ) {
    }
}

package edu.szu.agent.client.payment;

import edu.szu.agent.client.http.EhallSportVenueClient;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.error.LogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves olepay order parameters from the ehall booking record and the
 * olepay CreateOrder HTML page.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public class EhallPaymentOrderClient implements PaymentOrderClient {

    private static final Logger log = LoggerFactory.getLogger(EhallPaymentOrderClient.class);

    private static final String DEFAULT_MERCHANT_NO = "1100058";
    private static final String DEFAULT_REGISTER_ID = "paychangguan_2023";

    private static final Pattern ORDER_ID = Pattern.compile(
        "id=\"txtorderid\"\\s+value=\"([^\"]+)\"");
    private static final Pattern ACCOUNT = Pattern.compile(
        "id=\"txtotheraccount\"\\s+value=\"([^\"]+)\"");
    private static final Pattern STUDENT_NAME = Pattern.compile(
        "id=\"txtName\"\\s+value=\"([^\"]+)\"");
    private static final Pattern STUDENT_ID = Pattern.compile(
        "学工号：[^\\d]*(\\d{10})");
    private static final Pattern AMOUNT = Pattern.compile(
        "交易金额：[^\\d]*([\\d.]+)");

    private final Function<String, EhallSportVenueClient.BookingRecord> bookingLookup;
    private final Function<String, String> createOrderHtmlProvider;

    /**
     * Creates a client with injectable lookup and HTML providers.
     *
     * @param bookingLookup         resolves a booking record by DHID
     * @param createOrderHtmlProvider fetches the olepay CreateOrder HTML by DHID
     * @since 0.7.0
     * @author 王子豪
     */
    public EhallPaymentOrderClient(
        Function<String, EhallSportVenueClient.BookingRecord> bookingLookup,
        Function<String, String> createOrderHtmlProvider) {
        this.bookingLookup = Objects.requireNonNull(bookingLookup, "bookingLookup");
        this.createOrderHtmlProvider = Objects.requireNonNull(createOrderHtmlProvider, "createOrderHtmlProvider");
    }

    @Override
    public PaymentInitParams resolve(String dhid) {
        EhallSportVenueClient.BookingRecord record = bookingLookup.apply(dhid);
        if (record == null) {
            throw new BookingException(ErrorCode.PAYMENT_ORDER_NOT_FOUND,
                "Booking not found for dhid=" + LogMasker.scrub(dhid));
        }

        int amountFen = parseAmount(record.amount());
        String html = createOrderHtmlProvider.apply(dhid);
        if (html == null || html.isBlank()) {
            throw new BookingException(ErrorCode.PAYMENT_GATEWAY_ERROR,
                "Empty olepay CreateOrder response for dhid=" + LogMasker.scrub(dhid));
        }

        String olepayOrderId = firstMatch(ORDER_ID, html);
        if (olepayOrderId.isBlank()) {
            olepayOrderId = "P" + dhid;
        }

        String account = firstMatch(ACCOUNT, html);
        if (account.isBlank()) {
            account = extractAccountFromScript(html);
        }

        String studentName = firstMatch(STUDENT_NAME, html);
        String studentId = firstMatch(STUDENT_ID, html);
        int parsedAmountFen = parseAmountFromHtml(html);
        if (parsedAmountFen > 0) {
            amountFen = parsedAmountFen;
        }

        boolean paid = isPaid(record);
        log.info("Resolved olepay params for dhid={}, orderId={}, amountFen={}, paid={}",
            LogMasker.scrub(dhid), olepayOrderId, amountFen, paid);

        return new PaymentInitParams(
            olepayOrderId,
            dhid,
            DEFAULT_MERCHANT_NO,
            DEFAULT_REGISTER_ID,
            account,
            amountFen,
            amountFen,
            "",
            "%E5%9C%BA%E9%A6%86%E9%A2%84%E7%BA%A6",
            "%E4%BD%93%E8%82%B2%E4%B8%93%E9%A1%B9%E7%BB%8F%E8%B4%B9",
            studentName,
            studentId,
            "",
            "",
            paid
        );
    }

    private static boolean isPaid(EhallSportVenueClient.BookingRecord record) {
        if (record == null) {
            return false;
        }
        // SFZF: 1 = paid, 0 = unpaid
        if ("1".equals(record.paidFlag())) {
            return true;
        }
        // VERIFY_TYPE codes ending with _YZF are paid; _WZF are unpaid.
        String verifyType = record.verifyType();
        if (verifyType != null && verifyType.endsWith("_YZF")) {
            return true;
        }
        return false;
    }

    private static String firstMatch(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String extractAccountFromScript(String html) {
        Pattern p = Pattern.compile("otheraccount\\s*:\\s*'([^']+)'");
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1).trim() : "";
    }

    private static int parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return 0;
        }
        try {
            return (int) Math.round(Double.parseDouble(amount.trim()) * 100);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseAmountFromHtml(String html) {
        Matcher matcher = AMOUNT.matcher(html);
        if (!matcher.find()) {
            return 0;
        }
        return parseAmount(matcher.group(1));
    }
}

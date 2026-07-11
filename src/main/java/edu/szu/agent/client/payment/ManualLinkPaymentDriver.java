package edu.szu.agent.client.payment;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.LogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Fallback driver that returns the official olepay CreateOrder URL.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public class ManualLinkPaymentDriver implements PaymentAutomationDriver {

    private static final Logger log = LoggerFactory.getLogger(ManualLinkPaymentDriver.class);

    private static final String CREATE_ORDER_URL = "https://olepay.szu.edu.cn/Order/CreateOrder";

    @Override
    public boolean supports(PaymentInitParams params, PaymentMethod method) {
        return method == PaymentMethod.MANUAL_LINK;
    }

    @Override
    public PaymentResult execute(BrowserLifecycle browser,
                                 PaymentInitParams params,
                                 PaymentCredentials credentials) {
        String url = buildUrl(params);
        log.info("Returning manual payment link for orderId={}",
            LogMasker.scrub(params.olepayOrderId()));
        return PaymentResult.pending(
            params.olepayOrderId(),
            params.thirdOrderId(),
            PaymentMethod.MANUAL_LINK,
            null,
            url,
            "Complete payment manually using the returned link"
        );
    }

    private static String buildUrl(PaymentInitParams params) {
        StringBuilder sb = new StringBuilder(CREATE_ORDER_URL);
        sb.append("?merr=").append(encode(params.merchantNo()));
        sb.append("&registerid=").append(encode(params.registerId()));
        sb.append("&orderid=").append(encode(params.olepayOrderId()));
        sb.append("&account=").append(encode(params.account()));
        if (!params.orderDesc().isBlank()) {
            sb.append("&orderdesc=").append(encode(params.orderDesc()));
        }
        return sb.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

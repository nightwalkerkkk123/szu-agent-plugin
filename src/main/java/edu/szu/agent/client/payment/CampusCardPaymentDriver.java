package edu.szu.agent.client.payment;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.error.LogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automates campus-card payment via olepay SynCard/SynAccType.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public class CampusCardPaymentDriver implements PaymentAutomationDriver {

    private static final Logger log = LoggerFactory.getLogger(CampusCardPaymentDriver.class);

    @Override
    public boolean supports(PaymentInitParams params, PaymentMethod method) {
        return method == PaymentMethod.CAMPUS_CARD;
    }

    @Override
    public PaymentResult execute(BrowserLifecycle browser,
                                 PaymentInitParams params,
                                 PaymentCredentials credentials) {
        if (!credentials.hasCampusCardPassword()) {
            throw new BookingException(ErrorCode.PAYMENT_PASSWORD_REQUIRED,
                "Campus-card payment requires SZU_CAMPUS_CARD_PASSWORD environment variable");
        }

        String payUrl = "https://olepay.szu.edu.cn/Pay/SynCard?orderid="
            + params.olepayOrderId() + "&payid=" + params.account();
        log.info("Navigating to campus-card payment page for orderId={}",
            LogMasker.scrub(params.olepayOrderId()));

        browser.navigateTo(payUrl);
        browser.fill("#txtaccount", params.account());
        browser.fill("#txtpasswd", credentials.campusCardPassword());
        browser.click("#btn_login");

        boolean success = browser.waitForVisible(".pay-success", 5000L)
            || browser.currentUrl().contains("PaySuccess");

        if (success) {
            String paidAt = java.time.Instant.now().toString();
            log.info("Campus-card payment succeeded for orderId={}",
                LogMasker.scrub(params.olepayOrderId()));
            return PaymentResult.success(params.olepayOrderId(), params.thirdOrderId(),
                params.actualAmountFen(), PaymentMethod.CAMPUS_CARD, paidAt);
        }

        String message = browser.textOf(".error-msg");
        if (message.contains("密码") || message.contains("password")) {
            throw new BookingException(ErrorCode.PAYMENT_PASSWORD_INCORRECT,
                "Campus-card password incorrect");
        }
        return PaymentResult.failed(params.olepayOrderId(), params.thirdOrderId(),
            PaymentMethod.CAMPUS_CARD, message);
    }
}

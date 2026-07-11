package edu.szu.agent.client.payment;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.error.LogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * High-level orchestrator for SZU olepay payments.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentOrderClient orderClient;
    private final PaymentMethodResolver methodResolver;
    private final List<PaymentAutomationDriver> drivers;
    private final PaymentStatusPoller statusPoller;
    private final BrowserLifecycle browser;

    /**
     * Creates a new payment service.
     *
     * @param orderClient   resolves olepay order parameters
     * @param methodResolver resolves concrete payment method
     * @param drivers       available payment automation drivers
     * @param statusPoller  payment status poller
     * @param browser       browser lifecycle for automation-capable drivers
     * @since 0.7.0
     * @author 王子豪
     */
    public PaymentService(PaymentOrderClient orderClient,
                          PaymentMethodResolver methodResolver,
                          List<PaymentAutomationDriver> drivers,
                          PaymentStatusPoller statusPoller,
                          BrowserLifecycle browser) {
        this.orderClient = Objects.requireNonNull(orderClient, "orderClient");
        this.methodResolver = Objects.requireNonNull(methodResolver, "methodResolver");
        this.drivers = List.copyOf(Objects.requireNonNull(drivers, "drivers"));
        this.statusPoller = Objects.requireNonNull(statusPoller, "statusPoller");
        this.browser = browser;
    }

    /**
     * Resolves the order and executes the chosen payment method.
     *
     * @param dhid   ehall booking DHID
     * @param method requested payment method
     * @param credentials env-sourced credentials
     * @return payment result
     * @since 0.7.0
     * @author 王子豪
     */
    public PaymentResult pay(String dhid, PaymentMethod method, PaymentCredentials credentials) {
        PaymentInitParams params = orderClient.resolve(dhid);

        if (params.paid()) {
            log.info("Order dhid={} is already marked paid (VERIFY_TYPE/SFZF)",
                LogMasker.scrub(dhid));
            return PaymentResult.alreadyPaid(params.olepayOrderId(), dhid, params.actualAmountFen());
        }

        PaymentMethod resolved = methodResolver.resolve(params, method, credentials);
        log.info("Resolved payment method {} for dhid={}", resolved, LogMasker.scrub(dhid));

        for (PaymentAutomationDriver driver : drivers) {
            if (driver.supports(params, resolved)) {
                PaymentResult result = driver.execute(browser, params, credentials);
                if (result.status() == PaymentStatus.PENDING && resolved != PaymentMethod.MANUAL_LINK) {
                    PaymentStatus polled = statusPoller.query(params.olepayOrderId());
                    if (polled == PaymentStatus.SUCCESS) {
                        return PaymentResult.success(params.olepayOrderId(), dhid,
                            params.actualAmountFen(), resolved, java.time.Instant.now().toString());
                    }
                }
                return result;
            }
        }

        throw new BookingException(ErrorCode.PAYMENT_METHOD_UNAVAILABLE,
            "No driver available for method " + resolved);
    }

    /**
     * Queries the current payment status for an olepay order.
     *
     * @param olepayOrderId olepay order id
     * @return current status
     * @since 0.7.0
     * @author 王子豪
     */
    public PaymentStatus queryStatus(String olepayOrderId) {
        return statusPoller.query(olepayOrderId);
    }
}

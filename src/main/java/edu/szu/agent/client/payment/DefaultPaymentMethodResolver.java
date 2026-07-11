package edu.szu.agent.client.payment;

import edu.szu.agent.error.LogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default resolver: campus card when password is present, otherwise manual link.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public class DefaultPaymentMethodResolver implements PaymentMethodResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentMethodResolver.class);

    @Override
    public PaymentMethod resolve(PaymentInitParams params, PaymentMethod preferred,
                                 PaymentCredentials credentials) {
        if (preferred == PaymentMethod.CAMPUS_CARD || preferred == PaymentMethod.AUTO) {
            if (credentials.hasCampusCardPassword()) {
                log.info("Selected CAMPUS_CARD for orderId={}", LogMasker.scrub(params.olepayOrderId()));
                return PaymentMethod.CAMPUS_CARD;
            }
        }
        log.info("Selected MANUAL_LINK for orderId={}", LogMasker.scrub(params.olepayOrderId()));
        return PaymentMethod.MANUAL_LINK;
    }
}

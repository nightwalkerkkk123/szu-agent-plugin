package edu.szu.agent.client.payment;

/**
 * Resolves olepay order parameters for a given ehall booking DHID.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public interface PaymentOrderClient {

    /**
     * Resolves the ehall booking record and olepay CreateOrder page into
     * wire-ready payment initialization parameters.
     *
     * @param dhid ehall booking DHID
     * @return payment initialization parameters
     * @throws edu.szu.agent.error.BookingException if the order cannot be resolved
     * @since 0.7.0
     * @author 王子豪
     */
    PaymentInitParams resolve(String dhid);
}

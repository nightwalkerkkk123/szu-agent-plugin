package edu.szu.agent.client.payment;

/**
 * Polls the current status of an olepay payment order.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public interface PaymentStatusPoller {

    /**
     * Queries the current status of the given olepay order.
     *
     * @param olepayOrderId olepay order identifier
     * @return current payment status
     * @since 0.7.0
     * @author 王子豪
     */
    PaymentStatus query(String olepayOrderId);
}

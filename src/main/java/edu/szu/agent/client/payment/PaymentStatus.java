package edu.szu.agent.client.payment;

/**
 * Lifecycle status of an olepay payment.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    UNKNOWN
}

package edu.szu.agent.client.payment;

/**
 * Selects the concrete payment method given user preference and credentials.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public interface PaymentMethodResolver {

    /**
     * Resolves the concrete payment method to use.
     *
     * @param params     payment initialization parameters
     * @param preferred  user-preferred payment method
     * @param credentials env-sourced payment credentials
     * @return concrete payment method
     * @since 0.7.0
     * @author 王子豪
     */
    PaymentMethod resolve(PaymentInitParams params, PaymentMethod preferred, PaymentCredentials credentials);
}

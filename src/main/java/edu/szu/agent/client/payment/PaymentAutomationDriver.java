package edu.szu.agent.client.payment;

import edu.szu.agent.browser.BrowserLifecycle;

/**
 * Strategy interface for executing a specific payment method.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public interface PaymentAutomationDriver {

    /**
     * Returns whether this driver can handle the requested payment method.
     *
     * @param params payment initialization parameters
     * @param method requested payment method
     * @return true if this driver should execute the payment
     * @since 0.7.0
     * @author 王子豪
     */
    boolean supports(PaymentInitParams params, PaymentMethod method);

    /**
     * Executes the payment method and returns the result.
     *
     * @param browser     browser lifecycle for automation-capable drivers
     * @param params      payment initialization parameters
     * @param credentials env-sourced payment credentials
     * @return payment result
     * @since 0.7.0
     * @author 王子豪
     */
    PaymentResult execute(BrowserLifecycle browser,
                          PaymentInitParams params,
                          PaymentCredentials credentials);
}

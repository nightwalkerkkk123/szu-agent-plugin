package edu.szu.agent.client.payment;

/**
 * Payment credentials sourced from environment variables.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public record PaymentCredentials(String campusCardPassword) {
    public PaymentCredentials {
        if (campusCardPassword == null) {
            campusCardPassword = "";
        }
    }

    /**
     * Returns whether a non-blank campus card password is present.
     *
     * @return {@code true} if a password is available
     * @since 0.7.0
     * @author 王子豪
     */
    public boolean hasCampusCardPassword() {
        return !campusCardPassword.isBlank();
    }
}

package edu.szu.agent.client.payment;

/**
 * Outcome of a payment attempt.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public record PaymentResult(
    boolean success,
    PaymentStatus status,
    String olepayOrderId,
    String dhid,
    int amountFen,
    PaymentMethod method,
    String paidAt,
    String qrCodeUrl,
    String manualPaymentUrl,
    String message
) {
    public PaymentResult {
        olepayOrderId = olepayOrderId == null ? "" : olepayOrderId;
        dhid = dhid == null ? "" : dhid;
        paidAt = paidAt == null ? "" : paidAt;
        qrCodeUrl = qrCodeUrl == null ? "" : qrCodeUrl;
        manualPaymentUrl = manualPaymentUrl == null ? "" : manualPaymentUrl;
        message = message == null ? "" : message;
    }

    /**
     * Creates a result indicating the order is already paid or has zero amount.
     *
     * @param olepayOrderId the olepay order id
     * @param dhid the order dhid
     * @param amountFen the paid amount in fen
     * @return a successful {@link PaymentResult}
     * @since 0.7.0
     * @author 王子豪
     */
    public static PaymentResult alreadyPaid(String olepayOrderId, String dhid, int amountFen) {
        return new PaymentResult(true, PaymentStatus.SUCCESS, olepayOrderId, dhid,
            amountFen, PaymentMethod.MANUAL_LINK, null, null, null,
            "Order is already paid or amount is zero");
    }

    /**
     * Creates a pending result for payment methods requiring further user action.
     *
     * @param olepayOrderId the olepay order id
     * @param dhid the order dhid
     * @param method the selected payment method
     * @param qrCodeUrl the QR code URL, may be null
     * @param manualPaymentUrl the manual payment URL, may be null
     * @param message a human-readable message
     * @return a pending {@link PaymentResult}
     * @since 0.7.0
     * @author 王子豪
     */
    public static PaymentResult pending(String olepayOrderId, String dhid,
                                        PaymentMethod method, String qrCodeUrl,
                                        String manualPaymentUrl, String message) {
        return new PaymentResult(false, PaymentStatus.PENDING, olepayOrderId, dhid,
            0, method, null, qrCodeUrl, manualPaymentUrl, message);
    }

    /**
     * Creates a successful result for a completed payment.
     *
     * @param olepayOrderId the olepay order id
     * @param dhid the order dhid
     * @param amountFen the paid amount in fen
     * @param method the payment method used
     * @param paidAt the payment completion timestamp
     * @return a successful {@link PaymentResult}
     * @since 0.7.0
     * @author 王子豪
     */
    public static PaymentResult success(String olepayOrderId, String dhid,
                                        int amountFen, PaymentMethod method,
                                        String paidAt) {
        return new PaymentResult(true, PaymentStatus.SUCCESS, olepayOrderId, dhid,
            amountFen, method, paidAt, null, null, "Payment completed");
    }

    /**
     * Creates a failed result for an unsuccessful payment attempt.
     *
     * @param olepayOrderId the olepay order id
     * @param dhid the order dhid
     * @param method the payment method used
     * @param message a human-readable failure reason
     * @return a failed {@link PaymentResult}
     * @since 0.7.0
     * @author 王子豪
     */
    public static PaymentResult failed(String olepayOrderId, String dhid,
                                       PaymentMethod method, String message) {
        return new PaymentResult(false, PaymentStatus.FAILED, olepayOrderId, dhid,
            0, method, null, null, null, message);
    }
}

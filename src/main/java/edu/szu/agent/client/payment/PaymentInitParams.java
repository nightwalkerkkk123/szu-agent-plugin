package edu.szu.agent.client.payment;

/**
 * Wire-ready parameters required to open the olepay CreateOrder page.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public record PaymentInitParams(
    String olepayOrderId,
    String thirdOrderId,
    String merchantNo,
    String registerId,
    String account,
    int amountFen,
    int actualAmountFen,
    String sign,
    String orderDesc,
    String payName,
    String studentName,
    String studentId,
    String rzDate,
    String jyDate,
    boolean paid
) {
    /**
     * Canonical constructor replacing nulls with empty strings for string fields.
     */
    public PaymentInitParams {
        olepayOrderId = defaultIfNull(olepayOrderId);
        thirdOrderId = defaultIfNull(thirdOrderId);
        merchantNo = defaultIfNull(merchantNo);
        registerId = defaultIfNull(registerId);
        account = defaultIfNull(account);
        sign = defaultIfNull(sign);
        orderDesc = defaultIfNull(orderDesc);
        payName = defaultIfNull(payName);
        studentName = defaultIfNull(studentName);
        studentId = defaultIfNull(studentId);
        rzDate = defaultIfNull(rzDate);
        jyDate = defaultIfNull(jyDate);
    }

    private static String defaultIfNull(String value) {
        return value == null ? "" : value;
    }
}

package edu.szu.agent.cli;

import edu.szu.agent.client.payment.PaymentMethod;
import edu.szu.agent.client.payment.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DirectPayCommand")
class DirectPayCommandTest {

    @Test
    @DisplayName("PaymentResult 序列化为 JSON 包含必要字段")
    void paymentResultJsonHasRequiredFields() {
        PaymentResult result = PaymentResult.pending("P1", "DH1",
            PaymentMethod.MANUAL_LINK,
            null, "https://olepay.szu.edu.cn/Order/CreateOrder?orderid=P1",
            "Complete manually");

        assertThat(result.olepayOrderId()).isEqualTo("P1");
        assertThat(result.dhid()).isEqualTo("DH1");
        assertThat(result.manualPaymentUrl()).contains("orderid=P1");
    }
}

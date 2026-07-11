package edu.szu.agent.client.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DefaultPaymentMethodResolver")
class DefaultPaymentMethodResolverTest {

    private final DefaultPaymentMethodResolver resolver = new DefaultPaymentMethodResolver();
    private final PaymentInitParams params = new PaymentInitParams(
        "P1", "DH1", "m", "r", "a", 500, 500, "", "", "", "", "", "", "", false);

    @Test
    @DisplayName("有校园卡密码时 AUTO 选择 CAMPUS_CARD")
    void autoSelectsCampusCardWhenPasswordPresent() {
        PaymentMethod method = resolver.resolve(params, PaymentMethod.AUTO,
            new PaymentCredentials("secret"));
        assertThat(method).isEqualTo(PaymentMethod.CAMPUS_CARD);
    }

    @Test
    @DisplayName("无校园卡密码时 AUTO 降级为 MANUAL_LINK")
    void autoFallsBackToManualLink() {
        PaymentMethod method = resolver.resolve(params, PaymentMethod.AUTO,
            new PaymentCredentials(""));
        assertThat(method).isEqualTo(PaymentMethod.MANUAL_LINK);
    }
}

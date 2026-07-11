package edu.szu.agent.client.payment;

import edu.szu.agent.browser.BrowserLifecycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PaymentService")
class PaymentServiceTest {

    private final PaymentOrderClient orderClient = mock(PaymentOrderClient.class);
    private final PaymentStatusPoller poller = mock(PaymentStatusPoller.class);
    private final BrowserLifecycle browser = mock(BrowserLifecycle.class);
    private final ManualLinkPaymentDriver manualDriver = new ManualLinkPaymentDriver();
    private final DefaultPaymentMethodResolver resolver = new DefaultPaymentMethodResolver();

    private final PaymentService service = new PaymentService(
        orderClient, resolver, List.of(manualDriver), poller, browser);

    @Test
    @DisplayName("零金额订单直接返回已支付")
    void zeroAmountReturnsAlreadyPaid() {
        when(orderClient.resolve("202607102327025769"))
            .thenReturn(new PaymentInitParams("P1", "202607102327025769", "m", "r", "a",
                0, 0, "", "", "", "", "", "", ""));

        PaymentResult result = service.pay("202607102327025769", PaymentMethod.AUTO,
            new PaymentCredentials(""));

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("非零金额返回手动支付链接")
    void nonZeroAmountReturnsManualLink() {
        when(orderClient.resolve("202607102327025769"))
            .thenReturn(new PaymentInitParams("P1", "202607102327025769", "m", "r", "a",
                500, 500, "", "", "", "", "", "", ""));

        PaymentResult result = service.pay("202607102327025769", PaymentMethod.MANUAL_LINK,
            new PaymentCredentials(""));

        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.method()).isEqualTo(PaymentMethod.MANUAL_LINK);
        assertThat(result.manualPaymentUrl()).isNotBlank();
    }
}

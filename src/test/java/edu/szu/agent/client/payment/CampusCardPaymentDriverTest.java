package edu.szu.agent.client.payment;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CampusCardPaymentDriver")
class CampusCardPaymentDriverTest {

    private final CampusCardPaymentDriver driver = new CampusCardPaymentDriver();
    private final BrowserLifecycle browser = mock(BrowserLifecycle.class);

    @Test
    @DisplayName("无密码时抛出 PAYMENT_PASSWORD_REQUIRED")
    void requiresPassword() {
        PaymentInitParams params = sampleParams();
        assertThatThrownBy(() -> driver.execute(browser, params, new PaymentCredentials("")))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code())
                .isEqualTo(ErrorCode.PAYMENT_PASSWORD_REQUIRED));
    }

    @Test
    @DisplayName("支付成功返回 SUCCESS")
    void successWhenPageConfirms() {
        when(browser.currentUrl()).thenReturn("https://olepay.szu.edu.cn/Pay/PaySuccess?orderid=P1");
        when(browser.waitForVisible(".pay-success", 5000L)).thenReturn(false);

        PaymentResult result = driver.execute(browser, sampleParams(),
            new PaymentCredentials("secret"));

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.method()).isEqualTo(PaymentMethod.CAMPUS_CARD);
    }

    private static PaymentInitParams sampleParams() {
        return new PaymentInitParams(
            "P2026071023270396455588000733",
            "202607102327025769",
            "1100058",
            "paychangguan_2023",
            "455588",
            500,
            500,
            "",
            "",
            "",
            "王子豪",
            "2023150090",
            "",
            "",
            false
        );
    }
}

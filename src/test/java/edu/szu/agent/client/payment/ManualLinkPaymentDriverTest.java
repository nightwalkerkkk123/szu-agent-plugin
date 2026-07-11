package edu.szu.agent.client.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ManualLinkPaymentDriver")
class ManualLinkPaymentDriverTest {

    private final ManualLinkPaymentDriver driver = new ManualLinkPaymentDriver();

    @Test
    @DisplayName("仅支持 MANUAL_LINK")
    void supportsOnlyManualLink() {
        PaymentInitParams params = sampleParams();
        assertThat(driver.supports(params, PaymentMethod.MANUAL_LINK)).isTrue();
        assertThat(driver.supports(params, PaymentMethod.CAMPUS_CARD)).isFalse();
    }

    @Test
    @DisplayName("返回 olepay CreateOrder 手动链接")
    void returnsManualPaymentUrl() {
        PaymentInitParams params = sampleParams();
        PaymentResult result = driver.execute(null, params, new PaymentCredentials(""));

        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.method()).isEqualTo(PaymentMethod.MANUAL_LINK);
        assertThat(result.manualPaymentUrl())
            .contains("https://olepay.szu.edu.cn/Order/CreateOrder")
            .contains("orderid=P2026071023270396455588000733")
            .contains("merr=1100058")
            .contains("registerid=paychangguan_2023");
        assertThat(result.qrCodeUrl()).isNullOrEmpty();
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
            "%E5%9C%BA%E9%A6%86%E9%A2%84%E7%BA%A6",
            "%E4%BD%93%E8%82%B2%E4%B8%93%E9%A1%B9%E7%BB%8F%E8%B4%B9",
            "王子豪",
            "2023150090",
            "",
            "",
            false
        );
    }
}

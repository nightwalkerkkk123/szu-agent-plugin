package edu.szu.agent.client.payment;

import edu.szu.agent.client.http.EhallSportVenueClient;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EhallPaymentOrderClient")
class EhallPaymentOrderClientTest {

    @Test
    @DisplayName("解析 olepay CreateOrder 表单")
    void parsesOlepayCreateOrderForm() throws IOException {
        String html = new String(getClass().getResourceAsStream("/payment/olepay-create-order.html")
            .readAllBytes(), StandardCharsets.UTF_8);

        EhallPaymentOrderClient client = new EhallPaymentOrderClient(
            dhid -> new EhallSportVenueClient.BookingRecord(
                dhid, "wid", "1", "粤海", "004", "网球", "015", "北区网球场",
                "venue-wid", "北区网球1号场", "1.0", "CG_YY", "已预约",
                "2026-07-10 15:00~16:00", "2026-07-10 22:35:42", "5.00"
            ),
            dhid -> html
        );

        PaymentInitParams params = client.resolve("202607102327025769");

        assertThat(params.olepayOrderId()).isEqualTo("P2026071023270396455588000733");
        assertThat(params.thirdOrderId()).isEqualTo("202607102327025769");
        assertThat(params.account()).isEqualTo("455588");
        assertThat(params.studentName()).isEqualTo("王子豪");
        assertThat(params.studentId()).isEqualTo("2023150090");
        assertThat(params.amountFen()).isEqualTo(500);
        assertThat(params.merchantNo()).isEqualTo("1100058");
        assertThat(params.registerId()).isEqualTo("paychangguan_2023");
    }

    @Test
    @DisplayName("订单不存在时抛出 PAYMENT_ORDER_NOT_FOUND")
    void throwsWhenOrderNotFound() {
        EhallPaymentOrderClient client = new EhallPaymentOrderClient(
            dhid -> null,
            dhid -> ""
        );

        assertThatThrownBy(() -> client.resolve("missing"))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code())
                .isEqualTo(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
    }
}

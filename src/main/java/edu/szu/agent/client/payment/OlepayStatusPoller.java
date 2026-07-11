package edu.szu.agent.client.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.client.http.CampusHttpClient;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.error.LogMasker;
import edu.szu.agent.json.JsonMappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Polls {@code https://olepay.szu.edu.cn/AjaxHandler/Pay/GetOrderIdState}.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public class OlepayStatusPoller implements PaymentStatusPoller {

    private static final Logger log = LoggerFactory.getLogger(OlepayStatusPoller.class);

    private static final String STATUS_URL = "https://olepay.szu.edu.cn/AjaxHandler/Pay/GetOrderIdState";
    private static final ObjectMapper MAPPER = JsonMappers.standard();

    private final CampusHttpClient http;

    /**
     * Creates a poller backed by the given HTTP client.
     *
     * @param http the HTTP client (must already carry a logged-in session)
     * @since 0.7.0
     * @author 王子豪
     */
    public OlepayStatusPoller(CampusHttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public PaymentStatus query(String olepayOrderId) {
        try {
            String body = http.postForm(STATUS_URL, Map.of("orderid", olepayOrderId));
            log.debug("Status response for orderId={}: {}",
                LogMasker.scrub(olepayOrderId), LogMasker.scrub(body));
            return parse(body);
        } catch (BookingException e) {
            throw e;
        } catch (Exception e) {
            throw new BookingException(ErrorCode.PAYMENT_GATEWAY_ERROR,
                "Failed to query payment status: " + e.getMessage(), e);
        }
    }

    private PaymentStatus parse(String body) {
        if (body == null || body.isBlank()) {
            return PaymentStatus.UNKNOWN;
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            int state = root.path("state").asInt(-1);
            return switch (state) {
                case 0 -> PaymentStatus.PENDING;
                case 1 -> PaymentStatus.SUCCESS;
                case 2 -> PaymentStatus.FAILED;
                default -> PaymentStatus.UNKNOWN;
            };
        } catch (Exception e) {
            log.warn("Unparseable status response: {}", LogMasker.scrub(body));
            return PaymentStatus.UNKNOWN;
        }
    }
}

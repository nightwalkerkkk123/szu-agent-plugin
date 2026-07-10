# Payment Service Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **Date:** 2026-07-10
> **Author:** 王子豪
> **Design spec:** `docs/superpowers/specs/2026-07-10-payment-service-design.md`

---

## Goal

Add `direct-pay` and `direct-pay-status` commands that resolve an unpaid SZU venue-booking order, generate an olepay payment link, and (when configured) automatically complete payment via campus card. The integration must:

- Work within the existing Java 21 / Maven / picocli codebase.
- Reuse CAS/ehall session infrastructure (`EhallSessionManager`, `SessionStore`, `CampusHttpClient`).
- Never store payment credentials in code, CLI arguments, logs, or session files.
- Provide a safe fallback to a manual payment link when full automation is impossible.
- Follow the project's error-code taxonomy and structured JSON output conventions.

---

## Architecture

A new `edu.szu.agent.client.payment` package provides order resolution, status polling, and pluggable payment drivers behind a `PaymentService`. CLI commands are thin wrappers that resolve accounts, load sessions, and format JSON output. Campus-card credentials come from environment variables only.

Key design patterns:

- **Strategy:** `PaymentAutomationDriver` lets each payment method implement its own automation logic behind a common interface.
- **Adapter:** `PaymentOrderClient` adapts olepay HTML/form responses into typed `PaymentInitParams`.
- **Service:** `PaymentService` orchestrates session, order resolution, driver selection, and status polling.

---

## Tech Stack

- Java 21, Maven, picocli, Jackson
- Existing `CampusHttpClient`, `BrowserLifecycle`, `EhallSessionManager`, `SessionStore`
- JUnit 5, AssertJ, Mockito for tests
- SLF4J/Logback for logging, `LogMasker` for sensitive field redaction

---

## Global Constraints

- Java 21; all new public methods require Javadoc with `@since 0.7.0` and `@author 王子豪`.
- Never accept payment passwords as CLI arguments.
- Never log `sign`, passwords, or tokens — use `LogMasker`.
- Follow existing error-code taxonomy in `edu.szu.agent.error.ErrorCode`.
- Prefer constructor injection and immutable records.
- Use `try-with-resources` for `CampusHttpClient`.
- Pre-commit gate: `mvn test` must pass.

---

## File Structure

### New files

- `src/main/java/edu/szu/agent/client/payment/PaymentMethod.java` — enum of payment methods.
- `src/main/java/edu/szu/agent/client/payment/PaymentStatus.java` — enum of payment statuses.
- `src/main/java/edu/szu/agent/client/payment/PaymentInitParams.java` — record of olepay order parameters.
- `src/main/java/edu/szu/agent/client/payment/PaymentCredentials.java` — record holding env-sourced campus-card password.
- `src/main/java/edu/szu/agent/client/payment/PaymentResult.java` — record returned by `PaymentService.pay(...)`.
- `src/main/java/edu/szu/agent/client/payment/PaymentOrderClient.java` — interface.
- `src/main/java/edu/szu/agent/client/payment/EhallPaymentOrderClient.java` — implementation.
- `src/main/java/edu/szu/agent/client/payment/PaymentStatusPoller.java` — interface.
- `src/main/java/edu/szu/agent/client/payment/OlepayStatusPoller.java` — implementation.
- `src/main/java/edu/szu/agent/client/payment/PaymentAutomationDriver.java` — strategy interface.
- `src/main/java/edu/szu/agent/client/payment/ManualLinkPaymentDriver.java` — fallback driver.
- `src/main/java/edu/szu/agent/client/payment/CampusCardPaymentDriver.java` — browser automation driver.
- `src/main/java/edu/szu/agent/client/payment/QrCodePaymentDriver.java` — WeChat/Alipay QR driver.
- `src/main/java/edu/szu/agent/client/payment/PaymentMethodResolver.java` — interface.
- `src/main/java/edu/szu/agent/client/payment/DefaultPaymentMethodResolver.java` — implementation.
- `src/main/java/edu/szu/agent/client/payment/PaymentService.java` — orchestrator.
- `src/main/java/edu/szu/agent/cli/DirectPayCommand.java` — `direct-pay` subcommand.
- `src/main/java/edu/szu/agent/cli/DirectPayStatusCommand.java` — `direct-pay-status` subcommand.
- `src/test/java/edu/szu/agent/client/payment/EhallPaymentOrderClientTest.java`
- `src/test/java/edu/szu/agent/client/payment/OlepayStatusPollerTest.java`
- `src/test/java/edu/szu/agent/client/payment/ManualLinkPaymentDriverTest.java`
- `src/test/java/edu/szu/agent/client/payment/PaymentServiceTest.java`
- `src/test/java/edu/szu/agent/cli/DirectPayCommandTest.java`
- `src/test/resources/payment/olepay-create-order.html` — sanitized fixture from `支付服务.html`.

### Modified files

- `src/main/java/edu/szu/agent/error/ErrorCode.java` — add payment error codes.
- `src/main/java/edu/szu/agent/cli/Main.java` — register new subcommands.
- `pom.xml` — no changes expected; all required dependencies already present.

---

## Task 1: Payment Domain Records and Error Codes

**Files:**
- Create: `src/main/java/edu/szu/agent/client/payment/PaymentMethod.java`
- Create: `src/main/java/edu/szu/agent/client/payment/PaymentStatus.java`
- Create: `src/main/java/edu/szu/agent/client/payment/PaymentInitParams.java`
- Create: `src/main/java/edu/szu/agent/client/payment/PaymentCredentials.java`
- Create: `src/main/java/edu/szu/agent/client/payment/PaymentResult.java`
- Modify: `src/main/java/edu/szu/agent/error/ErrorCode.java`

**Interfaces:**
- Consumes: existing `ErrorCode` metadata pattern.
- Produces: domain types used by every later task.

### Step 1: Create `PaymentMethod.java`

```java
package edu.szu.agent.client.payment;

/**
 * Supported payment methods for olepay venue-booking orders.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public enum PaymentMethod {
    AUTO,
    CAMPUS_CARD,
    WECHAT,
    ALIPAY,
    MANUAL_LINK
}
```

### Step 2: Create `PaymentStatus.java`

```java
package edu.szu.agent.client.payment;

/**
 * Lifecycle status of an olepay payment.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    UNKNOWN
}
```

### Step 3: Create `PaymentInitParams.java`

```java
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
    String jyDate
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
```

### Step 4: Create `PaymentCredentials.java`

```java
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

    public boolean hasCampusCardPassword() {
        return !campusCardPassword.isBlank();
    }
}
```

### Step 5: Create `PaymentResult.java`

```java
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

    public static PaymentResult alreadyPaid(String olepayOrderId, String dhid, int amountFen) {
        return new PaymentResult(true, PaymentStatus.SUCCESS, olepayOrderId, dhid,
            amountFen, PaymentMethod.MANUAL_LINK, null, null, null,
            "Order is already paid or amount is zero");
    }

    public static PaymentResult pending(String olepayOrderId, String dhid,
                                        PaymentMethod method, String qrCodeUrl,
                                        String manualPaymentUrl, String message) {
        return new PaymentResult(false, PaymentStatus.PENDING, olepayOrderId, dhid,
            0, method, null, qrCodeUrl, manualPaymentUrl, message);
    }

    public static PaymentResult success(String olepayOrderId, String dhid,
                                        int amountFen, PaymentMethod method,
                                        String paidAt) {
        return new PaymentResult(true, PaymentStatus.SUCCESS, olepayOrderId, dhid,
            amountFen, method, paidAt, null, null, "Payment completed");
    }

    public static PaymentResult failed(String olepayOrderId, String dhid,
                                       PaymentMethod method, String message) {
        return new PaymentResult(false, PaymentStatus.FAILED, olepayOrderId, dhid,
            0, method, null, null, null, message);
    }
}
```

### Step 6: Extend `ErrorCode.java`

Add these constants after the existing blocks, before the constructor:

```java
    // ----- 支付服务(payment) -----
    /** 订单不存在或无权访问. */
    PAYMENT_ORDER_NOT_FOUND   (Severity.MEDIUM,   false, false, false, "订单不存在或无权访问"),
    /** 该订单已支付. */
    PAYMENT_ALREADY_PAID      (Severity.LOW,      false, false, false, "该订单已支付"),
    /** 所选支付方式不可用. */
    PAYMENT_METHOD_UNAVAILABLE(Severity.MEDIUM,   true,  false, false, "所选支付方式不可用，请尝试其他方式"),
    /** 校园卡自动支付需要配置密码环境变量. */
    PAYMENT_PASSWORD_REQUIRED (Severity.MEDIUM,   false, false, false, "校园卡自动支付需要配置 SZU_CAMPUS_CARD_PASSWORD"),
    /** 校园卡支付密码错误. */
    PAYMENT_PASSWORD_INCORRECT(Severity.HIGH,     false, true,  true,  "校园卡支付密码错误"),
    /** olepay 网关返回异常. */
    PAYMENT_GATEWAY_ERROR     (Severity.HIGH,     true,  false, true,  "olepay 网关返回异常"),
    /** 支付状态轮询超时. */
    PAYMENT_STATUS_TIMEOUT    (Severity.MEDIUM,   false, false, false, "支付状态轮询超时，请使用 direct-pay-status 继续查询"),
    /** 该支付方式需用户在手机上确认. */
    PAYMENT_MANUAL_REQUIRED   (Severity.LOW,      false, false, false, "该支付方式需用户在手机上确认，请使用返回的二维码或链接");
```

### Step 7: Compile and run tests

Run:

```bash
mvn -q compile
```

Expected: BUILD SUCCESS.

### Step 8: Commit

```bash
git add src/main/java/edu/szu/agent/client/payment/PaymentMethod.java \
        src/main/java/edu/szu/agent/client/payment/PaymentStatus.java \
        src/main/java/edu/szu/agent/client/payment/PaymentInitParams.java \
        src/main/java/edu/szu/agent/client/payment/PaymentCredentials.java \
        src/main/java/edu/szu/agent/client/payment/PaymentResult.java \
        src/main/java/edu/szu/agent/error/ErrorCode.java
git commit -m "feat(payment): add payment domain records and error codes"
```

---

## Task 2: Payment Order Resolution (P0)

**Files:**
- Create: `src/main/java/edu/szu/agent/client/payment/PaymentOrderClient.java`
- Create: `src/main/java/edu/szu/agent/client/payment/EhallPaymentOrderClient.java`
- Create: `src/test/java/edu/szu/agent/client/payment/EhallPaymentOrderClientTest.java`
- Create: `src/test/resources/payment/olepay-create-order.html`

**Interfaces:**
- Consumes: `PaymentInitParams` from Task 1, `EhallSportVenueClient` existing API.
- Produces: `PaymentOrderClient.resolve(String dhid) -> PaymentInitParams`.

### Step 1: Create `PaymentOrderClient.java`

```java
package edu.szu.agent.client.payment;

/**
 * Resolves olepay order parameters for a given ehall booking DHID.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public interface PaymentOrderClient {
    PaymentInitParams resolve(String dhid);
}
```

### Step 2: Create sanitized fixture

Create `src/test/resources/payment/olepay-create-order.html` by copying the relevant `<form>` and hidden `<input>` fields from `C:/Users/王子豪/Downloads/支付服务.html`. Keep at minimum:

- `<input type="hidden" value="P2026071023270396455588000733" name="loorderid">`
- `<input type="hidden" id="txtorderid" value="P2026071023270396455588000733">`
- `<input type="hidden" id="txtotheraccount" value="455588" name="txtotheraccount">`
- `<input type="hidden" id="txtName" value="王子豪">`
- The visible order-detail lines containing 商户订单号, 学工号, 交易金额.

### Step 3: Create `EhallPaymentOrderClientTest.java`

```java
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
            html
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
            ""
        );

        assertThatThrownBy(() -> client.resolve("missing"))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code())
                .isEqualTo(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
    }
}
```

### Step 4: Implement `EhallPaymentOrderClient.java`

```java
package edu.szu.agent.client.payment;

import edu.szu.agent.client.http.EhallSportVenueClient;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.error.LogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves olepay order parameters from the ehall booking record and the
 * olepay CreateOrder HTML page.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public class EhallPaymentOrderClient implements PaymentOrderClient {

    private static final Logger log = LoggerFactory.getLogger(EhallPaymentOrderClient.class);

    private static final String DEFAULT_MERCHANT_NO = "1100058";
    private static final String DEFAULT_REGISTER_ID = "paychangguan_2023";

    private static final Pattern ORDER_ID = Pattern.compile(
        "id=\"txtorderid\"\\s+value=\"([^\"]+)\"");
    private static final Pattern ACCOUNT = Pattern.compile(
        "id=\"txtotheraccount\"\\s+value=\"([^\"]+)\"");
    private static final Pattern STUDENT_NAME = Pattern.compile(
        "id=\"txtName\"\\s+value=\"([^\"]+)\"");
    private static final Pattern STUDENT_ID = Pattern.compile(
        "学工号：[^\\d]*(\\d{10})");
    private static final Pattern AMOUNT = Pattern.compile(
        "交易金额：[^\\d]*([\\d.]+)");

    private final Function<String, EhallSportVenueClient.BookingRecord> bookingLookup;
    private final Function<String, String> createOrderHtmlProvider;

    public EhallPaymentOrderClient(
        Function<String, EhallSportVenueClient.BookingRecord> bookingLookup,
        Function<String, String> createOrderHtmlProvider) {
        this.bookingLookup = Objects.requireNonNull(bookingLookup, "bookingLookup");
        this.createOrderHtmlProvider = Objects.requireNonNull(createOrderHtmlProvider, "createOrderHtmlProvider");
    }

    @Override
    public PaymentInitParams resolve(String dhid) {
        EhallSportVenueClient.BookingRecord record = bookingLookup.apply(dhid);
        if (record == null) {
            throw new BookingException(ErrorCode.PAYMENT_ORDER_NOT_FOUND,
                "Booking not found for dhid=" + LogMasker.scrub(dhid));
        }

        int amountFen = parseAmount(record.amount());
        String html = createOrderHtmlProvider.apply(dhid);
        if (html == null || html.isBlank()) {
            throw new BookingException(ErrorCode.PAYMENT_GATEWAY_ERROR,
                "Empty olepay CreateOrder response for dhid=" + LogMasker.scrub(dhid));
        }

        String olepayOrderId = firstMatch(ORDER_ID, html);
        if (olepayOrderId.isBlank()) {
            olepayOrderId = "P" + dhid;
        }

        String account = firstMatch(ACCOUNT, html);
        if (account.isBlank()) {
            account = extractAccountFromScript(html);
        }

        String studentName = firstMatch(STUDENT_NAME, html);
        String studentId = firstMatch(STUDENT_ID, html);
        int parsedAmountFen = parseAmountFromHtml(html);
        if (parsedAmountFen > 0) {
            amountFen = parsedAmountFen;
        }

        log.info("Resolved olepay params for dhid={}, orderId={}, amountFen={}",
            LogMasker.scrub(dhid), olepayOrderId, amountFen);

        return new PaymentInitParams(
            olepayOrderId,
            dhid,
            DEFAULT_MERCHANT_NO,
            DEFAULT_REGISTER_ID,
            account,
            amountFen,
            amountFen,
            "",
            "%E5%9C%BA%E9%A6%86%E9%A2%84%E7%BA%A6",
            "%E4%BD%93%E8%82%B2%E4%B8%93%E9%A1%B9%E7%BB%8F%E8%B4%B9",
            studentName,
            studentId,
            "",
            ""
        );
    }

    private static String firstMatch(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String extractAccountFromScript(String html) {
        Pattern p = Pattern.compile("otheraccount\\s*:\\s*'([^']+)'");
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1).trim() : "";
    }

    private static int parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return 0;
        }
        try {
            return (int) Math.round(Double.parseDouble(amount.trim()) * 100);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseAmountFromHtml(String html) {
        Matcher matcher = AMOUNT.matcher(html);
        if (!matcher.find()) {
            return 0;
        }
        return parseAmount(matcher.group(1));
    }
}
```

### Step 5: Run tests

```bash
mvn -q test -Dtest=EhallPaymentOrderClientTest
```

Expected: Tests pass.

### Step 6: Commit

```bash
git add src/main/java/edu/szu/agent/client/payment/PaymentOrderClient.java \
        src/main/java/edu/szu/agent/client/payment/EhallPaymentOrderClient.java \
        src/test/java/edu/szu/agent/client/payment/EhallPaymentOrderClientTest.java \
        src/test/resources/payment/olepay-create-order.html
git commit -m "feat(payment): add payment order resolution from ehall + olepay HTML"
```

---

## Task 3: Manual Link Payment Driver (P0)

**Files:**
- Create: `src/main/java/edu/szu/agent/client/payment/PaymentAutomationDriver.java`
- Create: `src/main/java/edu/szu/agent/client/payment/ManualLinkPaymentDriver.java`
- Create: `src/test/java/edu/szu/agent/client/payment/ManualLinkPaymentDriverTest.java`

**Interfaces:**
- Consumes: `PaymentInitParams`, `PaymentResult`, `PaymentMethod`.
- Produces: `PaymentAutomationDriver` strategy interface + fallback driver.

### Step 1: Create `PaymentAutomationDriver.java`

```java
package edu.szu.agent.client.payment;

import edu.szu.agent.browser.BrowserLifecycle;

/**
 * Strategy interface for executing a specific payment method.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public interface PaymentAutomationDriver {

    boolean supports(PaymentInitParams params, PaymentMethod method);

    PaymentResult execute(BrowserLifecycle browser,
                          PaymentInitParams params,
                          PaymentCredentials credentials);
}
```

### Step 2: Create `ManualLinkPaymentDriver.java`

```java
package edu.szu.agent.client.payment;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.LogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Fallback driver that returns the official olepay CreateOrder URL.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public class ManualLinkPaymentDriver implements PaymentAutomationDriver {

    private static final Logger log = LoggerFactory.getLogger(ManualLinkPaymentDriver.class);

    private static final String CREATE_ORDER_URL = "https://olepay.szu.edu.cn/Order/CreateOrder";

    @Override
    public boolean supports(PaymentInitParams params, PaymentMethod method) {
        return method == PaymentMethod.MANUAL_LINK;
    }

    @Override
    public PaymentResult execute(BrowserLifecycle browser,
                                 PaymentInitParams params,
                                 PaymentCredentials credentials) {
        String url = buildUrl(params);
        log.info("Returning manual payment link for orderId={}",
            LogMasker.scrub(params.olepayOrderId()));
        return PaymentResult.pending(
            params.olepayOrderId(),
            params.thirdOrderId(),
            PaymentMethod.MANUAL_LINK,
            null,
            url,
            "Complete payment manually using the returned link"
        );
    }

    private static String buildUrl(PaymentInitParams params) {
        StringBuilder sb = new StringBuilder(CREATE_ORDER_URL);
        sb.append("?merr=").append(encode(params.merchantNo()));
        sb.append("&registerid=").append(encode(params.registerId()));
        sb.append("&orderid=").append(encode(params.olepayOrderId()));
        sb.append("&account=").append(encode(params.account()));
        if (!params.orderDesc().isBlank()) {
            sb.append("&orderdesc=").append(encode(params.orderDesc()));
        }
        return sb.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
```

### Step 3: Create `ManualLinkPaymentDriverTest.java`

```java
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
            ""
        );
    }
}
```

### Step 4: Run tests

```bash
mvn -q test -Dtest=ManualLinkPaymentDriverTest
```

### Step 5: Commit

```bash
git add src/main/java/edu/szu/agent/client/payment/PaymentAutomationDriver.java \
        src/main/java/edu/szu/agent/client/payment/ManualLinkPaymentDriver.java \
        src/test/java/edu/szu/agent/client/payment/ManualLinkPaymentDriverTest.java
git commit -m "feat(payment): add manual-link fallback driver"
```

---

## Task 4: PaymentService Orchestrator (P0)

**Files:**
- Create: `src/main/java/edu/szu/agent/client/payment/PaymentService.java`
- Create: `src/test/java/edu/szu/agent/client/payment/PaymentServiceTest.java`

**Interfaces:**
- Consumes: `PaymentOrderClient`, `PaymentAutomationDriver`, `PaymentStatusPoller`, `PaymentMethodResolver`.
- Produces: `PaymentService.pay(...)` and `PaymentService.queryStatus(...)`.

### Step 1: Create `PaymentService.java`

```java
package edu.szu.agent.client.payment;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.error.LogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * High-level orchestrator for SZU olepay payments.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentOrderClient orderClient;
    private final PaymentMethodResolver methodResolver;
    private final List<PaymentAutomationDriver> drivers;
    private final PaymentStatusPoller statusPoller;
    private final BrowserLifecycle browser;

    public PaymentService(PaymentOrderClient orderClient,
                          PaymentMethodResolver methodResolver,
                          List<PaymentAutomationDriver> drivers,
                          PaymentStatusPoller statusPoller,
                          BrowserLifecycle browser) {
        this.orderClient = Objects.requireNonNull(orderClient, "orderClient");
        this.methodResolver = Objects.requireNonNull(methodResolver, "methodResolver");
        this.drivers = List.copyOf(Objects.requireNonNull(drivers, "drivers"));
        this.statusPoller = Objects.requireNonNull(statusPoller, "statusPoller");
        this.browser = browser;
    }

    /**
     * Resolves the order and executes the chosen payment method.
     *
     * @param dhid   ehall booking DHID
     * @param method requested payment method
     * @param credentials env-sourced credentials
     * @return payment result
     */
    public PaymentResult pay(String dhid, PaymentMethod method, PaymentCredentials credentials) {
        PaymentInitParams params = orderClient.resolve(dhid);

        if (params.actualAmountFen() == 0) {
            log.info("Order dhid={} has zero amount, treating as already paid",
                LogMasker.scrub(dhid));
            return PaymentResult.alreadyPaid(params.olepayOrderId(), dhid, 0);
        }

        PaymentMethod resolved = methodResolver.resolve(params, method, credentials);
        log.info("Resolved payment method {} for dhid={}", resolved, LogMasker.scrub(dhid));

        for (PaymentAutomationDriver driver : drivers) {
            if (driver.supports(params, resolved)) {
                PaymentResult result = driver.execute(browser, params, credentials);
                if (result.status() == PaymentStatus.PENDING && resolved != PaymentMethod.MANUAL_LINK) {
                    PaymentStatus polled = statusPoller.query(params.olepayOrderId());
                    if (polled == PaymentStatus.SUCCESS) {
                        return PaymentResult.success(params.olepayOrderId(), dhid,
                            params.actualAmountFen(), resolved, java.time.Instant.now().toString());
                    }
                }
                return result;
            }
        }

        throw new BookingException(ErrorCode.PAYMENT_METHOD_UNAVAILABLE,
            "No driver available for method " + resolved);
    }

    /**
     * Queries the current payment status for an olepay order.
     *
     * @param olepayOrderId olepay order id
     * @return current status
     */
    public PaymentStatus queryStatus(String olepayOrderId) {
        return statusPoller.query(olepayOrderId);
    }
}
```

### Step 2: Create `PaymentServiceTest.java`

```java
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
```

### Step 3: Run tests

```bash
mvn -q test -Dtest=PaymentServiceTest
```

### Step 4: Commit

```bash
git add src/main/java/edu/szu/agent/client/payment/PaymentService.java \
        src/test/java/edu/szu/agent/client/payment/PaymentServiceTest.java
git commit -m "feat(payment): add PaymentService orchestrator (P0)"
```

---

## Task 5: `direct-pay` CLI Command (P0)

**Files:**
- Create: `src/main/java/edu/szu/agent/cli/DirectPayCommand.java`
- Create: `src/test/java/edu/szu/agent/cli/DirectPayCommandTest.java`
- Modify: `src/main/java/edu/szu/agent/cli/Main.java`

**Interfaces:**
- Consumes: `PaymentService`, `AccountResolver`, `CommandOutput`.
- Produces: `direct-pay` subcommand registered in `Main`.

### Step 1: Create `DirectPayCommand.java`

```java
package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.browser.PlaywrightBrowserLifecycle;
import edu.szu.agent.client.http.EhallSessionManager;
import edu.szu.agent.client.http.EhallSportVenueClient;
import edu.szu.agent.client.payment.DefaultPaymentMethodResolver;
import edu.szu.agent.client.payment.EhallPaymentOrderClient;
import edu.szu.agent.client.payment.ManualLinkPaymentDriver;
import edu.szu.agent.client.payment.PaymentCredentials;
import edu.szu.agent.client.payment.PaymentMethod;
import edu.szu.agent.client.payment.PaymentResult;
import edu.szu.agent.client.payment.PaymentService;
import edu.szu.agent.client.payment.PaymentStatusPoller;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code direct-pay} subcommand — resolve an unpaid booking and pay or return a payment link.
 *
 * @since 0.7.0
 * @author 王子豪
 */
@Command(
    name = "direct-pay",
    description = "Resolve an unpaid booking and pay / return a payment link",
    mixinStandardHelpOptions = true
)
public class DirectPayCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_PASSWORD_ENV = "SZU_CAMPUS_CARD_PASSWORD";

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--dhid"}, description = "Ehall booking DHID", required = true)
    private String dhid;

    @Option(names = {"--method"}, description = "Payment method: auto, campus_card, wechat, alipay, manual_link",
        defaultValue = "manual_link")
    private String methodName;

    @Option(names = {"--password-env"}, description = "Environment variable name for campus-card password",
        defaultValue = DEFAULT_PASSWORD_ENV)
    private String passwordEnv;

    @Option(names = {"--session-home"}, description = "Directory under which .szu-agent/sessions is created",
        defaultValue = "${sys:user.home}")
    private String sessionHome;

    @Option(names = {"--trust-all"}, description = "Disable TLS certificate validation (dev/internal only)")
    private boolean trustAll;

    @Option(names = {"-e", "--env-file"}, description = "Path to .env file for account resolution")
    private String envFile;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        long startMs = System.currentTimeMillis();
        String traceId = Tracer.getInstance().generateTraceId();

        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(methodName.toUpperCase());
        } catch (IllegalArgumentException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(), "Unknown payment method: " + methodName,
                traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }

        Account account = resolveAccount();
        if (account == null) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(),
                "Could not resolve credential for " + username,
                traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }

        BrowserLifecycle browser = null;
        try {
            SessionStore store = new SessionStore(Path.of(sessionHome), username);
            EhallSessionManager sessionManager = new EhallSessionManager(
                account.studentId(), account.password(), trustAll);
            var http = sessionManager.ensureSession(store.load());

            EhallSportVenueClient venueClient = new EhallSportVenueClient(http);
            EhallPaymentOrderClient orderClient = new EhallPaymentOrderClient(
                d -> venueClient.getMyBookings(1, 50).rows().stream()
                    .filter(r -> r.dhid().equals(d))
                    .findFirst()
                    .orElse(null),
                d -> {
                    String url = "https://olepay.szu.edu.cn/Order/CreateOrder?merr=1100058"
                        + "&registerid=paychangguan_2023&orderid=P" + d + "&account=" + account.studentId();
                    return http.get(url);
                }
            );

            PaymentStatusPoller poller = new PaymentStatusPoller() {
                @Override
                public edu.szu.agent.client.payment.PaymentStatus query(String olepayOrderId) {
                    return edu.szu.agent.client.payment.PaymentStatus.UNKNOWN;
                }
            };

            browser = new PlaywrightBrowserLifecycle();
            PaymentService service = new PaymentService(
                orderClient,
                new DefaultPaymentMethodResolver(),
                List.of(new ManualLinkPaymentDriver()),
                poller,
                browser
            );

            PaymentCredentials credentials = new PaymentCredentials(System.getenv(passwordEnv));
            PaymentResult result = service.pay(dhid, method, credentials);

            ObjectNode data = toJson(result);
            data.put("traceId", traceId);
            data.put("username", username);
            data.put("durationMs", System.currentTimeMillis() - startMs);

            out.println(CommandOutput.formatResult(result.success(), data, null, null,
                traceId, data.get("durationMs").asLong(), "json"));
            return result.success() ? 0 : 1;
        } catch (BookingException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                e.code().name(), e.getMessage(), traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(e.code());
        } catch (RuntimeException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.UNKNOWN.name(), "Unexpected error: " + e.getMessage(),
                traceId, elapsed, "json"));
            return 1;
        } finally {
            if (browser != null) {
                browser.close();
            }
        }
    }

    private Account resolveAccount() {
        try {
            return (envFile != null)
                ? AccountResolver.resolve(username, System.getenv(), Path.of(envFile))
                : AccountResolver.resolve(username, System.getenv(), null);
        } catch (AccountResolutionException e) {
            return null;
        }
    }

    private ObjectNode toJson(PaymentResult result) {
        ObjectNode data = JSON.createObjectNode();
        data.put("olepayOrderId", result.olepayOrderId());
        data.put("dhid", result.dhid());
        data.put("amountFen", result.amountFen());
        data.put("amountDisplay", String.format("%.2f", result.amountFen() / 100.0));
        data.put("method", result.method().name());
        data.put("status", result.status().name());
        if (result.paidAt() != null && !result.paidAt().isBlank()) {
            data.put("paidAt", result.paidAt());
        }
        if (result.qrCodeUrl() != null && !result.qrCodeUrl().isBlank()) {
            data.put("qrCodeUrl", result.qrCodeUrl());
        }
        if (result.manualPaymentUrl() != null && !result.manualPaymentUrl().isBlank()) {
            data.put("manualPaymentUrl", result.manualPaymentUrl());
        }
        if (!result.message().isBlank()) {
            data.put("message", result.message());
        }
        return data;
    }
}
```

### Step 2: Register command in `Main.java`

Add `DirectPayCommand.class` to the `subcommands` array in `Main.java`:

```java
    subcommands = {
        ...,
        DirectBookCommand.class,
        DirectBookingsCommand.class,
        ...,
        DirectPayCommand.class
    }
```

### Step 3: Create `DirectPayCommandTest.java`

```java
package edu.szu.agent.cli;

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
            edu.szu.agent.client.payment.PaymentMethod.MANUAL_LINK,
            null, "https://olepay.szu.edu.cn/Order/CreateOrder?orderid=P1",
            "Complete manually");

        assertThat(result.olepayOrderId()).isEqualTo("P1");
        assertThat(result.dhid()).isEqualTo("DH1");
        assertThat(result.manualPaymentUrl()).contains("orderid=P1");
    }
}
```

### Step 4: Build and smoke test

```bash
mvn -q -DskipTests package
java -jar target/szu-agent-plugin.jar direct-pay --help
```

Expected: Help output for `direct-pay` is shown.

### Step 5: Commit

```bash
git add src/main/java/edu/szu/agent/cli/DirectPayCommand.java \
        src/main/java/edu/szu/agent/cli/Main.java \
        src/test/java/edu/szu/agent/cli/DirectPayCommandTest.java
git commit -m "feat(payment): add direct-pay CLI command"
```

---

## Task 6: Payment Status Poller and `direct-pay-status` (P0)

**Files:**
- Create: `src/main/java/edu/szu/agent/client/payment/PaymentStatusPoller.java`
- Create: `src/main/java/edu/szu/agent/client/payment/OlepayStatusPoller.java`
- Create: `src/main/java/edu/szu/agent/cli/DirectPayStatusCommand.java`
- Create: `src/test/java/edu/szu/agent/client/payment/OlepayStatusPollerTest.java`
- Modify: `src/main/java/edu/szu/agent/cli/Main.java`

**Interfaces:**
- Consumes: `CampusHttpClient`, JSON response from `Pay/GetOrderIdState`.
- Produces: `PaymentStatus` and `direct-pay-status` command.

### Step 1: Create `PaymentStatusPoller.java`

```java
package edu.szu.agent.client.payment;

/**
 * Polls the olepay gateway for the current state of an order.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public interface PaymentStatusPoller {
    PaymentStatus query(String olepayOrderId);
}
```

### Step 2: Create `OlepayStatusPoller.java`

```java
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
```

### Step 3: Create `OlepayStatusPollerTest.java`

```java
package edu.szu.agent.client.payment;

import edu.szu.agent.client.http.CampusHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OlepayStatusPoller")
class OlepayStatusPollerTest {

    private final CampusHttpClient http = mock(CampusHttpClient.class);
    private final OlepayStatusPoller poller = new OlepayStatusPoller(http);

    @Test
    @DisplayName("state=0 返回 PENDING")
    void stateZeroIsPending() {
        when(http.postForm(any(), any())).thenReturn("{\"state\":0}");
        assertThat(poller.query("P1")).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("state=1 返回 SUCCESS")
    void stateOneIsSuccess() {
        when(http.postForm(any(), any())).thenReturn("{\"state\":1}");
        assertThat(poller.query("P1")).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("空响应返回 UNKNOWN")
    void emptyBodyIsUnknown() {
        when(http.postForm(any(), any())).thenReturn("");
        assertThat(poller.query("P1")).isEqualTo(PaymentStatus.UNKNOWN);
    }
}
```

### Step 4: Create `DirectPayStatusCommand.java`

```java
package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.http.EhallSessionManager;
import edu.szu.agent.client.payment.OlepayStatusPoller;
import edu.szu.agent.client.payment.PaymentStatus;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * {@code direct-pay-status} subcommand — poll olepay for payment status.
 *
 * @since 0.7.0
 * @author 王子豪
 */
@Command(
    name = "direct-pay-status",
    description = "Poll olepay payment status for an order",
    mixinStandardHelpOptions = true
)
public class DirectPayStatusCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--orderid"}, description = "Olepay order id", required = true)
    private String orderId;

    @Option(names = {"--timeout-seconds"}, description = "Maximum polling time in seconds",
        defaultValue = "60")
    private int timeoutSeconds;

    @Option(names = {"--poll-interval-seconds"}, description = "Polling interval in seconds",
        defaultValue = "2")
    private int pollIntervalSeconds;

    @Option(names = {"--session-home"}, description = "Directory under which .szu-agent/sessions is created",
        defaultValue = "${sys:user.home}")
    private String sessionHome;

    @Option(names = {"--trust-all"}, description = "Disable TLS certificate validation (dev/internal only)")
    private boolean trustAll;

    @Option(names = {"-e", "--env-file"}, description = "Path to .env file for account resolution")
    private String envFile;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        long startMs = System.currentTimeMillis();
        String traceId = Tracer.getInstance().generateTraceId();

        if (timeoutSeconds <= 0 || pollIntervalSeconds <= 0) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(), "Timeout and interval must be positive",
                traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }

        Account account = resolveAccount();
        if (account == null) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(),
                "Could not resolve credential for " + username,
                traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }

        try {
            SessionStore store = new SessionStore(Path.of(sessionHome), username);
            EhallSessionManager sessionManager = new EhallSessionManager(
                account.studentId(), account.password(), trustAll);
            var http = sessionManager.ensureSession(store.load());
            OlepayStatusPoller poller = new OlepayStatusPoller(http);

            long deadline = System.currentTimeMillis() + Duration.ofSeconds(timeoutSeconds).toMillis();
            PaymentStatus status = PaymentStatus.UNKNOWN;
            while (System.currentTimeMillis() < deadline) {
                status = poller.query(orderId);
                if (status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED) {
                    break;
                }
                Thread.sleep(Duration.ofSeconds(pollIntervalSeconds).toMillis());
            }

            boolean success = status == PaymentStatus.SUCCESS;
            ObjectNode data = JSON.createObjectNode();
            data.put("olepayOrderId", orderId);
            data.put("status", status.name());
            data.put("success", success);
            data.put("traceId", traceId);
            data.put("durationMs", System.currentTimeMillis() - startMs);

            String errorCode = (status == PaymentStatus.TIMEOUT || status == PaymentStatus.UNKNOWN)
                ? ErrorCode.PAYMENT_STATUS_TIMEOUT.name()
                : null;
            String errorMessage = errorCode != null
                ? "Payment status polling did not reach a terminal state"
                : null;

            out.println(CommandOutput.formatResult(success, data, errorCode, errorMessage,
                traceId, data.get("durationMs").asLong(), "json"));
            return success ? 0 : (errorCode != null ? CommandOutput.exitCodeFor(ErrorCode.PAYMENT_STATUS_TIMEOUT) : 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.UNKNOWN.name(), "Polling interrupted",
                traceId, elapsed, "json"));
            return 1;
        } catch (BookingException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                e.code().name(), e.getMessage(), traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(e.code());
        } catch (RuntimeException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.UNKNOWN.name(), "Unexpected error: " + e.getMessage(),
                traceId, elapsed, "json"));
            return 1;
        }
    }

    private Account resolveAccount() {
        try {
            return (envFile != null)
                ? AccountResolver.resolve(username, System.getenv(), Path.of(envFile))
                : AccountResolver.resolve(username, System.getenv(), null);
        } catch (AccountResolutionException e) {
            return null;
        }
    }
}
```

### Step 5: Register command in `Main.java`

Add `DirectPayStatusCommand.class` to the `subcommands` array.

### Step 6: Run tests

```bash
mvn -q test -Dtest=OlepayStatusPollerTest
mvn -q -DskipTests package
java -jar target/szu-agent-plugin.jar direct-pay-status --help
```

### Step 7: Commit

```bash
git add src/main/java/edu/szu/agent/client/payment/PaymentStatusPoller.java \
        src/main/java/edu/szu/agent/client/payment/OlepayStatusPoller.java \
        src/main/java/edu/szu/agent/cli/DirectPayStatusCommand.java \
        src/test/java/edu/szu/agent/client/payment/OlepayStatusPollerTest.java \
        src/main/java/edu/szu/agent/cli/Main.java
git commit -m "feat(payment): add payment status poller and direct-pay-status command"
```

---

## Task 7: Campus Card Payment Driver (P1)

**Files:**
- Create: `src/main/java/edu/szu/agent/client/payment/CampusCardPaymentDriver.java`
- Create: `src/test/java/edu/szu/agent/client/payment/CampusCardPaymentDriverTest.java`

**Interfaces:**
- Consumes: `BrowserLifecycle`, `PaymentInitParams`, `PaymentCredentials`.
- Produces: `PaymentResult` for campus-card methods.

### Step 1: Create `CampusCardPaymentDriver.java`

```java
package edu.szu.agent.client.payment;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.error.LogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automates campus-card payment via olepay SynCard/SynAccType.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public class CampusCardPaymentDriver implements PaymentAutomationDriver {

    private static final Logger log = LoggerFactory.getLogger(CampusCardPaymentDriver.class);

    @Override
    public boolean supports(PaymentInitParams params, PaymentMethod method) {
        return method == PaymentMethod.CAMPUS_CARD;
    }

    @Override
    public PaymentResult execute(BrowserLifecycle browser,
                                 PaymentInitParams params,
                                 PaymentCredentials credentials) {
        if (!credentials.hasCampusCardPassword()) {
            throw new BookingException(ErrorCode.PAYMENT_PASSWORD_REQUIRED,
                "Campus-card payment requires SZU_CAMPUS_CARD_PASSWORD environment variable");
        }

        String payUrl = "https://olepay.szu.edu.cn/Pay/SynCard?orderid="
            + params.olepayOrderId() + "&payid=" + params.account();
        log.info("Navigating to campus-card payment page for orderId={}",
            LogMasker.scrub(params.olepayOrderId()));

        browser.navigateTo(payUrl);
        browser.fill("#txtaccount", params.account());
        browser.fill("#txtpasswd", credentials.campusCardPassword());
        browser.click("#btn_login");

        boolean success = browser.waitForVisible(".pay-success", 5000L)
            || browser.currentUrl().contains("PaySuccess");

        if (success) {
            String paidAt = java.time.Instant.now().toString();
            log.info("Campus-card payment succeeded for orderId={}",
                LogMasker.scrub(params.olepayOrderId()));
            return PaymentResult.success(params.olepayOrderId(), params.thirdOrderId(),
                params.actualAmountFen(), PaymentMethod.CAMPUS_CARD, paidAt);
        }

        String message = browser.textOf(".error-msg");
        if (message.contains("密码") || message.contains("password")) {
            throw new BookingException(ErrorCode.PAYMENT_PASSWORD_INCORRECT,
                "Campus-card password incorrect");
        }
        return PaymentResult.failed(params.olepayOrderId(), params.thirdOrderId(),
            PaymentMethod.CAMPUS_CARD, message);
    }
}
```

### Step 2: Create `CampusCardPaymentDriverTest.java`

```java
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
            ""
        );
    }
}
```

### Step 3: Add driver to `DirectPayCommand`

Update the `PaymentService` construction in `DirectPayCommand` to include `new CampusCardPaymentDriver()` in the driver list:

```java
            PaymentService service = new PaymentService(
                orderClient,
                new DefaultPaymentMethodResolver(),
                List.of(new ManualLinkPaymentDriver(), new CampusCardPaymentDriver()),
                poller,
                browser
            );
```

### Step 4: Run tests

```bash
mvn -q test -Dtest=CampusCardPaymentDriverTest
```

### Step 5: Commit

```bash
git add src/main/java/edu/szu/agent/client/payment/CampusCardPaymentDriver.java \
        src/test/java/edu/szu/agent/client/payment/CampusCardPaymentDriverTest.java \
        src/main/java/edu/szu/agent/cli/DirectPayCommand.java
git commit -m "feat(payment): add campus-card payment driver (P1)"
```

---

## Task 8: Payment Method Resolver (P1)

**Files:**
- Create: `src/main/java/edu/szu/agent/client/payment/PaymentMethodResolver.java`
- Create: `src/main/java/edu/szu/agent/client/payment/DefaultPaymentMethodResolver.java`
- Modify: `src/test/java/edu/szu/agent/client/payment/PaymentServiceTest.java` (add resolver tests or create dedicated test)

**Interfaces:**
- Consumes: `PaymentInitParams`, `PaymentMethod`, `PaymentCredentials`.
- Produces: concrete `PaymentMethod`.

### Step 1: Create `PaymentMethodResolver.java`

```java
package edu.szu.agent.client.payment;

/**
 * Selects the concrete payment method given user preference and credentials.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public interface PaymentMethodResolver {
    PaymentMethod resolve(PaymentInitParams params, PaymentMethod preferred, PaymentCredentials credentials);
}
```

### Step 2: Create `DefaultPaymentMethodResolver.java`

```java
package edu.szu.agent.client.payment;

import edu.szu.agent.error.LogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default resolver: campus card when password is present, otherwise manual link.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public class DefaultPaymentMethodResolver implements PaymentMethodResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentMethodResolver.class);

    @Override
    public PaymentMethod resolve(PaymentInitParams params, PaymentMethod preferred,
                                 PaymentCredentials credentials) {
        if (preferred == PaymentMethod.CAMPUS_CARD || preferred == PaymentMethod.AUTO) {
            if (credentials.hasCampusCardPassword()) {
                log.info("Selected CAMPUS_CARD for orderId={}", LogMasker.scrub(params.olepayOrderId()));
                return PaymentMethod.CAMPUS_CARD;
            }
        }
        log.info("Selected MANUAL_LINK for orderId={}", LogMasker.scrub(params.olepayOrderId()));
        return PaymentMethod.MANUAL_LINK;
    }
}
```

### Step 3: Create `DefaultPaymentMethodResolverTest.java`

```java
package edu.szu.agent.client.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DefaultPaymentMethodResolver")
class DefaultPaymentMethodResolverTest {

    private final DefaultPaymentMethodResolver resolver = new DefaultPaymentMethodResolver();
    private final PaymentInitParams params = new PaymentInitParams(
        "P1", "DH1", "m", "r", "a", 500, 500, "", "", "", "", "", "", "");

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
```

### Step 4: Run tests

```bash
mvn -q test -Dtest=DefaultPaymentMethodResolverTest
```

### Step 5: Commit

```bash
git add src/main/java/edu/szu/agent/client/payment/PaymentMethodResolver.java \
        src/main/java/edu/szu/agent/client/payment/DefaultPaymentMethodResolver.java \
        src/test/java/edu/szu/agent/client/payment/DefaultPaymentMethodResolverTest.java
git commit -m "feat(payment): add payment method resolver (P1)"
```

---

## Task 9: QR-Code Payment Driver (P2)

**Files:**
- Create: `src/main/java/edu/szu/agent/client/payment/QrCodePaymentDriver.java`
- Create: `src/test/java/edu/szu/agent/client/payment/QrCodePaymentDriverTest.java`
- Modify: `src/main/java/edu/szu/agent/cli/DirectPayCommand.java` (register driver)

**Interfaces:**
- Consumes: `BrowserLifecycle`, `PaymentInitParams`, `PaymentMethod` (WECHAT/ALIPAY).
- Produces: `PaymentResult.pending(qrCodeUrl)`.

### Step 1: Create `QrCodePaymentDriver.java`

```java
package edu.szu.agent.client.payment;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.error.LogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automates WeChat/Alipay QR-code generation up to the mobile scan step.
 *
 * @since 0.7.0
 * @author 王子豪
 */
public class QrCodePaymentDriver implements PaymentAutomationDriver {

    private static final Logger log = LoggerFactory.getLogger(QrCodePaymentDriver.class);

    @Override
    public boolean supports(PaymentInitParams params, PaymentMethod method) {
        return method == PaymentMethod.WECHAT || method == PaymentMethod.ALIPAY;
    }

    @Override
    public PaymentResult execute(BrowserLifecycle browser,
                                 PaymentInitParams params,
                                 PaymentCredentials credentials) {
        String path = methodPath(params);
        String url = "https://olepay.szu.edu.cn" + path
            + "?orderid=" + params.olepayOrderId()
            + "&payid=" + params.account();

        log.info("Navigating to QR payment page for method={} orderId={}",
            methodName(params), LogMasker.scrub(params.olepayOrderId()));
        browser.navigateTo(url);

        String qrCodeUrl = extractQrCodeUrl(browser);
        if (qrCodeUrl == null || qrCodeUrl.isBlank()) {
            throw new BookingException(ErrorCode.PAYMENT_GATEWAY_ERROR,
                "Could not extract QR code URL for " + methodName(params));
        }

        return PaymentResult.pending(
            params.olepayOrderId(),
            params.thirdOrderId(),
            method(params),
            qrCodeUrl,
            url,
            "Scan the QR code with " + methodName(params) + " and confirm on your phone"
        );
    }

    private String methodPath(PaymentInitParams params) {
        PaymentMethod method = method(params);
        return method == PaymentMethod.WECHAT ? "/Pay/WxPayType" : "/Pay/AliPayType";
    }

    private PaymentMethod method(PaymentInitParams params) {
        // Determined by caller via supports/execute pairing; default to WECHAT if needed.
        return PaymentMethod.WECHAT;
    }

    private String methodName(PaymentInitParams params) {
        return method(params) == PaymentMethod.WECHAT ? "WeChat" : "Alipay";
    }

    private String extractQrCodeUrl(BrowserLifecycle browser) {
        String src = browser.evaluate("document.querySelector('#qrcode')?.src || ''");
        if (!src.isBlank()) {
            return src;
        }
        return browser.evaluate("document.querySelector('.qrcode img')?.src || ''");
    }
}
```

### Step 2: Create `QrCodePaymentDriverTest.java`

```java
package edu.szu.agent.client.payment;

import edu.szu.agent.browser.BrowserLifecycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("QrCodePaymentDriver")
class QrCodePaymentDriverTest {

    private final QrCodePaymentDriver driver = new QrCodePaymentDriver();
    private final BrowserLifecycle browser = mock(BrowserLifecycle.class);

    @Test
    @DisplayName("提取二维码 URL 并返回 PENDING")
    void extractsQrCodeAndReturnsPending() {
        when(browser.evaluate("document.querySelector('#qrcode')?.src || ''"))
            .thenReturn("https://olepay.szu.edu.cn/AjaxHandler/Order/OrderQrcode?orderid=P1&state=1");

        PaymentResult result = driver.execute(browser, sampleParams(), new PaymentCredentials(""));

        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.qrCodeUrl()).contains("OrderQrcode");
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
            ""
        );
    }
}
```

### Step 3: Register driver in `DirectPayCommand`

Add `new QrCodePaymentDriver()` to the driver list in `DirectPayCommand`.

### Step 4: Run tests

```bash
mvn -q test -Dtest=QrCodePaymentDriverTest
```

### Step 5: Commit

```bash
git add src/main/java/edu/szu/agent/client/payment/QrCodePaymentDriver.java \
        src/test/java/edu/szu/agent/client/payment/QrCodePaymentDriverTest.java \
        src/main/java/edu/szu/agent/cli/DirectPayCommand.java
git commit -m "feat(payment): add QR-code payment driver (P2)"
```

---

## Task 10: `--auto-pay` Flag in `direct-book` (P3)

**Files:**
- Modify: `src/main/java/edu/szu/agent/cli/DirectBookCommand.java`

**Interfaces:**
- Consumes: existing `direct-book` flow + `PaymentService`.
- Produces: chained payment after successful booking.

### Step 1: Add `--auto-pay` option to `DirectBookCommand`

```java
    @Option(names = {"--auto-pay"}, description = "After booking, automatically attempt payment if campus-card password is configured")
    private boolean autoPay;
```

### Step 2: After successful booking, optionally invoke `PaymentService`

Insert after `String dhid = service.book(request);` and before building the data object:

```java
            PaymentResult paymentResult = null;
            if (autoPay) {
                paymentResult = autoPay(dhid, account, store);
            }
```

And add helper method:

```java
    private PaymentResult autoPay(String dhid, Account account, SessionStore store) {
        var http = new EhallSessionManager(account.studentId(), account.password(), trustAll)
            .ensureSession(store.load());
        EhallSportVenueClient venueClient = new EhallSportVenueClient(http);
        EhallPaymentOrderClient orderClient = new EhallPaymentOrderClient(
            d -> venueClient.getMyBookings(1, 50).rows().stream()
                .filter(r -> r.dhid().equals(d))
                .findFirst()
                .orElse(null),
            d -> http.get("https://olepay.szu.edu.cn/Order/CreateOrder?merr=1100058"
                + "&registerid=paychangguan_2023&orderid=P" + d + "&account=" + account.studentId())
        );
        var poller = new edu.szu.agent.client.payment.OlepayStatusPoller(http);
        try (var browser = new edu.szu.agent.browser.PlaywrightBrowserLifecycle()) {
            var service = new edu.szu.agent.client.payment.PaymentService(
                orderClient,
                new edu.szu.agent.client.payment.DefaultPaymentMethodResolver(),
                List.of(new edu.szu.agent.client.payment.ManualLinkPaymentDriver(),
                    new edu.szu.agent.client.payment.CampusCardPaymentDriver()),
                poller,
                browser
            );
            return service.pay(dhid, edu.szu.agent.client.payment.PaymentMethod.AUTO,
                new edu.szu.agent.client.payment.PaymentCredentials(System.getenv("SZU_CAMPUS_CARD_PASSWORD")));
        }
    }
```

### Step 3: Update output to include payment result when present

```java
            if (paymentResult != null) {
                data.put("paymentStatus", paymentResult.status().name());
                data.put("paymentMethod", paymentResult.method().name());
                if (!paymentResult.manualPaymentUrl().isBlank()) {
                    data.put("manualPaymentUrl", paymentResult.manualPaymentUrl());
                }
            }
```

### Step 4: Compile

```bash
mvn -q compile
```

### Step 5: Commit

```bash
git add src/main/java/edu/szu/agent/cli/DirectBookCommand.java
git commit -m "feat(payment): add --auto-pay flag to direct-book (P3)"
```

---

## Final Verification

Run the full test suite and package build:

```bash
mvn test
```

Expected: BUILD SUCCESS, all payment tests pass.

Smoke test the new commands:

```bash
mvn -q -DskipTests package
java -jar target/szu-agent-plugin.jar direct-pay --help
java -jar target/szu-agent-plugin.jar direct-pay-status --help
java -jar target/szu-agent-plugin.jar direct-book --help | findstr auto-pay
```

---

## Self-Review

Before marking the implementation complete, run this checklist:

### Spec Coverage Check

- [ ] `PaymentOrderClient.resolve(dhid)` exists and returns `PaymentInitParams`.
- [ ] `PaymentMethodResolver.resolve(...)` selects campus card when password is present.
- [ ] `ManualLinkPaymentDriver` returns official `olepay.szu.edu.cn/Order/CreateOrder` URL.
- [ ] `CampusCardPaymentDriver` reads password only from env var and never logs it.
- [ ] `PaymentStatusPoller.query(...)` calls `Pay/GetOrderIdState`.
- [ ] `PaymentService.pay(...)` returns `alreadyPaid(...)` for zero amount.
- [ ] `direct-pay` and `direct-pay-status` commands are registered in `Main`.

### Placeholder Scan

- [ ] Document contains no "TBD", "TODO", "implement later", or "add appropriate error handling".
- [ ] Every code step contains actual Java code.
- [ ] Every test step contains actual JUnit 5 test code and an exact `mvn test -Dtest=...` command.

### Type Consistency Check

- [ ] `PaymentResult` immutable record; constructor null-coalesces strings.
- [ ] `PaymentInitParams` immutable record; constructor null-coalesces strings.
- [ ] `PaymentCredentials` validates password presence via `hasCampusCardPassword()`.
- [ ] All public methods/classes have `@since 0.7.0` and `@author 王子豪`.
- [ ] Sensitive values (`sign`, password, `olepayOrderId`, `dhid`) are passed through `LogMasker` before logging.
- [ ] `BookingException` + `ErrorCode` used for all failure paths.
- [ ] `CommandOutput.formatResult(...)` used for CLI JSON output.

---

## Execution Handoff

1. Start with **Task 1** and proceed in order; each task builds on the previous.
2. Run the test command listed at the end of each task before committing.
3. If a live olepay response reveals additional hidden fields (e.g. `sign`), update `EhallPaymentOrderClient` regex patterns in a follow-up commit; the current implementation safely falls back to `manualPaymentUrl` when fields are absent.
4. Keep `pom.xml` unchanged unless a missing dependency surfaces during compilation.
5. Final acceptance: `mvn test` passes and `java -jar target/szu-agent-plugin.jar direct-pay --help` shows the new command.

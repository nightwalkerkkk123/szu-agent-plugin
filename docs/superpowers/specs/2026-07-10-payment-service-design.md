# SZU Agent Plugin — Payment Service Integration Design

> **Status**: Draft — awaiting implementation plan  
> **Date**: 2026-07-10  
> **Author**: 王子豪  
> **Scope**: Integrate `olepay.szu.edu.cn` payment gateway with the existing SZU agent CLI/MCP plugin.

---

## 1. Goal

Enable the agent to **automatically progress a venue-booking order to the paid state** through the official SZU campus-card payment gateway (`olepay.szu.edu.cn`).

The integration must:

- Work within the existing Java 21 / Maven / picocli codebase.
- Reuse the CAS/ehall session infrastructure (`EhallSessionManager`, `SessionStore`, `CampusHttpClient`).
- Never store payment credentials in code, CLI arguments, logs, or session files.
- Provide a safe fallback to a manual payment link when full automation is impossible.
- Follow the project's error-code taxonomy and structured JSON output conventions.

---

## 2. Background

The existing `direct-book` command creates a booking and returns a `DHID`. Some venues require payment (observed amount: 5 CNY). The captured payment flow (`支付服务.html` + `ehall.szu.edu.cn11new111.har`) shows:

- Payment page: `https://olepay.szu.edu.cn/Order/CreateOrder`
- Merchant: `merr=1100058`
- Register: `registerid=paychangguan_2023`
- Available methods: `SynCard`, `SynAccType`, `UnionPay`, `WxPay`, `AliPay`
- Status polling: `Pay/GetOrderIdState`
- QR-code/WebSocket support for WeChat/Alipay

---

## 3. Automation Boundary

Not all payment methods can be fully automated. The design acknowledges the following hard limits:

| Method | Automation Level | Required Credential | Notes |
|---|---|---|---|
| `SynCard` / `SynAccType` (campus card / campus account) | **Fully automatic** | Campus-card payment password | Page password form can be filled and submitted by browser automation. |
| `UnionPay` | Hard | Bank card info + SMS | Usually requires SMS verification; not a primary target. |
| `WxPay` | Semi-automatic | Mobile phone scan | Agent can generate/refresh the QR code and poll until the user scans and confirms on the phone. |
| `AliPay` | Semi-automatic | Mobile phone scan | Same as WeChat. |

Therefore, **true unattended payment is only realistic for campus-card methods**. For third-party QR-code methods, the agent automates everything up to the final mobile authorization and then polls for the result.

---

## 4. Architecture

### 4.1 New Package Layout

```
edu.szu.agent.client.payment
├── PaymentOrderClient              # Resolve olepay order parameters from ehall
├── PaymentMethodResolver           # Choose the best available payment method
├── PaymentAutomationDriver         # Strategy interface for payment execution
│   ├── CampusCardPaymentDriver     # SynCard / SynAccType full automation
│   ├── QrCodePaymentDriver         # WeChat / Alipay QR-code flow
│   └── ManualLinkPaymentDriver     # Fallback: return payment link only
├── PaymentStatusPoller             # Poll olepay Pay/GetOrderIdState
├── PaymentResult                   # Outcome record
└── PaymentService                  # High-level orchestrator

edu.szu.agent.cli
├── DirectPayCommand                # direct-pay --username --dhid [--method]
└── DirectPayStatusCommand          # direct-pay-status --username --orderid
```

### 4.2 Design Patterns

- **Strategy**: `PaymentAutomationDriver` lets each payment method implement its own automation logic behind a common interface.
- **Adapter**: `PaymentOrderClient` adapts olepay HTML/form responses into typed `PaymentInitParams`.
- **Service**: `PaymentService` orchestrates session, order resolution, driver selection, and status polling.

---

## 5. Components

### 5.1 `PaymentOrderClient`

Resolves the parameters needed to open the olepay CreateOrder page.

```java
public record PaymentInitParams(
    String olepayOrderId,    // e.g. P2026071023270396455588000733
    String thirdOrderId,     // ehall DHID
    String merchantNo,       // merr
    String registerId,       // registerid
    String account,          // olepay account, e.g. 455588
    int amountFen,           // tranamt in fen
    int actualAmountFen,     // actulamt in fen
    String sign,             // server signature
    String orderDesc,        // URL-encoded description
    String payName,          // payment item name
    String studentName,      // payer name
    String studentId,        // payer student id
    String rzDate,           // order creation time
    String jyDate            // transaction time
) {}

public interface PaymentOrderClient {
    PaymentInitParams resolve(String dhid);
}
```

Implementation notes:

- First attempt: call `EhallSportVenueClient.getMyBookings(...)` and match the row where `DHID` equals the requested `dhid`.
- If the ehall row does not contain the olepay `sign`, call the olepay `CreateOrder` endpoint and parse hidden form fields / response body.

### 5.2 `PaymentMethodResolver`

Selects the payment method based on user preference and availability.

```java
public enum PaymentMethod {
    AUTO, CAMPUS_CARD, WECHAT, ALIPAY, MANUAL_LINK
}

public interface PaymentMethodResolver {
    PaymentMethod resolve(PaymentInitParams params, PaymentMethod preferred);
}
```

Default logic for `AUTO`:

1. If `SZU_CAMPUS_CARD_PASSWORD` (or user-specified env var) is present, prefer `CAMPUS_CARD`.
2. Otherwise prefer `WECHAT` (most common). The QR-code driver will generate the code and poll.
3. If the page indicates a method is unavailable, fall back to `MANUAL_LINK`.

### 5.3 `PaymentAutomationDriver`

```java
public interface PaymentAutomationDriver {
    boolean supports(PaymentInitParams params, PaymentMethod method);
    PaymentResult execute(BrowserLifecycle browser,
                          PaymentInitParams params,
                          PaymentCredentials credentials);
}
```

Drivers:

- `CampusCardPaymentDriver`: navigate to olepay CreateOrder, select `SynCard`/`SynAccType`, fill the campus-card password, submit, and capture the result page.
- `QrCodePaymentDriver`: navigate to the QR-code payment page, extract the QR image URL or data, and return `PaymentResult.pending()` with the QR payload. A poller runs in the background (or via a separate command) until the user scans.
- `ManualLinkPaymentDriver`: always returns the olepay CreateOrder URL and form fields. This is the ultimate fallback.

### 5.4 `PaymentStatusPoller`

Polls `https://olepay.szu.edu.cn/AjaxHandler/Pay/GetOrderIdState`.

```java
public interface PaymentStatusPoller {
    PaymentStatus query(String olepayOrderId);
}

public enum PaymentStatus {
    PENDING, SUCCESS, FAILED, TIMEOUT, UNKNOWN
}
```

Polling defaults: interval 2s, max attempts 30 (60s total), configurable via CLI.

### 5.5 `PaymentResult` and `PaymentCredentials`

```java
public record PaymentCredentials(
    String campusCardPassword   // env-var sourced; null/blank if not using campus card
) {}

public record PaymentResult(
    boolean success,
    PaymentStatus status,
    String olepayOrderId,
    String dhid,
    int amountFen,
    PaymentMethod method,
    String paidAt,            // ISO-8601 when status == SUCCESS
    String qrCodeUrl,         // non-null when status == PENDING and method is WECHAT/ALIPAY
    String manualPaymentUrl,  // non-null when automation falls back
    String message
) {
    public static PaymentResult alreadyPaid(String olepayOrderId, String dhid) { ... }
    public static PaymentResult pending(String olepayOrderId, String dhid,
                                        PaymentMethod method, String qrCodeUrl) { ... }
}
```

### 5.6 `PaymentService`

High-level orchestrator used by CLI commands.

```java
public class PaymentService {
    public PaymentResult pay(String dhid, PaymentMethod method);
    public PaymentStatus queryStatus(String olepayOrderId);
}
```

Responsibilities:

1. Load or refresh ehall/olepay session.
2. Resolve order parameters via `PaymentOrderClient`.
3. If `actualAmountFen == 0`, return `PaymentResult.alreadyPaid(...)` immediately.
4. Decide payment method via `PaymentMethodResolver`.
5. Execute the appropriate `PaymentAutomationDriver`.
6. Poll for status if the driver returns `PENDING`.
7. Persist session changes best-effort.

### 5.7 CLI Commands

**`direct-pay`**

```bash
java -jar target/szu-agent-plugin.jar direct-pay \
  --username 2023150090 \
  --dhid 202607102256205748 \
  --method auto
```

Output (campus card success):

```json
{
  "success": true,
  "data": {
    "olepayOrderId": "P2026071023270396455588000733",
    "dhid": "202607102256205748",
    "amountFen": 500,
    "amountDisplay": "5.00",
    "method": "CAMPUS_CARD",
    "status": "SUCCESS",
    "paidAt": "2026-07-10T23:27:45"
  }
}
```

Output (WeChat pending):

```json
{
  "success": true,
  "data": {
    "olepayOrderId": "P2026071023270396455588000733",
    "dhid": "202607102256205748",
    "method": "WECHAT",
    "status": "PENDING",
    "qrCodeUrl": "https://olepay.szu.edu.cn/AjaxHandler/Order/OrderQrcode?orderid=...&state=1",
    "nextCommand": "direct-pay-status --username 2023150090 --orderid P2026071023270396455588000733"
  }
}
```

**`direct-pay-status`**

```bash
java -jar target/szu-agent-plugin.jar direct-pay-status \
  --username 2023150090 \
  --orderid P2026071023270396455588000733 \
  --timeout-seconds 120
```

---

## 6. Security and Audit

1. **Credential isolation**: The campus-card password is read only from an environment variable (`SZU_CAMPUS_CARD_PASSWORD` by default, or a user-specified name). It is never accepted as a CLI argument, never logged, and never persisted.
2. **Log masking**: All `sign`, password, and token fields are redacted by `LogMasker` before writing to SLF4J.
3. **Fail-open fallback**: If browser automation fails at any step, the service falls back to `ManualLinkPaymentDriver` and returns the official payment URL.
4. **Audit record**: Each payment attempt is recorded in `RunRecord` with `traceId`, `dhid`, `method`, and final status. Passwords and signatures are excluded.
5. **Feature flag**: Configuration `payment.automation.enabled=false` disables all browser automation; only the manual link is returned.
6. **Scope limitation**: The implementation only automates the official SZU campus-card gateway for the authenticated user. It does not attempt to bypass CAPTCHAs, rate limits, or third-party payment authorization screens.

---

## 7. Error Codes

Add the following constants to `edu.szu.agent.error.ErrorCode`:

| Error Code | Severity | Retryable | Screenshot | Hint |
|---|---|---|---|---|
| `PAYMENT_ORDER_NOT_FOUND` | MEDIUM | false | false | 订单不存在或无权访问 |
| `PAYMENT_ALREADY_PAID` | LOW | false | false | 该订单已支付 |
| `PAYMENT_METHOD_UNAVAILABLE` | MEDIUM | true | false | 所选支付方式不可用，请尝试其他方式 |
| `PAYMENT_PASSWORD_REQUIRED` | MEDIUM | false | false | 校园卡自动支付需要配置 SZU_CAMPUS_CARD_PASSWORD |
| `PAYMENT_PASSWORD_INCORRECT` | HIGH | false | true | 校园卡支付密码错误 |
| `PAYMENT_GATEWAY_ERROR` | HIGH | true | true | olepay 网关返回异常 |
| `PAYMENT_STATUS_TIMEOUT` | MEDIUM | false | false | 支付状态轮询超时，请使用 direct-pay-status 继续查询 |
| `PAYMENT_MANUAL_REQUIRED` | LOW | false | false | 该支付方式需用户在手机上确认，请使用返回的二维码或链接 |

---

## 8. Data Flow

### 8.1 Campus-Card Automatic Payment

```
User/Agent
   │ direct-pay --username X --dhid Y --method auto
   ▼
DirectPayCommand
   │ resolve account, load session
   ▼
PaymentService.pay(dhid, AUTO)
   │
   ├── PaymentOrderClient.resolve(dhid)
   │   └── ehall my-bookings + olepay CreateOrder form
   │
   ├── PaymentMethodResolver → CAMPUS_CARD
   │   (password env var present)
   │
   ├── CampusCardPaymentDriver.execute(browser, params, credentials)
   │   ├── navigate to olepay CreateOrder
   │   ├── select SynCard / SynAccType
   │   ├── fill password from env
   │   ├── submit
   │   └── capture result page
   │
   └── PaymentStatusPoller.query(orderId)
       └── SUCCESS / FAILED
   ▼
stdout JSON
```

### 8.2 QR-Code Payment

Same as above until driver selection:

```
PaymentMethodResolver → WECHAT
QrCodePaymentDriver.execute(...)
   ├── navigate to /Pay/WxPayType
   ├── extract QR code URL
   └── return PaymentResult.pending(qrCodeUrl)
PaymentStatusPoller.query(orderId)  // optional immediate poll
   ▼
stdout JSON with qrCodeUrl + nextCommand
```

The user scans the QR code with WeChat/Alipay and authorizes payment. The agent then calls `direct-pay-status` to detect `SUCCESS`.

---

## 9. Testing Strategy

1. **Unit tests**
   - `PaymentOrderClientTest`: parse olepay `CreateOrder` HTML fixture from `支付服务.html` into `PaymentInitParams`.
   - `PaymentMethodResolverTest`: verify method selection logic for all combinations of env vars and available methods.
   - `PaymentStatusPollerTest`: fake HTTP server returning `GetOrderIdState` JSON; verify state transitions.

2. **Driver tests**
   - `ManualLinkPaymentDriverTest`: always returns correct URL and form fields.
   - `CampusCardPaymentDriverTest`: use `FakeBrowser` to simulate page interactions and assert fill/submit sequence.
   - `QrCodePaymentDriverTest`: assert QR URL extraction.

3. **Integration tests**
   - `DirectPayCommandTest`: mock `PaymentService`, verify JSON output schema and exit codes.
   - `PaymentServiceEndToEndTest`: wire real `CampusHttpClient` against a local stub olepay server.

4. **HAR regression**
   - Keep a sanitized copy of `ehall.szu.edu.cn11new111.har` request/response snippets as fixtures under `src/test/resources/payment/`.

---

## 10. Implementation Phases

| Phase | Deliverable | Risk |
|---|---|---|
| P0 | `PaymentOrderClient`, `ManualLinkPaymentDriver`, `PaymentService`, `direct-pay` command | Low |
| P1 | `CampusCardPaymentDriver`, env-var password resolution, `direct-pay-status` | Medium (handles password) |
| P2 | `QrCodePaymentDriver`, WebSocket/polling support for WeChat/Alipay | Medium |
| P3 | `--auto-pay` flag in `direct-book`, `PaymentMethodResolver` default logic | Low |

---

## 11. Open Questions Resolved

- **Scope**: Full automation is supported only for campus-card methods; QR-code methods require final mobile authorization.
- **Credentials**: Campus-card password comes from environment variables only.
- **Fallback**: Manual payment link is always available.
- **Safety**: No credentials are logged or persisted.

---

## 12. Related Files

- `src/main/java/edu/szu/agent/client/http/EhallSportVenueClient.java`
- `src/main/java/edu/szu/agent/client/http/VenueBookingService.java`
- `src/main/java/edu/szu/agent/client/http/EhallSessionManager.java`
- `src/main/java/edu/szu/agent/error/ErrorCode.java`
- `src/main/java/edu/szu/agent/cli/DirectBookCommand.java`
- `src/main/java/edu/szu/agent/browser/BrowserLifecycle.java`
- `C:/Users/王子豪/Downloads/支付服务.html`
- `C:/Users/王子豪/Downloads/ehall.szu.edu.cn11new111.har`

# 交接说明：支付服务接入（payment-service）

**日期**：2026-07-10  
**交接人**：Kimi Code CLI 会话  
**目标接手人**：后续继续完善 `szu-agent-plugin` 深大内部网 AI-friendly 改造的开发者  
**相关背景**：用户希望把体育场馆预约后的支付环节也接入 Agent，使其能自动完成 olepay 校园卡支付，或在需要时生成支付链接。

---

## 一、本次完成的工作

### 1. 支付服务设计文档

已写入：

- `docs/superpowers/specs/2026-07-10-payment-service-design.md`

包含内容：

- 目标与范围
- olepay 支付流程分析（基于 `支付服务.html` + `ehall.szu.edu.cn11new111.har`）
- 自动化边界：校园卡/校园账户可全自动；微信/支付宝只能到生成二维码/轮询状态
- 架构设计：`PaymentService` + `PaymentAutomationDriver` 策略 + `PaymentOrderClient` + `PaymentStatusPoller`
- 安全与审计约束
- 新增错误码
- CLI 命令：`direct-pay`、`direct-pay-status`
- 实现阶段划分（P0 ~ P3）

### 2. 支付服务实现计划

已写入：

- `docs/superpowers/plans/2026-07-10-payment-service.md`

包含 10 个任务，每个任务有：

- 目标文件
- 接口契约（consumes/produces）
- 编号步骤与代码片段
- 测试命令与期望输出
- 提交命令

计划任务清单：

| 任务 | 内容 | 优先级 |
|---|---|---|
| Task 1 | 支付领域记录与错误码 | P0 |
| Task 2 | 订单解析 `PaymentOrderClient` | P0 |
| Task 3 | 手动链接驱动 `ManualLinkPaymentDriver` | P0 |
| Task 4 | `PaymentService` 编排器 | P0 |
| Task 5 | `direct-pay` CLI 命令 | P0 |
| Task 6 | 状态轮询与 `direct-pay-status` | P0 |
| Task 7 | 校园卡自动支付驱动 | P1 |
| Task 8 | 支付方式解析器 | P1/P2 |
| Task 9 | 二维码支付驱动 | P2 |
| Task 10 | `direct-book --auto-pay` 开关 | P3 |

### 3. 代码分支

已创建特性分支：

```bash
git checkout -b feat/payment-service
```

当前分支 `feat/payment-service` 已包含两个文档提交：

- `557c282 docs(payment): add payment service integration design spec`
- `d3d37c8 docs(payment): add payment service implementation plan`

### 4. 逆向分析素材

已识别并可用于后续实现的素材：

| 文件/路径 | 说明 |
|---|---|
| `C:/Users/王子豪/Downloads/支付服务.html` | olepay 支付页面快照，包含 CreateOrder 表单、JS 调用入口、支付方式选择逻辑 |
| `C:/Users/王子豪/Downloads/支付服务_files/` | 页面静态资源 |
| `C:/Users/王子豪/Downloads/ehall.szu.edu.cn11new111.har` | ehall + olepay 交互 HAR，包含 `sportVenue/payBookingInfo.do` 等端点引用 |
| `E:/CODE/reverse-skill/` | 逆向技能包，含 burp-mcp-full 等 HTTP 分析工具 |

---

## 二、关键决策与约束

### 1. 自动化边界（已和用户确认）

- **校园卡 / 校园账户（SynCard / SynAccType）**：可全自动，需要校园卡支付密码。
- **微信支付 / 支付宝**：只能自动到生成二维码并轮询状态，最终扣款仍需用户在手机上确认。
- **银联**：有短信验证，不作为主要目标。

### 2. 安全红线

- 校园卡密码**只能**从环境变量读取，默认变量名为 `SZU_CAMPUS_CARD_PASSWORD`。
- 不得通过 CLI 参数传递密码。
- `sign`、密码、token 等字段写入日志前必须用 `LogMasker` 脱敏。
- 自动支付失败时必须降级为返回手动支付链接。

### 3. 架构约定

- 新代码放在 `edu.szu.agent.client.payment` 包。
- CLI 命令放在 `edu.szu.agent.cli`。
- 复用现有 `EhallSessionManager`、`CampusHttpClient`、`SessionStore`、`AccountResolver`。
- 所有新增公共方法需带 Javadoc：`@since 0.7.0`、`@author 王子豪`。
- 错误处理沿用 `BookingException` + `ErrorCode`。

---

## 三、当前状态

### 已完成

- [x] 支付服务设计文档
- [x] 支付服务实现计划
- [x] 特性分支 `feat/payment-service` 创建
- [x] 设计文档提交到分支
- [x] 实现计划提交到分支

### 未完成

- [ ] 任何生产代码/测试代码尚未编写
- [ ] `direct-pay`、`direct-pay-status` 命令未实现
- [ ] `PaymentService` 及相关客户端未实现
- [ ] `ErrorCode` 未扩展支付错误码
- [ ] `Main.java` 未注册新命令
- [ ] 未运行任何新测试

### 中断原因

用户在开始执行 Task 1 前要求转为交接文档，因此子代理驱动的实现流程被中断，未产生代码提交。

---

## 四、关键端点与参数（来自抓包）

### olepay 支付页面

```
URL:     https://olepay.szu.edu.cn/Order/CreateOrder
Referer: https://olepay.szu.edu.cn/
```

POST body 示例（来自 HAR）：

```text
sign=...
&tranamt=500
&actulamt=500
&thirdorderid=202607102256205748
&account=455588
&toaccount=1100124
&orderdesc=%E5%9C%BA%E9%A6%86%E9%A2%84%E7%BA%A6
&sno=2023150090
&thirdsystem=paychangguan_2023
&state=1
&orderid=P2026071023242005455588000732
&payname=%E4%BD%93%E8%82%B2%E4%B8%93%E9%A1%B9%E7%BB%8F%E8%B4%B9
&praram1=
&name=%E7%8E%8B%E5%AD%90%E8%B1%AA
&rzdate=2026-07-10+23%3A24%3A40
&jydate=2026-07-10+23%3A24%3A40
```

### 关键常量

| 字段 | 值 | 说明 |
|---|---|---|
| `merr` | `1100058` | 商户号 |
| `registerid` | `paychangguan_2023` | 第三方系统注册标识 |
| `account` | `455588` | olepay 账户 |
| `toaccount` | `1100124` | 收款账户 |
| `thirdsystem` | `paychangguan_2023` | 第三方系统 |

### ehall 相关端点

- `sportVenue/payBookingInfo.do` — 可能用于获取支付所需参数
- `sportVenue/updateOrderInfo.do`
- `sportVenue/setYyinfoToMoney.do`
- `sportVenue/getUserInfoByIdForPay.do`

### olepay 相关端点

- `Order/CreateOrder`
- `Order/GetPayTypeList/`
- `Order/UpdateOrderLastpayids`
- `Order/OrderQrcode`
- `Pay/GetOrderIdState`
- `Pay/SynAccType`
- `Pay/SynBankCard`
- `Pay/SynCard`
- `Pay/UnionPay`
- `Pay/WxPayType`
- `Pay/AliPayType`

---

## 五、下一步建议

### 路线 A：继续按实现计划执行（推荐）

1. 回到 `feat/payment-service` 分支。
2. 按 `docs/superpowers/plans/2026-07-10-payment-service.md` 的 Task 1 ~ Task 7 顺序实现。
3. 每个任务完成后运行对应测试，最终运行 `mvn test`。
4. 如需子代理驱动，可调用 `superpowers:subagent-driven-development`。

### 路线 B：先补充抓包分析

如果对 `payBookingInfo.do` 的入参/返回、或 `CreateOrder` 的 sign 生成逻辑不确定，建议：

1. 使用 `E:/CODE/reverse-skill/burp-mcp-full` 或浏览器 DevTools 重新抓取一次完整支付流程。
2. 重点捕获：
   - 点击"支付"按钮到 olepay `CreateOrder` 之间的请求
   - `sportVenue/payBookingInfo.do` 的请求/响应
   - 校园卡支付提交后的响应
3. 更新 `docs/superpowers/specs/2026-07-10-payment-service-design.md` 中的数据流。

### 路线 C：先做 P0 手动链接

如果希望快速验证价值，可先只实现 Task 1 ~ Task 5：

- 输入 `DHID`
- 查询预约记录确认金额
- 生成 olepay `CreateOrder` 链接
- 返回给用户/Agent 手动支付

这是风险最低、最快可用的版本。

---

## 六、相关文件索引

### 设计/计划

- `docs/superpowers/specs/2026-07-10-payment-service-design.md`
- `docs/superpowers/plans/2026-07-10-payment-service.md`
- `docs/handoff/2026-07-10-payment-service-handover.md`（本文件）

### 现有相关代码

- `src/main/java/edu/szu/agent/client/http/EhallSportVenueClient.java`
- `src/main/java/edu/szu/agent/client/http/VenueBookingService.java`
- `src/main/java/edu/szu/agent/client/http/EhallSessionManager.java`
- `src/main/java/edu/szu/agent/client/http/CampusHttpClient.java`
- `src/main/java/edu/szu/agent/client/session/SessionStore.java`
- `src/main/java/edu/szu/agent/account/AccountResolver.java`
- `src/main/java/edu/szu/agent/error/ErrorCode.java`
- `src/main/java/edu/szu/agent/cli/Main.java`
- `src/main/java/edu/szu/agent/browser/BrowserLifecycle.java`

### 外部素材

- `C:/Users/王子豪/Downloads/支付服务.html`
- `C:/Users/王子豪/Downloads/ehall.szu.edu.cn11new111.har`
- `E:/CODE/reverse-skill/`

---

## 七、注意事项

1. **不要直接在 `master` 上开发**：当前分支 `feat/payment-service` 已创建，后续工作应在此分支上继续。
2. **支付敏感**：实现涉及真实资金操作，测试时务必使用小额测试订单，避免重复扣款。
3. **环境变量**：校园卡密码只能走环境变量，禁止写入 CLI 参数、日志或 session 文件。
4. **网络依赖**：olepay 和 ehall 需要校园网或 VPN，本地开发时可能需要 `--trust-all`。
5. **HAR 素材不外传**：`支付服务.html` 和 `.har` 文件包含个人订单信息，请勿提交到仓库。

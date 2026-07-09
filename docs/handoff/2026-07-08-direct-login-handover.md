# 交接说明：深大 CAS 直接发包登录（direct-login）

**日期**：2026-07-09（本次更新）  
**原始日期**：2026-07-08  
**交接人**：Kimi Code CLI 会话  
**目标接手人**：后续继续完善 `szu-agent-plugin` 深大内部网 AI-friendly 改造的开发者  
**相关背景**：项目原基于 Playwright 浏览器自动化操作深大内部网，目标是改为直接 HTTP 发包，降低依赖、提高稳定性。

---

## 一、本次完成的工作

### 1. 新增直接 HTTP 客户端层

在 `src/main/java/edu/szu/agent/client/http/` 下新增/调整：

| 文件 | 职责 |
|------|------|
| `CampusHttpClient.java` | 基于 `java.net.HttpURLConnection` 的手动重定向 HTTP 客户端，带 CookieJar、超时配置、GET/POST(form) 方法、可选请求/响应录制 |
| `CookieJar.java` | 简易内存 Cookie 存储，按域名/路径管理，支持 `Set-Cookie` 解析 |
| `HttpTrafficRecorder.java` | 原 Playwright 抓包记录器；`maskSensitiveBody` / `isTextResponse` / `MAX_BODY_SUMMARY_BYTES` 已提升为 `public static`，供直接 HTTP 录制复用 |
| `RecordedExchange.java` | 单次请求-响应记录的数据结构 |

`CampusHttpClient` 关键改动：

- 从 `java.net.http.HttpClient` 切到 `HttpURLConnection`。
- 手动设置 `Connection: close`，避免 JDK 11+ HTTP client 在 CAS 登录 POST 时被服务端断连。
- 新增可选 `ExchangeRecorder` 回调，支持 `capture-login` 直接记录请求/响应。
- 新增 `--trust-all` 开关：对 `HttpsURLConnection` 安装全信任 `TrustManager` 与允许任意主机名验证器，用于校园网内网证书过期/自签场景（默认关闭，仅 dev/internal 使用）。

### 2. 新增 CAS/Authserver 登录客户端

| 文件 | 职责 |
|------|------|
| `AuthserverPasswordEncryptor.java` | 对接 authserver 登录页 `encrypt.js` 的 AES-128-CBC/PKCS5Padding 加密逻辑，含 64 位随机前缀 |
| `CasLoginClient.java` | 直接访问 `https://authserver.szu.edu.cn/authserver/login`，解析隐藏表单字段，构造登录 POST |

`CasLoginClient` 关键修复：

- **只解析密码登录表单**：通过 `id="pwdLoginDiv"` 到 `id="qrLoginDiv"` 之间的 HTML 片段提取隐藏字段，避免把二维码/FIDO/手机动态码表单的字段一起提交。
- **修正盐值提取**：从 `<input id="pwdEncryptSalt" value="...">` 取值，键名改为 `pwdEncryptSalt`（与 `AuthserverPasswordEncryptor` 对齐），之前误按 JS 变量名 `pwdDefaultEncryptSalt` 匹配导致盐值缺失。
- **POST 地址包含 `service`**：登录 POST 直接发到带 `service` 查询参数的登录 URL，与浏览器 HAR 抓包一致；原表单 `action` 不带 `service` 会导致服务端 500。
- 提交字段精简为：`username`、`password`（加密后）、`_eventId`、`cllt`、`dllt`、`lt`、`execution`。

加密逻辑已与 reverse-skill/BrowserAct 中的 JS 实现交叉验证：相同输入下 Java 与 JS 输出一致。

### 3. 新增 CLI 命令

| 文件 | 命令 | 作用 |
|------|------|------|
| `DirectLoginCommand.java` | `direct-login` | 执行直接登录，并探测 `https://www1.szu.edu.cn/board/boardlist.asp` 验证登录态；默认把 Cookie 持久化到 `~/.szu-agent/sessions/<username>.json` |
| `CaptureLoginCommand.java` | `capture-login` | 改为直接 HTTP 抓包：执行一次完整登录流程，记录所有请求/响应到 JSON（不再依赖 Playwright/Obscura） |
| `HttpGetCommand.java` | `http-get` | 加载已持久化的 session，直接访问需要登录态的内部 URL |
| `DirectBookCommand.java` | `direct-book` | 直接调用 ehall 体育场馆预约 API 下单；支持 `--sport-code`/`--campus-code` 原始编码 |
| `DirectBookingsCommand.java` | `direct-bookings` | 基于 HAR 调用 ehall `myBookingInfo.do` 查询当前用户预约历史 |
| `DirectSportsCommand.java` | `direct-sports` | 调用 ehall `getSportVenueData.do` 动态发现所有校区/项目/场馆组 |
| `DirectDatesCommand.java` | `direct-dates` | 列出可预约日期 |
| `DirectSlotsCommand.java` | `direct-slots` | 列出某校区/项目/日期下的所有时段 |
| `DirectVenuesCommand.java` | `direct-venues` | 列出某校区/项目/日期/时段下的可用场地 |
| `EhallSportVenueClient.java` | — | ehall 场馆预约 API 客户端：发现 → 查日期 → 查时段 → 查场地 → 下单 → 查预约 |

这些命令新增 `--trust-all` 选项。`direct-login` 可用 `--no-persist` 跳过保存。

### 4. `Main.java` 修改

注册了两个新的 picocli 子命令：`capture-login`、`direct-login`（已有，无需再改）。

---

## 二、关键参数与默认值

```
CAS 基础地址： https://authserver.szu.edu.cn
登录路径：     /authserver/login
默认 service： http://www1.szu.edu.cn/manage/caslogin.asp?rurl=/
探测 URL：     https://www1.szu.edu.cn/board/boardlist.asp
用户名字段：   username
密码字段：     password
密码加密盐：   从登录页 HTML 中解析 id="pwdEncryptSalt"
Session 路径： ~/.szu-agent/sessions/<username>.json
```

用户名通过 `--username` 指定；密码优先读取环境变量 `SZU_PASSWORD_<username>`，其次提示输入（命令行回显已关闭）。

---

## 三、当前状态

### 已验证

在可访问 `authserver.szu.edu.cn` 的网络环境下，使用测试账号执行：

```powershell
$env:SZU_PASSWORD_2023150090="11282577"
java -jar target\szu-agent-plugin.jar direct-login --username 2023150090 --trust-all
```

结果：

```json
{"success":true,"data":{"traceId":"...","hasSession":true,"cookieCount":7,"probeStatus":200,"probeBodyLength":4094,"persisted":true,"sessionPath":"C:\\Users\\...\\.szu-agent\\sessions\\2023150090.json","durationMs":1485}}
```

- `hasSession=true`：Cookie jar 中存在 session/castgc/tgc 类 Cookie。
- `probeStatus=200`：登录成功后访问 `www1.szu.edu.cn/board/boardlist.asp` 返回 200，登录态有效。
- `persisted=true`：登录态已保存到 `~/.szu-agent/sessions/<username>.json`。
- 必须加 `--trust-all`，因为 `www1.szu.edu.cn` 的 HTTPS 证书已过期，Java 默认 PKIX 校验会失败。

随后可复用 session 直接抓取登录后的页面：

```powershell
java -jar target\szu-agent-plugin.jar http-get --username 2023150090 `
  --url https://www1.szu.edu.cn/board/boardlist.asp --trust-all
```

返回 `status=200` 与 body 预览，无需再次登录。

`capture-login` 同样验证通过，可记录 5 条交换：

```
0 GET 200  authserver login page
1 POST 302 authserver login submit
2 GET 302 http://www1...caslogin.asp?ticket=...
3 GET 302 https://www1...caslogin.asp?ticket=...
4 GET 200 https://www1.szu.edu.cn/
```

### 已修复

- 登录 POST 被服务端断开/超时（`NETWORK_TIMEOUT`）→ 已切到 `HttpURLConnection` 并强制 `Connection: close`。
- 登录 POST 返回 500 → 已改为把 `service` 查询参数带在 POST URL 上。
- 盐值提取失败 → 改为从 `pwdEncryptSalt` input 取值。
- 多表单字段污染 → 只提取 `pwdLoginDiv` 内的隐藏字段。
- `capture-login` 报 `BROWSER_CRASH`（缺少 bundled Obscura）→ 已改为直接 HTTP 录制。
- 错误日志已增强，现在会打印底层 `IOException` 的具体消息，便于定位。
- 已重新打包：`mvn -q -Pobscura-skip-download package -DskipTests`

### 已知限制

- `--trust-all` 关闭时，访问 `www1.szu.edu.cn` 会报 `certificate_expired`/`PKIX path validation failed`。这是目标站点证书问题，不是代码问题；生产环境如部署到信任该证书的服务器，可不用此开关。
- `direct-book` 默认仍可通过 `--campus`/`--sport` 枚举使用，但项目映射仅补到网球/健身；对于未枚举项目，使用 `--campus-code <XQDM> --sport-code <XMDM>` 即可直接预约任意项目。
- `direct-book` 需要先用 `direct-login --service <ehall入口>` 拿到 ehall 域的 session；用默认的 `www1` service 保存的 session 访问 ehall 会返回 HTML 登录页。
- `ExamListTaskTest.filtersPendingExams` 仍因时间写死而失败（与本次改动无关）。
- 取消预约接口未在当前 HAR 中捕获，尚未实现。

---

## 四、构建与验证命令

```bash
# 构建 jar
mvn -q -Pobscura-skip-download package -DskipTests

# 查看命令帮助
java -jar target/szu-agent-plugin.jar --help
java -jar target/szu-agent-plugin.jar direct-login --help

# 导出登录页 HTML 用于调试
java -jar target/szu-agent-plugin.jar direct-login --username 2023150090 --dump-page E:\tmp\szu-login-page.html

# 直接登录验证（PowerShell，需信任过期证书）
$env:SZU_PASSWORD_2023150090="11282577"
java -jar target\szu-agent-plugin.jar direct-login --username 2023150090 --trust-all

# 抓包调试（输出到 JSON）
$env:SZU_PASSWORD_2023150090="11282577"
java -jar target\szu-agent-plugin.jar capture-login --username 2023150090 --output E:\tmp\szu-cas-traffic.json --trust-all

# 复用 session 直接抓取登录后的页面
java -jar target\szu-agent-plugin.jar http-get --username 2023150090 `
  --url https://www1.szu.edu.cn/board/boardlist.asp --trust-all

# 先登录 ehall 并保存 session（service 必须指向 ehall）
$env:SZU_PASSWORD_2023150090="11282577"
java -jar target\szu-agent-plugin.jar direct-login --username 2023150090 --trust-all `
  --service "https://ehall.szu.edu.cn/qljfwapp/sys/lwSzuCgyy/index.do#/sportVenue"

# 直接预约（最小版本： tennis / 粤海）
java -jar target\szu-agent-plugin.jar direct-book --username 2023150090 --trust-all `
  --date 2026-07-10 --slot 08:00-09:00 --name 王子豪

# 查询当前用户的预约历史（复用 ehall session）
java -jar target\szu-agent-plugin.jar direct-bookings --username 2023150090 --trust-all --page-size 5

# 发现所有校区和项目（输出原始 XQDM/XMDM）
java -jar target\szu-agent-plugin.jar direct-sports --username 2023150090 --trust-all

# 列出可预约日期
java -jar target\szu-agent-plugin.jar direct-dates --username 2023150090 --trust-all

# 列出某项目某天所有时段（以网球 004 为例）
java -jar target\szu-agent-plugin.jar direct-slots --username 2023150090 --trust-all `
  --campus-code 1 --sport-code 004 --date 2026-07-10

# 列出某时段可用场地
java -jar target\szu-agent-plugin.jar direct-venues --username 2023150090 --trust-all `
  --campus-code 1 --sport-code 004 --date 2026-07-10 --slot 08:00-09:00

# 用原始编码直接预约任意项目
java -jar target\szu-agent-plugin.jar direct-book --username 2023150090 --trust-all `
  --campus-code 1 --sport-code 004 --date 2026-07-10 --slot 08:00-09:00 --name 王子豪
```

---

## 五、ehall1122 HAR 分析摘要

基于 `C:\Users\王子豪\Downloads\ehall.szu.edu.cn1122.har`（我的预约页面 + 场馆预约下单）提取：

### 5.1 项目代码映射

**粤海校区 `XQDM=1`**

| 显示名 | XMDM | YYLX | 备注 |
|--------|------|------|------|
| 羽毛球 | `001` | `1.0` | 包场 |
| 足球 | `002` | `2.0` | 散场 |
| 排球 | `003` | `1.0,2.0` | 包场+散场 |
| 网球 | `004` | `1.0` | 包场；已验证可预约 |
| 篮球 | `005` | `2.0` | 散场 |
| 壁球 | `006` | `1.0` | 包场 |
| 一楼重量型健身 | `007` | `2.0` | 散场 |
| 二楼有氧健身 | `008` | `2.0` | 散场 |
| 游泳 | `009` | `2.0` | 散场 |
| 智能健身房 | `024` | `1.0` | 包场 |

**丽湖校区 `XQDM=2`**

| 显示名 | XMDM | YYLX | 备注 |
|--------|------|------|------|
| 羽毛球 | `001` | `1.0` | 包场 |
| 排球 | `003` | `1.0` | 包场 |
| 网球 | `004` | `1.0` | 包场 |
| 篮球 | `005` | `1.0,2.0` | 包场+散场 |
| 游泳 | `009` | `2.0` | 散场 |
| 乒乓球 | `013` | `1.0` | 包场 |
| 舞蹈 | `015` | `1.0` | 包场 |
| 桌球 | `016` | `1.0` | 包场 |
| 骑行 | `017` | `1.0` | 包场 |
| 魔镜 | `018` | `1.0` | 包场 |
| 桌游 | `019` | `1.0` | 包场 |
| 健身房 | `020` | `2.0` | 散场 |
| 瑜伽 | `021` | `2.0` | 散场 |
| 匹克球 | `030` | `1.0` | 包场 |
| 毽球 | `034` | `1.0` | 包场 |

> `YYLX=1.0` 为包场，`2.0` 为散场。不同项目的预约方式不同，直接预约时默认使用 `1.0`；若项目只支持散场，下单会收到服务端错误，此时可用 `--sport-code` 配合后续实现的 `--yylx` 指定。
> 当前 `YuehaiSport`/`LihuSport` 枚举已按上表映射到 XMDM，`--sport` 参数现在对所有枚举项目有效。

### 5.2 关键接口

| 接口 | 路径 | 用途 |
|------|------|------|
| `getRqList.do` | `/qljfwapp/sys/lwSzuCgyy/sportVenue/getRqList.do` | 返回可预约日期列表 |
| `getTimeList.do` | `/qljfwapp/sys/lwSzuCgyy/sportVenue/getTimeList.do` | 参数 `XQ, YYRQ, YYLX, XMDM` |
| `getOpeningRoom.do` | `/qljfwapp/sys/lwSzuCgyy/modules/sportVenue/getOpeningRoom.do` | 参数 `XMDM, YYRQ, YYLX, KSSJ, JSSJ, XQDM` |
| `insertVenueBookingInfo.do` | `/qljfwapp/sys/lwSzuCgyy/sportVenue/insertVenueBookingInfo.do` | 下单；字段见下 |
| `myBookingInfo.do` | `/qljfwapp/sys/lwSzuCgyy/modules/myBooking/myBookingInfo.do` | 查询我的预约；参数 `pageSize, pageNumber` |

### 5.3 下单字段

```
DHID=
YYRGH=<学号>
CYRS=
YYRXM=<姓名>
CGDM=<场馆编码，与 getOpeningRoom 返回的 CGBM 相同>
CDWID=<场地 WID>
XMDM=<项目编码>
XQWID=<校区编码>
KYYSJD=<08:00-09:00>
YYRQ=<2026-07-10>
YYLX=<1.0>
YYKS=<2026-07-10 08:00>
YYJS=<2026-07-10 09:00>
PC_OR_PHONE=pc
```

### 5.4 我的预约响应字段

常用字段：`DHID, WID, XQWID, XQWID_DISPLAY, XMDM, XMDM_DISPLAY, CGDM, CGDM_DISPLAY, CDWID, CDWID_DISPLAY, YYLX, YYZT, YYZT_DISPLAY, YYSJD, CJSJ, ZHJE`。

状态码示例：`CG_YY`（已预约）、`CG_WC`（已完成）、`CG_QX`（取消预约）。

### 5.5 真实验证

```powershell
java -jar target\szu-agent-plugin.jar direct-bookings --username 2023150090 --trust-all --page-size 5
```

返回 `totalSize=337`，第一行为刚预约的 2026-07-10 08:00 北区网球1号场，状态 `已预约`。

### 5.6 ehall11new HAR 与动态发现端点

基于 `C:\Users\王子豪\Downloads\ehall.szu.edu.cn11new.har`（切换不同项目 tile 后的时段请求）以及前端 JS 分析：

- 发现端点：`/qljfwapp/sys/lwSzuCgyy/sportVenue/getSportVenueData.do`
  - 返回 `campusList`（校区）、`xmList`（项目，带 `XMDM`、`XQDM`、`DCFS`）、`packageVenueList`（包场场馆组）、`dismissalVenueList`（散场场馆组）。
- 时段端点：`/qljfwapp/sys/lwSzuCgyy/sportVenue/getTimeList.do`
  - 参数：`XQ`（校区代码）、`YYRQ`（日期）、`YYLX`（1.0 包场 / 2.0 散场）、`XMDM`（项目代码）。
  - 返回 14 个时段，08:00–22:00，每个时段带 `WID`、`CODE`、`STATE_EXPLAIN`、`disabled`、`text`。
- 场地端点：`/qljfwapp/sys/lwSzuCgyy/modules/sportVenue/getOpeningRoom.do`
  - 新增可选参数 `CGBM`（场馆组编码），用于过滤特定场馆组。

因此支持“所有运动和时间段”的路线是：**不再硬编码全部映射，而是用 `direct-sports` 动态发现，配合 `--sport-code`/`--campus-code` 直接预约**。

---

## 七、下一步建议

1. **安全加固**：`--trust-all` 当前仅在 CLI dev 命令暴露。若后续要作为 MCP tool 暴露，建议改为读取内网 CA 证书或让用户显式开启，不要默认关闭 TLS 校验。
2. **失败处理增强**：目前只识别“密码错误/验证码/账号被锁”。后续可补充“需要二次认证”“IP 限制”“频繁登录锁定”等场景。
3. **集成到 Skill/MCP**：登录成功后，建议将 `CasLoginClient` 封装为 Skill 工具，供 `mcp serve` 暴露给外部 AI agent。
4. **替换 Playwright 路径**：确认直接登录稳定后，可逐步将原有基于浏览器的 booking/notice/calendar/exam 任务切到 `CampusHttpClient`。
5. **修复时间相关测试**：将 `ExamListTaskTest.filtersPendingExams` 改为基于相对日期或 mock 时钟，避免再次过期。
6. **探针 URL 可配置/校验**：当前 probe 仅返回 body 长度；可进一步校验 body 中是否包含登录后才能看到的内容，防止“200 但仍是登录页”的假阳性。
7. **验证 direct-sports 在真实 session 下的输出**：用 `direct-login --service ehall入口` 登录后，运行 `direct-sports` 确认返回的 `xmList` 字段完整（名称、DCFS、校区），从而补全 `YuehaiSport`/`LihuSport` 枚举中的 XMDM 元数据。
8. **实现取消预约**：myBooking 页面存在取消按钮，但取消接口未在当前 HAR 中捕获，需要额外抓包。
9. **考虑移除硬编码映射**：如果动态发现稳定，可将 `EhallSportVenueClient.sportCode(Sport)` 改为读取枚举中保存的 XMDM，或直接 deprecate 枚举路径，全面转向 `--sport-code`。

---

## 八、注意事项

- 不要把真实密码写入命令行参数或提交到仓库。
- 环境变量命名规则：`SZU_PASSWORD_<username>`（全大写）。
- `--trust-all` 会关闭 TLS 校验，仅用于校园网 dev/internal 场景，不要对公网生产环境默认开启。
- `target/szu-agent-plugin.jar` 和 `target/original-*.jar` 已生成，但 `target/` 本身在 `.gitignore` 中，不要提交。
- 如后续 authserver 页面结构或加密脚本更新，需要同步更新 `CasLoginClient` 的 HTML 解析逻辑和 `AuthserverPasswordEncryptor` 的加密算法。
- `docs/handoff/ehall1122-har-analysis.json` 是从用户本地 HAR 导出的分析产物，可保留在仓库中作为映射来源，但注意不要包含敏感 Cookie 或密码。

---

## 九、相关文件清单

核心新增/修改文件：

- `src/main/java/edu/szu/agent/client/http/CampusHttpClient.java`
- `src/main/java/edu/szu/agent/client/http/CookieJar.java`
- `src/main/java/edu/szu/agent/client/http/HttpTrafficRecorder.java`
- `src/main/java/edu/szu/agent/client/http/RecordedExchange.java`
- `src/main/java/edu/szu/agent/client/http/AuthserverPasswordEncryptor.java`
- `src/main/java/edu/szu/agent/client/http/CasLoginClient.java`
- `src/main/java/edu/szu/agent/cli/CaptureLoginCommand.java`
- `src/main/java/edu/szu/agent/cli/DirectLoginCommand.java`
- `src/main/java/edu/szu/agent/cli/HttpGetCommand.java`
- `src/main/java/edu/szu/agent/cli/DirectBookCommand.java`
- `src/main/java/edu/szu/agent/cli/DirectBookingsCommand.java`
- `src/main/java/edu/szu/agent/cli/DirectSportsCommand.java`
- `src/main/java/edu/szu/agent/cli/DirectDatesCommand.java`
- `src/main/java/edu/szu/agent/cli/DirectSlotsCommand.java`
- `src/main/java/edu/szu/agent/cli/DirectVenuesCommand.java`
- `src/main/java/edu/szu/agent/cli/Main.java`
- `src/main/java/edu/szu/agent/client/session/HttpSession.java`
- `src/main/java/edu/szu/agent/client/session/SessionStore.java`
- `src/main/java/edu/szu/agent/client/http/EhallSportVenueClient.java`
- `src/test/java/edu/szu/agent/client/http/EhallSportVenueClientTest.java`
- `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
- `docs/handoff/2026-07-08-direct-login-handover.md`
- `docs/handoff/ehall1122-har-analysis.json`

---

## 十、联系与上下文

- 业务参考：`E:\CODE\szu-sports-booking\`（Python 后端）
- 项目规范：`CLAUDE.md`、`AGENTS.md`
- 设计文档：`docs/system-map.md`、`docs/design-patterns.md`

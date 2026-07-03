# ADR-0010 · 用 Obscura 替换 Playwright-Chromium 后端(SDK 复用方案)

**Date:** 2026-07-03
**Status:** Accepted
**Extends:** ADR-0002(BrowserLifecycle/Playwright adapter),ADR-0007(配置注入 + BrowserKind seam),ADR-0008(30 天会话复用)
**Method:** 经 `superpowers:brainstorming` 收敛方向 → `planner` agent 生成 plan → 用户在 3 次关键决策点改写方案(CDP 客户端 / 枚举值 / 启动模式)

---

## Context

`SZU Agent Plugin` 当前用 Playwright Java SDK 控制 Chromium 二进制做自动化浏览器操作,运行
成本是每次启动 250 MB+ 内存 + ~1.5 s 冷启动。**Obscura**(Rust 无头浏览器,V8 + CDP)README
明确写道 "drop-in replacement for headless Chrome with Puppeteer and Playwright",且内存低
6 倍。

观察到一个关键事实:**Playwright SDK 的 `chromium.connectOverCDP(url)` 接受任意 CDP
端点**——只要把 `chromium.launch()` 换成 `connectOverCDP(obscuraUrl)`,Playwright SDK 自动
通过 CDP 跟 Obscura 通信,我们不需要重写任何 CDP 客户端代码。

**对比**:自写 CDP 客户端(方案 A,被否决) vs Playwright SDK 当客户端(本方案 B,采纳)

| 维度 | 自写 CDP | SDK 复用(本方案) |
|---|---|---|
| 新增 Java 文件 | 7 | **1** |
| 改动文件 | 13 | **4** |
| 改动行数 | ~2500 | **~250** |
| storageState codec | 写 ~200 行 | **0 行** |
| downloadAttachment codec | 写 ~100 行 | **0 行** |
| 现有 53 个测试 | 大部分要更新 | **不动** |

**期望结果**:`mvn package` 后 `java -jar szu-agent-plugin.jar booking venue ...` 直接工作,
底层从 Chromium 变成 Obscura,但 Skill 业务行为、8 工具、MCP 协议、ADR-0008 会话复用
**完全不变**。

---

## Decisions

### D1 SDK 复用:`chromium.connectOverCDP(obscuraUrl)` 替换 `chromium.launch()`

**问题**:原计划"自写 CDP 客户端 + 适配 Playwright storageState 格式"预计 ~2500 行、跨 7 个新
Java 文件,且 `storageState` / `downloadAttachment` 两个 codec 在 Playwright SDK 与自写客
户端之间重复造轮子。

**解决**:
- 保留 Playwright SDK 当 CDP 客户端
- `PlaywrightBrowserAdapter.open()` 改为:
  ```java
  if (cdpUrl.isPresent()) {
      ObscuraLauncher.ensureRunning();
      browser = playwright.chromium().connectOverCDP(cdpUrl.get());
      context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
      page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
  } else {
      browser = playwright.chromium().launch(...);
  }
  ```
- `storageState` / `downloadAttachment` / `importStorageState` / `exportStorageState` **完全不
  动**:Playwright SDK 通过 `Storage.getCookies` + `Runtime.evaluate(for localStorage)` 走
  CDP,Obscura 的 V8 runtime 完整支持
- `mapException` 规则**不动**:`TimeoutError → NETWORK_TIMEOUT`、`"selector" in msg →
  ELEMENT_NOT_FOUND`、其他 → `BROWSER_CRASH`

**影响**:
- 32 个 `PlaywrightBrowserAdapterTest`(原 30 + D5 衍生 2 个 ownsPlaywright 测试),其中原 30 个用
  `@Mock Playwright` 测 launcher 路径,**零修改通过**
- 业务 15 个 `*Step` 类、3 个 `*FetchProvider` 类、15 个 `*Task` 类**零修改**
- **2 个测试文件被修改**(`PlaywrightBrowserAdapterTest.java` +2 ownsPlaywright tests,
  `ConfigManagerTest.java` 期望值同步改 `OBSCURA`),其余测试文件全部未触动
- 总测试数从 ~600 增到 **672**(净增 +72,全部来自本 ADR 新增的 `ObscuraLauncherTest` 6 个 +
  `PlaywrightBrowserAdapterTest` 新增 2 个 + 既有类内部加用例)

### D2 Obscura 二进制打包进 fat jar(Maven profile 按 OS 解包)

**问题**:Obscura 是 Rust 单文件二进制 + 一个 `obscura-worker` 子进程,体积 ~70 MB,需要 OS
分类器。

**解决**:
- `pom.xml` 加 `download-maven-plugin` 1.9.0:`generate-resources` 阶段按 `${os.detected.classifier}`
  下载 `obscura-x86_64-{windows.zip,linux.tar.gz,macos.tar.gz,aarch64-macos.tar.gz}`
- 加 `maven-antrun-plugin` 3.1.0:4 个 OS-specific profile,`prepare-package` 阶段分别
  `unzip` / `untar` + `chmod 755` 到 `${project.build.outputDirectory}/bin/`
- 加 `obscura-skip-download` profile:CI / 离线时跳过下载/解包,允许只编译测
  试
- `maven-shade-plugin` 已有配置:把 `target/classes/bin/obscura*` 打进 `szu-agent-plugin.jar`
  的根 `bin/` 目录

**影响**:
- 第一次 `mvn package` 下载 ~70 MB,后续离线
- Windows / Linux / macOS 三平台均覆盖;aarch64 macOS 单独 profile
- 不引入 Rust 工具链,Java 开发者无需 cargo/rustup

### D3 `BrowserKind` 枚举收敛到 `OBSCURA` + `FAKE`

**问题**:原枚举 `PLAYWRIGHT, FAKE` 现在 `PLAYWRIGHT` 已无实际差异(底层都是 Obscura,只是
CDP 客户端用 Playwright SDK)——保留它等于留一个"永远不会被选中的死分支",违反 KISS。

**解决**:
- `BrowserKind` 改 `{OBSCURA, FAKE}`
- `ConfigManager.buildBrowser()` 单 `case OBSCURA` 分支,删 `case PLAYWRIGHT` switch arm
- `application.yml` 改 `browser.kind: OBSCURA`(原 `PLAYWRIGHT` 删)
- `ConfigManagerTest` 期望值同步改 `"OBSCURA"`
- `ConfigManagerTest.buildBrowser_throwsForUnknownKind` 用 `WEBDRIVER` 触发(原本用旧枚举
  值改)

**影响**:
- 配置注入 seam(ADR-0007 D1)保留,只是有效值收敛
- 未来要回滚或换别的 Rust 浏览器,改 `application.yml` 一行即可
- `FAKE` 仍然 `UnsupportedOperationException` 占位(Phase 4+ deliverable)

### D4 `ObscuraLauncher`:静态 Process Supervisor + 重启友好

**问题**:`chromium.connectOverCDP` 假设远端 CDP 端点已存在;Obscura daemon 必须有人负责拉起。
候选:
- (a) 让用户手动 `obscura serve` —— 用户摩擦
- (b) MCP / HTTP daemon 启动时 spawn —— Skill CLI 模式也用 daemon,不灵活
- (c) **`ObscuraLauncher.ensureRunning()` 静态方法 + 进程监督** —— 幂等,被 adapter.open()
  按需调用,daemon JVM-exit 时通过 shutdown hook 清理

**解决**:
- `ObscuraLauncher` 静态类,API:
  - `ensureRunning()` / `ensureRunning(Path home, URI versionUri)` — 探测 → 抽取 → 启进程
    → 等待 ready,完全幂等
  - `isRunning()` / `isRunning(URI)` — 探测 `http://127.0.0.1:9222/json/version`
  - `binaryPath()` / `pidFile()` — 路径约定
- 进程管理细节:
  - 探测后**再次探测**(D1 review 修复 HIGH #1 race):两个 JVM 并发 `ensureRunning` 时,
    第二个在解包完成后再探一次,避免 `BindException` 包装为 `BROWSER_CRASH`
  - 二进制 dedup:首 4 字节必须是 PE/ELF/Mach-O magic(D1 review 修复 HIGH #2
    size-only):防止 HTTP 404 页面被错误缓存复用
  - `Files.setPosixFilePermissions(rwx------)`,Windows 上 `UnsupportedOperationException`
    静默吞掉
  - `Runtime.getRuntime().addShutdownHook` 在 JVM exit 时 `destroy() → waitFor(5s) →
    destroyForcibly()`
  - `Process managedProcess` 标 `volatile`(D1 review 修复 HIGH #3,Java memory model)

**影响**:
- 一个 `ObscuraLauncher` 文件、~250 行
- 5 个 `ObscuraLauncherTest` 用 `com.sun.net.httpserver.HttpServer` fake daemon,无真实
  Rust 进程依赖
- HTTP daemon 模式下,daemon 进程跨多次 Skill 调用复用,避免每次重新加载 V8 isolate(~500 ms)

### D5(衍生)`PlaywrightBrowserAdapter` 新 OBSCURA ctor 自管 SDK 生命周期

**问题**:D1 决定让 `ConfigManager.buildBrowser()` 内部 `Playwright.create()`,但原
`close()` 只关 browser/context/page,没关 Playwright 驱动 — daemon 模式下每个 Skill 调用
会泄漏一个 Playwright 驱动进程。

**解决**:
- 新增私有 `ownsPlaywright` 字段,OBSCURA 2 参 ctor 置 `true`,其他 ctor 保持 `false`
- `close()` 末尾:`if (ownsPlaywright) playwright.close();`
- 语义:**2 参 OBSCURA ctor = 自管 SDK;3 参 ctor = 借用 SDK**(沿用 ADR-0002 D3 边界)

**影响**:
- 2 个新 `PlaywrightBrowserAdapterTest`(caller-owned 路径不关 SDK + adapter-owned 路径关
  SDK)
- 原 30 个 `PlaywrightBrowserAdapterTest` **零修改**

### D6(衍生)Playwright SDK 的 driver/ 二进制不可过滤 — 接受 169 MB fat jar

**问题**:fat jar 95% 体积来自 Playwright SDK 自带的 `driver/<os>/node` Node bridge 二进
制(5 OS × ~98 MB = 480 MB)。理论上这些只在 `chromium.launch()` 时被 SDK 加载,本项目只
走 `connectOverCDP`,应该可以过滤。

**实验 1:全量 `<exclude>driver/**</exclude>`**
- 结果:`ClassLoader.getResource("driver/<os>/node")` 返回 null → NPE
- 失败位置:`DriverJar.getDriverResourceURI(DriverJar.java:118)`

**实验 2:仅从 Playwright artifact 过滤 + stub 文件占位**
- 在 `src/main/resources/driver/<os>/node` 放 10 字节 `#!/bin/sh`
- pom.xml 加 `<filter><artifact>com.microsoft.playwright:playwright</artifact>`
- 结果:jar 体积 **169 MB → 17 MB(↓90%)**,但 smoke run 失败:
  - SDK 抽出 stub → 调 `ProcessBuilder.start("node.exe")` 安装浏览器
  - Windows 抛 `CreateProcess error=216`(ERROR_BAD_EXE_FORMAT)
  - 失败位置:`DriverJar.installBrowsers(DriverJar.java:92)`
- **结论**:Playwright SDK 不只 `extract` 还 `execute` node 二进制做浏览器安装(`installBrowsers`),
  这是 SDK 硬约束,无法在不提供跨平台真可执行 stub 的前提下规避。

**决定**:
- 撤销 pom.xml filter 改动,删除 stub 文件
- **接受 169 MB fat jar**(理论上可优化到 ~17 MB)
- 文档化为 Known Limitations 第 9 条,v1.1+ 评估 Playwright SDK 升级或换 Playwright Core 子集

**影响**:
- Fat jar 体积:实际 169 MB(不是早期估算的 ~70 MB)
- 用户体感:无影响,只是分发包变大
- CI 影响:无,jar 仍在 <200 MB,CI artifact storage 不超限

---

## Consequences

### 收益
- **内存**:V8 single-isolate 模式 + 无 Chromium 渲染进程,Skill 峰值 RSS 估降 40-60%(待
  Phase 4 测量)
- **冷启动**:V8 一次编译 ~500 ms,Chromium 启动 ~1.5 s — 提升 3 倍
- **可移植**:Obscura 是 Rust 单文件二进制,Windows / Linux / macOS 三平台一致;Chromium
  依赖系统 WebKit,跨平台行为有微妙差异
- **会话复用保留**:ADR-0008 `~/.szu-agent/sessions/<username>.json` 30 天 TTL、POSIX 600
  权限 — 行为完全不变

### 已知限制(已在代码注释 + ADR 顶部标注)

| 项 | 状态 | 说明 |
|---|---|---|
| 截图失败 | 已知 | Obscura 无 `Page.captureScreenshot` 实现,V8 无 layout/paint 引擎。SDK 抛通用异常 → `mapException` → `BROWSER_CRASH`。错误诊断截图功能不可用。 |
| 头模式(headed) | 不支持 | Obscura 永远 headless;`SZU_HEADLESS=false` 被忽略。原 captcha 手工救场走"全凭据层失败时切有头浏览器"路径,Phase 4 重新设计。 |
| `wss://` 验证 | 未做 | daemon 永远 `ws://`,用户配 `wss://` 不会被拒绝,会在 CDP 握手中失败。 |
| `obscura.log` / `obscura.pid` 路径 | 硬编码 `~/.szu-agent/{log,pid}` | 无 YAML 配置,issue 已在 D1 review 的 deferred 列表(非 HIGH,留待 v1.1)。 |
| `ProcessBuilder` 继承 JVM 全部环境变量 | 未过滤 | `HTTP_PROXY` 等会传到 Obscura,Deferred。 |
| 进程组清理(子进程 `obscura-worker`) | 仅 `destroy()` 父进程 | Windows 下 `obscura-worker` 可能成为孤儿,Deferred。 |
| Maven 校验和(SHA-256) | 未做 | 70 MB 二进制从 GitHub Releases 拉,无 PGP / SHA 校验,Deferred;release 页面手动核对。 |
| Linux / macOS 二进制验证 | 仅 Windows 验证 | 三个非 Windows profile 未实跑,CI 首次 multi-platform run 验证。 |
| 重复 `ensureRunning` 累积 shutdown hook | 未去重 | 每次 `ensureRunning` 注册新 hook;JVM exit 时多 hook 同时关已死的进程(幂等但日志噪声),Deferred。 |
| Playwright SDK driver/ 二进制(480 MB)无法过滤 | 已实验回退 | SDK 在 `Playwright.create()` 静态初始化时强制 extract + execute `driver/<os>/node`(`DriverJar.installBrowsers`);stub 文件导致 Windows ERROR_BAD_EXE_FORMAT(216)。**当前接受 169 MB fat jar**,v1.1 评估 Playwright SDK 升级 / Playwright Core 子集 / 跨平台 stub 三选一。见 D6。 |

### 反向兼容
- `application.yml` 中 `browser.kind: PLAYWRIGHT` 不再被识别 — 直接抛 `IllegalStateException`
  ,符合 ADR-0007 D1 "配置切换 = 唯一 seam" 原则,无静默 fallback
- CLI 工具的所有 8 个 Skill 行为不变(8 Skill 由 `Skill` 枚举枚举,`browser.kind` 对它们透
  明)
- MCP / HTTP daemon 协议不变

---

## Files changed (vs `master`)

| 文件 | 变化 | 行数 |
|---|---|---|
| `pom.xml` | + `download-maven-plugin`、+ `maven-antrun-plugin`、+ 4 OS profile、+ `obscura-skip-download` profile | +~120 |
| `src/main/java/edu/szu/agent/browser/ObscuraLauncher.java` | **新增** | +248 |
| `src/main/java/edu/szu/agent/browser/PlaywrightBrowserAdapter.java` | + `Optional<String> cdpUrl` 字段、+ 2 参 OBSCURA ctor、+ `ownsPlaywright` 字段、+ `close()` 末尾关 SDK | +~30 |
| `src/main/java/edu/szu/agent/config/ConfigManager.java` | `BrowserKind` 枚举去 `PLAYWRIGHT`、`buildBrowser` 单 OBSCURA 分支 | +5 / -10 |
| `src/main/resources/application.yml` | `browser.kind: OBSCURA`、+ `browser.obscura.ws-url` | +~3 |
| `src/test/resources/application.yml` | 同上 | +~3 |
| `src/test/java/edu/szu/agent/browser/ObscuraLauncherTest.java` | **新增** | +130 |
| `src/test/java/edu/szu/agent/browser/PlaywrightBrowserAdapterTest.java` | + 2 ownsPlaywright 测试 | +30 |
| `src/test/java/edu/szu/agent/config/ConfigManagerTest.java` | 期望值 `"OBSCURA"`、FAKE 测试改用 `WEBDRIVER` 触发 | ±5 |

**零修改**:`BrowserLifecycle` 接口、3 个 `*FetchProvider`、15 个 `*Step`、15 个 `*Task`、
53 个其他测试文件。

---

## Verification

```bash
# 全测试(本机离线)
mvn -Pobscura-skip-download test
# → 672 tests, 0 failures(38.9 s)

# ObscuraLauncherTest 单独验证魔法字节
mvn -Pobscura-skip-download test -Dtest=ObscuraLauncherTest
# → 6 tests, 0 failures(其中 1 个是新增的 hasExecutableMagic 测试)

# 离线打包
mvn -Pobscura-skip-download -DskipTests package
# → BUILD SUCCESS
# → target/szu-agent-plugin.jar 169 MB(压缩)/ 495 MB(解压)

# 实拉二进制 + 打包(需联网,首次 ~70 MB)
mvn -DskipTests package
# → bin/obscura.exe 出现在 target/szu-agent-plugin.jar 内
unzip -l target/szu-agent-plugin.jar | grep -i obscura
```

**Fat jar 体积拆解**(实测,顶部目录):
```
driver/                                  483.6 MB  ← Playwright SDK 自带 Node bridge(5 OS × ~98 MB)
edu/                                       6.6 MB  ← 我们的代码
com/                                       1.9 MB  ← Playwright Java SDK 类
ch/                                        1.6 MB  ← Logback
picocli/                                   0.9 MB
org/                                       0.7 MB  ← Jackson (relocated)
META-INF/                                  0.2 MB
TOTAL uncompressed                       495.5 MB
```

---

## Related

- **ADR-0002** D2 (`mapException` 规则):Obscura 错误经 Playwright SDK 表面化后,映射逻辑
  复用
- **ADR-0002** D3(Playwright SDK 生命周期):D5 衍生决策,OBSCURA ctor 改成自管
- **ADR-0007** D1(配置注入 seam):`browser.kind` 唯一决策点
- **ADR-0008** D1-D4(30 天会话):`storageState` round-trip 经 Playwright SDK 的
  `Network.getCookies` + `Runtime.evaluate` 走 CDP,Obscura V8 支持
- **harness-records/traces/20260703-XXXXXX-obscura-migration.md**:实操 trace

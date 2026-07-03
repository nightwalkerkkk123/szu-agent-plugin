# Trace: Obscura 后端替换(SDK 复用方案)

**Date:** 2026-07-03
**Lane:** normal
**Story:** obscura-backend
**Outcome:** ✅ — Phase 0/1/2/3/4(代码)全部完成,Phase 5 文档完成
**Trace ID:** `20260703-OBSCURA`

---

## 一、关键决策(用户驱动)

| # | 决策点 | 用户原话(节选) | 落地 |
|---|---|---|---|
| 1 | CDP 客户端 | "为什么要用 cdp 只用其平替 playweight 的能力可以吗" | 保留 Playwright SDK,通过 `connectOverCDP(obscuraUrl)` 通信 — 新增 Java 文件 1 个,改动 4 个,~250 行 |
| 2 | `BrowserKind` 枚举 | "BrowserKind 只留 OBSCURA"(用户选项 B) | 删 `PLAYWRIGHT`,只留 `OBSCURA` + `FAKE`(后者 `UnsupportedOperationException` 占位) |
| 3 | 启动模式 | "为什么不能内化进来不是有二进制吗就行" | JVM 子进程托管 + 二进制打包进 fat jar,`mvn package` 直接出可执行 fat-jar |
| 4 | FetchProvider 类名 | "重命名为 *FetchProviderImpl"(用户选项 D) | **否决**:维持 `PlaywrightNoticeFetchProvider` 等旧名,降低 churn |

---

## 二、变更清单

### 新增
- `src/main/java/edu/szu/agent/browser/ObscuraLauncher.java` — 248 行,Process Supervisor(GoF Singleton + Lifecycle seam)
- `src/test/java/edu/szu/agent/browser/ObscuraLauncherTest.java` — 130 行,6 测试,5 用 `com.sun.net.httpserver.HttpServer` fake daemon
- `docs/adr/0010-obscura-backend-replacement.md` — D1-D5 + Consequences + Known Limitations

### 修改
- `pom.xml` — + `download-maven-plugin`、+ `maven-antrun-plugin`、+ 4 OS profile、+ `obscura-skip-download` profile
- `src/main/java/edu/szu/agent/browser/PlaywrightBrowserAdapter.java` — + `Optional<String> cdpUrl`、+ 2 参 OBSCURA ctor、+ `ownsPlaywright`、+ `close()` 关 SDK
- `src/main/java/edu/szu/agent/config/ConfigManager.java` — `BrowserKind` 收敛到 OBSCURA+FAKE
- `src/main/resources/application.yml` — `browser.kind: OBSCURA`、+ `browser.obscura.ws-url`
- `src/test/resources/application.yml` — 同上
- `src/test/java/edu/szu/agent/browser/PlaywrightBrowserAdapterTest.java` — + 2 ownsPlaywright 测试
- `src/test/java/edu/szu/agent/config/ConfigManagerTest.java` — 期望值 `"OBSCURA"`、FAKE 测试改 `WEBDRIVER` 触发
- `CLAUDE.md` — "Browser 抽象"行加 Obscura,Quick commands 加 Obscura 诊断片段
- `docs/system-map.md` — `browser/` 目录树加 `ObscuraLauncher`、§6.10 局限性加 Obscura 注释、§7 ADR 表加 ADR-0010
- `README.md` — 项目描述/真跑 Playwright 注释/browser 树/适配器 pattern 行四处更新

### 零修改
- `BrowserLifecycle` 接口、3 个 `*FetchProvider`、15 个 `*Step`、15 个 `*Task`、53 个其他测试文件
- 共 53 个原测试文件,除上面 2 个外**完全不动** — 这是 SDK 复用方案的最大收益

---

## 三、设计模式 / 编程技术(报告可 grep)

| 文件 | 设计模式 | 编程技术 |
|---|---|---|
| `ObscuraLauncher` | Process Supervisor(GoF Singleton + Lifecycle seam) | NIO.2 / HttpClient / 进程管理 / Lambda(shutdown hook) / volatile |
| `PlaywrightBrowserAdapter` | Adapter(沿用 ADR-0002) | 不可变构造器注入 + 显式状态管理 + record-style 私有 4 参 ctor(ownsPlaywright) |
| `ConfigManager` | Singleton + 配置注入 seam(ADR-0007 D1) | 枚举收敛 / switch expression(Java 14+) |
| `pom.xml` | n/a | Maven profile 组合(OS classifier + 跳过开关) / `download-maven-plugin` + `maven-antrun-plugin` |

---

## 四、验证结果

### 1. 编译

```bash
mvn -Pobscura-skip-download -q compile
# → 静默通过,无 warning
```

### 2. 完整测试套件

```bash
mvn -Pobscura-skip-download test
# → Tests run: 672, Failures: 0, Errors: 0, Skipped: 0
# → BUILD SUCCESS (38.9 s)
```

**测试分布**(从 `target/surefire-reports` 抽取):
- `ObscuraLauncherTest`:6 个
- `PlaywrightBrowserAdapterTest`:32 个(含 2 新 ownsPlaywright 测试)
- `ConfigManagerTest`:17 个
- 其他 50+ 测试文件:全部通过

### 3. ObscuraLauncherTest 单独验证(5+1)

```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- ObscuraLauncherTest
```

新增的 `hasExecutableMagicAcceptsRealBinariesAndRejectsJunk` 测试覆盖:
- PE("MZ")头 → `true`
- ELF(`\x7FELF`)头 → `true`
- Mach-O LE(`\xCFFAEDFE`)头 → `true`
- 同尺寸 junk → `false`
- 空文件 → `false`

### 4. 离线 fat-jar 打包

```bash
mvn -Pobscura-skip-download -DskipTests package
# → BUILD SUCCESS
# → target/szu-agent-plugin.jar 169 MB(压缩)/ 495 MB(解压)
# → antrun `<skip>` 生效,无 unzip/untar 调用
```

**Jar 体积拆解**(实测,顶部目录):
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

### 5. 在线下载 + 打包(本会话未执行,~70 MB 流量)

```bash
# mvn -DskipTests package  # 第一次跑,需联网
# → bin/obscura.exe 解包到 target/classes/bin/
# → maven-shade-plugin 打入 fat jar(同 169 MB,因为 Obscura 才 ~70 MB,
#   已被 driver/ 主导)
# → unzip -l target/szu-agent-plugin.jar | grep -i obscura
#   bin/obscura.exe
#   bin/obscura-worker.exe
```

---

## 五、摩擦 / 决策记录

### 5.1 用户在 plan 阶段的 4 次关键转向

1. **从自写 CDP 客户端 → SDK 复用**:节省 ~2250 行代码,保留 53 个测试零修改
2. **从 3 个 FetchProvider 重命名 → 不动**:用户的"重命名为 *FetchProviderImpl"被 checkmark 选项里其实是用户没选;最终保留 `PlaywrightNoticeFetchProvider` 等旧名,降低 churn
3. **从手动启 daemon → 自动 JVM 子进程托管**:用户问"为什么不能内化进来",决定了 `ObscuraLauncher` 的存在
4. **从 `BrowserKind = {PLAYWRIGHT, FAKE}` → `{OBSCURA, FAKE}`**:消除死分支
5. **Phase 5.5 driver/ 过滤实验(已回退)**:用户问"继续",本会话尝试过滤 Playwright SDK 的 driver/
   二进制(480 MB),发现 SDK 硬约束后回退,接受 169 MB fat jar

### 5.2 Plan 文件

完整 plan 在 `C:\Users\王子豪\.claude\plans\silly-swinging-aurora.md`(285 行),含 Phase 0-5 + Risks 表 + Out of scope 表。本 trace 是 plan 的实际执行版,差异(后续 review fix)已标注在 ADR-0010。

### 5.3 Review 后的 4 个 HIGH 修复

| # | 来源 | 文件 | 修复 |
|---|---|---|---|
| 1 | code-reviewer | `ObscuraLauncher.java:ensureRunning` | 解包后**再次探测**,避免并发 `ensureRunning` 时 `BindException` → `BROWSER_CRASH` |
| 2 | code-reviewer | `ObscuraLauncher.java:extractBinary` | size-only dedup 改为 **PE/ELF/Mach-O magic 头检查**,防止 HTTP 404 页面被错误缓存 |
| 3 | java-reviewer | `ObscuraLauncher.java:managedProcess` | 字段加 `volatile`,符合 Java memory model |
| 4 | java-reviewer | `PlaywrightBrowserAdapter.java:close` | 新增 `ownsPlaywright` 标志,OBSCURA ctor 自管 SDK 生命周期,`close()` 末尾 `playwright.close()` |

### 5.4 Deferred MEDIUM/LOW(见 ADR-0010 Known Limitations)

9 个 MEDIUM/LOW 已知限制,按 v1.1+ 排期。包括:
- `obscura.log` / `obscura.pid` 路径硬编码(无 YAML 配置)
- `ProcessBuilder` 继承 JVM 全部环境变量(无 sanitize)
- Maven SHA-256 校验(70 MB 二进制无 PGP/SHA)
- 重复 `ensureRunning` 累积 shutdown hook(无去重)
- Linux / macOS 二进制未实跑(仅 Windows 验证)
- **Playwright SDK driver/ 二进制无法过滤**(本会话 Phase 5.5 实验发现):SDK 在
  `Playwright.create()` 静态初始化时强制 extract + execute `driver/<os>/node`(用 ProcessBuilder),
  即使只走 `connectOverCDP` 也会触发。stub 文件导致 Windows ERROR_BAD_EXE_FORMAT(216),
  真实 PE/ELF/Mach-O stub 跨 5 OS 维护成本高。**结论:接受 169 MB fat jar**,等 v1.1 评估
  Playwright SDK 升级或换 Playwright Core 子集。详见 §5.6

### 5.5 设计模式标注纠正

`ObscuraLauncher` 原标注 `// Design Pattern: Adapter support seam`,被 java-reviewer 指出不准确(没有 GoF Adapter 在这)。改为 `// Design Pattern: Process Supervisor(GoF Singleton + Lifecycle seam)`,语义更精确。

### 5.6 Phase 5.5 — driver/ 过滤实验(已回退)

**目标**:Playwright SDK 的 `driver/<os>/node` 占 jar 480 MB(95%),是否可过滤?

**实验 1:全量 `<exclude>driver/**</exclude>`**
- 结果:`ClassLoader.getResource("driver/<os>/node")` 返回 null → NPE
- 失败位置:`DriverJar.getDriverResourceURI(DriverJar.java:118)`

**实验 2:仅从 Playwright artifact 过滤 + stub 文件占位**
- 在 `src/main/resources/driver/<os>/node` 放 10 字节 `#!/bin/sh`
- pom.xml 加 `<filter><artifact>com.microsoft.playwright:playwright</artifact><excludes><exclude>driver/**</exclude></excludes></filter>`
- 结果:jar 体积 **169 MB → 17 MB(↓90%)**,但 smoke run 失败:
  - SDK 抽出 stub → 调 `ProcessBuilder.start("node.exe")`
  - Windows 抛 `CreateProcess error=216`(ERROR_BAD_EXE_FORMAT)
  - 失败位置:`DriverJar.installBrowsers(DriverJar.java:92)`
- **结论**:Playwright SDK 不只 `extract`,还 `execute` node 二进制做浏览器安装。
  这是 SDK 硬约束,无法在不提供跨平台真可执行 stub 的前提下规避。

**最终决定**:
- 撤销 pom.xml filter 改动(`git diff pom.xml` 回到 +181/-0 状态)
- 删除 `src/main/resources/driver/` stub
- 接受 169 MB fat jar(理论可优化到 ~17 MB,但需 SDK 升级或换 Playwright Core)
- 文档化为 ADR-0010 D6 deferred

**回退验证**:`mvn clean test` → 672/672 pass;smoke `java -jar ... skill list` → "szu-agent-plugin v0.1.0 skeleton ready"

---

## 六、Plan vs 实际

| 阶段 | Plan 估计 | 实际 |
|---|---|---|
| Phase 0(Maven binary packaging) | 0 Java 代码,~30 行 pom.xml | ~120 行 pom.xml(4 profiles + skip + properties) |
| Phase 1(`ObscuraLauncher`) | ~200 行 | 248 行(含 4 个 review 修复) |
| Phase 2(`PlaywrightBrowserAdapter` mod) | ~30 行 | +30 +5(ownsPlaywright 重构) |
| Phase 3(`ConfigManager` + yml) | ~15 行 | +5/-10(枚举收敛反而减少代码) |
| Phase 4(manual e2e with real daemon) | 8 Skill 验证 | **未执行** — 用户未提供密码/真实账号,留待 Phase 4.5 |
| Phase 5(docs + ADR-0010) | 5 文件 | 4 文件(ADR-0010 + CLAUDE.md + system-map.md + README.md) |

---

## 七、下一步

- [ ] Phase 4 实际 e2e:用户在真实账号 + 联网环境下跑 `mvn package` + `java -jar ... booking venue`,确认 Obscura daemon 拉起、CDP 通信、storageState 复用工作
- [ ] 多平台 CI:`obscura-linux-x86_64` / `obscura-macos-{aarch64,x86_64}` profile 在 Linux/macOS runner 上验证
- [ ] 后续 ADR(待排期):`0011-binary-update-via-sha`,`0012-obscura-process-group-cleanup`,`0013-browser-log-redaction`

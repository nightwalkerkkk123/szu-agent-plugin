# ADR-0002 · BrowserLifecycle 接口与 Playwright 适配

**Date:** 2026-06-11
**Status:** Accepted
**Extends:** ADR-0001 D9(Adapter 模式), ADR-0006 §二(error 元数据)

---

## Context

Phase 1 完成后,业务编排(task/ + client/)需要调用浏览器完成 ehall 操作。直接调
Playwright Java SDK 会让业务代码:

1. 紧耦合 Playwright 的 fluent API(`page.locator(sel).click()`),业务层堆链式调用
2. 异常类型分散(`TimeoutError` / `Error` / `PlaywrightException` 等),业务层 catch
   逻辑爆炸
3. 无法用 FakeBrowser 写单测(Playwright 强依赖真浏览器二进制)

本 ADR 定义 **BrowserLifecycle 接口**(10 个方法,业务侧直接调)+ **PlaywrightBrowserAdapter
实现**(包装 Playwright SDK,异常映射到 ErrorCode)+ **测试策略**(用 FakePlaywright 测
Adapter,业务层用 FakeBrowser 测,都不引真浏览器)。

---

## Decisions

### D1 接口方法清单(10 个,Playwright fluent API "减法")

```java
public interface BrowserLifecycle {

    /** 启动 headless Chromium + 创建 page. */
    void open();

    /** 关闭 page → browser → playwright 顺序. */
    void close();

    /** 导航到 URL,等待 load 状态. */
    void navigateTo(String url);

    /** 点击元素,等待可点. */
    void click(String selector);

    /** 填 input. */
    void fill(String selector, String value);

    /** 元素是否可见. */
    boolean isVisible(String selector);

    /** 单元素文本. */
    String textOf(String selector);

    /** 多元素文本(场地列表用). */
    List<String> allTextOf(String selector);

    /** 当前 URL(CAS 跳转判断用). */
    String currentUrl();

    /** 截图到绝对路径(ErrorCode.shouldScreenshot=true 时调用). */
    void screenshot(String absolutePath);
}
```

**为什么这 10 个**:
- 登录 / 选场地 / 提交按钮 / 取场地列表 — 这 4 类操作覆盖预约全流程
- `allTextOf` + Phase 1 的 `VenueIndexMatcher` 配合,完成"读场地列表 + 按 N 号过滤"
- `currentUrl` 唯一不可替代:登录跳转链 CAS → ehall,业务层需要确认是否到了目标页
- `screenshot` 由 `ErrorCode.shouldScreenshot()` 元数据驱动(ADR-0006 §二.2)

**为什么去掉**:hover / doubleClick / keyboard.press / drag / fileChooser 等 — P0
业务预约流不用,YAGNI。后续要加,**只动这一个接口文件**。

### D2 异常策略 — 直接抛 BookingException

BrowserLifecycle 方法签名上抛 `BookingException`(unchecked)。Adapter 内部按
Playwright 异常类型映射到 ErrorCode:

| Playwright 异常 | ErrorCode | retryable | screenshot |
|---|---|---|---|
| `playwright.TimeoutError` | `NETWORK_TIMEOUT` | true | false |
| `playwright.Error` (selector 找不到) | `ELEMENT_NOT_FOUND` | true | true |
| `playwright.Error` (其他) | `BROWSER_CRASH` | true | true |
| 登录相关(P0 业务侧 catch) | 业务层包 | — | — |

**为什么直接抛而非 IOException**:
- ADR-0006 §2.4:"业务层 catch Exception 必须 wrap 成 BookingException,不裸异常飘上去"
- Adapter 已是业务边界最后一层,内部包好,业务层 1 处 catch 即可
- 业务代码不再写 `try { ... } catch (IOException e) { wrap(e) }`,只写业务逻辑

**耦合**:`browser/` → `error/` 单向依赖。`error/` 不反向依赖,合规。

### D3 Playwright 注入 — 构造器注入

```java
public final class PlaywrightBrowserAdapter implements BrowserLifecycle {
    private final Playwright playwright;

    public PlaywrightBrowserAdapter(Playwright playwright) {
        this.playwright = Objects.requireNonNull(playwright, "playwright");
    }
    // ...
}
```

- 业务层 / ConfigManager 负责 new Playwright() 并传入
- 测试用 FakePlaywright 替换,无需修改 Adapter
- 生命周期由调用方管理,Adapter 只用不创建

### D4 open() 默认 headless=true

```java
public void open() {
    Browser browser = playwright.chromium().launch(
        new BrowserType.LaunchOptions().setHeadless(true));
    this.page = browser.newPage();
}
```

P0 默认 headless,演示用真窗口(Phase 4 CLI 加 `--headed` flag 切换)。不读
ConfigManager(Phase 3 才建 ConfigManager,YAGNI)。

### D5 测试策略 — TDD with FakePlaywright

**Phase 2 范围**:接口 + Adapter 实现 + **单元测试**(用 FakePlaywright)。

**FakePlaywright** 设计:
- 放在 `src/test/java/edu/szu/agent/browser/FakePlaywright.java`
- Mock Playwright SDK 的 `Browser` / `Page` / `Locator` 接口(用 Mockito spy 真实
  Playwright 实例,或手写 minimal stub)
- 测试断言 "Adapter 把 click 转到 page.locator(sel).click()" 而非真点击

**为什么不用真 Playwright**:
- CI 跑 `playwright install chromium` 大且慢(P0 没必要)
- 单测应快(当前 86 测试 < 1s,真浏览器会拖到分钟级)
- e2e 在 Phase 3-4 跑,届时单独隔离

**Adapter 单测覆盖**:
- 每个方法的"happy path 转发"(verify 调用了 FakePlaywright 对应方法)
- 每个方法的"异常映射"(FakePlaywright 抛 TimeoutError → verify Adapter 抛
  BookingException(NETWORK_TIMEOUT))
- `open()` / `close()` 生命周期顺序
- 暂不覆盖"selector 找不到"等真实 Playwright 错误路径(那要 mock Playwright 内部)

**业务层测试**:Phase 3 引入 `FakeBrowser implements BrowserLifecycle`,业务侧用
它测,无需 Adapter 参与。

---

## Consequences

### 好处
- **业务层零 Playwright 依赖**:Phase 3 起的 `task/` / `client/` 只 import `browser/`
  接口,不 import `com.microsoft.playwright.*`
- **异常策略统一**:业务层一个 `catch (BookingException e)` 即可,根据
  `e.code().isRetryable()` 决定重试,根据 `e.code().shouldScreenshot()` 决定截图
- **可测试**:FakePlaywright 让 Adapter 单测在 < 1ms 内完成,无外部依赖
- **演示时 0 摩擦**:`open()` 默认 headless=true,演示不弹窗;Phase 4 加 `--headed` 时
  仅改 `open()` 一处

### 代价 / 风险
- **Adapter 单测价值有限**:FakePlaywright 只能验证"调用了哪个方法",不能验证
  "真浏览器行为对不对"。真 ehall DOM 行为需 Phase 3-4 e2e 覆盖
- **Playwright SDK 升级脆弱**:Playwright 大版本升级可能改 API,Adapter 是唯一冲击点
  (这正是 Adapter 模式的价值)
- **"10 个方法"是 P0 契约**:后续要加,先更新本 ADR 的 D1,再 TDD 新方法,不要在
  TDD 过程中扩方法

### 风险缓解
- Adapter 单测**覆盖异常映射**(最高 ROI,Adapter 唯一的纯逻辑)
- Phase 3-4 e2e 用真 Playwright 跑 happy path,验证 FakePlaywright 假设与现实一致

---

## 实施路径(Phase 2,1.0d,严格 TDD)

```
Cycle 1  open() + close()  — 验证 FakePlaywright 整套可工作
Cycle 2  navigateTo(url)   — 验证异常映射(TimeoutError → NETWORK_TIMEOUT)
Cycle 3  click(selector)   — 验证 ELEMENT_NOT_FOUND 映射
Cycle 4  fill(selector, value)
Cycle 5  isVisible(selector)
Cycle 6  textOf(selector)
Cycle 7  allTextOf(selector)
Cycle 8  currentUrl()
Cycle 9  screenshot(path)
Cycle 10 package-info 收尾
```

每个 Cycle:RED(witness 编译失败)→ GREEN(最小实现)→ REFACTOR。Cycle 1-3
最关键(建立 FakePlaywright 模式);Cycle 4-9 重复同一模板。

---

## 引用

- ADR-0001 D9(Adapter 模式)
- ADR-0006 §二(error 元数据 + LogMasker)
- ADR-0007 D9(删 BrowserFactory / Static Factory)
- Playwright Java SDK 1.45.0 docs

package edu.szu.agent.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PlaywrightBrowserAdapter} lifecycle methods.
 *
 * <p>Per ADR-0002 D1: open() launches headless Chromium + creates a page;
 * close() releases page → browser. The Playwright instance itself is not
 * closed by the adapter — lifecycle is owned by the caller (Phase 4 Main).
 *
 * <p>Per ADR-0002 D5: uses Mockito mocks for Playwright SDK interfaces,
 * no real browser binary required.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlaywrightBrowserAdapter — lifecycle")
class PlaywrightBrowserAdapterTest {

    @Mock Playwright playwright;
    @Mock BrowserType browserType;
    @Mock Browser browser;
    @Mock Page page;

    @Test
    @DisplayName("open() launches a headless Chromium and creates a new page")
    void openLaunchesHeadlessBrowserAndCreatesPage() {
        when(playwright.chromium()).thenReturn(browserType);
        when(browserType.launch(any())).thenReturn(browser);
        when(browser.newPage()).thenReturn(page);

        PlaywrightBrowserAdapter adapter = new PlaywrightBrowserAdapter(playwright);
        adapter.open();

        // Verify headless=true was requested
        ArgumentCaptor<BrowserType.LaunchOptions> optsCaptor =
            ArgumentCaptor.forClass(BrowserType.LaunchOptions.class);
        verify(browserType).launch(optsCaptor.capture());
        assertThat(optsCaptor.getValue().headless)
            .as("open() must launch headless=true (per ADR-0002 D4)")
            .isTrue();

        // Verify a page was created
        verify(browser).newPage();
    }

    @Test
    @DisplayName("close() releases page first, then browser (in order)")
    void closeReleasesPageThenBrowser() {
        when(playwright.chromium()).thenReturn(browserType);
        when(browserType.launch(any())).thenReturn(browser);
        when(browser.newPage()).thenReturn(page);

        PlaywrightBrowserAdapter adapter = new PlaywrightBrowserAdapter(playwright);
        adapter.open();
        adapter.close();

        InOrder order = inOrder(page, browser);
        order.verify(page).close();
        order.verify(browser).close();
    }

    @Test
    @DisplayName("close() is a no-op when open() was never called")
    void closeWithoutOpenIsNoOp() {
        PlaywrightBrowserAdapter adapter = new PlaywrightBrowserAdapter(playwright);

        // No exception, no interactions with Playwright SDK
        adapter.close();

        verify(playwright, org.mockito.Mockito.never()).chromium();
    }
}

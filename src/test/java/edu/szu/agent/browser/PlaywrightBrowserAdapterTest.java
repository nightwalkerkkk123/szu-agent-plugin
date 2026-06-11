package edu.szu.agent.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PlaywrightBrowserAdapter}.
 *
 * <p>Per ADR-0002 D1-D5, with one TDD cycle per method. Currently covers
 * Cycles 1-2: lifecycle (open/close) and navigation (navigateTo).
 *
 * <p>Per ADR-0002 D5: uses Mockito mocks for Playwright SDK interfaces
 * and the real {@code com.microsoft.playwright.TimeoutError} class
 * for timeout mapping tests.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlaywrightBrowserAdapter")
class PlaywrightBrowserAdapterTest {

    @Mock Playwright playwright;
    @Mock BrowserType browserType;
    @Mock Browser browser;
    @Mock Page page;
    @Mock Locator locator;

    /**
     * Wires the standard mock chain and returns an opened adapter.
     * Saves 5 lines of setup boilerplate per test.
     */
    private PlaywrightBrowserAdapter openAdapter() {
        when(playwright.chromium()).thenReturn(browserType);
        when(browserType.launch(any())).thenReturn(browser);
        when(browser.newPage()).thenReturn(page);
        PlaywrightBrowserAdapter adapter = new PlaywrightBrowserAdapter(playwright);
        adapter.open();
        return adapter;
    }

    // ----- Cycle 1: open() + close() -----

    @Test
    @DisplayName("open() launches a headless Chromium and creates a new page")
    void openLaunchesHeadlessBrowserAndCreatesPage() {
        PlaywrightBrowserAdapter adapter = openAdapter();

        ArgumentCaptor<BrowserType.LaunchOptions> optsCaptor =
            ArgumentCaptor.forClass(BrowserType.LaunchOptions.class);
        verify(browserType).launch(optsCaptor.capture());
        assertThat(optsCaptor.getValue().headless)
            .as("open() must launch headless=true (per ADR-0002 D4)")
            .isTrue();

        verify(browser).newPage();
    }

    @Test
    @DisplayName("close() releases page first, then browser (in order)")
    void closeReleasesPageThenBrowser() {
        PlaywrightBrowserAdapter adapter = openAdapter();
        adapter.close();

        InOrder order = inOrder(page, browser);
        order.verify(page).close();
        order.verify(browser).close();
    }

    @Test
    @DisplayName("close() is a no-op when open() was never called")
    void closeWithoutOpenIsNoOp() {
        PlaywrightBrowserAdapter adapter = new PlaywrightBrowserAdapter(playwright);

        adapter.close();

        verify(playwright, never()).chromium();
    }

    // ----- Cycle 2: navigateTo(url) -----

    @Test
    @DisplayName("navigateTo(url) calls page.navigate(url)")
    void navigateToCallsPageNavigate() {
        PlaywrightBrowserAdapter adapter = openAdapter();
        adapter.navigateTo("https://ehall.szu.edu.cn");

        verify(page).navigate("https://ehall.szu.edu.cn");
    }

    @Test
    @DisplayName("navigateTo() maps Playwright TimeoutError to BookingException(NETWORK_TIMEOUT)")
    void navigateToMapsTimeoutErrorToNetworkTimeout() {
        when(page.navigate(anyString())).thenThrow(
            new com.microsoft.playwright.TimeoutError("page load timed out"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThatThrownBy(() -> adapter.navigateTo("https://ehall.szu.edu.cn"))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.NETWORK_TIMEOUT);
    }

    @Test
    @DisplayName("navigateTo() maps exception with 'selector' in message to ELEMENT_NOT_FOUND")
    void navigateToMapsSelectorErrorToElementNotFound() {
        when(page.navigate(anyString())).thenThrow(
            new RuntimeException("selector '.foo' not found"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThatThrownBy(() -> adapter.navigateTo("https://ehall.szu.edu.cn"))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.ELEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("navigateTo() maps other exception to BROWSER_CRASH")
    void navigateToMapsOtherErrorToBrowserCrash() {
        when(page.navigate(anyString())).thenThrow(
            new RuntimeException("browser disconnected"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThatThrownBy(() -> adapter.navigateTo("https://ehall.szu.edu.cn"))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.BROWSER_CRASH);
    }

    // ----- Cycle 3: click(selector) -----

    @Test
    @DisplayName("click(selector) calls page.locator(selector).click()")
    void clickCallsPageLocatorClick() {
        when(page.locator(anyString())).thenReturn(locator);
        PlaywrightBrowserAdapter adapter = openAdapter();
        adapter.click("#submit-btn");

        ArgumentCaptor<String> selectorCaptor = ArgumentCaptor.forClass(String.class);
        verify(page).locator(selectorCaptor.capture());
        assertThat(selectorCaptor.getValue()).isEqualTo("#submit-btn");
        verify(locator).click();
    }

    @Test
    @DisplayName("click() maps Playwright TimeoutError to BookingException(NETWORK_TIMEOUT)")
    void clickMapsTimeoutErrorToNetworkTimeout() {
        when(page.locator(anyString())).thenThrow(
            new com.microsoft.playwright.TimeoutError("element not clickable in time"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThatThrownBy(() -> adapter.click("#submit-btn"))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.NETWORK_TIMEOUT);
    }

    @Test
    @DisplayName("click() maps exception with 'selector' in message to ELEMENT_NOT_FOUND")
    void clickMapsSelectorErrorToElementNotFound() {
        when(page.locator(anyString())).thenThrow(
            new RuntimeException("selector '#missing' resolved to 0 elements"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThatThrownBy(() -> adapter.click("#missing"))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.ELEMENT_NOT_FOUND);
    }

    // ----- Cycle 4: fill(selector, value) -----

    @Test
    @DisplayName("fill(selector, value) calls page.locator(selector).fill(value)")
    void fillCallsPageLocatorFill() {
        when(page.locator(anyString())).thenReturn(locator);
        PlaywrightBrowserAdapter adapter = openAdapter();
        adapter.fill("#username", "2023150090");

        ArgumentCaptor<String> selectorCaptor = ArgumentCaptor.forClass(String.class);
        verify(page).locator(selectorCaptor.capture());
        assertThat(selectorCaptor.getValue()).isEqualTo("#username");

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(locator).fill(valueCaptor.capture());
        assertThat(valueCaptor.getValue()).isEqualTo("2023150090");
    }

    @Test
    @DisplayName("fill() maps Playwright TimeoutError to BookingException(NETWORK_TIMEOUT)")
    void fillMapsTimeoutErrorToNetworkTimeout() {
        when(page.locator(anyString())).thenThrow(
            new com.microsoft.playwright.TimeoutError("element not visible"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThatThrownBy(() -> adapter.fill("#username", "2023150090"))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.NETWORK_TIMEOUT);
    }

    @Test
    @DisplayName("fill() maps exception with 'selector' in message to ELEMENT_NOT_FOUND")
    void fillMapsSelectorErrorToElementNotFound() {
        when(page.locator(anyString())).thenThrow(
            new RuntimeException("selector '#missing' resolved to 0 elements"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThatThrownBy(() -> adapter.fill("#missing", "x"))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.ELEMENT_NOT_FOUND);
    }

    // ----- Cycle 5: isVisible(selector) -----

    @Test
    @DisplayName("isVisible(selector) returns true when element is visible")
    void isVisibleReturnsTrueWhenElementVisible() {
        when(page.locator(anyString())).thenReturn(locator);
        when(locator.isVisible()).thenReturn(true);
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThat(adapter.isVisible("#login-btn")).isTrue();
    }

    @Test
    @DisplayName("isVisible(selector) returns false when element is hidden (no throw)")
    void isVisibleReturnsFalseWhenElementHidden() {
        when(page.locator(anyString())).thenReturn(locator);
        when(locator.isVisible()).thenReturn(false);
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThat(adapter.isVisible("#hidden-div")).isFalse();
    }

    @Test
    @DisplayName("isVisible() maps Playwright TimeoutError to BookingException(NETWORK_TIMEOUT)")
    void isVisibleMapsTimeoutErrorToNetworkTimeout() {
        when(page.locator(anyString())).thenThrow(
            new com.microsoft.playwright.TimeoutError("element not found in DOM"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThatThrownBy(() -> adapter.isVisible("#missing"))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.NETWORK_TIMEOUT);
    }

    // ----- Cycle 6: textOf(selector) -----

    @Test
    @DisplayName("textOf(selector) returns locator.textContent()")
    void textOfReturnsTextContent() {
        when(page.locator(anyString())).thenReturn(locator);
        when(locator.textContent()).thenReturn("网球1号场");
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThat(adapter.textOf(".venue-name")).isEqualTo("网球1号场");
    }

    @Test
    @DisplayName("textOf() maps Playwright TimeoutError to BookingException(NETWORK_TIMEOUT)")
    void textOfMapsTimeoutErrorToNetworkTimeout() {
        when(page.locator(anyString())).thenThrow(
            new com.microsoft.playwright.TimeoutError("element not found"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThatThrownBy(() -> adapter.textOf("#missing"))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.NETWORK_TIMEOUT);
    }

    @Test
    @DisplayName("textOf() maps exception with 'selector' in message to ELEMENT_NOT_FOUND")
    void textOfMapsSelectorErrorToElementNotFound() {
        when(page.locator(anyString())).thenThrow(
            new RuntimeException("selector '#bad' resolved to 0 elements"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThatThrownBy(() -> adapter.textOf("#bad"))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.ELEMENT_NOT_FOUND);
    }

    // ----- Cycle 7: allTextOf(selector) -----

    @Test
    @DisplayName("allTextOf(selector) returns list of all matching elements' text")
    void allTextOfReturnsListOfTexts() {
        when(page.locator(anyString())).thenReturn(locator);
        when(locator.allTextContents()).thenReturn(
            List.of("网球1号场", "网球2号场", "网球3号场"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThat(adapter.allTextOf(".venue-name"))
            .containsExactly("网球1号场", "网球2号场", "网球3号场");
    }

    @Test
    @DisplayName("allTextOf() maps Playwright TimeoutError to BookingException(NETWORK_TIMEOUT)")
    void allTextOfMapsTimeoutErrorToNetworkTimeout() {
        when(page.locator(anyString())).thenThrow(
            new com.microsoft.playwright.TimeoutError("element not found"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThatThrownBy(() -> adapter.allTextOf("#missing"))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.NETWORK_TIMEOUT);
    }

    @Test
    @DisplayName("allTextOf() maps exception with 'selector' in message to ELEMENT_NOT_FOUND")
    void allTextOfMapsSelectorErrorToElementNotFound() {
        when(page.locator(anyString())).thenThrow(
            new RuntimeException("selector '#bad' resolved to 0 elements"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThatThrownBy(() -> adapter.allTextOf("#bad"))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.ELEMENT_NOT_FOUND);
    }

    // ----- Cycle 8: currentUrl() -----

    @Test
    @DisplayName("currentUrl() returns page.url()")
    void currentUrlReturnsPageUrl() {
        when(page.url()).thenReturn("https://ehall.szu.edu.cn/booking");
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThat(adapter.currentUrl()).isEqualTo("https://ehall.szu.edu.cn/booking");
    }

    @Test
    @DisplayName("currentUrl() is a pure getter — does not throw under normal page state")
    void currentUrlDoesNotThrow() {
        when(page.url()).thenReturn("about:blank");
        PlaywrightBrowserAdapter adapter = openAdapter();

        // Should return the mocked URL, no exception
        assertThat(adapter.currentUrl()).isEqualTo("about:blank");
    }

    // ----- Cycle 9: screenshot(path) -----

    @Test
    @DisplayName("screenshot(path) calls page.screenshot with ScreenshotOptions.setPath(path)")
    void screenshotCallsPageScreenshotWithPath() {
        when(page.screenshot(any(Page.ScreenshotOptions.class)))
            .thenReturn(new byte[]{1, 2, 3});
        PlaywrightBrowserAdapter adapter = openAdapter();
        adapter.screenshot("/tmp/ehall-error.png");

        ArgumentCaptor<Page.ScreenshotOptions> optsCaptor =
            ArgumentCaptor.forClass(Page.ScreenshotOptions.class);
        verify(page).screenshot(optsCaptor.capture());
        assertThat(optsCaptor.getValue().path)
            .as("screenshot() must set the path on ScreenshotOptions")
            .isEqualTo(java.nio.file.Paths.get("/tmp/ehall-error.png"));
    }

    @Test
    @DisplayName("screenshot() maps Playwright TimeoutError to BookingException(NETWORK_TIMEOUT)")
    void screenshotMapsTimeoutErrorToNetworkTimeout() {
        when(page.screenshot(any(Page.ScreenshotOptions.class))).thenThrow(
            new com.microsoft.playwright.TimeoutError("page unresponsive during screenshot"));
        PlaywrightBrowserAdapter adapter = openAdapter();

        assertThatThrownBy(() -> adapter.screenshot("/tmp/x.png"))
            .isInstanceOf(BookingException.class)
            .extracting(t -> ((BookingException) t).code())
            .isEqualTo(ErrorCode.NETWORK_TIMEOUT);
    }
}

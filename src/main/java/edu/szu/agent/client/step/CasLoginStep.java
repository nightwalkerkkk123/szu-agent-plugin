package edu.szu.agent.client.step;

import edu.szu.agent.browser.BrowserLifecycle;
import edu.szu.agent.domain.BookingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step 0 — CAS login.
 *
 * <p>Navigates to the CAS login page, fills student ID and password,
 * then submits. Credentials come from {@link BookingContext#account()},
 * which is injected by the caller after {@link edu.szu.agent.account.AccountResolver}
 * resolves them.
 *
 * // Design Pattern: Strategy (concrete step in booking pipeline)
 * // 编程技术: 枚举 / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class CasLoginStep implements BookingStep {

    private static final Logger log = LoggerFactory.getLogger(CasLoginStep.class);

    static final String SEL_USERNAME = "#username";
    static final String SEL_PASSWORD = "#password";
    static final String SEL_LOGIN_SUBMIT = "button[type='submit'], input[type='submit']";
    static final String CAS_LOGIN_URL = "https://ehall.szu.edu.cn/login";

    @Override
    public String name() {
        return "CAS_LOGIN";
    }

    @Override
    public BookingResult execute(BrowserLifecycle browser, BookingContext ctx) {
        var account = ctx.account();
        if (account == null) {
            throw new IllegalStateException(
                "CasLoginStep requires BookingContext.account, but it was null. "
                    + "Did you forget to call AccountResolver before constructing the context?");
        }

        log.info("Logging in as {}", account.studentId());
        browser.navigateTo(CAS_LOGIN_URL);
        browser.fill(SEL_USERNAME, account.studentId());
        browser.fill(SEL_PASSWORD, account.password());
        browser.click(SEL_LOGIN_SUBMIT);
        log.info("CAS login submitted for {}", account.studentId());

        return null;
    }
}
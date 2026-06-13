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

    static final String SEL_USERNAME = "#pwdFromId #username";
    static final String SEL_PASSWORD = "#pwdFromId #password";
    static final String SEL_LOGIN_SUBMIT = "#login_submit";
    static final String SEL_LOGGED_IN_INDICATOR = ".bh-btn";

    /**
     * Direct ehall venue URL. Hitting this when unauthenticated triggers
     * the CAS redirect to {@code authserver.szu.edu.cn/authserver/login},
     * and a successful login redirects right back here. Mirrors how the
     * Python reference (see {@code 登录体育馆_cloak/main.py}) drives the flow.
     */
    static final String EHALL_VENUE_URL =
        "https://ehall.szu.edu.cn/qljfwapp/sys/lwSzuCgyy/index.do#/sportVenue";

    /**
     * Fills both inputs via direct DOM access, bypassing Playwright's
     * visibility / editable checks. The ehall CAS form uses an SPA layout
     * where the inputs are in DOM but flagged "not visible" by Playwright,
     * so {@code fill} loops on retry until the call hangs. Setting
     * {@code .value} directly works because the form posts via
     * {@code startLogin(this)}.
     *
     * <p>Also strips {@code readonly} + the {@code no-auto-input} guard
     * class on the password field, which ehall adds to defeat auto-fillers.
     */
    static String buildLoginScript(String username, String password) {
        // JSON-escape both values to keep quotes and backslashes safe.
        return "(function(){"
            + "var u=document.querySelector('#pwdFromId #username');"
            + "var p=document.querySelector('#pwdFromId #password');"
            + "var btn=document.querySelector('#login_submit');"
            + "if(!u||!p||!btn){return 'no-input';}"
            + "p.removeAttribute('readonly');"
            + "p.classList.remove('no-auto-input');"
            + "u.value=" + jsString(username) + ";"
            + "p.value=" + jsString(password) + ";"
            + "u.dispatchEvent(new Event('input',{bubbles:true}));"
            + "p.dispatchEvent(new Event('input',{bubbles:true}));"
            + "p.dispatchEvent(new Event('blur',{bubbles:true}));"
            + "if(typeof startLogin==='function'){startLogin(btn);return 'submitted-via-startLogin';}"
            + "btn.click();return 'clicked';"
            + "})()";
    }

    private static String jsString(String s) {
        StringBuilder out = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\'' -> out.append("\\'");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }
        return out.append("'").toString();
    }

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
        // Hit ehall directly; the unauthenticated request 302's to authserver
        // CAS, presenting the login page in the same tab.
        browser.navigateTo(EHALL_VENUE_URL);

        // Inject username + password and submit via the page's own
        // startLogin() function, which handles password salt-encryption.
        // Playwright's fill() can't see the inputs as visible on this SPA
        // login page; DOM injection sidesteps the visibility wait loop.
        String result = browser.evaluate(buildLoginScript(account.studentId(), account.password()));
        log.info("Submitted login form (script result={})", result);

        // Block until the post-login landing is reached (ehall header buttons).
        boolean landed = browser.isVisible(SEL_LOGGED_IN_INDICATOR);
        log.info("CAS login submitted for {} (landed on ehall: {})",
            account.studentId(), landed);

        return null;
    }
}
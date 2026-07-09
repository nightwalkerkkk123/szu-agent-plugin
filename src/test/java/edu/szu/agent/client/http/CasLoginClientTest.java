package edu.szu.agent.client.http;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CasLoginClient} form parsing.
 *
 * @since 0.6.0
 * @author 王子豪
 */
class CasLoginClientTest {

    @Test
    void parsesHiddenCasFields() {
        CampusHttpClient http = CampusHttpClient.create();
        CasLoginClient client = CasLoginClient.builder(http, "https://auth.szu.edu.cn").build();

        String html = """
            <form id="fm1" action="/cas/login" method="post">
              <input type="hidden" name="lt" value="LT-abc" />
              <input type="hidden" name="execution" value="e1s1" />
              <input type="hidden" name="_eventId" value="submit" />
              <input id="username" name="username" type="text" />
              <input id="password" name="password" type="password" />
            </form>
            """;

        Map<String, String> fields = client.parseHiddenFields(html);
        assertThat(fields).containsEntry("lt", "LT-abc");
        assertThat(fields).containsEntry("execution", "e1s1");
        assertThat(fields).containsEntry("_eventId", "submit");
        assertThat(fields).doesNotContainKey("username");
        assertThat(fields).doesNotContainKey("password");
    }

    @Test
    void handlesEmptyValueHiddenFields() {
        CampusHttpClient http = CampusHttpClient.create();
        CasLoginClient client = CasLoginClient.builder(http, "https://auth.szu.edu.cn").build();

        String html = "<input type=\"hidden\" name=\"execution\" value=\"\" />";
        Map<String, String> fields = client.parseHiddenFields(html);
        assertThat(fields).containsEntry("execution", "");
    }

    @Test
    void extractsPwdEncryptSalt() {
        CampusHttpClient http = CampusHttpClient.create();
        CasLoginClient client = CasLoginClient.builder(http, "https://authserver.szu.edu.cn").build();

        String html = """
            <input type="hidden" id="pwdEncryptSalt" value="KOYOA2HcMufRxupp">
            <input type="hidden" name="execution" value="e2s1">
            """;
        Map<String, String> fields = client.parseHiddenFields(html);
        assertThat(fields).containsEntry("execution", "e2s1");
        // pwdEncryptSalt is not a form field; it is extracted separately for the encryptor.
        assertThat(fields).doesNotContainKey("pwdEncryptSalt");
    }
}

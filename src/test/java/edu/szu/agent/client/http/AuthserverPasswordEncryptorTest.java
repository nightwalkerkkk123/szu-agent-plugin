package edu.szu.agent.client.http;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuthserverPasswordEncryptor}.
 *
 * @since 0.6.0
 * @author 王子豪
 */
class AuthserverPasswordEncryptorTest {

    @Test
    void encryptsWhenSaltPresent() {
        AuthserverPasswordEncryptor encryptor = new AuthserverPasswordEncryptor();
        String cipher = encryptor.encrypt("myPassword", Map.of("pwdEncryptSalt", "KOYOA2HcMufRxupp"));

        assertThat(cipher).isNotNull().isNotBlank();
        assertThat(cipher).isNotEqualTo("myPassword");
        // CryptoJS AES default output is Base64.
        assertThat(cipher).matches("^[A-Za-z0-9+/=]+$");
    }

    @Test
    void fallsBackToPlaintextWhenSaltMissing() {
        AuthserverPasswordEncryptor encryptor = new AuthserverPasswordEncryptor();
        String result = encryptor.encrypt("myPassword", Map.of());

        assertThat(result).isEqualTo("myPassword");
    }

    @Test
    void fallsBackToPlaintextWhenSaltBlank() {
        AuthserverPasswordEncryptor encryptor = new AuthserverPasswordEncryptor();
        String result = encryptor.encrypt("myPassword", Map.of("pwdEncryptSalt", "  "));

        assertThat(result).isEqualTo("myPassword");
    }
}

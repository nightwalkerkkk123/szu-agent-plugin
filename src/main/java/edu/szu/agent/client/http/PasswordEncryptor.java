package edu.szu.agent.client.http;

import java.util.Map;

/**
 * Pluggable password encryption for CAS / authserver login forms.
 *
 * <p>Some identity providers (e.g. 深圳大学 authserver) encrypt the password
 * client-side with RSA before submitting the form. Implementations of this
 * interface receive the plaintext password and any parameters extracted from
 * the login page (such as {@code pwdDefaultEncryptSalt} or a public key).
 *
 * <p>// Design Pattern: Strategy (encryption algorithm is interchangeable)
 * // 编程技术: 函数式接口 / Lambda
 *
 * @since 0.6.0
 * @author 王子豪
 */
@FunctionalInterface
public interface PasswordEncryptor {

    /**
     * Returns the encrypted password to submit, or the plaintext if no
     * encryption is required.
     *
     * @param plaintextPassword the user's plaintext password
     * @param loginPageParams   parameters parsed from the login page HTML
     * @return the value to place in the password form field
     */
    String encrypt(String plaintextPassword, Map<String, String> loginPageParams);

    /**
     * No-op implementation that returns the password unchanged.
     */
    static PasswordEncryptor plaintext() {
        return (password, params) -> password;
    }
}

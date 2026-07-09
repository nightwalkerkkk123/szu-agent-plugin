package edu.szu.agent.client.http;

import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Password encryptor for 深圳大学 authserver login forms.
 *
 * <p>Implements the client-side encryption observed in
 * {@code encrypt.js} / {@code login.js}:
 * <ol>
 *   <li>Generate a 64-character random prefix from a restricted charset.</li>
 *   <li>Prepend it to the plaintext password.</li>
 *   <li>Encrypt with AES-128-CBC using the login page's
 *       {@code pwdEncryptSalt} as the key and a fresh 16-character random IV.</li>
 *   <li>Return the Base64 ciphertext.</li>
 * </ol>
 *
 * <p>This matches the JavaScript implementation:
 * <pre>
 *   encryptPassword(pwd, salt) -&gt; encryptAES(pwd, salt)
 *   encryptAES(pwd, salt) -&gt; getAesString(randomString(64)+pwd, salt, randomString(16))
 *   getAesString(data, key, iv) -&gt; CryptoJS.AES.encrypt(..., CBC, Pkcs7)
 * </pre>
 *
 * <p>// Design Pattern: Strategy (concrete PasswordEncryptor)
 * // 编程技术: SecureRandom / javax.crypto / Base64 / Lambda
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class AuthserverPasswordEncryptor implements PasswordEncryptor {

    private static final String SALT_KEY = "pwdEncryptSalt";
    private static final String AES_CHARS =
        "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";
    private static final int RANDOM_PREFIX_LEN = 64;
    private static final int IV_LEN = 16;
    private static final String AES_MODE = "AES/CBC/PKCS5Padding";

    private final SecureRandom secureRandom;

    /**
     * Creates an encryptor using the default {@link SecureRandom}.
     */
    public AuthserverPasswordEncryptor() {
        this(new SecureRandom());
    }

    /**
     * Creates an encryptor with an explicit random source (for tests).
     *
     * @param secureRandom random source; must not be null
     */
    public AuthserverPasswordEncryptor(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public String encrypt(String plaintextPassword, Map<String, String> loginPageParams) {
        Objects.requireNonNull(plaintextPassword, "plaintextPassword");
        Objects.requireNonNull(loginPageParams, "loginPageParams");

        String salt = loginPageParams.get(SALT_KEY);
        if (salt == null || salt.isBlank()) {
            // No salt on the page: fall back to plaintext (old CAS behavior).
            return plaintextPassword;
        }

        String prefix = randomString(RANDOM_PREFIX_LEN);
        String iv = randomString(IV_LEN);
        String data = prefix + plaintextPassword;

        try {
            return aesEncrypt(data, salt, iv);
        } catch (Exception e) {
            throw new BookingException(ErrorCode.PASSWORD_INCORRECT,
                "Failed to encrypt password for authserver", e);
        }
    }

    private String aesEncrypt(String data, String keyString, String ivString) throws Exception {
        byte[] keyBytes = keyString.getBytes(StandardCharsets.UTF_8);
        byte[] ivBytes = ivString.getBytes(StandardCharsets.UTF_8);

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance(AES_MODE);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = secureRandom.nextInt(AES_CHARS.length());
            sb.append(AES_CHARS.charAt(idx));
        }
        return sb.toString();
    }
}

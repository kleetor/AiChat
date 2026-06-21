package com.example.aichat.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES 加密工具类。
 * 静态块先尝试从系统属性/环境变量加载密钥（兜底），
 * Spring 启动后通过 {@link #setKey(String)} 覆盖为 application.properties 中的值。
 */
public final class AESUtil {

    private static final Logger logger = LoggerFactory.getLogger(AESUtil.class);
    private static final String ALGORITHM = "AES";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;   // 96 bits, NIST recommended
    private static final int GCM_TAG_LENGTH = 128;  // bits
    private static final String DEFAULT_KEY = "aichat-dev-key-!";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static SecretKeySpec KEY_SPEC;

    static {
        reloadKey(readKeyFromEnv());
    }

    private AESUtil() {}

    /** Spring 配置类启动后调用，使用 application.properties / .env 中的密钥覆盖 */
    public static synchronized void setKey(String key) {
        reloadKey(key);
    }

    public static String getKeySource() {
        String k = System.getProperty("ENCRYPTION_KEY");
        if (k != null && !k.isEmpty()) return "system-property";
        k = System.getenv("ENCRYPTION_KEY");
        if (k != null && !k.isEmpty()) return "environment";
        return (KEY_SPEC != null && !matchesDefault()) ? "spring-config" : "default";
    }

    private static boolean matchesDefault() {
        return KEY_SPEC != null && java.util.Arrays.equals(
                KEY_SPEC.getEncoded(),
                new SecretKeySpec(DEFAULT_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM).getEncoded());
    }

    private static String readKeyFromEnv() {
        String key = System.getProperty("ENCRYPTION_KEY");
        if (key == null || key.isEmpty()) {
            key = System.getenv("ENCRYPTION_KEY");
        }
        if (key == null || key.isEmpty()) {
            key = DEFAULT_KEY;
        }
        return key;
    }

    private static void reloadKey(String key) {
        if (key == null || key.isEmpty()) {
            key = DEFAULT_KEY;
        }
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16) {
            byte[] padded = new byte[16];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 16));
            keyBytes = padded;
        }
        KEY_SPEC = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public static String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, KEY_SPEC, gcmSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext: IV(12 bytes) + ciphertext
            byte[] combined = new byte[GCM_IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, GCM_IV_LENGTH, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            logger.error("AES 加密失败", e);
            throw new RuntimeException("加密失败", e);
        }
    }

    /** 解密失败时不抛异常，返回 null 让调用方降级处理 */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null) return null;
        byte[] decoded = Base64.getDecoder().decode(encryptedText);

        // 1. Try GCM first (new format: IV(12) + ciphertext)
        try {
            return decryptGCM(decoded);
        } catch (Exception e) {
            logger.debug("GCM 解密失败，尝试 ECB 解密（兼容旧数据）: {}", e.getMessage());
        }

        // 2. Fallback: ECB (old data, no IV prefix)
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, KEY_SPEC);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("AES 解密失败（密钥可能不匹配，将交由迁移程序重新加密）: {}", e.getMessage());
            return null;
        }
    }

    private static String decryptGCM(byte[] combined) throws Exception {
        if (combined.length < GCM_IV_LENGTH + 16) {
            throw new IllegalArgumentException("密文长度不足");
        }
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, KEY_SPEC, gcmSpec);
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}

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
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static SecretKeySpec KEY_SPEC;
    private static boolean initialized = false;

    private AESUtil() {}

    /** Spring 配置类启动后调用，使用 application.properties / .env 中的密钥覆盖 */
    public static synchronized void setKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException("ENCRYPTION_KEY 未配置，请设置环境变量 ENCRYPTION_KEY（必须为 16 位字符）");
        }
        reloadKey(key);
        initialized = true;
    }

    public static String getKeySource() {
        if (!initialized) return "not-initialized";
        String k = System.getProperty("ENCRYPTION_KEY");
        if (k != null && !k.isEmpty()) return "system-property";
        k = System.getenv("ENCRYPTION_KEY");
        if (k != null && !k.isEmpty()) return "environment";
        return "spring-config";
    }

    /**
     * 在加密/解密前检查密钥是否已初始化，未初始化则抛出明确错误，
     * 防止因配置遗漏导致使用弱密钥。
     */
    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("AES 密钥未初始化，请设置 ENCRYPTION_KEY 环境变量");
        }
    }

    private static void reloadKey(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16) {
            byte[] padded = new byte[16];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 16));
            keyBytes = padded;
        }
        KEY_SPEC = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public static String encrypt(String plainText) {
        ensureInitialized();
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
        ensureInitialized();
        if (encryptedText == null) return null;
        byte[] decoded = Base64.getDecoder().decode(encryptedText);

        // 仅支持 GCM 解密 (IV(12) + ciphertext)
        try {
            return decryptGCM(decoded);
        } catch (Exception e) {
            logger.warn("AES GCM 解密失败（密钥可能不匹配）: {}", e.getMessage());
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

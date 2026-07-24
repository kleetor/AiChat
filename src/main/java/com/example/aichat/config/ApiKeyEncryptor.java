package com.example.aichat.config;

import com.example.aichat.util.AESUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ModelConfig.apiKey 字段的加密转换器。
 * JPA 通过 @Convert(converter = ...) 自动创建实例，不依赖 Spring 注入。
 */
@Converter
public class ApiKeyEncryptor implements AttributeConverter<String, String> {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyEncryptor.class);
    private static final String ENCRYPTED_PREFIX = "ENC:";

    @Override
    public String convertToDatabaseColumn(String plainApiKey) {
        if (plainApiKey == null) return null;
        if (plainApiKey.startsWith(ENCRYPTED_PREFIX)) {
            return plainApiKey;
        }
        return ENCRYPTED_PREFIX + AESUtil.encrypt(plainApiKey);
    }

    @Override
    public String convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return null;
        if (dbValue.startsWith(ENCRYPTED_PREFIX)) {
            String decrypted = AESUtil.decrypt(dbValue.substring(ENCRYPTED_PREFIX.length()));
            if (decrypted != null) {
                return decrypted;
            }
            // 解密失败（密钥不匹配），返回 null 让下游处理
            logger.warn("API Key 解密失败（密钥可能已变更），返回 null: id in model_configs");
            return null;
        }
        // 兼容历史明文数据
        logger.warn("检测到数据库中仍有明文 API Key，建议重新保存 ModelConfig 完成加密迁移");
        return dbValue;
    }
}

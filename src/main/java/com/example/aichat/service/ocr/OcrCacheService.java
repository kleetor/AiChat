package com.example.aichat.service.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * OCR 结果本地缓存，以文件 SHA-256 哈希为 key。
 * 重索引时直接读取缓存，避免重复 OCR 处理。
 */
@Service
public class OcrCacheService {

    private static final Logger log = LoggerFactory.getLogger(OcrCacheService.class);
    /** 单条缓存最大字符数（约 1MB），防止缓存投毒 */
    private static final int MAX_CACHE_SIZE = 1_000_000;
    private final Path cacheDir;

    public OcrCacheService() {
        this(Path.of("./uploads/kb/ocr_cache"));
    }

    /** 测试用：允许指定缓存目录 */
    OcrCacheService(Path cacheDir) {
        this.cacheDir = cacheDir;
        try {
            Files.createDirectories(cacheDir);
            log.info("OCR 缓存目录: {}", cacheDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("无法创建 OCR 缓存目录: " + cacheDir, e);
        }
    }

    public Optional<String> get(byte[] fileBytes) {
        String hash = sha256(fileBytes);
        Path cacheFile = cacheDir.resolve(hash + ".txt");
        if (Files.exists(cacheFile)) {
            try {
                // 校验缓存文件大小，防止投毒注入超大文件
                long size = Files.size(cacheFile);
                if (size > MAX_CACHE_SIZE) {
                    log.warn("OCR 缓存文件过大 ({} bytes)，视为无效，删除: hash={}", size, hash.substring(0, 8));
                    Files.delete(cacheFile);
                    return Optional.empty();
                }
                String cached = Files.readString(cacheFile);
                log.debug("OCR 缓存命中: hash={}", hash.substring(0, 8));
                return Optional.of(cached);
            } catch (IOException e) {
                log.warn("OCR 缓存读取失败: {}", cacheFile, e);
            }
        }
        return Optional.empty();
    }

    public void put(byte[] fileBytes, String ocrResult) {
        if (ocrResult == null || ocrResult.isBlank()) return;
        // 拒绝缓存超大结果
        if (ocrResult.length() > MAX_CACHE_SIZE) {
            log.warn("OCR 结果过大 ({} chars)，拒绝缓存", ocrResult.length());
            return;
        }
        String hash = sha256(fileBytes);
        Path cacheFile = cacheDir.resolve(hash + ".txt");
        try {
            Files.writeString(cacheFile, ocrResult);
            log.debug("OCR 缓存写入: hash={}, size={}", hash.substring(0, 8), ocrResult.length());
        } catch (IOException e) {
            log.warn("OCR 缓存写入失败: {}", cacheFile, e);
        }
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }
}

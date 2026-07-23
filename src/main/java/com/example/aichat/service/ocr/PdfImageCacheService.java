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
 * PDF 嵌入图片视觉识别结果本地缓存。
 * Key = PDF文件SHA-256 + 图片字节SHA-256，避免重复调用视觉 API。
 */
@Service
public class PdfImageCacheService {

    private static final Logger log = LoggerFactory.getLogger(PdfImageCacheService.class);
    private final Path cacheDir;

    public PdfImageCacheService() {
        this(Path.of("./uploads/kb/image_cache"));
    }

    PdfImageCacheService(Path cacheDir) {
        this.cacheDir = cacheDir;
        try {
            Files.createDirectories(cacheDir);
            log.info("图片识别缓存目录: {}", cacheDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("无法创建图片识别缓存目录: " + cacheDir, e);
        }
    }

    /**
     * 查询缓存。
     * @param pdfHash PDF 文件的 SHA-256
     * @param imageHash 图片字节的 SHA-256
     */
    public Optional<String> get(String pdfHash, String imageHash) {
        Path cacheFile = cacheDir.resolve(cacheKey(pdfHash, imageHash));
        if (Files.exists(cacheFile)) {
            try {
                String cached = Files.readString(cacheFile);
                log.debug("图片识别缓存命中: pdf={}, img={}", shortHash(pdfHash), shortHash(imageHash));
                return Optional.of(cached);
            } catch (IOException e) {
                log.warn("图片识别缓存读取失败: {}", cacheFile, e);
            }
        }
        return Optional.empty();
    }

    public void put(String pdfHash, String imageHash, String description) {
        if (description == null || description.isBlank()) return;
        Path cacheFile = cacheDir.resolve(cacheKey(pdfHash, imageHash));
        try {
            Files.writeString(cacheFile, description);
            log.debug("图片识别缓存写入: pdf={}, img={}, descLen={}",
                    shortHash(pdfHash), shortHash(imageHash), description.length());
        } catch (IOException e) {
            log.warn("图片识别缓存写入失败: {}", cacheFile, e);
        }
    }

    public String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }

    private String cacheKey(String pdfHash, String imageHash) {
        return pdfHash + "_" + imageHash;
    }

    private String shortHash(String hash) {
        return hash != null ? hash.substring(0, 8) : "null";
    }
}

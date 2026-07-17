package com.example.aichat.config;

import com.example.aichat.model.TokenBlacklistEntry;
import com.example.aichat.repository.TokenBlacklistEntryRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单 — 用于登出时失效 JWT。
 * 双层保障：Caffeine 内存缓存（快速查询）+ MySQL 持久化（防止重启丢失）。
 */
@Component
public class TokenBlacklist {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklist.class);

    private final Cache<String, Boolean> blacklist;
    private final TokenBlacklistEntryRepository blacklistRepo;
    private final long expirationMs;

    public TokenBlacklist(@Value("${jwt.expiration}") long expirationMs,
                          TokenBlacklistEntryRepository blacklistRepo) {
        this.expirationMs = expirationMs;
        this.blacklistRepo = blacklistRepo;
        this.blacklist = Caffeine.newBuilder()
                .expireAfterWrite(expirationMs, TimeUnit.MILLISECONDS)
                .build();
    }

    public void blacklist(String token) {
        String hash = sha256(token);
        blacklist.put(hash, Boolean.TRUE);
        try {
            LocalDateTime expiresAt = LocalDateTime.now().plusNanos(
                    TimeUnit.MILLISECONDS.toNanos(expirationMs));
            TokenBlacklistEntry entry = TokenBlacklistEntry.builder()
                    .tokenHash(hash)
                    .expiresAt(expiresAt)
                    .build();
            blacklistRepo.save(entry);
        } catch (Exception e) {
            logger.warn("Token 黑名单持久化失败（内存缓存仍然有效）: {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String token) {
        String hash = sha256(token);
        if (blacklist.getIfPresent(hash) != null) {
            return true;
        }
        // 内存未命中时回退查询 DB（处理服务重启场景）
        return blacklistRepo.existsByTokenHash(hash);
    }

    @Scheduled(fixedRate = 3600000) // 每小时清理过期记录
    public void cleanExpired() {
        int deleted = blacklistRepo.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            logger.debug("清理过期 Token 黑名单记录: {} 条", deleted);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }
}

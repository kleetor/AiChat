package com.example.aichat.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置 —— 基于 Caffeine 的本地缓存层。
 *
 * 缓存策略：
 *   modelConfigs    TTL 10min, maxSize 50    模型配置列表（极少变化）
 *   promptsByUser   TTL 5min,  maxSize 200   用户提示词列表
 *   kbList          TTL 3min,  maxSize 100   知识库列表
 *   kbDocs          TTL 3min,  maxSize 100   知识库文档列表
 *   billingBalance  TTL 30s,   maxSize 500   用户可用余额（高频轮询，短TTL防过期）
 *   billingSpent    TTL 2min,  maxSize 200   用户累计消费
 *   billingTokens   TTL 2min,  maxSize 200   用户累计Token
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("modelConfigs",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(50)
                        .recordStats()
                        .build());
        manager.registerCustomCache("promptsByUser",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .recordStats()
                        .build());
        manager.registerCustomCache("kbList",
                Caffeine.newBuilder()
                        .expireAfterWrite(3, TimeUnit.MINUTES)
                        .maximumSize(100)
                        .recordStats()
                        .build());
        manager.registerCustomCache("kbDocs",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .maximumSize(100)
                        .recordStats()
                        .build());
        manager.registerCustomCache("billingBalance",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .maximumSize(500)
                        .recordStats()
                        .build());
        manager.registerCustomCache("billingSpent",
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .recordStats()
                        .build());
        manager.registerCustomCache("billingTokens",
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .recordStats()
                        .build());
        return manager;
    }
}

package com.example.aichat.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 速率限制拦截器 — 支持多路径、多时间窗口的灵活速率控制
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final List<RateLimitRule> rules = new ArrayList<>();
    private final Cache<String, Bucket> counters;

    public RateLimitInterceptor() {
        // Caffeine 缓存：每个用户+规则 key 的计数器，最大 5000 条目，写入后按规则周期过期
        this.counters = Caffeine.newBuilder()
                .maximumSize(5000)
                .build();

        // === 速率限制规则（按匹配顺序生效，命中第一个规则即停止） ===

        // 赞助相关 3 次/天
        rules.add(new RateLimitRule(
                uri -> uri.startsWith("/api/billing/sponsor-"),
                "POST",
                3,
                TimeUnit.DAYS.toMillis(1),
                "billing-sponsor"
        ));

        // 评论区 5 次/分钟
        rules.add(new RateLimitRule(
                uri -> uri.matches(".*/api/prompts-hub/\\d+/comments"),
                "POST",
                5,
                TimeUnit.MINUTES.toMillis(1),
                "prompt-comment"
        ));

        // 上传 20 次/天
        rules.add(new RateLimitRule(
                uri -> uri.startsWith("/api/prompts-hub/upload"),
                "POST",
                20,
                TimeUnit.DAYS.toMillis(1),
                "prompt-upload"
        ));

        // 点赞/点踩 10 次/分钟
        rules.add(new RateLimitRule(
                uri -> uri.matches(".*/api/prompts-hub/\\d+/(like|dislike)"),
                "POST",
                10,
                TimeUnit.MINUTES.toMillis(1),
                "prompt-like"
        ));

        // 聊天 30 次/分钟
        rules.add(new RateLimitRule(
                uri -> uri.startsWith("/api/chat/") && !uri.endsWith("/history"),
                null, // 任意方法
                30,
                TimeUnit.MINUTES.toMillis(1),
                "chat"
        ));

        // 登录 5 次/分钟（防暴力破解）
        rules.add(new RateLimitRule(
                uri -> uri.equals("/api/auth/login"),
                "POST",
                5,
                TimeUnit.MINUTES.toMillis(1),
                "auth-login"
        ));

        // 管理员登录 3 次/分钟（防暴力破解）
        rules.add(new RateLimitRule(
                uri -> uri.equals("/api/admin/login"),
                "POST",
                3,
                TimeUnit.MINUTES.toMillis(1),
                "admin-login"
        ));

        // 注册 3 次/分钟（防滥用）
        rules.add(new RateLimitRule(
                uri -> uri.equals("/api/auth/register"),
                "POST",
                3,
                TimeUnit.MINUTES.toMillis(1),
                "auth-register"
        ));

        // 发送验证码 1 次/分钟
        rules.add(new RateLimitRule(
                uri -> uri.equals("/api/auth/send-code"),
                "POST",
                1,
                TimeUnit.MINUTES.toMillis(1),
                "auth-send-code"
        ));

        // 发送重置密码验证码 1 次/分钟
        rules.add(new RateLimitRule(
                uri -> uri.equals("/api/auth/send-reset-code"),
                "POST",
                1,
                TimeUnit.MINUTES.toMillis(1),
                "auth-send-reset-code"
        ));

        // 重置密码 5 次/分钟（防暴力破解）
        rules.add(new RateLimitRule(
                uri -> uri.equals("/api/auth/reset-password"),
                "POST",
                5,
                TimeUnit.MINUTES.toMillis(1),
                "auth-reset-password"
        ));

        // 修改密码 5 次/分钟（防暴力枚举）
        rules.add(new RateLimitRule(
                uri -> uri.equals("/api/auth/change-password"),
                "POST",
                5,
                TimeUnit.MINUTES.toMillis(1),
                "auth-change-password"
        ));

        // 头像上传 3 次/天
        rules.add(new RateLimitRule(
                uri -> uri.equals("/api/auth/upload-avatar"),
                "POST",
                3,
                TimeUnit.DAYS.toMillis(1),
                "auth-upload-avatar"
        ));

        // 聊天图片上传 30 次/天
        rules.add(new RateLimitRule(
                uri -> uri.equals("/api/image/upload"),
                "POST",
                30,
                TimeUnit.DAYS.toMillis(1),
                "image-upload"
        ));

        // 文件上传 30 次/天（工具调用路径）
        rules.add(new RateLimitRule(
                uri -> uri.equals("/api/file/upload"),
                "POST",
                30,
                TimeUnit.DAYS.toMillis(1),
                "file-upload"
        ));

        // 知识库文档上传 50 次/天
        rules.add(new RateLimitRule(
                uri -> uri.matches("/api/kb/\\d+/docs/upload"),
                "POST",
                50,
                TimeUnit.DAYS.toMillis(1),
                "kb-docs-upload"
        ));

        // Prompt 图片更新 10 次/天
        rules.add(new RateLimitRule(
                uri -> uri.matches("/api/prompts-hub/\\d+/image"),
                "POST",
                10,
                TimeUnit.DAYS.toMillis(1),
                "prompt-image-update"
        ));

        // 知识库文档重索引 10 次/天（防资源滥用）
        rules.add(new RateLimitRule(
                uri -> uri.matches("/api/kb/docs/\\d+/reindex"),
                "POST",
                10,
                TimeUnit.DAYS.toMillis(1),
                "kb-docs-reindex"
        ));

        // 记忆手动添加 30 次/天
        rules.add(new RateLimitRule(
                uri -> uri.equals("/api/memory/add"),
                "POST",
                30,
                TimeUnit.DAYS.toMillis(1),
                "memory-add"
        ));

        // 记忆搜索 30 次/分钟（高频操作）
        rules.add(new RateLimitRule(
                uri -> uri.equals("/api/memory/search"),
                "POST",
                30,
                TimeUnit.MINUTES.toMillis(1),
                "memory-search"
        ));

        // 清空记忆 3 次/天（破坏性操作）
        rules.add(new RateLimitRule(
                uri -> uri.equals("/api/memory/clear"),
                "DELETE",
                3,
                TimeUnit.DAYS.toMillis(1),
                "memory-clear"
        ));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 寻找匹配的规则（使用 stream 确保 effectively final）
        RateLimitRule matchedRule = rules.stream()
                .filter(r -> r.matches(uri) && (r.httpMethod == null
                        || r.httpMethod.equalsIgnoreCase(method)))
                .findFirst()
                .orElse(null);

        if (matchedRule == null) {
            return true; // 无匹配规则，放行
        }

        // 获取用户标识
        String userId = getUserId(request);
        if (userId == null) {
            return true; // 未认证用户由 Security 处理
        }

        String counterKey = userId + ":" + matchedRule.ruleId;
        long now = System.currentTimeMillis();

        Bucket bucket = counters.get(counterKey, k -> new Bucket(now, matchedRule.windowMs));

        synchronized (bucket) {
            // 检查窗口是否过期
            if (now - bucket.windowStart > bucket.windowMs) {
                bucket.windowStart = now;
                bucket.count.set(0);
            }

            int current = bucket.count.incrementAndGet();

            if (current > matchedRule.maxRequests) {
                long resetAt = bucket.windowStart + bucket.windowMs;
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json;charset=UTF-8");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setHeader("X-RateLimit-Reset", String.valueOf(resetAt / 1000));
                response.setHeader("X-RateLimit-Limit", String.valueOf(matchedRule.maxRequests));
                response.getWriter().write("{\"error\":\"请求过于频繁，请稍后再试\"}");
                logger.warn("Rate limit exceeded: user={}, uri={}, rule={}", userId, uri, matchedRule.ruleId);
                return false;
            }

            long resetAt = bucket.windowStart + bucket.windowMs;
            response.setHeader("X-RateLimit-Remaining",
                    String.valueOf(matchedRule.maxRequests - current));
            response.setHeader("X-RateLimit-Reset", String.valueOf(resetAt / 1000));
            response.setHeader("X-RateLimit-Limit", String.valueOf(matchedRule.maxRequests));
        }

        return true;
    }

    private String getUserId(HttpServletRequest request) {
        Object principal = request.getUserPrincipal();
        if (principal != null) {
            return principal.toString();
        }
        // 回退：使用 IP 作为标识
        return request.getRemoteAddr();
    }

    // --- 内部类 ---

    private static class Bucket {
        long windowStart;
        final long windowMs;
        final AtomicInteger count = new AtomicInteger(0);

        Bucket(long start, long windowMs) {
            this.windowStart = start;
            this.windowMs = windowMs;
        }
    }

    @FunctionalInterface
    private interface UriMatcher {
        boolean matches(String uri);
    }

    private static class RateLimitRule {
        final UriMatcher uriMatcher;
        final String httpMethod; // null = 任意方法
        final int maxRequests;
        final long windowMs;
        final String ruleId;

        RateLimitRule(UriMatcher uriMatcher, String httpMethod,
                      int maxRequests, long windowMs, String ruleId) {
            this.uriMatcher = uriMatcher;
            this.httpMethod = httpMethod;
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
            this.ruleId = ruleId;
        }

        boolean matches(String uri) {
            return uriMatcher.matches(uri);
        }
    }
}

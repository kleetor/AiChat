package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.*;
import com.example.aichat.repository.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final RechargeOrderRepository rechargeOrderRepository;
    private final ModelConfigRepository modelConfigRepository;

    public AdminService(UserRepository userRepository,
                        ConversationRepository conversationRepository,
                        ChatMessageRepository chatMessageRepository,
                        TokenUsageRepository tokenUsageRepository,
                        RechargeOrderRepository rechargeOrderRepository,
                        ModelConfigRepository modelConfigRepository) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.tokenUsageRepository = tokenUsageRepository;
        this.rechargeOrderRepository = rechargeOrderRepository;
        this.modelConfigRepository = modelConfigRepository;
    }

    // ========== 仪表盘 ==========
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalConversations", conversationRepository.count());
        stats.put("totalMessages", chatMessageRepository.count());

        BigDecimal revenue = tokenUsageRepository.sumCostBetween(
                LocalDateTime.of(2000, 1, 1, 0, 0),
                LocalDateTime.now());
        stats.put("totalRevenue", revenue != null ? revenue : BigDecimal.ZERO);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        stats.put("todayNewUsers", userRepository.countByCreatedAtBetween(todayStart, todayEnd));
        stats.put("todayMessages", tokenUsageRepository.countTodayMessages(todayStart, todayEnd));
        stats.put("pendingReviews", rechargeOrderRepository.findByReviewStatus("PENDING").size());

        return stats;
    }

    public Map<String, Object> getChartData(int days) {
        Map<String, Object> result = new LinkedHashMap<>();

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);

        List<Object[]> convData = tokenUsageRepository.countConversationsByDateBetween(start, end);
        List<String> dates = new ArrayList<>();
        List<Long> convCounts = new ArrayList<>();

        for (int i = days; i >= 0; i--) {
            dates.add(LocalDate.now().minusDays(i).toString());
            convCounts.add(0L);
        }

        for (Object[] row : convData) {
            String date = row[0] != null ? row[0].toString() : "";
            Long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            int idx = dates.indexOf(date);
            if (idx >= 0) {
                convCounts.set(idx, count);
            }
        }

        result.put("dates", dates);
        result.put("conversationCounts", convCounts);

        List<Object[]> modelData = tokenUsageRepository.sumTokensByModelBetween(start, end);
        List<Map<String, Object>> modelStats = new ArrayList<>();
        long totalTokens = 0;
        for (Object[] row : modelData) {
            String modelName = row[0] != null ? row[0].toString() : "未知";
            Long tokens = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            Long count = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            totalTokens += tokens;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("modelName", modelName);
            m.put("totalTokens", tokens);
            m.put("count", count);
            modelStats.add(m);
        }

        for (Map<String, Object> m : modelStats) {
            Long tokens = (Long) m.get("totalTokens");
            double percentage = totalTokens > 0 ? (tokens * 100.0 / totalTokens) : 0;
            m.put("percentage", Math.round(percentage * 100) / 100.0);
        }

        result.put("modelStats", modelStats);

        return result;
    }

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS =
            java.util.Set.of("id", "username", "email", "createdAt", "balance", "role");

    // ========== 用户列表查询 ==========
    public Page<User> getUsers(String keyword, int page, int size, String sortBy, String order) {
        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = "id";
        }
        Sort sort = "desc".equalsIgnoreCase(order) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        if (keyword != null && !keyword.isEmpty()) {
            return userRepository.searchByKeyword(keyword, pageable);
        }
        return userRepository.findAll(pageable);
    }

    // ========== 模型配置管理 ==========
    @Cacheable(value = "modelConfigs", key = "'adminAll'")
    public List<ModelConfig> getModelConfigs() {
        return modelConfigRepository.findAll();
    }

    @CacheEvict(value = "modelConfigs", allEntries = true)
    public ModelConfig createModelConfig(ModelConfig config) {
        return modelConfigRepository.save(config);
    }

    @CacheEvict(value = "modelConfigs", allEntries = true)
    public ModelConfig updateModelConfig(Long id, ModelConfig config) {
        ModelConfig existing = modelConfigRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("模型配置不存在"));
        if (config.getApiKey() != null) existing.setApiKey(config.getApiKey());
        if (config.getApiUrl() != null) existing.setApiUrl(config.getApiUrl());
        if (config.getModelName() != null) existing.setModelName(config.getModelName());
        if (config.getDisplayName() != null) existing.setDisplayName(config.getDisplayName());
        if (config.getInputTokenPrice() != null) existing.setInputTokenPrice(config.getInputTokenPrice());
        if (config.getOutputTokenPrice() != null) existing.setOutputTokenPrice(config.getOutputTokenPrice());
        return modelConfigRepository.save(existing);
    }

    @CacheEvict(value = "modelConfigs", allEntries = true)
    public void deleteModelConfig(Long id) {
        modelConfigRepository.deleteById(id);
    }

    // ========== 消费记录管理 ==========
    public Page<TokenUsage> getUsageRecords(Long userId, LocalDateTime startDate, LocalDateTime endDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (startDate == null) startDate = LocalDateTime.of(2000, 1, 1, 0, 0);
        if (endDate == null) endDate = LocalDateTime.now();
        return tokenUsageRepository.findByFilters(userId, startDate, endDate, pageable);
    }

    public Map<String, Object> getRevenueStats(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusDays(30).with(LocalTime.MIN);
        if (endDate == null) endDate = LocalDateTime.now();
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRevenue", tokenUsageRepository.sumCostBetween(startDate, endDate));
        stats.put("totalRechargeRevenue", rechargeOrderRepository.sumRevenueBetween(startDate, endDate));
        return stats;
    }

    // ========== 聊天记录管理 ==========
    public Page<Conversation> getConversations(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (userId != null) {
            return conversationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return conversationRepository.findAll(pageable);
    }

    public List<ChatMessage> getConversationMessages(Long conversationId) {
        return chatMessageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
    }
}

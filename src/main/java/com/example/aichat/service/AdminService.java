package com.example.aichat.service;

import com.example.aichat.model.*;
import com.example.aichat.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private TokenUsageRepository tokenUsageRepository;
    @Autowired
    private RechargeOrderRepository rechargeOrderRepository;
    @Autowired
    private ModelConfigRepository modelConfigRepository;
    @Autowired
    private PromptsHubRepository promptsHubRepository;
    @Autowired
    private BillingService billingService;

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

    // ========== 用户管理 ==========
    public Page<User> getUsers(String keyword, int page, int size, String sortBy, String order) {
        Sort sort = "desc".equalsIgnoreCase(order) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        if (keyword != null && !keyword.isEmpty()) {
            return userRepository.searchByKeyword(keyword, pageable);
        }
        return userRepository.findAll(pageable);
    }

    public User getUserDetail(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    public Map<String, Object> getUserStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("conversationCount", conversationRepository.countByUserId(userId));
        stats.put("totalSpent", billingService.getTotalSpent(userId));
        stats.put("totalTokens", billingService.getTotalTokens(userId));
        return stats;
    }

    @Transactional
    public void updateUserBalance(Long userId, BigDecimal amount, String reason, Long reviewerId) {
        billingService.adminRecharge(userId, amount, reason, reviewerId);
    }

    @Transactional
    public User updateUserRole(Long userId, String role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw new RuntimeException("无效的角色");
        }
        user.setRole(role);
        return userRepository.save(user);
    }

    @Transactional
    public User updateUserStatus(Long userId, Boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setEnabled(enabled);
        return userRepository.save(user);
    }

    // ========== 赞助审核 ==========
    public Page<RechargeOrder> getSponsorReviews(String reviewStatus, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (reviewStatus != null && !reviewStatus.isEmpty()) {
            return rechargeOrderRepository.findByReviewStatus(reviewStatus, pageable);
        }
        return rechargeOrderRepository.findAll(pageable);
    }

    @Transactional
    public RechargeOrder approveSponsor(Long orderId, BigDecimal tokens, String comment, Long reviewerId) {
        RechargeOrder order = rechargeOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!"PENDING".equals(order.getReviewStatus())) {
            throw new RuntimeException("该订单已审核过");
        }
        order.setReviewStatus("APPROVED");
        order.setReviewComment(comment);
        order.setReviewerId(reviewerId);
        order.setReviewedAt(LocalDateTime.now());
        rechargeOrderRepository.save(order);

        // 增加用户余额
        billingService.adminRecharge(order.getUserId(), tokens, "赞助审核通过: " + comment, reviewerId);
        return order;
    }

    @Transactional
    public RechargeOrder rejectSponsor(Long orderId, String comment, Long reviewerId) {
        RechargeOrder order = rechargeOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!"PENDING".equals(order.getReviewStatus())) {
            throw new RuntimeException("该订单已审核过");
        }
        order.setReviewStatus("REJECTED");
        order.setReviewComment(comment);
        order.setReviewerId(reviewerId);
        order.setReviewedAt(LocalDateTime.now());
        return rechargeOrderRepository.save(order);
    }

    // ========== 模型配置管理 ==========
    public List<ModelConfig> getModelConfigs() {
        return modelConfigRepository.findAll();
    }

    public ModelConfig createModelConfig(ModelConfig config) {
        return modelConfigRepository.save(config);
    }

    public ModelConfig updateModelConfig(Long id, ModelConfig config) {
        ModelConfig existing = modelConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模型配置不存在"));
        if (config.getApiKey() != null) existing.setApiKey(config.getApiKey());
        if (config.getApiUrl() != null) existing.setApiUrl(config.getApiUrl());
        if (config.getModelName() != null) existing.setModelName(config.getModelName());
        if (config.getDisplayName() != null) existing.setDisplayName(config.getDisplayName());
        if (config.getInputTokenPrice() != null) existing.setInputTokenPrice(config.getInputTokenPrice());
        if (config.getOutputTokenPrice() != null) existing.setOutputTokenPrice(config.getOutputTokenPrice());
        return modelConfigRepository.save(existing);
    }

    public void deleteModelConfig(Long id) {
        modelConfigRepository.deleteById(id);
    }

    // ========== 提示词社区管理 ==========
    public Page<PromptsHub> getPromptsHub(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (keyword != null && !keyword.isEmpty()) {
            return promptsHubRepository.searchByKeyword(keyword, pageable);
        }
        return promptsHubRepository.findAll(pageable);
    }

    public void deletePromptHub(Long id) {
        promptsHubRepository.deleteById(id);
    }

    @Transactional
    public PromptsHub setFeatured(Long id, Boolean featured) {
        PromptsHub prompt = promptsHubRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("提示词不存在"));
        prompt.setFeatured(featured);
        return promptsHubRepository.save(prompt);
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
            // 如果提供了 userId，我们需要自己构建分页
            List<Conversation> conversations = conversationRepository.findByUserIdOrderByCreatedAtDesc(userId);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), conversations.size());
            if (start > conversations.size()) {
                return Page.empty(pageable);
            }
            // 简单实现：返回所有然后截取（对于小量数据可以接受）
            return new org.springframework.data.domain.PageImpl<>(
                    conversations.subList(start, end), pageable, conversations.size());
        }
        return conversationRepository.findAll(pageable);
    }

    public List<ChatMessage> getConversationMessages(Long conversationId) {
        return chatMessageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
    }
}

package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.User;
import com.example.aichat.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final BillingService billingService;

    public AdminUserService(UserRepository userRepository, BillingService billingService) {
        this.userRepository = userRepository;
        this.billingService = billingService;
    }

    public User getUserDetail(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    public Map<String, Object> getUserStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
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
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw BusinessException.badRequest("无效的角色");
        }
        user.setRole(role);
        return userRepository.save(user);
    }

    @Transactional
    public User updateUserStatus(Long userId, Boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        user.setEnabled(enabled);
        return userRepository.save(user);
    }
}

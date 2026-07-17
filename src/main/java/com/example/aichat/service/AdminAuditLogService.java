package com.example.aichat.service;

import com.example.aichat.model.AdminOperationLog;
import com.example.aichat.repository.AdminOperationLogRepository;
import com.example.aichat.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AdminAuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AdminAuditLogService.class);
    private final AdminOperationLogRepository logRepository;
    private final UserRepository userRepository;

    public AdminAuditLogService(AdminOperationLogRepository logRepository,
                                 UserRepository userRepository) {
        this.logRepository = logRepository;
        this.userRepository = userRepository;
    }

    /**
     * 解析管理员用户名
     */
    public String resolveAdminUsername(Long adminId) {
        return userRepository.findById(adminId)
                .map(u -> u.getUsername())
                .orElse("未知(" + adminId + ")");
    }

    /**
     * 异步写入审计日志，不阻塞主业务
     */
    @Async
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void log(Long adminId, String adminUsername, String action,
                    String targetType, Long targetId, String detail, String ipAddress) {
        try {
            AdminOperationLog log = AdminOperationLog.builder()
                    .adminId(adminId)
                    .adminUsername(adminUsername)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .detail(detail)
                    .ipAddress(ipAddress)
                    .createdAt(LocalDateTime.now())
                    .build();
            logRepository.save(log);
            logger.debug("审计日志已写入: admin={}, action={}, target={}:{}",
                    adminUsername, action, targetType, targetId);
        } catch (Exception e) {
            logger.error("审计日志写入失败: admin={}, action={}", adminUsername, action, e);
        }
    }

    /**
     * 查询审计日志
     */
    public Page<AdminOperationLog> query(Long adminId, String action, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (adminId != null) {
            return logRepository.findByAdminIdOrderByCreatedAtDesc(adminId, pageable);
        }
        if (action != null && !action.isEmpty()) {
            return logRepository.findByActionOrderByCreatedAtDesc(action, pageable);
        }
        return logRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * 定时清理 90 天前的日志
     */
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨 3 点
    @Transactional
    public void cleanOldLogs() {
        int deleted = logRepository.deleteOlderThan(LocalDateTime.now().minusDays(90));
        if (deleted > 0) {
            logger.info("已清理 {} 条过期审计日志（90天前）", deleted);
        }
    }

    // ========== 便捷方法 ==========

    public void logBalanceUpdate(Long adminId, String adminUsername, Long targetUserId,
                                  String amount, String reason, String ip) {
        log(adminId, adminUsername, "BALANCE_UPDATE", "USER", targetUserId,
                String.format("{\"amount\":\"%s\",\"reason\":\"%s\"}", amount, reason), ip);
    }

    public void logRoleUpdate(Long adminId, String adminUsername, Long targetUserId,
                                String newRole, String ip) {
        log(adminId, adminUsername, "ROLE_UPDATE", "USER", targetUserId,
                String.format("{\"newRole\":\"%s\"}", newRole), ip);
    }

    public void logUserStatus(Long adminId, String adminUsername, Long targetUserId,
                                boolean enabled, String ip) {
        log(adminId, adminUsername, "USER_STATUS", "USER", targetUserId,
                String.format("{\"enabled\":%s}", enabled), ip);
    }

    public void logSponsorApprove(Long adminId, String adminUsername, Long orderId,
                                    String tokens, String ip) {
        log(adminId, adminUsername, "SPONSOR_APPROVE", "RECHARGE_ORDER", orderId,
                String.format("{\"tokens\":\"%s\"}", tokens), ip);
    }

    public void logSponsorReject(Long adminId, String adminUsername, Long orderId,
                                   String comment, String ip) {
        log(adminId, adminUsername, "SPONSOR_REJECT", "RECHARGE_ORDER", orderId,
                String.format("{\"comment\":\"%s\"}", comment), ip);
    }

    public void logModelCreate(Long adminId, String adminUsername, Long modelId,
                                 String modelName, String ip) {
        log(adminId, adminUsername, "MODEL_CREATE", "MODEL_CONFIG", modelId,
                String.format("{\"modelName\":\"%s\"}", modelName), ip);
    }

    public void logModelUpdate(Long adminId, String adminUsername, Long modelId,
                                 String modelName, String ip) {
        log(adminId, adminUsername, "MODEL_UPDATE", "MODEL_CONFIG", modelId,
                String.format("{\"modelName\":\"%s\"}", modelName), ip);
    }

    public void logModelDelete(Long adminId, String adminUsername, Long modelId, String ip) {
        log(adminId, adminUsername, "MODEL_DELETE", "MODEL_CONFIG", modelId, null, ip);
    }

    public void logRuleCreate(Long adminId, String adminUsername, Long ruleId,
                                String ruleName, String ip) {
        log(adminId, adminUsername, "RULE_CREATE", "SYSTEM_RULE", ruleId,
                String.format("{\"ruleName\":\"%s\"}", ruleName), ip);
    }

    public void logRuleUpdate(Long adminId, String adminUsername, Long ruleId,
                                String ruleName, String ip) {
        log(adminId, adminUsername, "RULE_UPDATE", "SYSTEM_RULE", ruleId,
                String.format("{\"ruleName\":\"%s\"}", ruleName), ip);
    }

    public void logRuleDelete(Long adminId, String adminUsername, Long ruleId,
                                String ruleName, String ip) {
        log(adminId, adminUsername, "RULE_DELETE", "SYSTEM_RULE", ruleId,
                String.format("{\"ruleName\":\"%s\"}", ruleName), ip);
    }

    public void logRuleToggle(Long adminId, String adminUsername, Long ruleId,
                                String ruleName, boolean active, String ip) {
        log(adminId, adminUsername, "RULE_TOGGLE", "SYSTEM_RULE", ruleId,
                String.format("{\"ruleName\":\"%s\",\"active\":%s}", ruleName, active), ip);
    }

    public void logPromptDelete(Long adminId, String adminUsername, Long promptId,
                                  String ip) {
        log(adminId, adminUsername, "PROMPT_DELETE", "PROMPTS_HUB", promptId, null, ip);
    }

    public void logPromptApprove(Long adminId, String adminUsername, Long promptId,
                                   String ip) {
        log(adminId, adminUsername, "PROMPT_APPROVE", "PROMPTS_HUB", promptId, null, ip);
    }

    public void logPromptReject(Long adminId, String adminUsername, Long promptId,
                                  String reason, String ip) {
        log(adminId, adminUsername, "PROMPT_REJECT", "PROMPTS_HUB", promptId,
                String.format("{\"reason\":\"%s\"}", reason), ip);
    }

    public void logPromptUnpublish(Long adminId, String adminUsername, Long promptId,
                                     String ip) {
        log(adminId, adminUsername, "PROMPT_UNPUBLISH", "PROMPTS_HUB", promptId, null, ip);
    }

    public void logPromptFeatured(Long adminId, String adminUsername, Long promptId,
                                    boolean featured, String ip) {
        log(adminId, adminUsername, "PROMPT_FEATURED", "PROMPTS_HUB", promptId,
                String.format("{\"featured\":%s}", featured), ip);
    }

    public void logAdminLogin(Long adminId, String adminUsername, String ip) {
        log(adminId, adminUsername, "ADMIN_LOGIN", "ADMIN", adminId, null, ip);
    }

    public void logAdminLogout(Long adminId, String adminUsername, String ip) {
        log(adminId, adminUsername, "ADMIN_LOGOUT", "ADMIN", adminId, null, ip);
    }
}

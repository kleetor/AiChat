# 安全增强实施计划：安全响应头 + 管理员操作审计日志

## 背景

经过全项目安全审查，确认以下两项安全措施完全缺失，需要补充：

- **安全响应头**：无 CSP、HSTS、X-Frame-Options、X-Content-Type-Options 等
- **管理员操作审计日志**：无任何管理员操作的记录，无法事后追溯

---

## 一、安全响应头

### 1.1 涉及改动

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/main/java/com/example/aichat/config/SecurityConfig.java` | **修改** | `filterChain()` 中增加 `.headers()` 配置 |

### 1.2 具体改动

在 `SecurityConfig.filterChain()` 方法现有的 `.securityContext(...)` 之后、`;` 之前，插入以下配置：

```java
                // ========== 安全响应头 ==========
                .headers(headers -> headers
                        // 点击劫持防护
                        .frameOptions(frame -> frame.deny())
                        // 内容类型嗅探防护
                        .contentTypeOptions(contentTypeOptions -> {})
                        // 强制 HTTPS（生产环境启用）
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000)
                                .includeSubDomains(true))
                        // XSS 防护（关闭浏览器过时 Auditor，由 CSP 接管）
                        .xssProtection(xss -> xss.headerValue(XXssConfig::block))
                        // 内容安全策略
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline' https://unpkg.com https://cdn.jsdelivr.net; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data: blob: https:; " +
                                        "font-src 'self'; " +
                                        "connect-src 'self' https:; " +
                                        "frame-ancestors 'none'"))
                )
```

> **需要新增 import**：无需额外 import。`xss.disable()` 直接可用。
>
> **XSS 防护说明**：Spring Security 6.x 使用 `.xssProtection(xss -> xss.disable())` 移除已废弃的 X-XSS-Protection 响应头（现代浏览器已不支持 XSS Auditor，由 CSP 接管）。

### 1.3 各响应头作用

| 响应头 | 策略值 | 防护能力 |
|--------|--------|----------|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | 强制 HTTPS，防 SSL 剥离中间人攻击 |
| `X-Frame-Options` | `DENY` | 禁止页面被嵌入 iframe，防点击劫持 |
| `X-Content-Type-Options` | `nosniff` | 禁止浏览器 MIME 类型嗅探，防 MIME 混淆攻击 |
| `X-XSS-Protection` | `0` | 关闭已废弃的浏览器 XSS Auditor，由 CSP 接管 |
| `Content-Security-Policy` | 见上方策略 | 限制脚本/样式/图片/字体/连接来源，防 XSS 和数据注入 |
| `Cache-Control` | Spring Security 默认 `no-cache, no-store, max-age=0, must-revalidate` | 防止敏感页面被缓存 |

---

## 二、管理员操作审计日志

### 2.1 涉及改动

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/main/java/com/example/aichat/model/AdminOperationLog.java` | **新建** | 审计日志 JPA 实体 |
| `src/main/java/com/example/aichat/repository/AdminOperationLogRepository.java` | **新建** | 日志仓库接口 |
| `src/main/java/com/example/aichat/service/AdminAuditLogService.java` | **新建** | 日志写入服务 |
| `src/main/java/com/example/aichat/controller/admin/AdminAuditLogController.java` | **新建** | 审计日志查看接口 |
| `src/main/resources/templates/admin.html` | **修改** | 侧边栏 + 内容区增加「操作日志」页面 |
| `src/main/resources/static/admin.js` | **修改** | 增加操作日志页面加载逻辑 |

### 2.2 实体设计 — `AdminOperationLog.java`

```java
package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_operation_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作管理员ID */
    @Column(nullable = false)
    private Long adminId;

    /** 操作管理员用户名（冗余，方便查询时无需联表） */
    @Column(nullable = false, length = 50)
    private String adminUsername;

    /** 操作类型，见下方枚举 */
    @Column(nullable = false, length = 50)
    private String action;

    /** 操作目标实体类型 */
    @Column(nullable = false, length = 50)
    private String targetType;

    /** 操作目标实体ID */
    @Column
    private Long targetId;

    /** 操作详情（JSON 格式，记录变更前后的关键字段） */
    @Column(columnDefinition = "TEXT")
    private String detail;

    /** 操作者 IP */
    @Column(length = 45)
    private String ipAddress;

    /** 操作时间 */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

### 2.3 数据库 DDL

```sql
CREATE TABLE admin_operation_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id        BIGINT      NOT NULL,
    admin_username  VARCHAR(50) NOT NULL,
    action          VARCHAR(50) NOT NULL,
    target_type     VARCHAR(50) NOT NULL,
    target_id       BIGINT      NULL,
    detail          TEXT        NULL,
    ip_address      VARCHAR(45) NULL,
    created_at      DATETIME    NOT NULL,
    INDEX idx_admin_id   (admin_id),
    INDEX idx_action     (action),
    INDEX idx_target     (target_type, target_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.4 Repository — `AdminOperationLogRepository.java`

```java
package com.example.aichat.repository;

import com.example.aichat.model.AdminOperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface AdminOperationLogRepository extends JpaRepository<AdminOperationLog, Long> {

    Page<AdminOperationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AdminOperationLog> findByAdminIdOrderByCreatedAtDesc(Long adminId, Pageable pageable);

    Page<AdminOperationLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM AdminOperationLog a WHERE a.createdAt < :before")
    int deleteOlderThan(LocalDateTime before);
}
```

### 2.5 Service — `AdminAuditLogService.java`

```java
package com.example.aichat.service;

import com.example.aichat.model.AdminOperationLog;
import com.example.aichat.repository.AdminOperationLogRepository;
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

    public AdminAuditLogService(AdminOperationLogRepository logRepository) {
        this.logRepository = logRepository;
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
```

### 2.6 Controller — `AdminAuditLogController.java`

```java
package com.example.aichat.controller.admin;

import com.example.aichat.model.AdminOperationLog;
import com.example.aichat.service.AdminAuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminAuditLogController {

    private final AdminAuditLogService auditLogService;

    public AdminAuditLogController(AdminAuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AdminOperationLog>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String action) {
        return ResponseEntity.ok(auditLogService.query(adminId, action, page, size));
    }
}
```

### 2.7 Controller 层埋点方案

不对现有 Controller 做侵入式修改，而是在各 Admin Controller 的写操作方法的**最后步骤**直接调用 `adminAuditLogService.log*()`。每个需要记录的方法只需加一行调用。

**每个 Controller 需要做的两处改动：**

1. **注入 `AdminAuditLogService`**（构造器注入）
2. **方法签名中添加 `HttpServletRequest request` 参数**，用于获取客户端 IP

**示例 — AdminUserController.updateBalance() 添加审计日志：**

修改前：
```java
@PutMapping("/{id}/balance")
public ResponseEntity<?> updateBalance(
        @PathVariable Long id,
        @RequestBody Map<String, Object> body,
        @RequestAttribute("userId") Long reviewerId) {
    // ... 现有校验逻辑 ...
    adminUserService.updateUserBalance(id, amount, reason, reviewerId);
    return ResponseEntity.ok(Map.of("message", "余额更新成功"));
}
```

修改后：
```java
@PutMapping("/{id}/balance")
public ResponseEntity<?> updateBalance(
        @PathVariable Long id,
        @RequestBody Map<String, Object> body,
        @RequestAttribute("userId") Long reviewerId,
        HttpServletRequest request) {   // 新增：用于获取 IP
    // ... 现有校验逻辑 ...
    adminUserService.updateUserBalance(id, amount, reason, reviewerId);
    // 记录审计日志
    auditLogService.logBalanceUpdate(reviewerId, getAdminUsername(reviewerId), id,
            amount.toString(), reason, request.getRemoteAddr());
    return ResponseEntity.ok(Map.of("message", "余额更新成功"));
}
```

> **关键注意点**：
> - 所有写操作的 Controller 方法都需添加 `HttpServletRequest request` 参数
> - 需要提供 `getAdminUsername(Long adminId)` 辅助方法，通过 `UserRepository.findById(adminId)` 查询用户名
> - `request.getRemoteAddr()` 获取的 IP 在反向代理环境下可能为代理地址，生产环境需配合 `X-Forwarded-For` 头

### 2.8 需要审计的操作清单

| Controller | 方法 | 操作类型 | 目标类型 |
|------------|------|----------|----------|
| `AdminAuthController` | `login` | `ADMIN_LOGIN` | `ADMIN` |
| `AdminAuthController` | `logout` | `ADMIN_LOGOUT` | `ADMIN` |
| `AdminUserController` | `updateBalance` | `BALANCE_UPDATE` | `USER` |
| `AdminUserController` | `updateRole` | `ROLE_UPDATE` | `USER` |
| `AdminUserController` | `updateStatus` | `USER_STATUS` | `USER` |
| `AdminSponsorController` | `approve` | `SPONSOR_APPROVE` | `RECHARGE_ORDER` |
| `AdminSponsorController` | `reject` | `SPONSOR_REJECT` | `RECHARGE_ORDER` |
| `AdminPromptsHubController` | `delete` | `PROMPT_DELETE` | `PROMPTS_HUB` |
| `AdminPromptsHubController` | `approve` | `PROMPT_APPROVE` | `PROMPTS_HUB` |
| `AdminPromptsHubController` | `reject` | `PROMPT_REJECT` | `PROMPTS_HUB` |
| `AdminPromptsHubController` | `unpublish` | `PROMPT_UNPUBLISH` | `PROMPTS_HUB` |
| `AdminPromptsHubController` | `setFeatured` | `PROMPT_FEATURED` | `PROMPTS_HUB` |
| `AdminModelConfigController` | `create` | `MODEL_CREATE` | `MODEL_CONFIG` |
| `AdminModelConfigController` | `update` | `MODEL_UPDATE` | `MODEL_CONFIG` |
| `AdminModelConfigController` | `delete` | `MODEL_DELETE` | `MODEL_CONFIG` |
| `AdminSystemRuleController` | `create` | `RULE_CREATE` | `SYSTEM_RULE` |
| `AdminSystemRuleController` | `update` | `RULE_UPDATE` | `SYSTEM_RULE` |
| `AdminSystemRuleController` | `delete` | `RULE_DELETE` | `SYSTEM_RULE` |
| `AdminSystemRuleController` | `toggle` | `RULE_TOGGLE` | `SYSTEM_RULE` |

### 2.9 前端改动

**admin.html：** 侧边栏增加「操作日志」菜单项

```html
<a data-page="audit-logs" onclick="switchPage('audit-logs', this)">
    <i data-lucide="scroll-text" class="nav-icon"></i> 操作日志
</a>
```

以及对应的内容面板（表格展示：时间、管理员、操作类型、目标、详情、IP）。

**admin.js：** 增加 `loadAuditLogs()` 函数，过滤条件包含管理员ID筛选和操作类型下拉筛选。

### 2.10 异步支持

项目中 `@EnableAsync` 已在 `AichatApplication.java` 中启用，无需额外配置。`AdminAuditLogService.log()` 使用 `@Async` + `@Transactional(propagation = REQUIRES_NEW)` 确保日志写入不阻塞主业务且独立提交。

---

## 三、实施顺序

1. **先做安全响应头**（1 个文件修改，无依赖，10 分钟可完成）
2. **再做审计日志**（按 2.2 → 2.3 → 2.4 → 2.5 → 2.6 → 2.7 → 2.9 顺序，约 2 小时）

> **审计日志实施要点**：
> - 2.7 埋点时，每个 Controller 需注入 `HttpServletRequest` 来获取客户端 IP
> - 各 Controller 需添加 `getAdminUsername(Long)` 辅助方法（如没有现成工具类）
> - admin.js 中已预留 `currentAuditPage` 变量，可直接复用

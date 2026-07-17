package com.example.aichat.controller.admin;

import com.example.aichat.model.User;
import com.example.aichat.service.AdminAuditLogService;
import com.example.aichat.service.AdminService;
import com.example.aichat.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminService adminService;
    private final AdminUserService adminUserService;
    private final AdminAuditLogService auditLogService;

    public AdminUserController(AdminService adminService, AdminUserService adminUserService,
                                AdminAuditLogService auditLogService) {
        this.adminService = adminService;
        this.adminUserService = adminUserService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<Page<User>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String order) {
        return ResponseEntity.ok(adminService.getUsers(keyword, page, size, sortBy, order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserDetail(@PathVariable Long id) {
        User user = adminUserService.getUserDetail(id);
        Map<String, Object> stats = adminUserService.getUserStats(id);
        return ResponseEntity.ok(Map.of("user", user, "stats", stats));
    }

    @PutMapping("/{id}/balance")
    public ResponseEntity<?> updateBalance(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestAttribute("userId") Long reviewerId,
            HttpServletRequest request) {
        Object amountObj = body.get("amount");
        if (amountObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "金额不能为空"));
        }
        BigDecimal amount = new BigDecimal(amountObj.toString());
        String reason = body.getOrDefault("reason", "管理员手动操作").toString();
        adminUserService.updateUserBalance(id, amount, reason, reviewerId);
        // 记录审计日志
        auditLogService.logBalanceUpdate(reviewerId, auditLogService.resolveAdminUsername(reviewerId), id,
                amount.toString(), reason, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "余额更新成功"));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestAttribute("userId") Long adminId,
            HttpServletRequest request) {
        if (adminId.equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "不能修改自己的角色"));
        }
        String role = body.get("role");
        adminUserService.updateUserRole(id, role);
        // 记录审计日志
        auditLogService.logRoleUpdate(adminId, auditLogService.resolveAdminUsername(adminId), id,
                role, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "角色更新成功"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body,
            @RequestAttribute("userId") Long adminId,
            HttpServletRequest request) {
        if (adminId.equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "不能对自己执行此操作"));
        }
        Boolean enabled = body.get("enabled");
        adminUserService.updateUserStatus(id, enabled);
        // 记录审计日志
        auditLogService.logUserStatus(adminId, auditLogService.resolveAdminUsername(adminId), id,
                enabled != null && enabled, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "状态更新成功"));
    }
}

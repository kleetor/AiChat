package com.example.aichat.controller.admin;

import com.example.aichat.model.User;
import com.example.aichat.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Autowired
    private AdminService adminService;

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
        User user = adminService.getUserDetail(id);
        Map<String, Object> stats = adminService.getUserStats(id);
        return ResponseEntity.ok(Map.of("user", user, "stats", stats));
    }

    @PutMapping("/{id}/balance")
    public ResponseEntity<?> updateBalance(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestAttribute("userId") Long reviewerId) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String reason = body.getOrDefault("reason", "管理员手动操作").toString();
        adminService.updateUserBalance(id, amount, reason, reviewerId);
        return ResponseEntity.ok(Map.of("message", "余额更新成功"));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String role = body.get("role");
        adminService.updateUserRole(id, role);
        return ResponseEntity.ok(Map.of("message", "角色更新成功"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        adminService.updateUserStatus(id, enabled);
        return ResponseEntity.ok(Map.of("message", "状态更新成功"));
    }
}

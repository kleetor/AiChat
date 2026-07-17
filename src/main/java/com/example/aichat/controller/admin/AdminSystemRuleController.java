package com.example.aichat.controller.admin;

import com.example.aichat.dto.SystemRuleRequest;
import com.example.aichat.model.SystemRule;
import com.example.aichat.service.AdminAuditLogService;
import com.example.aichat.service.AdminSystemRuleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system-rules")
public class AdminSystemRuleController {

    private final AdminSystemRuleService service;
    private final AdminAuditLogService auditLogService;

    public AdminSystemRuleController(AdminSystemRuleService service,
                                      AdminAuditLogService auditLogService) {
        this.service = service;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<List<SystemRule>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    public ResponseEntity<SystemRule> create(@Valid @RequestBody SystemRuleRequest body,
                                              HttpServletRequest request) {
        SystemRule created = service.createFromRequest(body);
        // 记录审计日志
        Long adminId = (Long) request.getAttribute("userId");
        auditLogService.logRuleCreate(adminId, auditLogService.resolveAdminUsername(adminId), created.getId(),
                created.getName(), request.getRemoteAddr());
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SystemRule> update(@PathVariable Long id, @Valid @RequestBody SystemRuleRequest body,
                                              HttpServletRequest request) {
        SystemRule updated = service.updateFromRequest(id, body);
        // 记录审计日志
        Long adminId = (Long) request.getAttribute("userId");
        auditLogService.logRuleUpdate(adminId, auditLogService.resolveAdminUsername(adminId), updated.getId(),
                updated.getName(), request.getRemoteAddr());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
        service.delete(id);
        // 记录审计日志
        Long adminId = (Long) request.getAttribute("userId");
        auditLogService.logRuleDelete(adminId, auditLogService.resolveAdminUsername(adminId), id,
                "", request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "已删除"));
    }

    /** 切换启用/禁用 */
    @PostMapping("/{id}/toggle")
    public ResponseEntity<SystemRule> toggle(@PathVariable Long id, HttpServletRequest request) {
        SystemRule toggled = service.toggle(id);
        // 记录审计日志
        Long adminId = (Long) request.getAttribute("userId");
        auditLogService.logRuleToggle(adminId, auditLogService.resolveAdminUsername(adminId), toggled.getId(),
                toggled.getName(), toggled.getIsActive(), request.getRemoteAddr());
        return ResponseEntity.ok(toggled);
    }

    /** 批量更新排序 */
    @PutMapping("/sort")
    public ResponseEntity<?> updateSort(@RequestBody List<Map<String, Object>> items) {
        service.updateSort(items);
        return ResponseEntity.ok(Map.of("message", "排序已更新"));
    }
}

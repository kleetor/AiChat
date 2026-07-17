package com.example.aichat.controller.admin;

import com.example.aichat.model.PromptsHub;
import com.example.aichat.service.AdminAuditLogService;
import com.example.aichat.service.AdminPromptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/prompts-hub")
public class AdminPromptsHubController {

    private final AdminPromptService adminPromptService;
    private final AdminAuditLogService auditLogService;

    public AdminPromptsHubController(AdminPromptService adminPromptService,
                                      AdminAuditLogService auditLogService) {
        this.adminPromptService = adminPromptService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<Page<PromptsHub>> getPromptsHub(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(adminPromptService.getPromptsHub(keyword, page, size));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                     @RequestAttribute("userId") Long adminId,
                                     HttpServletRequest request) {
        adminPromptService.deletePromptHub(id);
        // 记录审计日志
        auditLogService.logPromptDelete(adminId, auditLogService.resolveAdminUsername(adminId), id,
                request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "提示词已删除"));
    }

    @PutMapping("/{id}/feature")
    public ResponseEntity<?> setFeatured(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body,
            @RequestAttribute("userId") Long adminId,
            HttpServletRequest request) {
        Boolean featured = body.get("featured");
        adminPromptService.setFeatured(id, featured);
        // 记录审计日志
        auditLogService.logPromptFeatured(adminId, auditLogService.resolveAdminUsername(adminId), id,
                featured != null && featured, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "精选状态已更新"));
    }

    // ======== 审核 ========

    /** 审核队列 */
    @GetMapping("/audit")
    public ResponseEntity<Page<PromptsHub>> getAuditQueue(
            @RequestParam(defaultValue = "pending_review") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminPromptService.getAuditQueue(status, page, size));
    }

    /** 审核通过 */
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id,
                                      @RequestAttribute("userId") Long adminId,
                                      HttpServletRequest request) {
        adminPromptService.approvePrompt(id);
        // 记录审计日志
        auditLogService.logPromptApprove(adminId, auditLogService.resolveAdminUsername(adminId), id,
                request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "已审核通过"));
    }

    /** 审核拒绝 */
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id,
                                     @RequestBody Map<String, String> body,
                                     @RequestAttribute("userId") Long adminId,
                                     HttpServletRequest request) {
        String reason = body.getOrDefault("reason", "");
        adminPromptService.rejectPrompt(id, reason);
        // 记录审计日志
        auditLogService.logPromptReject(adminId, auditLogService.resolveAdminUsername(adminId), id,
                reason, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "已拒绝"));
    }

    /** 下架提示词 */
    @PostMapping("/{id}/unpublish")
    public ResponseEntity<?> unpublish(@PathVariable Long id,
                                        @RequestAttribute("userId") Long adminId,
                                        HttpServletRequest request) {
        adminPromptService.unpublishPrompt(id);
        // 记录审计日志
        auditLogService.logPromptUnpublish(adminId, auditLogService.resolveAdminUsername(adminId), id,
                request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "已下架"));
    }
}

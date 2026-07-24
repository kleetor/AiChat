package com.example.aichat.controller.admin;

import com.example.aichat.model.RechargeOrder;
import com.example.aichat.service.AdminAuditLogService;
import com.example.aichat.service.SponsorReviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/sponsor-reviews")
public class AdminSponsorController {

    private final SponsorReviewService sponsorReviewService;
    private final AdminAuditLogService auditLogService;

    public AdminSponsorController(SponsorReviewService sponsorReviewService,
                                   AdminAuditLogService auditLogService) {
        this.sponsorReviewService = sponsorReviewService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<Page<RechargeOrder>> getReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "PENDING") String status) {
        return ResponseEntity.ok(sponsorReviewService.getSponsorReviews(status, page, size));
    }

    @PutMapping("/{orderId}/approve")
    public ResponseEntity<?> approve(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute("userId") Long reviewerId,
            HttpServletRequest request) {
        BigDecimal tokens;
        try {
            tokens = new BigDecimal(body.get("tokens").toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "金额格式无效"));
        }
        if (tokens.compareTo(new BigDecimal("1000000")) > 0 || tokens.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "金额超出允许范围"));
        }
        String comment = body.getOrDefault("comment", "").toString();
        sponsorReviewService.approveSponsor(orderId, tokens, comment, reviewerId);
        // 记录审计日志
        auditLogService.logSponsorApprove(reviewerId, auditLogService.resolveAdminUsername(reviewerId), orderId,
                tokens.toString(), request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "审核通过"));
    }

    @PutMapping("/{orderId}/reject")
    public ResponseEntity<?> reject(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body,
            @RequestAttribute("userId") Long reviewerId,
            HttpServletRequest request) {
        String comment = body.getOrDefault("comment", "");
        sponsorReviewService.rejectSponsor(orderId, comment, reviewerId);
        // 记录审计日志
        auditLogService.logSponsorReject(reviewerId, auditLogService.resolveAdminUsername(reviewerId), orderId,
                comment, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "已拒绝"));
    }
}

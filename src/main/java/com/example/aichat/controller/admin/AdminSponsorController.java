package com.example.aichat.controller.admin;

import com.example.aichat.model.RechargeOrder;
import com.example.aichat.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/sponsor-reviews")
public class AdminSponsorController {

    @Autowired
    private AdminService adminService;

    @GetMapping
    public ResponseEntity<Page<RechargeOrder>> getReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "PENDING") String status) {
        return ResponseEntity.ok(adminService.getSponsorReviews(status, page, size));
    }

    @PutMapping("/{orderId}/approve")
    public ResponseEntity<?> approve(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute("userId") Long reviewerId) {
        BigDecimal tokens = new BigDecimal(body.get("tokens").toString());
        String comment = body.getOrDefault("comment", "").toString();
        adminService.approveSponsor(orderId, tokens, comment, reviewerId);
        return ResponseEntity.ok(Map.of("message", "审核通过"));
    }

    @PutMapping("/{orderId}/reject")
    public ResponseEntity<?> reject(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body,
            @RequestAttribute("userId") Long reviewerId) {
        String comment = body.getOrDefault("comment", "");
        adminService.rejectSponsor(orderId, comment, reviewerId);
        return ResponseEntity.ok(Map.of("message", "已拒绝"));
    }
}
